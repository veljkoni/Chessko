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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.logic.Loc
import com.veljkoni.chessko.logic.LessonRepository
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF
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

/// Boja lekcije je jedina stvar koja je ostala u kodu — JSON je ne nosi.
/// Nepoznat id (nova lekcija bez unosa ovde) dobija podrazumevani akcent umesto
/// da bude nevidljiv ili da obori ekran.
internal fun accentFor(id: String): Color = when (id) {
    "board-and-pieces" -> Color(0xFF3B82F6)
    "notation" -> Color(0xFF8B5CF6)
    "openings" -> Color(0xFF10B981)
    "tactics" -> Color(0xFF06B6D4)
    "middlegame" -> Color(0xFFF59E0B)
    "endgame" -> Color(0xFFEF4444)
    else -> Color(0xFF3B82F6)
}

@Composable
fun LessonDetailView(
    lessonId: String,
    onClose: () -> Unit,
    onComplete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repo = remember { LessonRepository(context) }
    // Sopstveni model po ekranu — isti obrazac kao Faza 4a na iOS-u:
    // `LessonExplorer` unutar lekcije 1 treba sopstveno stanje, ne deljeno sa
    // ostatkom aplikacije.
    val explorerViewModel = remember { LearnViewModel() }

    // NIJE `Loc.getLanguage()`: on za kineski vraca „zh", a fajl se zove
    // `.zh-Hans.json`. Sa pogresnim kodom bi kineski korisnik tiho dobio
    // engleski — bez pada i bez poruke.
    val lang = Loc.fileLanguageCode()

    val doc = remember(lessonId, lang) { repo.lesson(lessonId, lang) }
    val accent = accentFor(lessonId)
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
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "◀ " + loc("Lekcije"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                    color = Color.White.copy(alpha = 0.6f),
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
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = doc.subtitle,
                            color = Color.White.copy(alpha = 0.6f),
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
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            Text(
                                text = loc("Završi korak"),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
