package com.veljkoni.chessko.logic

import com.veljkoni.chessko.models.Position

/**
 * Gde na tabli stoje koordinatne oznake: slova a–h na DONJOJ ivici ekrana, brojevi
 * 1–8 na LEVOJ — i kad igrač igra belim i kad igra crnim.
 *
 * **Namerno NIJE iOS formula.** iOS okreće tablu tako što je crta normalno pa celu
 * rotira za 180° (`Chessko/Views/BoardView.swift:82-83`, `:130`), pa mu je „donja
 * ivica" kod okrenute table prikazni red 0. Android ne rotira nijednu tablu — ni u
 * igri crnim ni u igri u dvoje — nego redove i kolone obilazi obrnutim redom
 * (`BoardView.kt`, `boardRow = 7 - displayRow`), pa je donja ivica ekrana UVEK prikazni
 * red 7, a leva UVEK prikazna kolona 0. Prepisana iOS formula je do izdanja 2.4
 * crtala slova na gornjem redu i brojeve na desnoj koloni kad je tabla okrenuta.
 *
 * Funkcije rade u koordinatama TABLE (`Position`: red 0 = 8. red, kolona 0 = linija a),
 * jer tekst oznake se i ispisuje iz njih. Bez ijednog `android.*` / Compose uvoza, pa
 * ih pokriva JVM test (`BoardCoordinatesTest`).
 */
object BoardCoordinates {
    /** Polje u donjem redu ekrana: 1. red za belog, 8. red za okrenutu tablu. */
    fun isBottomEdge(position: Position, isFlipped: Boolean): Boolean =
        position.row == (if (isFlipped) 0 else 7)

    /** Polje u levoj koloni ekrana: linija a za belog, linija h za okrenutu tablu. */
    fun isLeftEdge(position: Position, isFlipped: Boolean): Boolean =
        position.col == (if (isFlipped) 7 else 0)
}
