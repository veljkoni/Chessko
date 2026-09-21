package com.veljkoni.chessko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import com.veljkoni.chessko.ui.theme.DS
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
            is LessonBlock.Heading -> LSectionHeader(lessonGlyph(b.icon), b.text, accent)
            is LessonBlock.Paragraph -> LPara(b.text)
            is LessonBlock.Bullets -> for (it in b.items) {
                LBullet(lessonGlyph(it.icon), it.title, it.text, colorFor(it.style, accent))
            }
            is LessonBlock.Box -> LBox(lessonGlyph(b.icon), b.title, b.text, colorFor(b.style, accent))
            is LessonBlock.Quote -> LQuote(b.text, b.author)
            is LessonBlock.PieceRow -> LPieceRow(b.piece, b.name, b.count, accent)
            is LessonBlock.NumberedRule -> LNumberedRule(b.number, b.title, b.text, accent)
            is LessonBlock.PieceValueTable -> LPieceValueTable(b.rows, accent)
            is LessonBlock.Board -> LStaticBoard(b.fen, b.caption, b.interactive)
            is LessonBlock.Explorer -> LExplorer(explorerViewModel, accent)
            is LessonBlock.Exercise -> LExercise(b.spec, accent)
            is LessonBlock.Divider -> HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = DS.line
            )
        }
    }
}

/// `null` znaci „koristi akcent lekcije". `RULE` i `WARNING` su jedina dva koja
/// namerno izlaze iz akcenta — zlatno pravilo i upozorenje NOSE ZNACENJE, nisu
/// ukras (CLAUDE.md: „Dve stvari koje nose znacenje i ne idu na akcent").
/// Podloga iza oba je `DS.ground` (kutije crtaju sopstvenu providnu tintu preko
/// njega, ne opipljivu Card/Surface) — provereno u `ContrastTest.lessonBoxStylesMeetAA`.
@Composable
@ReadOnlyComposable
private fun colorFor(style: BoxStyle?, accent: Color): Color = when (style) {
    null -> accent
    BoxStyle.INFO -> accent
    BoxStyle.RULE -> DS.warning
    BoxStyle.WARNING -> DS.danger
}

@Composable
private fun LQuote(text: String, author: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = mdBold(text), fontSize = 15.sp, color = DS.ink)
        Spacer(Modifier.height(4.dp))
        Text(text = "— $author", fontSize = 13.sp, color = DS.inkMuted)
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
        // Nepoznat naziv figure daje `DS.danger`, ne ukras -- vidljiva poruka o
        // pokvarenom sadrzaju JSON-a (CLAUDE.md, „Android cita isti JSON").
        color = if (symbol != null) DS.ink else DS.danger,
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
        // IZUZETAK od pravila „zateceno 0,75–0,9 → `ink`": zateceno je bilo
        // `White@0,75`, ali iOS `L_PieceRow` daje kolicini `.secondary`
        // (`Chessko/Views/LessonRenderer.swift:474`) — otvoreno i provereno. `name`
        // levo je `accent`; da je i kolicina `ink`, red bi imao dva jednako jaka
        // glasa umesto naziva i njegovog dodatka.
        Text(text = count, fontSize = 15.sp, color = DS.inkMuted)
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
                Text(text = r.name, fontSize = 15.sp, color = DS.ink)
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
            Text(text = "⚠︎ $fen", fontSize = 13.sp, color = DS.danger)
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
            Text(text = caption, fontSize = 13.sp, color = DS.inkMuted)
        }
        if (interactive) {
            // Interaktivna tabla u lekciji jos ne postoji ni na iOS-u. Umesto
            // tihe razlike izmedju platformi, kaze se sta fali.
            Text(text = loc("Interaktivna tabla još nije dostupna."),
                 fontSize = 12.sp, color = DS.inkMuted)
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
                icon = lessonGlyph(spec.icon),
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
                icon = lessonGlyph(spec.icon),
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
            icon = lessonGlyph(spec.icon),
            color = accent
        )
    }
}

