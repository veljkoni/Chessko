package com.veljkoni.chessko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.models.BoxStyle
import com.veljkoni.chessko.models.ExerciseKind
import com.veljkoni.chessko.models.LessonBlock
import com.veljkoni.chessko.models.PieceValueRow
import com.veljkoni.chessko.viewmodels.LearnViewModel

// MARK: - Lesson Renderer
//
// JEDINO mesto koje zna kako se blok crta. Nov tip bloka = jedna grana ovde i
// jedan `case` u `LessonBlock`.
//
// Tekst iz JSON-a stize VEC PREVEDEN i NE ide kroz `loc()`. Kroz `loc()` ide
// samo hrom koji renderer sam dodaje (podrazumevane poruke vezbi, poruka da
// interaktivna tabla nije dostupna).

@Composable
fun LessonBlocks(
    blocks: List<LessonBlock>,
    accent: Color,
    explorerViewModel: LearnViewModel
) {
    for (b in blocks) {
        when (b) {
            is LessonBlock.Heading -> LSectionHeader(b.icon, b.text, accent)
            is LessonBlock.Paragraph -> LPara(b.text)
            is LessonBlock.Bullets -> for (it in b.items) {
                LBullet(it.icon, it.title, it.text, colorFor(it.style, accent))
            }
            is LessonBlock.Box -> LBox(b.icon, b.title, b.text, colorFor(b.style, accent))
            is LessonBlock.Quote -> LQuote(b.text, b.author)
            is LessonBlock.PieceRow -> LPieceRow(b.piece, b.name, b.count, accent)
            is LessonBlock.NumberedRule -> LNumberedRule(b.number, b.title, b.text, accent)
            is LessonBlock.PieceValueTable -> LPieceValueTable(b.rows, accent)
            is LessonBlock.Board -> LStaticBoard(b.fen, b.caption, b.interactive)
            is LessonBlock.Explorer -> LExplorer(explorerViewModel, accent)
            is LessonBlock.Exercise -> LExercise(b.spec, accent)
            is LessonBlock.Divider -> HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}

/// `null` znaci „koristi akcent lekcije". `WARNING` je jedini koji namerno
/// izlazi iz akcenta — nosi znacenje, nije ukras.
private fun colorFor(style: BoxStyle?, accent: Color): Color = when (style) {
    null -> accent
    BoxStyle.INFO -> accent
    BoxStyle.RULE -> Color(0xFFE0B252)
    BoxStyle.WARNING -> Color(0xFFF2857A)
}

@Composable
private fun LQuote(text: String, author: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = text, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))
        Spacer(Modifier.height(4.dp))
        Text(text = "— $author", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
fun LPieceRow(piece: String, name: String, count: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // `name` i `count` dolaze iz JSON-a vec prevedeni — NE kroz `loc()`.
        Text(text = name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = accent)
        Spacer(Modifier.weight(1f))
        Text(text = count, fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
fun LPieceValueTable(rows: List<PieceValueRow>, accent: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        for (r in rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = r.name, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.weight(1f))
                // `valueLabel` ako postoji, inace gola vrednost. NE sklapa se
                // „$value bodova" u kodu: srpska mnozina se ne poklapa ni sa
                // jednim drugim jezikom, a `value` je string jer kralj nosi „∞".
                Text(
                    text = r.valueLabel ?: r.value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun LStaticBoard(fen: String, caption: String, interactive: Boolean) {
    val state = remember(fen) { com.veljkoni.chessko.models.GameState.fromFEN(fen) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (state == null) {
            // Pokvaren FEN u sadrzaju mora da se VIDI, ne da ostavi prazninu.
            Text(text = "⚠︎ $fen", fontSize = 13.sp, color = Color(0xFFF2857A))
        } else {
            // POTPIS JE PROVEREN uz stvarni `BoardView` (`ui/BoardView.kt`):
            // prima `board`, ne `gameState`, i `onTap`, ne `onSquareClick`.
            // `BoardTheme.CLASSIC`/`PieceStyle.CLASSIC` su isto ono sto
            // `LearnView` vec prosledjuje na sva tri mesta (linije 632, 1029, 1132
            // pre izdvajanja `PieceExplorer` u Step 3; danas
            // `PieceExplorer`/`OpeningExerciseCard`/`MateExerciseCard`) —
            // lekcijska tabla namerno NE prati korisnikovu temu.
            BoardView(
                board = state.board,
                isFlipped = false,
                selectedPosition = null,
                legalMoves = emptyList(),
                lastMove = null,
                boardTheme = BoardTheme.CLASSIC,
                pieceStyle = PieceStyle.CLASSIC,
                onTap = {}
            )
        }
        if (caption.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(text = caption, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        if (interactive) {
            // Interaktivna tabla u lekciji jos ne postoji ni na iOS-u. Umesto
            // tihe razlike izmedju platformi, kaze se sta fali.
            Text(text = loc("Interaktivna tabla još nije dostupna."),
                 fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun LExplorer(viewModel: LearnViewModel, accent: Color) {
    // `PieceExplorer` je izdvojen iz `Lesson1Content` u `LearnView.kt` (Faza 6b,
    // Task 3, Step 3) BEZ izmene izgleda. Prima `accent` (umesto `LessonInfo`,
    // koji ovde ne postoji) — ista vrednost koju je pre izdvajanja nosio
    // `info.accentColor`.
    PieceExplorer(viewModel, accent)
}

@Composable
private fun LExercise(spec: com.veljkoni.chessko.models.ExerciseSpec, accent: Color) {
    when (spec.kind) {
        ExerciseKind.SCRIPTED -> OpeningExerciseCard(
            line = OpeningLine(
                name = spec.title,
                uciMoves = spec.uciMoves ?: emptyList(),
                hint = spec.hint,
                icon = spec.icon,
                accentColor = accent,
                // Podrazumevane poruke IDU kroz `loc()` — to je hrom, ne sadrzaj.
                solvedMessage = spec.solvedMessage ?: loc("Bravo! Otvaranje savladano! ✓"),
                wrongMessage = spec.wrongMessage ?: loc("Pogrešan potez — pokušaj ponovo."),
                playingPrompt = spec.playingPrompt,
                startFEN = spec.startFEN
            )
        )
        // POTPIS PROVEREN: `MateExerciseCard(fen, title, hint, icon, color)` —
        // `fen` je PRVI parametar i zove se `fen`, ne `startFEN`; boja je `color`.
        ExerciseKind.VS_ENGINE -> MateExerciseCard(
            fen = spec.startFEN ?: "",
            title = spec.title,
            hint = spec.hint,
            icon = spec.icon,
            color = accent
        )
    }
}
