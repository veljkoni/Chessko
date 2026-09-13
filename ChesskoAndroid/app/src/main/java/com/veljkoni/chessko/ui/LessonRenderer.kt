package com.veljkoni.chessko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.models.BoxStyle
import com.veljkoni.chessko.models.ChessPiece
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.models.PieceType
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
            is LessonBlock.Heading -> LSectionHeader(lessonIcon(b.icon), b.text, accent)
            is LessonBlock.Paragraph -> LPara(b.text)
            is LessonBlock.Bullets -> for (it in b.items) {
                LBullet(lessonIcon(it.icon), it.title, it.text, colorFor(it.style, accent))
            }
            is LessonBlock.Box -> LBox(lessonIcon(b.icon), b.title, b.text, colorFor(b.style, accent))
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
        Text(text = mdBold(text), fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))
        Spacer(Modifier.height(4.dp))
        Text(text = "— $author", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
    }
}

/**
 * Brojna vrednost figure iz JSON-a u vidljivu oznaku: `"1"` -> „1 bod" / „1 point".
 *
 * Isto sto iOS radi u `L_PieceValueTable` kad `valueLabel` nedostaje — a nedostaje
 * u sva 36 isporucena fajla. Kljucevi su namerno LITERALI unutar `loc()`, da ih
 * `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary` vidi i proveri; sklapanje
 * kljuca iz promenljive bi ga sakrilo od tog testa.
 *
 * „∞" (kralj) se ne prevodi — isto je na svih 8 jezika. Nepoznata vrednost se vraca
 * kakva jeste: bolje gola brojka nego izmisljena jedinica.
 */
internal fun valueUnitLabel(value: String): String = when (value) {
    "1" -> loc("1 bod")
    "3" -> loc("3 boda")
    "5" -> loc("5 boda")
    "9" -> loc("9 bodova")
    else -> value
}

/**
 * Naziv figure iz JSON-a (`"pawn"`, `"rook"`…) u Unicode simbol.
 *
 * Nepoznat naziv vraca `null`, a pozivalac tada crta upozorenje umesto figure —
 * isto kao iOS (`LessonRenderer.pieceType(lessonKey:)`). Tiho crtanje bilo koje
 * figure bi znacilo da tipfeler u sadrzaju niko nikad ne primeti.
 */
internal fun lessonPieceSymbol(lessonKey: String): String? = when (lessonKey) {
    "king" -> ChessPiece(PieceType.KING, PieceColor.WHITE).symbol
    "queen" -> ChessPiece(PieceType.QUEEN, PieceColor.WHITE).symbol
    "rook" -> ChessPiece(PieceType.ROOK, PieceColor.WHITE).symbol
    "bishop" -> ChessPiece(PieceType.BISHOP, PieceColor.WHITE).symbol
    "knight" -> ChessPiece(PieceType.KNIGHT, PieceColor.WHITE).symbol
    "pawn" -> ChessPiece(PieceType.PAWN, PieceColor.WHITE).symbol
    else -> null
}

@Composable
private fun LessonPieceGlyph(piece: String) {
    val symbol = lessonPieceSymbol(piece)
    Text(
        text = symbol ?: "\u26A0",
        fontSize = if (symbol != null) 22.sp else 15.sp,
        color = if (symbol != null) Color.White.copy(alpha = 0.9f) else Color(0xFFE05A5A),
        modifier = Modifier.width(30.dp)
    )
}

