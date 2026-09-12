package com.veljkoni.chessko.logic

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.veljkoni.chessko.models.ChessPuzzle
import java.io.File

// MARK: - Puzzle Repository
//
// Cita `puzzles.sqlite` — 20.000 Lichess zadataka, licenca CC0, ISTA baza koju
// isporucuje i iOS (bajt-identicna). Aplikacija od ove faze nema nijedan mrezni
// poziv za zadatke i radi u avionskom rezimu.
//
// Baza se KOPIRA iz `assets` u `filesDir` pri prvoj upotrebi: `SQLiteDatabase`
// trazi pravu putanju na disku, a asseti su unutar APK-a. Isti obrazac koji
// `StockfishEngine` vec koristi za `.nnue` mreze.
class PuzzleRepository(context: Context) {

    private val db: SQLiteDatabase

    init {
        val target = File(context.filesDir, DB_NAME)
        val expected = context.assets.openFd(DB_NAME).length
        // Kopira se samo ako fajla nema ILI je nepotpun. Bez provere velicine
        // bi prekinuta prva kopija (pun disk, ubijen proces) ostala zauvek kao
        // pokvarena baza koju niko vise ne bi prepisao.
        if (!target.exists() || target.length() != expected) {
            context.assets.open(DB_NAME).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        db = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun count(): Int =
        db.rawQuery("SELECT COUNT(*) FROM puzzles", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun puzzle(id: String): ChessPuzzle? =
        db.rawQuery("$SELECT_COLS WHERE id = ?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) c.toPuzzle() else null
        }

    /// Zadatak dana. `dayIndex` je broj dana od epohe — isti ulaz daje isti
    /// zadatak, bez cuvanja stanja. Deterministicno preko `ORDER BY id`, pa se
    /// ne oslanja na redosled umetanja.
    fun dailyPuzzle(dayIndex: Long): ChessPuzzle? {
        val n = count()
        if (n == 0) return null
        val offset = ((dayIndex % n) + n) % n   // i za negativan dayIndex
        return db.rawQuery("$SELECT_COLS ORDER BY id LIMIT 1 OFFSET ?",
                           arrayOf(offset.toString())).use { c ->
            if (c.moveToFirst()) c.toPuzzle() else null
        }
    }

    /// Nasumican zadatak u opsegu rejtinga, bez onih koji su vec resavani.
    ///
    /// `excluding` se vezuje kao JEDAN parametar po id-ju. Na tavanici od
    /// 20.000 resenih to je 20k vezivanja po dodiru, protiv SQLite granice od
    /// 999 parametara na starijim verzijama — zato se skup ovde SECE na
    /// `MAX_EXCLUDED` (redosled NIJE garantovan — `Set` ga nema, pa
    /// `take` uzima proizvoljnih 900; ako ikad zatreba da to budu bas najskoriji,
    /// pozivalac mora da posalje `LinkedHashSet` ili listu). Ponovljen zadatak je bolji od pada.
    fun randomPuzzle(ratingRange: IntRange, excluding: Set<String>): ChessPuzzle? {
        val excl = excluding.take(MAX_EXCLUDED)
        val holes = if (excl.isEmpty()) "" else
            " AND id NOT IN (${excl.joinToString(",") { "?" }})"
        val args = (listOf(ratingRange.first.toString(), ratingRange.last.toString()) + excl)
            .toTypedArray()
        return db.rawQuery(
            "$SELECT_COLS WHERE rating BETWEEN ? AND ?$holes ORDER BY RANDOM() LIMIT 1", args
        ).use { c -> if (c.moveToFirst()) c.toPuzzle() else null }
    }

    /// Svi zadaci, deterministicno po `id`. Postoji zbog testa integriteta koji
    /// prolazi CELU bazu; aplikacija ga ne zove.
    fun allPuzzlesOrderedById(): List<ChessPuzzle> {
        val out = ArrayList<ChessPuzzle>(20000)
        db.rawQuery("$SELECT_COLS ORDER BY id", null).use { c ->
            while (c.moveToNext()) out.add(c.toPuzzle())
        }
        return out
    }

    private fun android.database.Cursor.toPuzzle() = ChessPuzzle(
        puzzleId = getString(0),
        fen = getString(1),
        moves = getString(2),
        rating = getInt(3),
        themes = getString(4)
    )

    companion object {
        private const val DB_NAME = "puzzles.sqlite"
        private const val SELECT_COLS = "SELECT id, fen, moves, rating, themes FROM puzzles"
        /// Granica broja iskljucenih id-jeva, vidi `randomPuzzle`.
        const val MAX_EXCLUDED = 900
    }
}
