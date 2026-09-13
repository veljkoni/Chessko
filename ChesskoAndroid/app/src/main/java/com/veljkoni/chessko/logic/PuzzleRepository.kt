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
        // `.use`: `openFd` vraca deskriptor koji se mora zatvoriti. Bez toga
        // svaka nova instanca repozitorijuma procuri jedan fd.
        val expected = context.assets.openFd(DB_NAME).use { it.length }
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

    /// Zadaci za korak Puta (`practice`/`test`): filtrirani po TEMI, u opsegu
    /// rejtinga, bez vec iskljucenih id-jeva. Filtrira preko RAZLOZENE tabele
    /// `puzzle_themes` (`JOIN` + `IN`), NE `LIKE` nad tekstom — `LIKE '%mate%'`
    /// bi pogodio i `mateIn1`, `mateIn2` i `smotheredMate`, jer `IN` nad
    /// razlozenom tabelom poredi ceo string a `LIKE` pogadja podniz.
    /// Prazna lista tema preskace filter po temi (bilo koja tema). `GROUP BY
    /// p.id` sklanja duplikate koje bi JOIN napravio kad zadatak nosi vise od
    /// jedne trazene teme. Red se puni JEDNIM upitom (`ORDER BY RANDOM()
    /// LIMIT ?`), ne pojedinacnim izvlacenjima — traka napretka koraka mora da
    /// zna ukupan broj UNAPRED, a `test` koji krece ispocetka mora da garantuje
    /// NOVE zadatke u jednom potezu.
    fun puzzlesForStep(
        themes: List<String>, ratingRange: IntRange, excluding: Set<String>, limit: Int
    ): List<ChessPuzzle> {
        if (limit <= 0) return emptyList()
        val excl = excluding.take(MAX_EXCLUDED)

        val sql = StringBuilder("SELECT p.id, p.fen, p.moves, p.rating, p.themes FROM puzzles p")
        if (themes.isNotEmpty()) sql.append("\nJOIN puzzle_themes t ON t.puzzle_id = p.id")
        sql.append("\nWHERE p.rating BETWEEN ? AND ?")
        if (themes.isNotEmpty()) {
            sql.append("\nAND t.theme IN (${themes.joinToString(",") { "?" }})")
        }
        if (excl.isNotEmpty()) {
            sql.append("\nAND p.id NOT IN (${excl.joinToString(",") { "?" }})")
        }
        sql.append("\nGROUP BY p.id\nORDER BY RANDOM()\nLIMIT ?")

        val args = (listOf(ratingRange.first.toString(), ratingRange.last.toString())
            + themes + excl + limit.toString()).toTypedArray()

        val out = ArrayList<ChessPuzzle>(limit)
        db.rawQuery(sql.toString(), args).use { c ->
            while (c.moveToNext()) out.add(c.toPuzzle())
        }
        return out
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

        @Volatile private var instance: PuzzleRepository? = null

        /// Deljena instanca, isti obrazac kao `ProgressStore`/`StatsManager`.
        /// Bez ovoga je svaki ulazak u korak Puta (`StepPracticeView`) pravio
        /// SOPSTVENI `PuzzleViewModel`, a s njim i SOPSTVENU otvorenu SQLite
        /// konekciju koja se nikad ne zatvara (`db` iznad nema `close()`) —
        /// kurikulum nosi 9 `practice`/`test` koraka, pa bi obilazak celog
        /// Puta ostavio 9 otvorenih konekcija u istom procesu. Jedna konekcija
        /// koja zivi koliko i proces je ionako ono sto tab Zadaci oduvek imao
        /// (jedan `PuzzleViewModel` za ceo zivot procesa, pre ove faze).
        fun getInstance(context: Context): PuzzleRepository =
            instance ?: synchronized(this) {
                instance ?: PuzzleRepository(context.applicationContext).also { instance = it }
            }
    }
}
