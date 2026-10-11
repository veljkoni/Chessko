package com.veljkoni.chessko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.veljkoni.chessko.logic.locF
import com.veljkoni.chessko.ui.theme.dsCard
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
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.LearnViewModel

// MARK: - Lesson Renderer
//
// JEDINO mesto koje zna kako se blok crta. Nov tip bloka = jedna grana ovde i
// jedan `case` u `LessonBlock`.
//
// Tekst iz JSON-a stize VEC PREVEDEN i NE ide kroz `loc()`. Kroz `loc()` ide
// samo hrom koji renderer sam dodaje (podrazumevane poruke vezbi, poruka da
// interaktivna tabla nije dostupna).

/**
 * Kako se crtaju blokovi sa TABLOM (vezba, istrazivac, staticna tabla).
 *
 * `Inline` — tabla stoji u toku teksta (telefon, tablet portret).
 * `Markers` — tablet pejzaz (spec § 9): tekst levo, tabla desno i lepljiva.
 * U levoj koloni na mestu blokа sa tablom stoji samo OZNAKA (`LessonBoardMarker`);
 * sama tabla se crta u desnoj koloni (`LessonBoardBlock`). Oznaka javlja svoj
 * vertikalni polozaj, pa ekran zna koja je tabla „trenutna".
 */
sealed interface LessonBoardMode {
    data object Inline : LessonBoardMode
    class Markers(
        val activeIndex: Int?,
        val onMarkerPositioned: (index: Int, yInRoot: Float) -> Unit
    ) : LessonBoardMode
}

/** Blok koji nosi tablu — u pejzazu na tabletu ide u desnu kolonu. */
internal fun LessonBlock.carriesBoard(): Boolean =
    this is LessonBlock.Board || this is LessonBlock.Explorer || this is LessonBlock.Exercise

/**
 * Telo lekcije: `Type.body` 15 sa proredom 1,5 (spec § 9 — „Telo `Type.body` 15,
 * prored 1,5", `ink`). Velicina ostaje na skali; menja se samo prored, pa
 * `TypographyScaleTest` (meri `fontSize`) ostaje miran. iOS isto postize sa
 * `.lineSpacing(LessonBodyLineSpacing)` na `.dsBody`.
 */
internal val LessonBodyStyle = Type.body.copy(lineHeight = 22.5.sp)

/**
 * TODO(design § 13): privremena granica lekcijske table. Task 11 uvodi jednu
 * funkciju strane table za sve ekrane (tablet 560–800); do tada lekcija nosi
 * isti plafon kao telefon, da tabla na tabletu ne ide do 1336 pt (spec § 9).
 * `BoardWidthCapTest.lessonBoardsUseTheTemporaryCap` cuva da ova konstanta
 * postoji i da je kartice vezbi koriste.
 */
internal val LessonBoardMaxSide = DS.maxBoardSide

/**
 * Razmak IZNAD bloka (spec § 9 „Ritam"): `Space.xl` iznad i ispod razdelnika,
 * `Space.m` od naslova do teksta, inace `Space.l`. Jedno mesto umesto
 * `spacedBy` + padding-a po komponenti (ranije se razdelnik sabirao sa
 * razmakom kolone pa je ispadalo 28 dp sa jedne i 16 sa druge strane).
 */
private fun gapBefore(prev: LessonBlock?, cur: LessonBlock) = when {
    prev == null -> 0.dp
    cur is LessonBlock.Divider || prev is LessonBlock.Divider -> DS.Space.xl
    prev is LessonBlock.Heading -> DS.Space.m
    else -> DS.Space.l
}