// MARK: - Ikone
//
// JSON lekcija je prenet sa iOS-a i u polju `icon` nosi IME SF SIMBOLA
// („crown.fill", „quote.opening"). Android nema SF simbole, pa se ime prevodi
// OVDE, na granici gde podatak iz JSON-a ulazi u UI, a ne u samim komponentama
// (one primaju ono sto vec treba da nacrtaju — `LessonGlyph`, ne sirov string).
//
// `LessonGlyph` nosi tri slucaja jer emoji i prava Material ikona ne mogu da
// stoje u istoj `Map<String, X>`:
//   - `Icon`   — SF simbol preveden u Material `ImageVector` (Faza 9: Task 1
//                21 od 49, strelice i oznake; Task 2 jos 21, predmeti i pojmovi;
//                Task 3 jos jedan, naslovna ikona lekcije — ukupno 43 od 49)
//   - `Emoji`  — SF simbol koji OSTAJE emoji (Faza 9, Task 3, preostalih sest
//                simbola — sve figure i taktike), ili sadrzaj koji vec nosi
//                emoji direktno (rucno pisane lekcije)
//   - `Unknown`— simbol koji NIJE nasao prevod. Zatecen fallback je bio tih
//                (neutralna tacka ili sirov string); ovaj MORA da vikne, isto
//                nacelo kao nepoznat naziv figure u `pieceRow` (CLAUDE.md,
//                „Android cita isti JSON") — tiha praznina u lekciji je gora
//                od ruznog znaka.
//
// Mapa pokriva svih 49 simbola koji se pojavljuju u `assets/lessons/*.json`
// (provereno pretragom kroz sve fajlove), pa `Unknown` danas nikad ne stize na
// ekran — ali ostaje kao zastita ako sledeca lekcija donese trideseti simbol.
// Ne `internal`: rezultat ide u parametre javnih `@Composable` funkcija u
// `LearnView.kt` (`LBox`, `LBullet`, `LSectionHeader`, `MateExerciseCard`,
// `MatePuzzleCard`, `OpeningLine.icon`) — Kotlin ne dozvoljava da javna funkcija
// izlozi `internal` tip kroz svoj potpis.
sealed interface LessonGlyph {
    @JvmInline value class Icon(val vector: ImageVector) : LessonGlyph
    @JvmInline value class Emoji(val text: String) : LessonGlyph
    /** Nepoznat SF simbol — mora da VIKNE, ne da ostavi prazninu. */
    @JvmInline value class Unknown(val symbol: String) : LessonGlyph
}

/// Jedino mesto koje crta `LessonGlyph` — svih sedam potpisa u `LearnView.kt` i
/// `LessonDetailView.kt` prolaze kroz ovo, umesto da svaki grana na tip sam.
///
/// `tint` je OBAVEZAN (Faza 9, Task 1) — pre ovog taska ga `Icon` grana uopste
/// nije primala, pa bi svaka konvertovana ikona pala na ambijentalni
/// `LocalContentColor.current` (u ovoj aplikaciji `DS.ink`, jer Scaffold nema
/// eksplicitan `containerColor` — `contentColorFor(background) == onBackground`).
/// To ne prati boju susednog naslova (`accent`/`DS.warning`/`DS.danger`), za
/// razliku od emoji-ja, koji je nosio SOPSTVENU boju. iOS `L_Box`/`L_Bullet`/
/// `L_SectionHeader` (`Chessko/Views/LessonRenderer.swift:353-451`) rade tacno
/// ovo — `Image(systemName:).foregroundStyle(color)`, isti `color` kao naslov
/// pored — pa je `tint` ovde parametar, ne podrazumevana vrednost, da svako
/// pozivno mesto MORA da ga svesno izabere.
@Composable
internal fun LessonGlyphView(
    glyph: LessonGlyph,
    fontSize: TextUnit,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val iconSize: Dp = with(LocalDensity.current) { fontSize.toDp() }
    when (glyph) {
        is LessonGlyph.Icon -> Icon(
            imageVector = glyph.vector,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(iconSize)
        )
        is LessonGlyph.Emoji -> Text(text = glyph.text, fontSize = fontSize, modifier = modifier)
        is LessonGlyph.Unknown -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = DS.danger,
                modifier = Modifier.size(iconSize)
            )
            Text(text = glyph.symbol, fontSize = fontSize, color = DS.danger)
        }
    }
}

