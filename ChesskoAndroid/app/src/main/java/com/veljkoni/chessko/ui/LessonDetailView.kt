package com.veljkoni.chessko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.LessonRepository
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.viewmodels.LearnViewModel

// MARK: - Detalj lekcije
//
// Izdvojeno iz `LearnView.kt` (Faza 6c, Task 5): Put mora da otvori lekciju
// BEZ spiska koji je stajao ispred nje u `LearnView`. Sadrzaj je SELJEN, ne
// prepisan — isti header, ista traka nazad, isti `LessonBlocks` poziv.
//
// `onComplete` je jedina razlika u odnosu na staro ponasanje: dugme potvrde
// na dnu koje se vidi SAMO kad je lekcija otvorena kao korak Puta. `null`
// znaci da je lekcija otvorena iz obicnog spiska (`LearnView`), gde nema sta
// da se "zavrsi".

// `accentFor(id)` (sest boja, jedna po lekciji) je OBRISANA u Fazi 6d-2, Task 2.
// iOS je tacno ovo uklonio u Fazi 1: „per-lekcijske boje svedene na jedan
// akcent, a boja zadrzana samo tamo gde nosi znacenje" — spec 5.6 trazi jedan
// uzdrzan akcent, ne sest. Jedino pozivno mesto sada cita `DS.accent` direktno.

@Composable
fun LessonDetailView(
    lessonId: String,
    onClose: () -> Unit,
    onComplete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repo = remember { LessonRepository(context) }
    // Sopstveni model po ekranu, NE isti obrazac kao iOS: tamo jedan
    // `LearnViewModel` zivi u `ContentView`-u i ubacuje se kroz `@Environment`
    // (`Chessko/Views/PathView.swift:20-23`, `Chessko/Views/LessonRenderer.swift:222-228`).
    // Lokalna instanca je ovde svesno drugaciji izbor (jednostavnije, bez
    // ekvivalenta @Environment-u u ovom Compose kodu) — ostaje kako jeste, ali
    // komentar ne sme da tvrdi paritet koji ne postoji.
    val explorerViewModel = remember { LearnViewModel() }

    // NIJE `Loc.getLanguage()`: on za kineski vraca „zh", a fajl se zove
    // `.zh-Hans.json`. Sa pogresnim kodom bi kineski korisnik tiho dobio
    // engleski — bez pada i bez poruke.
    val lang = Loc.fileLanguageCode()

    val doc = remember(lessonId, lang) { repo.lesson(lessonId, lang) }
    val accent = DS.accent
    val number = remember(lessonId) {
        repo.discoveredLessonIds().indexOf(lessonId).let { if (it >= 0) it + 1 else null }
    }

    // `padding(16.dp)` je ranije davao `LearnView`-ov spoljni `Box`, deljen sa
    // spiskom lekcija. Ovaj ekran sada zivi i van `LearnView`-a (otvara ga
    // `PathView`, bez ijednog Box-a oko sebe), pa marginu mora da nosi SAM —
    // inace sadrzaj (i tabla) idu ivica-do-ivice ekrana kad se otvori iz Puta.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back Button Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    // Isti par kao `StepGameView`/`OpponentCard`: providna bela
                    // podloga na neutralnom ekranu -> `DS.fill` + `DS.ink`
                    // (pokriveno sa `textOnFillMeetsAA`).
                    containerColor = DS.fill,
                    contentColor = DS.ink
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Dugme vodi na Put (spisak lekcija je ova grana obrisala -- vidi
                // header komentar fajla), pa mora i da ga IMENUJE. Postojeci kljuc
                // "Put" (vec koriscen u `PathView`/`StepPracticeView`), ne "Lekcije".
                Text(text = "◀ " + loc("Put"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Scrollable Lesson Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (doc == null) {
                // Kartica postoji samo ako se lekcija vec jednom ucitala,
                // pa je ovo prakticno nedostizno — ali tiha praznina bi
                // bila gora od recenice.
                Text(
                    text = loc("Lekcija nije dostupna."),
                    // Direktno na `DS.ground` (skrol nema Card ni Surface iza ovog
                    // teksta) — isti par kao `inkMuted`/`ground` u `PathView`.
                    color = DS.inkMuted,
                    fontSize = 13.sp
                )
            } else {
                // Lesson Main Title Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = lessonIcon(doc.icon), fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        if (number != null) {
                            Text(
                                text = locF("Lekcija %d", number),
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = doc.title,
                            color = DS.ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = doc.subtitle,
                            color = DS.inkMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                LessonBlocks(doc.blocks, accent, explorerViewModel)

                // Vidi se SAMO kad je lekcija otvorena kao korak Puta.
                // `onComplete` (iz `PathView`) i zavrsava korak i vraca listu —
                // spec 5.1 trazi da se korak zavrsi tek kad korisnik dodje do
                // kraja lekcije i POTVRDI, ne samim otvaranjem.
                if (onComplete != null) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(color = DS.line)
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            Text(
                                text = loc("Završi korak"),
                                fontWeight = FontWeight.SemiBold,
                                // Tekst NA `accent` podlozi -> `DS.onAccent`, ne
                                // `DS.ink`/bela. `accent` menja svetlinu izmedju
                                // tema (`DS.onAccent` doc-komentar u
                                // `DesignSystem.kt`); bela bi u tamnoj temi pala
                                // na 2,6:1 (ista greska koju je iOS vec pravio).
                                color = DS.onAccent
                            )
                        }
                    }
                }
            }
        }
    }
}
