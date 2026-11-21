package com.veljkoni.chessko

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veljkoni.chessko.models.ChessPiece
import com.veljkoni.chessko.models.GameState
import com.veljkoni.chessko.ui.BoardTheme
import com.veljkoni.chessko.ui.BoardView
import com.veljkoni.chessko.ui.PieceStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Izdanje 2.4: figura koja se vuče NE SME da ostane da lebdi kad se tabla promeni
 * usred prevlačenja.
 *
 * Kako je bug izgledao kod testera (igra preko interneta): igrač podigne figuru dok
 * čeka, stigne protivnikov potez, `board` se promeni. `pointerInput(board, …)` ima
 * `board` kao ključ, pa Compose OTKAZUJE i ponovo pokreće korutinu gesta — a
 * otkazivanje je preskakalo `draggedPiece = null; dragStartPos = null` na kraju bloka.
 * Lebdeća figura (uvećana 1,18, sa senkom) je ostajala zauvek na poslednjem mestu
 * prsta, a polazno polje je i dalje krilo svoju figuru (`hidePiece`).
 *
 * Test radi baš to: spusti prst na e2, pređe touch slop (prevlačenje je počelo,
 * lebdeća figura POSTOJI — inače bi test prolazio i bez ijednog prevlačenja), pa
 * promeni tablu dok je prst i dalje dole (crni odigra e7-e5) i tvrdi da je lebdeća
 * figura nestala i da se pešak na e2 ponovo crta.
 *
 * Mora instrumentisano: ponašanje živi u `pointerInput`-u, a do njega se iz JVM testa
 * ne može. Drugi test u projektu sa `createComposeRule` (prvi je
 * `SoundReleaseOnLanguageKeyChangeTest`; `espresso-core` 3.7.0 zbog API 36).
 */
@RunWith(AndroidJUnit4::class)
class BoardDragSurvivesBoardChangeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun boardChangeMidDragRemovesFloatingPieceAndShowsOriginAgain() {
        val start = GameState.initial().board
        // Crni odigra e7-e5: red 1 (7. red) kolona 4 → red 3 (5. red). Tabla mora biti
        // STRUKTURNO drugačija, jer `pointerInput` ključeve poredi sa `equals`.
        val afterBlackMove: List<List<ChessPiece?>> = start.mapIndexed { r, row ->
            row.mapIndexed { c, piece ->
                when {
                    r == 1 && c == 4 -> null
                    r == 3 && c == 4 -> start[1][4]
                    else -> piece
                }
            }
        }

        var board by mutableStateOf(start)

        composeRule.setContent {
            BoardView(
                board = board,
                isFlipped = false,
                selectedPosition = null,
                legalMoves = emptyList(),
                lastMove = null,
                boardTheme = BoardTheme.CLASSIC,
                pieceStyle = PieceStyle.CLASSIC,
                onTap = {},
                modifier = Modifier.size(320.dp).testTag("board")
            )
        }

        val boardNode = composeRule.onNodeWithTag("board")
        val e2 = "board.piece.6.4"

        composeRule.onNodeWithTag(e2, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("board.dragOverlay", useUnmergedTree = true).assertDoesNotExist()

        // Prst na e2 (prikazni red 6, kolona 4) i pokret preko touch slop-a, u nekoliko
        // koraka — `drag()` posle slop-a čeka SLEDEĆI događaj.
        boardNode.performTouchInput {
            val square = width / 8f
            down(Offset(square * 4.5f, square * 6.5f))
        }
        repeat(4) {
            boardNode.performTouchInput {
                val square = width / 8f
                moveBy(Offset(0f, -square / 2f))
            }
        }
        composeRule.waitForIdle()

        // Prevlačenje je stvarno počelo — bez ovoga test ne dokazuje ništa.
        composeRule.onNodeWithTag("board.dragOverlay", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(e2, useUnmergedTree = true).assertDoesNotExist()

        // Protivnikov potez stiže dok je prst i dalje dole.
        composeRule.runOnIdle { board = afterBlackMove }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("board.dragOverlay", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(e2, useUnmergedTree = true).assertExists()

        // Prst se podiže tek sad; ni posle toga ništa ne sme da lebdi.
        boardNode.performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("board.dragOverlay", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(e2, useUnmergedTree = true).assertExists()
    }
}