// Faza 9, Task 1: prvih 21 od 49 simbola (strelice i oznake) prevedeno u
// Material ikone (15 razlicitih `Icons.Filled.*` klasa). Task 2 dodaje sledecih
// 21 (predmeti i pojmovi, 20 razlicitih klasa — `book.fill`/`text.book.closed.fill`
// dele `MenuBook`) — ukupno 42 od 49. Task 3 prevodi jos jedan (`GridOn`,
// naslovna ikona lekcije — obrazlozeno na mestu u mapi) i ostavlja poslednjih 6
// kao emoji, namerno; mapa je time konacna: 43 ikone + 6 emoji, sto tvrdi i
// `LessonGlyphMapTest` (instrumentisan, jer cita lekcijski JSON kroz `org.json`).
// Nijedno ime iz oba brief-a nije trebalo zamenu — svih 36 razlicitih
// `Icons.Filled.*` klasa u mapi ispod (broj IZVEDEN direktno iz nje, ne
// sabiranjem 15+20+1 — `sed -n '/val SYMBOL_TO_GLYPH/,/^)/p' LessonRenderer.kt |
// grep 'LessonGlyph\.Icon(' | grep -o 'Icons\.[A-Za-z]*\.[A-Za-z]*' | sort -u |
// wc -l` daje 36; filter na `LessonGlyph.Icon(` je od Task-a 3 OBAVEZAN, jer
// bez njega isti niz pokupi i `Icons.Filled.Restaurant` iz komentara ispod —
// ime koje je razmotreno pa odbijeno i nema nijedno pozivno mesto) provereno
// postoji u raspakovanom sources jar-u
// `material-icons-core`/`material-icons-extended` 1.7.8 pre pisanja, ne posle
// prve neuspele kompilacije.
private val SYMBOL_TO_GLYPH: Map<String, LessonGlyph> = mapOf(
    "arrow.clockwise" to LessonGlyph.Icon(Icons.Filled.Refresh),
    "arrow.forward.circle.fill" to LessonGlyph.Icon(Icons.Filled.ArrowCircleRight),
    "arrow.left.arrow.right" to LessonGlyph.Icon(Icons.Filled.SwapHoriz),
    "arrow.triangle.2.circlepath" to LessonGlyph.Icon(Icons.Filled.Refresh),
    "arrow.up" to LessonGlyph.Icon(Icons.Filled.ArrowUpward),
    "arrow.up.and.down" to LessonGlyph.Icon(Icons.Filled.SwapVert),
    "arrow.up.and.down.and.arrow.left.and.right" to LessonGlyph.Icon(Icons.Filled.OpenWith),
    "arrow.up.circle.fill" to LessonGlyph.Icon(Icons.Filled.ArrowUpward),
    "arrow.up.left.and.arrow.up.right" to LessonGlyph.Icon(Icons.Filled.OpenInFull),
    "arrow.up.right" to LessonGlyph.Icon(Icons.Filled.OpenInFull),
    "arrow.up.right.and.arrow.up.left" to LessonGlyph.Icon(Icons.Filled.OpenInFull),
    "checkmark.circle.fill" to LessonGlyph.Icon(Icons.Filled.CheckCircle),
    "checkmark.seal.fill" to LessonGlyph.Icon(Icons.Filled.CheckCircle),
    "circle.fill" to LessonGlyph.Icon(Icons.Filled.Circle),
    "exclamationmark.2" to LessonGlyph.Icon(Icons.Filled.PriorityHigh),
    "exclamationmark.circle.fill" to LessonGlyph.Icon(Icons.Filled.Error),
    "exclamationmark.triangle.fill" to LessonGlyph.Icon(Icons.Filled.Warning),
    "info.circle.fill" to LessonGlyph.Icon(Icons.Filled.Info),
    "minus.circle.fill" to LessonGlyph.Icon(Icons.Filled.RemoveCircle),
    "xmark.circle.fill" to LessonGlyph.Icon(Icons.Filled.Cancel),
    "xmark.shield.fill" to LessonGlyph.Icon(Icons.Filled.Cancel),

    // Faza 9, Task 2: sledecih 21 (predmeti i pojmovi).
    "bolt.fill" to LessonGlyph.Icon(Icons.Filled.Bolt),
    "book.fill" to LessonGlyph.Icon(Icons.Filled.MenuBook),
    "chart.line.uptrend.xyaxis" to LessonGlyph.Icon(Icons.Filled.TrendingUp),
    "eye.fill" to LessonGlyph.Icon(Icons.Filled.Visibility),
    "flag.checkered" to LessonGlyph.Icon(Icons.Filled.SportsScore),
    "flag.fill" to LessonGlyph.Icon(Icons.Filled.Flag),
    "flame.fill" to LessonGlyph.Icon(Icons.Filled.LocalFireDepartment),
    "globe" to LessonGlyph.Icon(Icons.Filled.Public),
    "hand.point.up.left.fill" to LessonGlyph.Icon(Icons.Filled.TouchApp),
    "heart.fill" to LessonGlyph.Icon(Icons.Filled.Favorite),
    "link" to LessonGlyph.Icon(Icons.Filled.Link),
    "person.fill" to LessonGlyph.Icon(Icons.Filled.Person),
    "person.2.fill" to LessonGlyph.Icon(Icons.Filled.People),
    "quote.opening" to LessonGlyph.Icon(Icons.Filled.FormatQuote),
    "ruler.fill" to LessonGlyph.Icon(Icons.Filled.Straighten),
    "scalemass.fill" to LessonGlyph.Icon(Icons.Filled.Scale),
    "shield.fill" to LessonGlyph.Icon(Icons.Filled.Shield),
    "square.grid.2x2.fill" to LessonGlyph.Icon(Icons.Filled.GridView),
    "star.fill" to LessonGlyph.Icon(Icons.Filled.Star),
    "text.book.closed.fill" to LessonGlyph.Icon(Icons.Filled.MenuBook),
    "trophy.fill" to LessonGlyph.Icon(Icons.Filled.EmojiEvents),

    // Faza 9, Task 3: 43. ikona, i jedina koju taj task prevodi.
    //
    // `square.grid.3x3.fill` je JEDINI od sedam koji se u isporucenom sadrzaju
    // NIKAD ne javlja u telu lekcije: 8 pojava, svih 8 na doc-level `icon`
    // polju (naslovna ikona lekcije „Tabla, figure i kretanje", svih 8 jezika;
    // prebrojano obilaskom `icon` polja u svih 36 fajlova u
    // `assets/lessons/`, ne preuzeto iz brief-a). Zato ovde nema izbora
    // „ikona u zaglavlju, emoji u telu" — nema tela; jedino pozivno mesto je
    // `LessonDetailView.kt:168`, plocica 50dp sa `accent@20%` podlogom, 24sp.
    //
    // Dve merene stvari su odlucile:
    //  - Posle Task-a 2 su ostale cetiri doc-level vrednosti
    //    (`bolt.fill` 10, `flag.checkered` 8, `flag.fill` 8,
    //    `text.book.closed.fill` 2) tintovane Material ikone. Da je ova ostala
    //    emoji, zaglavlje PRVE lekcije — one koju svaki korisnik otvara prvu —
    //    nosilo bi jedini saren glif u tom redu, i to preko `accent` plocice
    //    koja `tint` ocekuje.
    //  - Zatecen emoji ♟️ (pesak) NIJE ono sto simbol znaci. SF
    //    `square.grid.3x3.fill` je mreza 3x3 polja, a lekcija se zove „Tabla,
    //    figure i kretanje" — iOS na tom mestu crta TABLU, ne figuru. `GridOn`
    //    (okvir + 3x3 celija; proveren u raspakovanom sources jar-u
    //    `material-icons-extended` 1.7.8 pre pisanja) vraca i znacenje i tint.
    //    `Apps` je geometrijski blizi (9 odvojenih kvadratica) ali se na 24sp
    //    cita kao fioka aplikacija, i tako bi se i zvao u kodu.
    //
    // Cena, izricito: u istoj lekciji sad stoje dve ikone iz iste porodice —
    // ova u zaglavlju i `square.grid.2x2.fill` -> `GridView` na naslovu
    // „Kako se svaka figura kreće". Razlicite su (4 krupne celije naspram
    // ivicom uokvirene 3x3 mreze), razlicitih velicina (24sp naspram 18sp) i
    // nisu jedna uz drugu, ali su blize jedna drugoj nego sto je ♟️ bio bilo
    // cemu. Gubi se i jedini sahovski motiv u zaglavljima — sto je posledica
    // toga da ga simbol nikad nije ni tvrdio.
    "square.grid.3x3.fill" to LessonGlyph.Icon(Icons.Filled.GridOn),

    // Preostalih 6 -- Faza 9, Task 3: NAMERNO ostaju emoji. Ovo NIJE nedovrsen
    // posao nego merena odluka; ispod je cime je merena.
    //
    // (1) Material nema ikonu ni za jednu sahovsku figuru ni za ijedan
    //     takticki motiv. Prosao je raspakovan sources jar
    //     `material-icons-extended` 1.7.8: nijedno ime nije figura ni motiv.
    //     Imena u `Icons.Filled` ima **2083** — broj se reprodukuje na tri
    //     nacina, svi daju isto (spisak fajlova, jedinstvena imena fajlova, i
    //     brojanje deklaracija):
    //       unzip -Z1 <sources.jar> 'commonMain/*/icons/filled/*.kt' | wc -l
    //       unzip -p  <sources.jar> 'commonMain/*/icons/filled/*.kt' \
    //         | grep -c 'val Icons\.Filled\.'
    //     (2084 dobija onaj ko ne filtrira `*.kt` — tu upadne i sam
    //     direktorijum; `material-icons-core` nosi jos 49 imena, disjunktnih.)
    //     Postoji samo METAFORA — `Castle` za
    //     topa, `Restaurant` za viljusku — a metafora ovde gubi vise nego sto
    //     dobija: ona bi ista dva znacenja rekla generickim glifom, uz
    //     `Icons.Filled.Restaurant` u lekciji o taktici, sto sledeceg citaoca
    //     koda salje da trazi gresku koje nema.
    // (2) Emoji koji ostaju NOSE ZNACENJE, i to tacno ono koje lekcija na tom
    //     mestu izgovara (citati su doslovno iz `assets/lessons/*.sr.json`):
    //       🐴 `l.joystick.fill` (22 pojave) — „Kretanje: Kreće se u obliku
    //          slova \"L\": dva polja pravo pa jedno u stranu.", „Viljuška
    //          skakačem", „Sf3: Skakač je stao na f3."
    //       🍴 `tuningfork` (10) — naslovi „Viljuška (Rašlje)" i „Viljuška";
    //          i u srpskom i u engleskom je motiv nazvan po PRIBORU, pa je
    //          pribor jedini glif koji igru reci zadrzava.
    //       👑 `crown.fill` (70, najbrojniji) — „Promocija: Ako pion stigne do
    //          poslednjeg reda — pretvara se u bilo koju figuru, najčešće
    //          Damu.", ali i „Kralj u završnici postaje napadačka figura" i
    //          „#: Taraba na kraju znači **mat**." Kruna je ovde oznaka
    //          KRUNISANE strane uopste, ne jedna figura — vidi (3).
    //       ⬛ `dot.square.fill` (8) — „Kretanje: Kreće se samo jedno polje u
    //          bilo kom pravcu." Kvadrat je JEDNO POLJE, ne kralj.
    //       🏰 `rectangle.portrait.fill` (24) — „Vežba 1 — Kralj + Top",
    //          „Zadatak 3 — Žrtva Topa!"
    //       📐 `rhombus.fill` (16) — „Vežba 2 — Kralj + dva Lovca",
    //          „Zadatak 4 — Lovac + Top". Najslabiji par u setu (trougaoni
    //          lenjir za lovca je asocijacija na dijagonalu, ne na figuru);
    //          zapisano da se zna da je viđen, ne previđen.
    // (3) Unicode figure (`ChessPiece.symbol`, ♙♘♗♖♕♔) su razmotrene kao trece
    //     resenje i ODBIJENE, mereno:
    //       - `crown.fill` je 70 od 150 pojava ovih sest (47%) i POLISEMICAN je:
    //         od 11 razlicitih mesta u `*.sr.json` cetiri su Dama, tri Kralj, a
    //         cetiri nisu figura uopste („Slova figura", „#", „Španska
    //         partija", „Vezani branilac"). Jedan znak ne moze biti i ♔ i ♕.
    //       - `dot.square.fill`, `l.joystick.fill` i `tuningfork` su POTEZ i
    //         MOTIV, ne figura (citati gore).
    //       - Ostaju samo top i lovac — 40 od 150 pojava (27%).
    //       - Za njih bi trebao TRECI slucaj u `LessonGlyph`: emoji grana
    //         `LessonGlyphView`-a namerno ne prima boju (CLAUDE.md: „na emoji
    //         glif se boja ne pise uopste"), a Unicode figura `tint` prima i
    //         mora ga dobiti. Nov mehanizam za 2 od 49 simbola.
    //       - I sadrzinski se sudaraju: `board-and-pieces` vec crta ♙♖♗♘♕♔
    //         dvanaest puta (`pieceRow` 6 + `pieceValueTable` 6, kroz
    //         `LessonPieceGlyph` na 22sp), gde znak znaci „ovaj red JE ta
    //         figura". Isti ♖ kao oznaka vezbe „Kralj + Top" na istom ekranu
    //         dao bi jednom znaku dva posla.
    "crown.fill" to LessonGlyph.Emoji("👑"),
    "dot.square.fill" to LessonGlyph.Emoji("⬛"),
    "l.joystick.fill" to LessonGlyph.Emoji("🐴"),
    "rectangle.portrait.fill" to LessonGlyph.Emoji("🏰"),
    "rhombus.fill" to LessonGlyph.Emoji("📐"),
    "tuningfork" to LessonGlyph.Emoji("🍴")
)

