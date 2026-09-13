package com.veljkoni.chessko.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * `dynamicColor` je UKLONJEN, namerno.
 *
 * Zatecena verzija je bila Android Studio sablon sa `dynamicColor = true`, koji
 * na Androidu 12+ vuce boje sa KORISNIKOVE TAPETE. Za aplikaciju ciji spec
 * (5.6) trazi jedan fiksan akcent to nije funkcija nego greska — akcent bi se
 * menjao sa pozadinom telefona.
 *
 * `MaterialTheme.colorScheme` se i dalje popunjava, jer ga koriste zatecene
 * Material komponente (`Switch`, `Slider`, `Card`); bez toga bi one ostale
 * ljubicaste iz sablona.
 *
 * **Popunjeno je 13 od ~30 M3 uloga, ne samo prvobitnih 8** — izmereno koje
 * uloge komponente koje aplikacija STVARNO koristi (`Button` 33, `TextButton`
 * 13, `HorizontalDivider` 7, `Surface` 5, `AlertDialog` 4,
 * `CircularProgressIndicator` 3, `Card` 2, `Switch` 1, `Scaffold` 1) citaju iz
 * svojih `*Defaults` po podrazumevanoj vrednosti (izvor: M3 1.4.0
 * `material3-android-1.4.0-sources.jar`, `ColorScheme.kt`/`*Tokens.kt`).
 * Danas je svaki poziv ovih komponenti i dalje zakucao sopstvenu boju
 * (`Color(0xFF...)`) pa nijedna od ovih uloga jos ne dopire do ekrana — ali
 * Taskovi 2-6 bas te zakucane boje uklanjaju, i cim to urade, svaka
 * nepopunjena uloga bi procurela kao M3 baseline ljubicasta. Popravka je
 * odradjena OVDE, ne u Task-u 6, da se izbegne vracanje u fajl koji je vec
 * proglasen gotovim.
 *
 *   - `onSurfaceVariant` -> `inkMuted` (Button/TextButton disabled label text;
 *     AlertDialog telo teksta). Isti par kontrasta kao vec testirani
 *     `inkMuted`/`surface` u `ContrastTest.textOnBackgroundsMeetsAA` (>= 4.5 u
 *     obe teme) — `surfaceContainerHigh` je dole mapiran BAS na `surface`, pa
 *     je ovo bit-za-bit ista provera, ne nova.
 *   - `outlineVariant` -> `line` (HorizontalDivider). Isti token kao `outline`
 *     jer `ChesskoColors` ima samo jednu liniju/ivicu — nema odvojenog "suptilnijeg"
 *     tona za drugu M3 varijantu.
 *   - `surfaceContainerHigh` -> `surface` (AlertDialog pozadina dijaloga) i
 *     `surfaceContainerLow` -> `surface` (Card podrazumevana pozadina) — obe na
 *     isti token jer `ChesskoColors` nema stepenovanu skalu elevacije; dijalog
 *     i kartica su u ovom sistemu ista "izdignuta" povrsina.
 *   - `surfaceContainerHighest` -> `fill` (Switch `uncheckedTrackColor`,
 *     najbliza "izdignuta" povrsina iznad `surface` koju sistem ima).
 *
 * NAMERNO ostaju na M3 baseline-u (ljubicasti sablon): `secondary`/`tertiary`
 * i njihovi `on*`/`*Container` parovi, `errorContainer`/`onErrorContainer`,
 * `inverse*`, `surfaceBright`/`surfaceDim`, `*Fixed*`, `scrim`. Nijedna od 9
 * gore pobrojanih komponenti ih ne cita u kod-putanji koju aplikacija stvarno
 * koristi (npr. `AlertDialog`/`Switch` imaju `icon`/`thumbContent` slotove
 * koji bi citali `secondary`/`onPrimaryContainer`, ali se nijedan poziv u
 * kodu njima ne koristi — provereno grep-om). `surfaceTint` ostaje na
 * podrazumevanom `= primary` iz M3 potpisa (vec je `accent`, bez dodatnog rada).
 * Ako neki od ovih ikad postane stvarno citan (npr. dodavanje ikone u
 * AlertDialog), treba mu tada dodati token — ne unapred nagadjati.
 */
@Composable
fun ChesskoTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            error = colors.danger, outline = colors.line,
            onSurfaceVariant = colors.inkMuted, outlineVariant = colors.line,
            surfaceContainerHigh = colors.surface, surfaceContainerLow = colors.surface,
            surfaceContainerHighest = colors.fill
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.ground, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            error = colors.danger, outline = colors.line,
            onSurfaceVariant = colors.inkMuted, outlineVariant = colors.line,
            surfaceContainerHigh = colors.surface, surfaceContainerLow = colors.surface,
            surfaceContainerHighest = colors.fill
        )
    }

    CompositionLocalProvider(LocalChesskoColors provides colors) {
        MaterialTheme(colorScheme = material, typography = Typography, content = content)
    }
}
