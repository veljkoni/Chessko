package com.veljkoni.chessko.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.veljkoni.chessko.ui.theme.DS

// MARK: - Celoekranska odredista (spec § 4)
//
// Analiza, Podesavanja i Sahovski sat su do dizajnerskog pregleda bili
// `Dialog(usePlatformDefaultWidth = false)` preko glavnog `Scaffold`-a. Prozor
// dijaloga ne pokriva sistemske trake, pa se ispod sadrzaja videla donja
// navigacija (preseceni nazivi tabova) i siva zavesa preko statusne trake
// (snimci 02, 18, 20, 52, 96, 101, 106, 111).
//
// Spec § 4: „Najbolje: zasebna odredista navigacije bez donje trake." Projekat
// nema `androidx.navigation` (nova zavisnost je zabranjena), pa je odrediste
// ovde SLOJ preko celog prozora aktivnosti:
//
//   * `FullScreenHostLayout` stoji u `MainActivity` oko `Scaffold`-a, dakle IZNAD
//     donje trake i ispod sistemskih traka (aktivnost je edge-to-edge).
//   * `FullScreenDestination { … }` je API za pozivaoca — isti oblik kao
//     `Dialog(onDismissRequest) { … }`, pa se zamena svodi na promenu imena.
//     Radi sa BILO KOJE dubine stabla (npr. `StepGameView` unutar taba Put):
//     sadrzaj se registruje u host i crta se na nivou hosta, preko svega.
//   * Sistemsko „nazad" zatvara gornje odrediste (`BackHandler`).
//   * Sadrzaj dobija `WindowInsets.safeDrawing` padding (inseti se pri tom
//     TROSE, pa `TopAppBar` unutar sadrzaja ne dodaje statusnu traku drugi put);
//     podloga ide ivica-do-ivice ispod sistemskih traka.
//   * Ikone sistemskih traka se biraju po svetlini podloge (svetla podloga →
//     tamne ikone) dok je odrediste otvoreno, i vracaju se na zatecene posle.
//     Bez toga crna podloga sata u svetloj temi nosi crne (nevidljive) ikone.
//
// Za kasnije taskove (6 Analiza, 9 Sat, 10 Podesavanja): ekran se ne pravi
// kao dijalog. Pozivalac drzi `var show by remember { mutableStateOf(false) }`
// i pise `if (show) FullScreenDestination(onDismiss = { show = false }) { Ekran(...) }`.
// Ekran crta svoje zaglavlje (naslov + „Zatvori"/„Gotovo") i NE dodaje
// sopstvene insete — to radi kontejner. Ekran koji mora da crta ispod
// sistemskih traka (npr. polovine sata do ivice) prosledjuje
// `applySafeDrawingInsets = false` i sam postavlja insete svom sadrzaju.
// Sadrzaj odredista se komponuje na mestu HOSTA, ne pozivaoca: ne vidi
// `CompositionLocal`-e koje pozivalac postavi oko sebe (npr. `LocalContentColor`,
// `LocalTextStyle` iz okolnog `Surface`/`Button`-a). Boje i stil teksta zato
// zadaj eksplicitno (`DS.*`, `Type.*`) — ne oslanjaj se na nasledjene.

/** Stek otvorenih celoekranskih odredista. Pravi se jednom, u `MainActivity`. */
class FullScreenHost {
    internal val entries = mutableStateListOf<FullScreenEntry>()

    /** Da li je bar jedno odrediste otvoreno (npr. za sakrivanje sadrzaja od TalkBack-a). */
    val isShowing: Boolean get() = entries.isNotEmpty()
}

internal class FullScreenEntry(
    val onDismiss: State<() -> Unit>,
    val background: State<Color>,
    val applyInsets: State<Boolean>,
    val content: State<@Composable () -> Unit>
)

val LocalFullScreenHost = staticCompositionLocalOf<FullScreenHost?> { null }

/**
 * Omotac glavnog sadrzaja aktivnosti. Sve sto `content` otvori kroz
 * [FullScreenDestination] crta se preko `content`-a, preko celog prozora.
 */
@Composable
fun FullScreenHostLayout(
    host: FullScreenHost = remember { FullScreenHost() },
    content: @Composable () -> Unit
) {
    // Host je dostupan i SADRZAJU odredista, pa odrediste moze da otvori sledece
    // (npr. buduci podekran Podesavanja) bez pada na rezervni dijalog.
    CompositionLocalProvider(LocalFullScreenHost provides host) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Dok je odrediste otvoreno, sadrzaj ispod ostaje u kompoziciji
                    // (stanje partije, skrol), ali ga citac ekrana ne sme citati.
                    .then(if (host.isShowing) Modifier.clearAndSetSemantics { } else Modifier)
            ) {
                content()
            }
            val top = host.entries.lastOrNull()
            host.entries.forEach { entry ->
                key(entry) {
                    FullScreenSurface(entry = entry, isTop = entry === top)
                }
            }
        }
    }
}

/**
 * Celoekransko odrediste bez donje trake (spec § 4). Vidi komentar na vrhu fajla.
 *
 * @param background podloga ivica-do-ivice, i ispod sistemskih traka.
 * @param applySafeDrawingInsets `false` samo za ekran koji sam postavlja insete.
 */
@Composable
fun FullScreenDestination(
    onDismiss: () -> Unit,
    background: Color = DS.ground,
    applySafeDrawingInsets: Boolean = true,
    content: @Composable () -> Unit
) {
    val dismissState = rememberUpdatedState(onDismiss)
    val backgroundState = rememberUpdatedState(background)
    val insetsState = rememberUpdatedState(applySafeDrawingInsets)
    val contentState = rememberUpdatedState(content)

    val host = LocalFullScreenHost.current
    if (host == null) {
        // Bez hosta (npr. izolovan Compose test): isti izgled kroz dijalog preko
        // celog prozora — rezervni put koji spec § 4 navodi.
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            FullScreenFrame(background, applySafeDrawingInsets, content)
        }
        return
    }

    val entry = remember { FullScreenEntry(dismissState, backgroundState, insetsState, contentState) }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

@Composable
private fun FullScreenSurface(entry: FullScreenEntry, isTop: Boolean) {
    BackHandler(enabled = isTop) { entry.onDismiss.value() }
    if (isTop) SystemBarAppearance(lightBackground = entry.background.value.luminance() > 0.5f)
    FullScreenFrame(entry.background.value, entry.applyInsets.value, entry.content.value)
}

@Composable
private fun FullScreenFrame(
    background: Color,
    applyInsets: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            // Dodir ne sme da prodje do `Scaffold`-a ispod: cvor koji prima
            // pokazivac je dovoljan da hit-test stane na ovom sloju.
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }
            .then(if (applyInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
    ) {
        content()
    }
}

/** Ikone statusne i navigacione trake prate podlogu odredista; vraca zatecene pri zatvaranju. */
@Composable
private fun SystemBarAppearance(lightBackground: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, lightBackground) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNav = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = lightBackground
            controller.isAppearanceLightNavigationBars = lightBackground
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNav
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
