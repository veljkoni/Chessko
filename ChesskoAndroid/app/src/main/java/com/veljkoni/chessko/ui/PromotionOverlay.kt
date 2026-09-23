package com.veljkoni.chessko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.veljkoni.chessko.logic.SettingsManager
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.models.ChessPiece
import com.veljkoni.chessko.models.PieceType
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.viewmodels.GameViewModel

/**
 * Podloga plocice ispod figure — FIKSNA, iz istog razloga iz kog je fiksan i
 * okvir eval trake (`EvalBar.kt`): sadrzaj na njoj (SVG figure) ne prati temu.
 *
 * Plocica je pre ovoga bila `DS.onScrim` @8% nad `DS.scrim` nad dim-om
 * `Dialog`-a, sto se slaze u **#3D3D3D (svetla) / #17181A (tamna)**. Na toj
 * plocici crna figura stila „Ravne" (`piece_black_*_flat.svg`, puna silueta
 * `#2C2C30`) meri **1,280 odnosno 1,278** — prakticno nevidljiva. To NIJE greska
 * ove grane; postojalo je i pre nje, samo je selidba kartice na `DS.surface`
 * ucinila da se mora presuditi. Sa `DS.surface` plocicom bio bi isti problem sa
 * BELIM figurama u svetloj temi, pa nijedan tema-zavisan token ovde ne prolazi.
 *
 * `#708090` je izabran merenjem: jedini ton koji omedji i near-belu i near-crnu
 * siluetu. Izmereno (WCAG 2.1) prema stvarnim bojama iz isporucenih SVG-ova:
 *   „Ravne" bela `#FFFFFF` = 4,06    „Ravne" crna `#2C2C30` = 3,43
 *   podrazumevani set `#fff` = 4,06  podrazumevani set `#0c0c0c` = 4,83
 *   prema kartici `DS.surface`       = 4,05 (svetla) / 4,12 (tamna)
 * Set „Jednostavne tanke" nosi OBE boje (`#F4F7FA` 3,77 i `#34364C` 2,91) u obe
 * varijante figure, pa se omedjuje sam sobom.
 */
private val PromotionTile = Color(0xFF708090)

/**
 * Modalni preklop za izbor figure pri promociji.
 *
 * `GameViewModel` od pocetka postavlja `showPromotion = true` i ceka izbor
 * (`confirmPromotion`/`cancelPromotion` vec postoje), ali taj izbor do sada
 * NIJE renderovao niko -- `grep -rn "showPromotion"` pogadja samo
 * `GameViewModel.kt`. Sa `autoPromoteToQueen = false` (podrazumevano) to je
 * tablu u slobodnoj partiji zauvek blokiralo na osmom redu: nijedan dodir ne
 * radi jer `isPlayerTurn`/`tap()` cekaju potez koji nikad ne stize.
 *
 * Dodir van kartice (na scrim) otkazuje potez -- `cancelPromotion()` vraca
 * tablu na stanje pre poteza (brise selekciju), isto kao iOS overlay.
 */
@Composable
fun PromotionOverlay(viewModel: GameViewModel) {
    if (viewModel.promotionMove == null) return
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val activeTheme = remember(settings.boardTheme) {
        BoardTheme.values().find { it.rawValue == settings.boardTheme } ?: BoardTheme.CLASSIC
    }
    val activeStyle = remember(settings.pieceStyle) {
        PieceStyle.values().find { it.rawValue == settings.pieceStyle } ?: PieceStyle.CLASSIC
    }

    // Boja pesaka koji se promoviše: potez jos nije primenjen (showPromotion
    // se postavlja PRE `execute()`), pa je to boja igraca na potezu.
    val color = viewModel.gameState.currentTurn

    // D/T/L/S -- isti redosled kao iOS `promotionOverlay`.
    val choices = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

    Dialog(onDismissRequest = { viewModel.cancelPromotion() }) {
        // Zatamnjenje IZA kartice je scrim (ovde: sopstveni dim `Dialog`-a, 0,6
        // crno — iOS to radi eksplicitno, `DS.scrim.ignoresSafeArea()`,
        // `Chessko/Views/PromotionOverlay.swift:24`). Kartica NIJE scrim.
        //
        // Prethodna verzija je na oba mesta stavljala `DS.scrim` i to branila sa
        // „isto kao iOS". To NIJE bilo tacno: na iOS-u je kartica
        // `.ultraThinMaterial` (`:49`), a `DS.scrim` je iskljucivo celoekranska
        // podloga. `DS.scrim` je 55% crno i PROVIDNO — polja table su se citala
        // kroz panel, a tabela preslikavanja ovog plana salje `Color(0xFF1E293B)`
        // na `DS.surface`, sto grana postuje na svih pet ostalih mesta
        // (`MainActivity.kt:164,280,480`, `StepGameView.kt:222`, `SettingsView.kt:176`).
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(DS.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Kartica je `DS.surface`, pa naslov ide na `DS.ink` — `DS.onScrim` je
            // fiksna bela i nad `DS.surface` vise nije ispravna (u svetloj temi
            // bela na beloj). `ink`/`surface` = 17,43 (svetla) / 14,76 (tamna).
            Text(
                text = loc("Izaberi figuru"),
                color = DS.ink,
                // IZUZETAK, isti oblik kao naslov sekcije u info dijalogu sata
                // (`ChessClockView.kt`, `InfoCategorySection`): iOS je ovde na
                // `.title3.weight(.semibold)` = **20**
                // (`Chessko/Views/PromotionOverlay.swift:28`), dakle IZNAD
                // `heading`-a. Skala nema 20, pa mesto ide na najblizu ULOGU
                // (naslov kartice/dijaloga), ne na najblizi broj.
                // `fontWeight` ostaje: heading je SemiBold, a ovo mesto je Bold i
                // to se ne gubi usput.
                fontWeight = FontWeight.Bold,
                style = Type.heading
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (type in choices) {
                    val assetName = getAssetName(ChessPiece(type, color), activeStyle, activeTheme)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PromotionTile)
                            .clickable {
                                viewModel.confirmPromotion(type)
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "file:///android_asset/pieces/$assetName.svg",
                            contentDescription = type.srbName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