@Composable
fun LessonBlocks(
    blocks: List<LessonBlock>,
    explorerViewModel: LearnViewModel,
    boardMode: LessonBoardMode = LessonBoardMode.Inline
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var prev: LessonBlock? = null
        blocks.forEachIndexed { index, b ->
            if (prev != null) Spacer(Modifier.height(gapBefore(prev, b)))
            prev = b
            if (b.carriesBoard() && boardMode is LessonBoardMode.Markers) {
                LessonBoardMarker(
                    block = b,
                    isActive = boardMode.activeIndex == index,
                    modifier = Modifier.onGloballyPositioned {
                        boardMode.onMarkerPositioned(index, it.positionInRoot().y)
                    }
                )
                return@forEachIndexed
            }
            when (b) {
                is LessonBlock.Heading -> LSectionHeader(lessonGlyph(b.icon), b.text)
                is LessonBlock.Paragraph -> LPara(b.text)
                is LessonBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(DS.Space.m)) {
                    for (it in b.items) {
                        LBullet(lessonGlyph(it.icon), it.title, it.text, bulletColorFor(it.style))
                    }
                }
                is LessonBlock.Box -> LBox(lessonGlyph(b.icon), b.title, b.text, boxColorFor(b.style))
                is LessonBlock.Quote -> LBox(lessonGlyph("quote.opening"), b.author, b.text, DS.accent)
                is LessonBlock.PieceRow -> LPieceRow(b.piece, b.name, b.count)
                is LessonBlock.NumberedRule -> LNumberedRule(b.number, b.title, b.text)
                is LessonBlock.PieceValueTable -> LPieceValueTable(b.rows)
                is LessonBlock.Divider -> HorizontalDivider(color = DS.line)
                is LessonBlock.Board, is LessonBlock.Explorer, is LessonBlock.Exercise ->
                    LessonBoardBlock(b, explorerViewModel)
            }
        }
    }
}

/** Sam blok sa tablom — isti crtez u toku teksta i u desnoj koloni na tabletu. */
@Composable
fun LessonBoardBlock(block: LessonBlock, explorerViewModel: LearnViewModel) {
    when (block) {
        is LessonBlock.Board -> LStaticBoard(block.fen, block.caption, block.interactive)
        is LessonBlock.Explorer -> PieceExplorer(explorerViewModel)
        is LessonBlock.Exercise -> LExercise(block.spec)
        else -> Unit
    }
}

/**
 * Oznaka bloka sa tablom u levoj koloni (tablet pejzaz). Trenutna oznaka nosi
 * izgled izabranog reda (spec § 2: `accent` 12 % / 16 % preko `surface`, tekst
 * `accent`); ostale su obicna kartica. Strelica pokazuje gde je tabla.
 */