/// `LessonGlyph` za ime SF simbola iz JSON-a. Poznat simbol daje ono sto mapa
/// kaze — `Icon` za 43 od 49 (Faza 9: Task 1 21, Task 2 jos 21, Task 3 jos 1),
/// `Emoji` za ostatak (6, Task 3). Ako sadrzaj vec nosi GLIF direktno — rucno
/// pisana lekcija ne mora da zna za SF imena — prosledjuje se nepromenjen, i
/// dalje kao `Emoji`. Sve ostalo daje `Unknown`, koji MORA da vikne: Task 0 je
/// uveo samo taj slucaj (zatecen fallback je bio tih "•"). Danas je nedostizan
/// jer je mapa iscrpna za svih 49 simbola u isporucenom sadrzaju, sto od
/// Task-a 3 vise nije tvrdnja u komentaru nego test
/// (`LessonGlyphMapTest.everyIconInShippedLessonsResolvesToAKnownGlyph`).
///
/// **Granica „vikni" / „nacrtaj" je od runde ispravki Task-a 3 ASCII, ne tacka.**
/// Do tada je `Unknown` dobijalo samo ime SA TACKOM, a mutacija testa je
/// pokazala da tri isporucena SF imena tacku nemaju (`globe`, `link`,
/// `tuningfork`): da ijedno ispadne iz mape, proslo bi kroz granu „vec gotov
/// glif" i iscrtalo se kao BUKVALNA REC usred lekcije — tiho, tacno ono zbog
/// cega `Unknown` postoji. ASCII niz nikad nije glif nego ime; glif je po
/// definiciji izvan ASCII-ja. Provereno da popravka danas nista ne lomi: od 49
/// `icon` vrednosti u isporucenom sadrzaju **nijedna** nije ne-ASCII, pa
/// nijedna ne ide kroz rezervnu granu ni pre ni posle. Pravilo za tacku je
/// zadrzano uz ASCII, ne zamenjeno njime — novo je strogo sire od starog, pa
/// nijedan slucaj koji je ranije vikao sada ne cuti.
internal fun lessonGlyph(symbol: String): LessonGlyph {
    val glyph = SYMBOL_TO_GLYPH[symbol]
    return when {
        glyph != null -> glyph
        // Prazan niz upada ovde sam od sebe (`all` nad praznim je `true`) i to
        // je tacno — krnj JSON mora da vikne isto kao nepoznato ime.
        '.' in symbol || symbol.all { it.code < 128 } -> LessonGlyph.Unknown(symbol)
        else -> LessonGlyph.Emoji(symbol)
    }
}

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