@Composable
fun LPieceRow(piece: String, name: String, count: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Figura se crta iz `piece`; bez nje je red samo naziv i broj, a lekcija
        // o figurama upravo trazi da se figura VIDI (iOS crta `PieceImageView`).
        LessonPieceGlyph(piece)
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
                LessonPieceGlyph(r.piece)
                Text(text = r.name, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.weight(1f))
                // `valueLabel` je vec preveden (stize iz JSON-a na tom jeziku), ali
                // ga nijedna isporucena lekcija ne zadaje — pa se u praksi UVEK ide
                // na `valueUnitLabel`. Bez njega tabela pise golo „1" umesto
                // „1 bod" / „1 point", sto iOS crta na istom JSON-u.
                Text(
                    text = r.valueLabel ?: valueUnitLabel(r.value),
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
        // Skriptovana vezba sa `mateIn` ide u `MatePuzzleCard`, koja jedina
        // prikazuje bedz „Mat u N". Bez ovog grananja pet vezbi u isporucenom
        // sadrzaju tiho gubi bedz, a Android se razilazi od iOS-a, koji bira
        // isto (`LessonRenderer.swift:100-112`). `MatePuzzleCard` NE prima
        // `OpeningLine` nego raspakovane parametre, i `fen` joj nije opcion.
        ExerciseKind.SCRIPTED -> if (spec.mateIn != null && spec.startFEN != null) {
            MatePuzzleCard(
                fen = spec.startFEN,
                moves = spec.uciMoves ?: emptyList(),
                title = spec.title,
                hint = spec.hint,
                icon = lessonIcon(spec.icon),
                accentColor = accent,
                mateIn = spec.mateIn,
                // Poruke iz JSON-a moraju da stignu i ovde, inace bi za
                // mat-zadatke izmena sadrzaja tiho nestala.
                solvedMessage = spec.solvedMessage,
                wrongMessage = spec.wrongMessage,
                playingPrompt = spec.playingPrompt
            )
        } else OpeningExerciseCard(
            line = OpeningLine(
                name = spec.title,
                uciMoves = spec.uciMoves ?: emptyList(),
                hint = spec.hint,
                icon = lessonIcon(spec.icon),
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
            icon = lessonIcon(spec.icon),
            color = accent
        )
    }
}

// MARK: - Ikone
//
// JSON lekcija je prenet sa iOS-a i u polju `icon` nosi IME SF SIMBOLA
// („crown.fill", „quote.opening"). Android nema SF simbole, a `LBox`/`LBullet`/
// `LSectionHeader` ikonu crtaju kao obican `Text` — bez ovog prevoda bi se na
// ekranu bukvalno ispisivalo „crown.fill" umesto ikone, na svakom naslovu i u
// svakoj kutiji. Zato prevod stoji OVDE, na granici gde podatak iz JSON-a
// ulazi u UI, a ne u samim komponentama (one primaju ono sto vec treba da
// nacrtaju).
//
// Mapa pokriva svih 49 simbola koji se pojavljuju u `assets/lessons/*.json`
// (provereno pretragom kroz sve fajlove). Nepoznat simbol daje neutralnu tacku
// umesto sirovog imena — losa ikona je bolja od besmislenog teksta usred
// recenice.
private val SYMBOL_TO_EMOJI: Map<String, String> = mapOf(
    "arrow.clockwise" to "🔄",
    "arrow.forward.circle.fill" to "➡️",
    "arrow.left.arrow.right" to "↔️",
    "arrow.triangle.2.circlepath" to "🔄",
    "arrow.up" to "⬆️",
    "arrow.up.and.down" to "↕️",
    "arrow.up.and.down.and.arrow.left.and.right" to "✳️",
    "arrow.up.circle.fill" to "⬆️",
    "arrow.up.left.and.arrow.up.right" to "↗️",
    "arrow.up.right" to "↗️",
    "arrow.up.right.and.arrow.up.left" to "↗️",
    "bolt.fill" to "⚡",
    "book.fill" to "📖",
    "chart.line.uptrend.xyaxis" to "📈",
    "checkmark.circle.fill" to "✅",
    "checkmark.seal.fill" to "✅",
    "circle.fill" to "⚫",
    "crown.fill" to "👑",
    "dot.square.fill" to "⬛",
    "exclamationmark.2" to "‼️",
    "exclamationmark.circle.fill" to "❗",
    "exclamationmark.triangle.fill" to "⚠️",
    "eye.fill" to "👁️",
    "flag.checkered" to "🏁",
    "flag.fill" to "🚩",
    "flame.fill" to "🔥",
    "globe" to "🌐",
    "hand.point.up.left.fill" to "👆",
    "heart.fill" to "❤️",
    "info.circle.fill" to "ℹ️",
    // Svih pet pojava je vezano za skakaca (L-putanja) — otud konj, ne dzojstik.
    "l.joystick.fill" to "🐴",
    "link" to "🔗",
    "minus.circle.fill" to "➖",
    "person.2.fill" to "👥",
    "person.fill" to "👤",
    "quote.opening" to "💬",
    // „rectangle.portrait" je na iOS-u top, „rhombus" lovac — provereno po
    // naslovima svih pojava, ne po imenu simbola.
    "rectangle.portrait.fill" to "🏰",
    "rhombus.fill" to "📐",
    "ruler.fill" to "📏",
    "scalemass.fill" to "⚖️",
    "shield.fill" to "🛡️",
    "square.grid.2x2.fill" to "🔲",
    "square.grid.3x3.fill" to "♟️",
    "star.fill" to "⭐",
    "text.book.closed.fill" to "📚",
    "trophy.fill" to "🏆",
    // Motiv „viljuska" — iOS je za njega uzeo bas „tuningfork".
    "tuningfork" to "🍴",
    "xmark.circle.fill" to "❌",
    "xmark.shield.fill" to "❌"
)

/// Emoji za ime SF simbola iz JSON-a. Ako sadrzaj vec nosi emoji (nema tacke u
/// imenu), prosledjuje se nepromenjen — da rucno pisana lekcija ne mora da zna
/// za SF imena.
internal fun lessonIcon(symbol: String): String =
    SYMBOL_TO_EMOJI[symbol] ?: if ('.' in symbol || symbol.isEmpty()) "•" else symbol

// MARK: - Markdown
//
// JSON lekcija je prenet sa iOS-a, gde se telo bloka crta preko
// `AttributedString` i podebljanje se pise kao `**ovako**`. Compose `Text` ne
// zna za Markdown, pa bi bez ovoga na ekranu pisalo bukvalno „**64 polja**" —
// 307 takvih mesta u 36 fajlova, u svakoj lekciji. Podrzano je samo `**`;
// sadrzaj drugu sintaksu ne koristi (provereno pretragom kroz sve fajlove).
internal fun mdBold(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    var bold = false
    while (i <= text.length) {
        val next = text.indexOf("**", i)
        if (next < 0) {
            appendMaybeBold(text.substring(i), bold)
            break
        }
        appendMaybeBold(text.substring(i, next), bold)
        bold = !bold
        i = next + 2
    }
}

private fun AnnotatedString.Builder.appendMaybeBold(chunk: String, bold: Boolean) {
    if (chunk.isEmpty()) return
    if (bold) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(chunk) }
    } else {
        append(chunk)
    }
}
