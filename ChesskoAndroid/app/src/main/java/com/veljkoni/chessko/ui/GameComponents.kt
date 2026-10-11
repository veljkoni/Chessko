package com.veljkoni.chessko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.EvaluationLabel
import com.veljkoni.chessko.models.ChessPiece
import com.veljkoni.chessko.models.MoveClass
import com.veljkoni.chessko.models.PieceColor
import com.veljkoni.chessko.models.PieceType
import com.veljkoni.chessko.ui.theme.ButtonShape
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.DialogShape
import com.veljkoni.chessko.ui.theme.LocalChesskoColors
import com.veljkoni.chessko.ui.theme.Type
import kotlin.math.abs

// Zajednicke komponente (dizajnerski pregled, spec § 18 korak 4). Koriste ih Igra
// (§ 5), Zadaci i koraci Puta (§ 7) i Analiza (§ 6). iOS ima iste u
// `Chessko/Views/GameComponents.swift` — ista imena uloga, iste vrednosti.
//
//  - `PlayerCard`       kartica igraca / protivnika
//  - `InstructionBox`   okvir uputstva (fill, bez ivice, sijalica)
//  - `GameActionBar`    Vrati · Predaj · Vise
//  - `MoveClassBadge`   oznaka klase poteza (★ ! ✓ ?! ? ??)
//  - `selectionTint`    accent 12 % (svetla) / 16 % (tamna) — izabran potez/red
//  - dugmad i oblik dijaloga: `ui/theme/Components.kt`
//
// Lista poteza je `MoveHistoryView` (`ui/MoveHistoryView.kt`).

/**
 * Izabran red / potez: `accent` 12 % (svetla) / 16 % (tamna) preko `surface`
 * (spec § 2 „Izvedene boje" — nije nov token). Tekst na njoj je `accent`.
 */
val selectionTint: Color
    @Composable get() = DS.accent.copy(alpha = if (LocalChesskoColors.current.isDark) 0.16f else 0.12f)

// ── Kartica igraca ────────────────────────────────────────────────────────────

/** Status u kartici igraca (spec § 5). Poseban red „Tvoj potez" ne postoji. */
sealed interface PlayerCardStatus {
    /**
     * „Na potezu" (`Type.caption` medium, `ink`) + tacka `success`. `inCheck`: strana na
     * potezu je u sahu → „Na potezu · Šah" (sah u `danger`), deo labele kartice.
     */
    data class ToMove(val inCheck: Boolean = false) : PlayerCardStatus
    /** Racunar razmislja — tri tacke. */
    data object Thinking : PlayerCardStatus
    /** Kraj partije (mat, predaja, remi) — `Type.body`, `ink`. */
    data class Result(val text: String) : PlayerCardStatus
}

/** Procena u kartici igraca — logika i testovi: `EvaluationLabel` (`logic/LocalFormat.kt`). */
typealias Evaluation = EvaluationLabel

fun evaluationOf(score: Double, mateIn: Int?): Evaluation = EvaluationLabel.of(score, mateIn)

/**
 * Kartica igraca / protivnika (spec § 5): `surface` + ivica (`line` 1 dp, aktivna
 * `accent` 2 dp — 7,69 / 7,03), avatar kralja, ime, opcioni drugi red (nivo
 * protivnika — ako ga nema, red se NE prikazuje i ime je vertikalno centrirano;
 * nikad „–"), status, uzete figure, procena desno i strelica kad se karta tapa.
 */