@Composable
private fun LessonBoardMarker(block: LessonBlock, isActive: Boolean, modifier: Modifier = Modifier) {
    val (glyph, title) = when (block) {
        is LessonBlock.Exercise -> lessonGlyph(block.spec.icon) to block.spec.title
        is LessonBlock.Board -> lessonGlyph("square.grid.3x3.fill") to block.caption
        else -> lessonGlyph("hand.point.up.left.fill") to loc("Istraživač figura")
    }
    val tint = if (isActive) DS.accent else DS.ink
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dsCard()
            .then(if (isActive) Modifier.background(selectionTint) else Modifier)
            .padding(horizontal = DS.Space.m, vertical = DS.Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
    ) {
        LessonGlyphView(glyph, fontSize = Type.body.fontSize, tint = tint)
        Text(
            text = title,
            color = tint,
            style = Type.body,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = if (isActive) DS.accent else DS.inkMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/// Ikona uz stavku nabrajanja: `RULE`/`WARNING` nose znacenje (`warning`/`danger`);
/// neutralna stavka je `inkMuted`, ne `accent` — accent je samo za ono sto se tapa
/// (spec § 3, § 9: „naslovi sekcija nisu accent, izgledaju kao linkovi").
@Composable
@ReadOnlyComposable
private fun bulletColorFor(style: BoxStyle?): Color = when (style) {
    null, BoxStyle.INFO -> DS.inkMuted
    BoxStyle.RULE -> DS.warning
    BoxStyle.WARNING -> DS.danger
}

/// Boja kutije (ikona i tinta 10 % preko `surface`). Info kutija i citat ostaju u
/// akcentu kao tinta — spec § 2 meri „inkMuted u kutiji citata" upravo nad njom.
@Composable
@ReadOnlyComposable
private fun boxColorFor(style: BoxStyle): Color = when (style) {
    BoxStyle.INFO -> DS.accent
    BoxStyle.RULE -> DS.warning
    BoxStyle.WARNING -> DS.danger
}

/**
 * Brojna vrednost figure iz JSON-a u vidljivu oznaku: `"1"` -> „1 bod" / „1 point".
 *
 * Isto sto iOS radi u `L_PieceValueTable` kad `valueLabel` nedostaje — a nedostaje
 * u sva 36 isporucena fajla. Kljucevi su namerno LITERALI unutar `loc()`, da ih
 * `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary` vidi i proveri; sklapanje
 * kljuca iz promenljive bi ga sakrilo od tog testa.
 *
 * „∞" (kralj) je od spec § 9 rec „neprocenjiv" — znak beskonacnosti je citaocu
 * pocetniku izgledao kao greska u tabeli. Sadrzaj (JSON) se ne menja; prevod je
 * oznaka renderera, isto kao jedinice bodova. Nepoznata vrednost se vraca
 * kakva jeste: bolje gola brojka nego izmisljena jedinica.
 */
internal fun valueUnitLabel(value: String): String = when (value) {
    "1" -> loc("1 bod")
    "3" -> loc("3 boda")
    "5" -> loc("5 boda")
    "9" -> loc("9 bodova")
    "∞" -> loc("neprocenjiv")
    else -> value
}

/**
 * Kolicina figura u redu (`pieceRow.count`): JSON pise „× 2", spec § 9 trazi
 * „2 po strani" (znak × se citao kao „puta"). Prepoznaje se SAMO oblik „× N";
 * sve ostalo (slova figura u lekciji o notaciji: „K", „D"…) prolazi nepromenjeno.
 */
internal fun pieceCountLabel(count: String): String {
    val n = Regex("^×\\s*(\\d+)$").find(count.trim())?.groupValues?.get(1)?.toIntOrNull()
        ?: return count
    return locF("%d po strani", n)
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

/**
 * Velicina Unicode figure u lekcijskom redu (♙♘♗♖♕♔) — **namerno van
 * tipografske skale**: to je SADRZAJ, ne tekst interfejsa. iOS na istom mestu
 * uopste ne crta tekst nego sliku (`PieceImageView` u `L_PieceRow`), pa
 * font-pandan ne postoji i vezivanje za bilo koji korak skale bi bilo lazno
 * poreklo.
 */
private val LessonPieceGlyphSize = 22.sp

@Composable
private fun LessonPieceGlyph(piece: String) {
    val symbol = lessonPieceSymbol(piece)
    Text(
        text = symbol ?: "⚠",
        // Figura je SADRZAJ, pa velicinu uzima iz sopstvene konstante; znak
        // upozorenja je tekst interfejsa i ide na skalu.
        fontSize = if (symbol != null) LessonPieceGlyphSize else Type.body.fontSize,
        // Nepoznat naziv figure daje `DS.danger`, ne ukras -- vidljiva poruka o
        // pokvarenom sadrzaju JSON-a (CLAUDE.md, „Android cita isti JSON").
        color = if (symbol != null) DS.ink else DS.danger,
        modifier = Modifier.width(30.dp)
    )
}

@Composable
fun LPieceRow(piece: String, name: String, count: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Figura se crta iz `piece`; bez nje je red samo naziv i broj, a lekcija
        // o figurama upravo trazi da se figura VIDI (iOS crta `PieceImageView`).
        LessonPieceGlyph(piece)
        // `name` dolazi iz JSON-a vec preveden — NE kroz `loc()`. Naziv je
        // naslov pod-bloka (spec § 9: `Type.heading` bold `ink`, ne `accent`).
        Text(text = name, style = Type.heading, fontWeight = FontWeight.Bold, color = DS.ink)
        Spacer(Modifier.weight(1f))
        // Meta (spec § 9): `Type.caption`, `inkMuted`, na `fill` cipu.
        Text(
            text = pieceCountLabel(count),
            style = Type.caption,
            fontWeight = FontWeight.Medium,
            color = DS.inkMuted,
            modifier = Modifier
                .clip(RoundedCornerShape(DS.Radius.s))
                .background(DS.fill)
                .padding(horizontal = DS.Space.s, vertical = 3.dp)
        )
    }
}

/**
 * Tabela vrednosti (spec § 9): zaglavlje, redovi 44 sa `line` razdelnikom,
 * vrednost `ink` medium desno, tabelarne cifre, ne `accent`. Kartica = `surface`
 * + `line` (spec § 3).
 */
@Composable
fun LPieceValueTable(rows: List<PieceValueRow>) {
    Column(modifier = Modifier.fillMaxWidth().dsCard()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Space.m, vertical = DS.Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = loc("Figura"), style = Type.caption, fontWeight = FontWeight.Medium, color = DS.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(text = loc("Vrednost"), style = Type.caption, fontWeight = FontWeight.Medium, color = DS.inkMuted)
        }
        for (r in rows) {
            HorizontalDivider(color = DS.line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = DS.Space.m),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LessonPieceGlyph(r.piece)
                Text(text = r.name, style = Type.body, color = DS.ink)
                Spacer(Modifier.weight(1f))
                // `valueLabel` je vec preveden (stize iz JSON-a na tom jeziku), ali
                // ga nijedna isporucena lekcija ne zadaje — pa se u praksi UVEK ide
                // na `valueUnitLabel`.
                Text(
                    text = r.valueLabel ?: valueUnitLabel(r.value),
                    style = Type.body.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    color = DS.ink
                )
            }
        }
    }
}

@Composable
private fun LStaticBoard(fen: String, caption: String, interactive: Boolean) {
    val state = remember(fen) { com.veljkoni.chessko.models.GameState.fromFEN(fen) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state == null) {
            // Pokvaren FEN u sadrzaju mora da se VIDI, ne da ostavi prazninu.
            Text(text = "⚠︎ $fen", style = Type.body, color = DS.danger)
        } else {
            // `BoardTheme.CLASSIC`/`PieceStyle.CLASSIC` — lekcijska tabla namerno
            // NE prati korisnikovu temu (isto kao kartice vezbi u `LearnView.kt`).
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BoardView(
                    board = state.board,
                    isFlipped = false,
                    selectedPosition = null,
                    legalMoves = emptyList(),
                    lastMove = null,
                    boardTheme = BoardTheme.CLASSIC,
                    pieceStyle = PieceStyle.CLASSIC,
                    // Lekcijska tabla ne menja globalno podešavanje — isti izuzetak i
                    // istih pet mesta kao iOS (`allowsStyleSwipe: false`).
                    allowsStyleSwipe = false,
                    onTap = {},
                    modifier = Modifier.widthIn(max = LessonBoardMaxSide).fillMaxWidth().aspectRatio(1f)
                )
            }
        }
        if (caption.isNotEmpty()) {
            Spacer(Modifier.height(DS.Space.s))
            Text(text = caption, style = Type.caption, color = DS.inkMuted)
        }
        if (interactive) {
            // Interaktivna tabla u lekciji jos ne postoji ni na iOS-u. Umesto
            // tihe razlike izmedju platformi, kaze se sta fali.
            Text(text = loc("Interaktivna tabla još nije dostupna."),
                 style = Type.caption, color = DS.inkMuted)
        }
    }
}