@Composable
fun PlayerCard(
    color: PieceColor,
    name: String,
    boardTheme: BoardTheme,
    pieceStyle: PieceStyle,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    status: PlayerCardStatus? = null,
    isActive: Boolean = false,
    materialAdvantage: Int = 0,
    capturedPieces: List<ChessPiece> = emptyList(),
    evaluation: Evaluation? = null,
    showsChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(DS.Radius.m)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(DS.surface)
            .border(if (isActive) 2.dp else 1.dp, if (isActive) DS.accent else DS.line, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            // Jedna labela za citac ekrana: ime, nivo, status (ukljucujuci „Šah"), procena.
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = DS.Space.m, vertical = DS.Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(DS.Radius.s))
                .background(DS.fill),
            contentAlignment = Alignment.Center
        ) {
            val kingAsset = getAssetName(ChessPiece(PieceType.KING, color), pieceStyle, boardTheme)
            AsyncImage(
                model = "file:///android_asset/pieces/$kingAsset.svg",
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = name,
                    color = DS.ink,
                    style = Type.body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (materialAdvantage > 0) {
                    // spec § 3: statican broj nije `accent`.
                    Text(
                        text = "+$materialAdvantage",
                        color = DS.inkMuted,
                        style = Type.caption,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    color = DS.inkMuted,
                    style = Type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (status) {
                is PlayerCardStatus.ToMove -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(DS.success))
                    Text(text = loc("Na potezu"), color = DS.ink, style = Type.caption, fontWeight = FontWeight.Medium)
                    if (status.inCheck) {
                        Text(text = "· " + loc("Šah"), color = DS.danger, style = Type.caption, fontWeight = FontWeight.SemiBold)
                    }
                }
                PlayerCardStatus.Thinking -> Box(
                    modifier = Modifier
                        .padding(vertical = 3.dp)
                        .semantics { contentDescription = loc("Računar razmišlja...") }
                ) { ThinkingDots() }
                is PlayerCardStatus.Result -> Text(text = status.text, color = DS.ink, style = Type.body)
                null -> Unit
            }
            if (capturedPieces.isNotEmpty()) {
                CapturedPiecesView(
                    pieces = capturedPieces,
                    pieceStyle = pieceStyle,
                    boardTheme = boardTheme
                )
            }
        }

        if (evaluation != null) {
            EvaluationChip(evaluation)
        }

        if (showsChevron) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = DS.inkMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EvaluationChip(evaluation: Evaluation) {
    // Zlatna oznaka mata ostaje fiksna (§ 0) — i njena podloga mora biti fiksna:
    // `#FFD700` je na `DS.fill` svetle teme nevidljiv (1,16:1). Isto na iOS-u.
    val mateBadgeBackground = Color(0xFF1E293B)
    val mateBadgeBorder = Color.White.copy(alpha = 0.18f)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (evaluation.isMate) mateBadgeBackground else DS.fill)
            .border(1.dp, if (evaluation.isMate) mateBadgeBorder else DS.line, shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = evaluation.text,
            color = if (evaluation.isMate) Color(0xFFFFD700) else DS.ink,
            style = Type.caption,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Okvir uputstva ────────────────────────────────────────────────────────────

/**
 * Ton okvira uputstva. Okvir ostaje isti (`fill`, tekst `ink`) — menja se samo
 * ikona i njena boja, da ishod (greska / reseno / resenje) bude vidljiv a da okvir
 * ne pocne da lici na dugme (spec § 3). Ikone na `fill`: `inkMuted` 4,86 / 4,81,
 * `danger` 5,43 / 5,93, `success` 4,18 / 7,78 (>= 3 : 1 za ikone), `warning`
 * 4,75 / 7,51. Isto kao iOS `DSInstructionTone`.
 */
enum class InstructionTone(val icon: ImageVector) {
    HINT(Icons.Outlined.Lightbulb),
    WRONG(Icons.Filled.Cancel),
    SOLVED(Icons.Filled.Verified),
    SOLUTION(Icons.Filled.Visibility);

    val tint: Color
        @Composable get() = when (this) {
            HINT -> DS.inkMuted
            WRONG -> DS.danger
            SOLVED -> DS.success
            SOLUTION -> DS.warning
        }
}

/**
 * Okvir uputstva (spec § 7): `fill`, bez ivice, `Radius.m`; sijalica `inkMuted`
 * (4,86 / 4,81); tekst `Type.body` `ink` (14,47 / 13,08); uz krug strane na potezu.
 * Status — ne dugme i ne polje za unos.
 */
@Composable
fun InstructionBox(
    text: String,
    modifier: Modifier = Modifier,
    sideToMove: PieceColor? = null,
    tone: InstructionTone = InstructionTone.HINT
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.Radius.m))
            .background(DS.fill)
            .padding(DS.Space.m),
        horizontalArrangement = Arrangement.spacedBy(DS.Space.s),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = tone.icon,
            contentDescription = null,
            tint = tone.tint,
            modifier = Modifier.size(20.dp)
        )
        Text(text = text, color = DS.ink, style = Type.body, modifier = Modifier.weight(1f))
        if (sideToMove != null) {
            val sideName = if (sideToMove == PieceColor.WHITE) loc("Beli") else loc("Crni")
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    // Krug je STRANA u igri, ne tema (CLAUDE.md, Dizajn sistem) — fiksno
                    // belo/crno, uz `inkMuted` ivicu (4,86 / 4,81 na `fill`).
                    .background(if (sideToMove == PieceColor.WHITE) Color.White else Color.Black)
                    .border(1.dp, DS.inkMuted, CircleShape)
                    .clearAndSetSemantics { contentDescription = "$sideName, ${loc("Na potezu")}" }
            )
        }
    }
}

// ── Akciona traka ─────────────────────────────────────────────────────────────

/**
 * Jedno dugme akcione trake: `fill`, `Radius.m`, visina 48. Na uskom ekranu
 * (< 360 dp) ikona iznad labele (`Type.label`). Nedostupno dugme ostaje na mestu
 * sa 38 % providnosti (spec § 5).
 */