@Composable
private fun LExercise(spec: com.veljkoni.chessko.models.ExerciseSpec) {
    when (spec.kind) {
        // Skriptovana vezba sa `mateIn` ide u `MatePuzzleCard`, koja jedina
        // prikazuje bedz „Mat u N" — isti izbor kao iOS.
        ExerciseKind.SCRIPTED -> if (spec.mateIn != null && spec.startFEN != null) {
            MatePuzzleCard(
                fen = spec.startFEN,
                moves = spec.uciMoves ?: emptyList(),
                title = spec.title,
                hint = spec.hint,
                icon = lessonGlyph(spec.icon),
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
                // Podrazumevane poruke IDU kroz `loc()` — to je hrom, ne sadrzaj.
                solvedMessage = spec.solvedMessage ?: loc("Bravo! Otvaranje savladano! ✓"),
                wrongMessage = spec.wrongMessage ?: loc("Pogrešan potez — pokušaj ponovo."),
                playingPrompt = spec.playingPrompt,
                startFEN = spec.startFEN
            )
        )
        ExerciseKind.VS_ENGINE -> MateExerciseCard(
            fen = spec.startFEN ?: "",
            title = spec.title,
            hint = spec.hint,
            icon = lessonGlyph(spec.icon)
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
//   - `Glyph`  — tekstualni glif koji PRIMA boju: Unicode figura uz U+FE0E
//                (dizajnerski pregled, spec § 9 — ♔ ♖ ♘ ♗ umesto 👑 🏰 🐴 📐)
//   - `Emoji`  — sadrzaj koji vec nosi emoji direktno (rucno pisana lekcija).
//                Od spec § 9 mapa vise nema nijedan `Emoji` par; grana ostaje
//                samo za prolaz ne-ASCII glifa iz sadrzaja (vidi `lessonGlyph`).
//   - `Unknown`— simbol koji NIJE nasao prevod. Zatecen fallback je bio tih
//                (neutralna tacka ili sirov string); ovaj MORA da vikne, isto
//                nacelo kao nepoznat naziv figure u `pieceRow` (CLAUDE.md,
//                „Android cita isti JSON") — tiha praznina u lekciji je gora
//                od ruznog znaka.
//
// Mapa pokriva svih 49 simbola koji se pojavljuju u `assets/lessons/*.json`
// (provereno pretragom kroz sve fajlove), pa `Unknown` danas nikad ne stize na
// ekran — ali ostaje kao zastita ako sledeca lekcija donese pedeseti simbol.
// Ne `internal`: rezultat ide u parametre javnih `@Composable` funkcija u
// `LearnView.kt` (`LBox`, `LBullet`, `LSectionHeader`, `MateExerciseCard`,
// `MatePuzzleCard`, `OpeningLine.icon`) — Kotlin ne dozvoljava da javna funkcija
// izlozi `internal` tip kroz svoj potpis.
sealed interface LessonGlyph {
    @JvmInline value class Icon(val vector: ImageVector) : LessonGlyph
    @JvmInline value class Emoji(val text: String) : LessonGlyph
    /**
     * Tekstualni glif koji prima `tint` — Unicode figura sa VARIATION SELECTOR-15
     * (U+FE0E). Bez selektora Android ♔ moze da nacrta kao pun-kolor emoji, koji
     * boju ignorise (CLAUDE.md: „Emoji glif ne prima `color=`"); sa njim je
     * monohromatski tekst, isto kao `⚠︎` u `LStaticBoard`.
     */
    @JvmInline value class Glyph(val text: String) : LessonGlyph
    /** Nepoznat SF simbol — mora da VIKNE, ne da ostavi prazninu. */
    @JvmInline value class Unknown(val symbol: String) : LessonGlyph
}

/// Jedino mesto koje crta `LessonGlyph` — svih **sedam pozivnih mesta**
/// (`LessonDetailView.kt:168` + sest u `LearnView.kt`) prolazi kroz ovo, umesto
/// da svako grana na tip samo.
///
/// Pozivno mesto nije isto sto i potpis, i raniji oblik ovog komentara je to
/// mesao: potpisa koji PRIMAJU `LessonGlyph` ima **sest**, svi u `LearnView.kt`
/// — pet parametara (`LBox`, `LBullet`, `LSectionHeader`, `MateExerciseCard`,
/// `MatePuzzleCard`) i jedno polje (`OpeningLine.icon`). Sedmo pozivno mesto
/// (naslovna ikona lekcije) ne prolazi ni kroz jedan od njih nego zove
/// `lessonGlyph(...)` na licu mesta.
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
        // Figura je nesto sira od ikone iste visine; `lineHeight = fontSize` i
        // centriranje drze je u istoj kutiji kao Material ikonu pored nje.
        is LessonGlyph.Glyph -> Box(modifier = modifier.size(iconSize), contentAlignment = Alignment.Center) {
            Text(text = glyph.text, fontSize = fontSize, lineHeight = fontSize, color = tint, maxLines = 1)
        }
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
// [ISTORIJA — od dizajnerskog pregleda (spec § 9) tih 6 vise nisu emoji: 4 su
// `Glyph` (Unicode figure), 2 su ikone; mapa je 45 `Icon` + 4 `Glyph`, a
// `checkmark.seal.fill` je presao sa `CheckCircle` na `WorkspacePremium`.
// Brojevi ispod opisuju stanje Faze 9.]
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
    // Spec § 9: „Elementarni matovi" (i „Zlatna pravila otvaranja" — jedina dva
    // pozivna mesta, oba naslovi) nose ikonu koja NE izgleda kao status; kvacica
    // je citaocu govorila „zavrseno". Medalja (iOS `rosette`) je oznaka, ne stanje.
    "checkmark.seal.fill" to LessonGlyph.Icon(Icons.Filled.WorkspacePremium),
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

    // Poslednjih 6 — dizajnerski pregled, spec § 9 (obe platforme isto, iOS
    // `LessonSymbol` u `LessonRenderer.swift`). Faza 9 ih je ostavila kao emoji
    // jer Material nema figure; spec presudjuje drugacije: emoji su bili jedini
    // sareni glifovi u inace monohromatskoj lekciji, pa ih zamenjuju Unicode
    // figure UZ U+FE0E (tekstualna prezentacija, prima boju) i dve ikone.
    //
    // POSLEDICA, svesno prihvacena: `crown.fill` je POLISEMICAN (od 11 mesta u
    // `*.sr.json` cetiri su Dama, tri Kralj, cetiri nisu figura — CLAUDE.md
    // „Lekcijski glifovi"). Spec mapira kao 👑 → ♔, pa se na mestima gde se
    // govori o Dami (npr. „Promocija") sada crta KRALJ. Mapa ne zna kontekst; za
    // tacnu figuru po mestu bi trebalo menjati JSON (nov SF naziv po mestu) — to
    // je izmena sadrzaja na 8 jezika × 2 platforme i nije uradjena.
    "crown.fill" to LessonGlyph.Glyph("\u2654\uFE0E"),
    "rectangle.portrait.fill" to LessonGlyph.Glyph("\u2656\uFE0E"),
    "l.joystick.fill" to LessonGlyph.Glyph("\u2658\uFE0E"),
    "rhombus.fill" to LessonGlyph.Glyph("\u2657\uFE0E"),
    // Viljuska: „grananje" napada na dve mete (iOS `arrow.triangle.branch`).
    "tuningfork" to LessonGlyph.Icon(Icons.AutoMirrored.Filled.CallSplit),
    // „Krece se samo jedno polje" — kvadrat je JEDNO POLJE (iOS `square.fill`).
    "dot.square.fill" to LessonGlyph.Icon(Icons.Filled.Square)
)

/// `LessonGlyph` za ime SF simbola iz JSON-a. Poznat simbol daje ono sto mapa
/// kaze — od spec § 9 `Icon` za 45 od 49 i `Glyph` (Unicode figura + U+FE0E)
/// za 4; `Emoji` vise nema nijedan par u mapi. Ako sadrzaj vec nosi GLIF direktno — rucno
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