@Composable
fun ActionBarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = DS.ink,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .height(48.dp)
            .clip(ButtonShape)
            .background(DS.fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (compact) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Dekorativna: labela odmah ispod nosi ime radnje.
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Text(text = label, color = tint, style = Type.label, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Text(
                    text = label,
                    color = tint,
                    style = Type.body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Stavka menija „Više". */
data class MoreMenuItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * Akcije ispod table (spec § 5): Vrati · Predaj · Više. „Predaj" u `danger`
 * (na fill 5,43 / 5,93). Sadrzaj menija „Više" daje ekran (Igra: Podeli, Nova
 * partija, Analiza).
 */
@Composable
fun GameActionBar(
    canUndo: Boolean,
    canResign: Boolean,
    onUndo: () -> Unit,
    onResign: () -> Unit,
    moreItems: List<MoreMenuItem>,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DS.Space.s)
    ) {
        ActionBarButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            label = loc("Vrati"),
            onClick = onUndo,
            enabled = canUndo,
            compact = compact,
            modifier = Modifier.weight(1f)
        )
        ActionBarButton(
            icon = Icons.Outlined.Flag,
            label = loc("Predaj"),
            onClick = onResign,
            tint = DS.danger,
            enabled = canResign,
            compact = compact,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.weight(1f)) {
            ActionBarButton(
                icon = Icons.Default.MoreHoriz,
                label = loc("Više"),
                onClick = { menuOpen = true },
                compact = compact,
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = DS.surface,
                shape = DialogShape
            ) {
                moreItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(text = item.label, style = Type.body) },
                        leadingIcon = { Icon(imageVector = item.icon, contentDescription = null) },
                        enabled = item.enabled,
                        onClick = {
                            menuOpen = false
                            item.onClick()
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = DS.ink,
                            leadingIconColor = DS.ink,
                            disabledTextColor = DS.ink.copy(alpha = 0.38f),
                            disabledLeadingIconColor = DS.ink.copy(alpha = 0.38f)
                        )
                    )
                }
            }
        }
    }
}

// ── Oznaka klase poteza ───────────────────────────────────────────────────────

/** Simbol klase (spec § 6): ★ ! ✓ ?! ? ?? */
val MoveClass.symbol: String
    get() = when (this) {
        MoveClass.BEST -> "★"
        MoveClass.EXCELLENT -> "!"
        MoveClass.GOOD -> "✓"
        MoveClass.INACCURACY -> "?!"
        MoveClass.MISTAKE -> "?"
        MoveClass.BLUNDER -> "??"
    }

/** Puna oznaka (najbolji, greska, promasaj) ili obris. */
val MoveClass.badgeFilled: Boolean
    get() = this == MoveClass.BEST || this == MoveClass.MISTAKE || this == MoveClass.BLUNDER

/**
 * Boja = porodica. `accent` se NE koristi za klasu — u istoj listi znaci
 * „izabran potez".
 */
val MoveClass.badgeColor: Color
    @Composable get() = when (this) {
        MoveClass.BEST, MoveClass.EXCELLENT -> DS.success
        MoveClass.GOOD -> DS.inkMuted
        MoveClass.INACCURACY, MoveClass.MISTAKE -> DS.warning
        MoveClass.BLUNDER -> DS.danger
    }

/** Naziv klase za citac ekrana („Promašaj, 2. g4"). */
val MoveClass.accessibilityName: String
    get() {
        val raw = when (this) {
            MoveClass.BEST -> loc("najbolji")
            MoveClass.EXCELLENT -> loc("odličan")
            MoveClass.GOOD -> loc("dobar")
            MoveClass.INACCURACY -> loc("netačnost")
            MoveClass.MISTAKE -> loc("greška")
            MoveClass.BLUNDER -> loc("promašaj")
        }
        return raw.replaceFirstChar { it.uppercase() }
    }

/**
 * Oznaka klase poteza (spec § 6): `Radius.s`, visina 22, `Type.caption` bold.
 * Puna: `surface` na boji klase (5,04 / 8,78 za `success`); obris: boja klase na
 * providnom. Upotreba u Analizi je Task 6.
 */
@Composable
fun MoveClassBadge(moveClass: MoveClass, modifier: Modifier = Modifier) {
    val color = moveClass.badgeColor
    val shape = RoundedCornerShape(DS.Radius.s)
    val name = moveClass.accessibilityName
    Box(
        modifier = modifier
            .heightIn(min = 22.dp)
            .widthIn(min = 22.dp)
            .clip(shape)
            .background(if (moveClass.badgeFilled) color else Color.Transparent)
            .then(if (moveClass.badgeFilled) Modifier else Modifier.border(1.dp, color, shape))
            .padding(horizontal = 5.dp)
            .clearAndSetSemantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = moveClass.symbol,
            color = if (moveClass.badgeFilled) DS.surface else color,
            style = Type.caption,
            fontWeight = FontWeight.Bold
        )
    }
}
