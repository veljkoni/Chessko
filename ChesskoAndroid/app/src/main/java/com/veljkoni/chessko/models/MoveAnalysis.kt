package com.veljkoni.chessko.models

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// MARK: - Analiza partije: model i matematika
//
// Prenosi `Chessko/Models/MoveAnalysis.swift` doslovno. Ovaj fajl NE zna za
// Stockfish ni za Compose. Sve sto radi je racunica nad ocenama koje mu neko
// drugi doda, pa se u celosti testira JVM testom. Motor je zamenljiv; ova
// pravila nisu. Namerno BEZ ijednog `android.*` uvoza — isto pravilo kao
// `logic/PathProgress.kt` i `logic/PuzzleRating.kt`.

/** Ocena pozicije iz ugla strane koja je NA POTEZU. */
sealed class EngineScore {
    data class Cp(val value: Int) : EngineScore()

    /** Broj poteza do mata. Pozitivno = strana na potezu matira. */
    data class Mate(val n: Int) : EngineScore()

    /**
     * Jedinstvena centipionska vrednost za poredjenje.
     *
     * Mat nema centipionsku ocenu, a cela racunica ispod je centipionska, pa
     * se mora preslikati. Uslov je samo da mat bude (a) daleko iznad svake
     * realne centipionske ocene i (b) monoton — mat u 1 bolji od mata u 2.
     */
    val centipawns: Int
        get() = when (this) {
            is Cp -> value
            is Mate -> {
                val distance = min(abs(n), 99)
                if (n > 0) 10_000 - distance else -10_000 + distance
            }
        }
}

/**
 * Klasa odigranog poteza. Pragovi su iz spec-a 5.5 i ne smeju se menjati
 * bez izmene spec-a — prikazuju se korisniku kao ocena njegove igre.
 */
enum class MoveClass {
    BEST,       // odigran je potez motora
    EXCELLENT,  // < 20
    GOOD,       // < 50
    INACCURACY, // < 100
    MISTAKE,    // < 300
    BLUNDER;    // >= 300

    companion object {
        fun classify(cpLoss: Int, playedEngineBest: Boolean): MoveClass {
            // Potez motora je po definiciji najbolji. Provera ide PRVA jer merenje
            // ume da da mali gubitak i za potez motora: pozicija pre poteza i
            // pozicija posle njega pretrazuju se nezavisno, na istoj dubini ali iz
            // razlicitih cvorova, pa se ocene ne poklope do centipiona.
            if (playedEngineBest) return BEST
            return when {
                cpLoss < 20 -> EXCELLENT
                cpLoss < 50 -> GOOD
                cpLoss < 100 -> INACCURACY
                cpLoss < 300 -> MISTAKE
                else -> BLUNDER
            }
        }
    }
}

data class AnalyzedMove(
    /** Redni broj poluteza, od 0. `ply 0` je prvi beli potez. */
    val ply: Int,
    val notation: String,
    val byWhite: Boolean,
    val cpLoss: Int,
    val moveClass: MoveClass,
    val scoreBefore: EngineScore,
    val scoreAfter: EngineScore
) {
    val id: Int get() = ply

    /** Broj poteza kako ga korisnik broji (1, 1, 2, 2, 3, …). */
    val moveNumber: Int get() = ply / 2 + 1

    /** „17.Lc4" ili „17…Lc4" — oblik iz spec-a za prelomni potez. */
    val displayNotation: String
        get() = if (byWhite) "$moveNumber.$notation" else "$moveNumber…$notation"

    /**
     * Sta potez znaci za pocetnika (dizajnerski pregled § 6): umesto „(−233)"
     * ekran kaze „Beli gubi oko dva piona prednosti.". Mat se kaze recima, jer je
     * gubitak koji ga nosi vestacki (`MAX_CP_LOSS`). iOS: `AnalyzedMove.beginnerExplanation`.
     */
    val beginnerExplanation: BeginnerExplanation
        get() {
            // Posle poteza je na potezu protivnik: `Mate(n > 0)` = protivnik matira.
            val opponentMatesAfter = scoreAfter is EngineScore.Mate && scoreAfter.n > 0
            val moverMatedBefore = scoreBefore is EngineScore.Mate && scoreBefore.n <= 0
            val moverMatesBefore = scoreBefore is EngineScore.Mate && scoreBefore.n > 0
            val moverStillMatesAfter = scoreAfter is EngineScore.Mate && scoreAfter.n < 0
            if (moverMatesBefore && !moverStillMatesAfter) return BeginnerExplanation.MissedMate
            if (opponentMatesAfter && !moverMatedBefore) return BeginnerExplanation.AllowedMate
            return BeginnerExplanation.PawnsLost(Math.round(cpLoss / 100.0).toInt())
        }
}

/** Recenica ispod prelomnog poteza (§ 6). Tekst bira prikaz (8 jezika, mnozine). */
sealed interface BeginnerExplanation {
    /** Gubi oko N piona prednosti (zaokruzeno na ceo pion). */
    data class PawnsLost(val pawns: Int) : BeginnerExplanation
    /** Imao je mat, a potez ga je propustio. */
    data object MissedMate : BeginnerExplanation
    /** Potez dozvoljava protivniku mat. */
    data object AllowedMate : BeginnerExplanation
}

data class GameAnalysis(
    val moves: List<AnalyzedMove>,
    val whiteAccuracy: Double,
    val blackAccuracy: Double
) {
    companion object {
        /**
         * Ispod ovoga se potez ne proglasava prelomnim. U cisto odigranoj partiji
         * najveci gubitak ume da bude 12 centipiona; izdvojiti ga kao „prelomni"
         * bilo bi lazno dramatizovanje.
         */
        const val TURNING_POINT_MIN_LOSS = 100

        /**
         * Gornja granica gubitka po potezu.
         *
         * Propusten mat daje razliku od ~20.000 centipiona. Granica drzi broj u
         * prelomnoj kartici citljivim („−10,0", ne „−199,9"). Tacnost od § 17
         * dizajnerskog pregleda vise ne zavisi od `cpLoss` (racuna se iz procenta
         * pobede), pa granica sada utice samo na klasu i prelomni potez. 1000 je
         * trostruko iznad praga za promasaj (300), pa ne sakriva nijednu gresku.
         */
        const val MAX_CP_LOSS = 1000

        /**
         * Gubitak u centipionima za jedan potez.
         *
         * `before` je ocena pozicije PRE poteza, iz ugla igraca koji vuce.
         * `after` je ocena pozicije POSLE poteza — a tada je na potezu PROTIVNIK,
         * pa je i ocena iz njegovog ugla. Vrednost iz ugla igraca koji je vukao
         * je `-after`, i gubitak je `before - (-after)` = `before + after`.
         * Sabiranje ovde nije greska nego posledica okretanja perspektive.
         */
        fun cpLoss(before: EngineScore, after: EngineScore): Int {
            val raw = before.centipawns + after.centipawns
            return max(0, min(MAX_CP_LOSS, raw))
        }

        /**
         * Ispod ovoliko SOPSTVENIH poteza strana nema prikazanu tacnost („—" i
         * „Premalo poteza za procenu tačnosti."). Tacnost se sklapa iz poteza, pa
         * je kod 4 poteza jedan potez cetvrtina rezultata, a prvi potezi su skoro
         * uvek otvaranje sa gubitkom ~0.
         */
        const val MIN_MOVES_FOR_ACCURACY = 5

        /**
         * Procenat pobede (0…100) iz centipiona, Lichess `WinPercent.fromCentiPawns`:
         * `50 + 50 · (2 / (1 + e^(−0,00368208 · cp)) − 1)`, cp ogranicen na ±1000.
         */
        fun winPercent(centipawns: Int): Double {
            val cp = centipawns.coerceIn(-1000, 1000).toDouble()
            return 50 + 50 * (2 / (1 + exp(-0.00368208 * cp)) - 1)
        }

        /**
         * Koliko je procenata pobede potez odneo igracu koji ga je odigrao (≥ 0).
         * `after` je iz ugla protivnika (vidi `cpLoss`), pa je igracev procenat
         * posle poteza `winPercent(−after)`.
         */
        fun winPercentLoss(before: EngineScore, after: EngineScore): Double =
            max(0.0, winPercent(before.centipawns) - winPercent(-after.centipawns))

        /**
         * Tacnost JEDNOG poteza — Lichess formula iz spec-a 5.5, ogranicena na 0…100.
         *
         * Ulaz je GUBITAK PROCENTA POBEDE, ne centipioni. Konstante su Lichess-ove i
         * kalibrisane za procenat pobede; do dizajnerskog pregleda (§ 17) formula je
         * primenjivana na prosecan gubitak u centipionima, pa je prosek od ~80 cp vec
         * davao 0 % — jedna ostavljena figura u kratkoj partiji obarala je stranu na
         * „0,0 %". iOS: `GameAnalysis.accuracy(winPercentLoss:)`.
         */
        fun accuracy(winPercentLoss: Double): Double {
            val raw = 103.1668 * exp(-0.04354 * winPercentLoss) - 3.1669
            return min(100.0, max(0.0, raw))
        }

        /**
         * Tezina svakog poteza po Lichess-u: standardna devijacija (populaciona)
         * procenta pobede BELOG u kliznom prozoru pozicija, ogranicena na 0,5…12.
         * Prozor je N/10 poteza, ogranicen na 2…8; prvih `prozor − 2` poteza dobija
         * prvi prozor. Ostar deo partije (sanse se ljuljaju) nosi vise od mirnog.
         * Vraca tacno N tezina za N+1 ocena; `weights[i]` pripada potezu `i`.
         * iOS: `GameAnalysis.volatilityWeights(scores:)`.
         */
        fun volatilityWeights(scores: List<EngineScore>): List<Double> {
            val moveCount = scores.size - 1
            if (moveCount <= 0) return emptyList()
            // `scores[i]` je iz ugla strane na potezu; na parnom indeksu to je beli.
            val white = scores.indices.map { i ->
                winPercent(if (i % 2 == 0) scores[i].centipawns else -scores[i].centipawns)
            }
            val size = (moveCount / 10).coerceIn(2, 8)
            val windows = ArrayList<List<Double>>()
            repeat(max(0, min(size, white.size) - 2)) { windows.add(white.take(size)) }
            for (start in 0..(white.size - size)) windows.add(white.subList(start, start + size))
            return windows.take(moveCount).map { xs ->
                val mean = xs.average()
                val variance = xs.sumOf { (it - mean) * (it - mean) } / xs.size
                sqrt(variance).coerceIn(0.5, 12.0)
            }
        }

        /**
         * Sklapa analizu iz N notacija i N+1 ocena.
         *
         * `scores[i]` je ocena pozicije PRE poteza `i`; `scores[i+1]` je ocena
         * pozicije posle njega. Zato je svaka pozicija pretrazena tacno jednom, a
         * ne dvaput — partija od 40 poteza trazi 81 pretragu, ne 160.
         */
        fun build(
            notations: List<String>,
            scores: List<EngineScore>,
            engineBestMatched: List<Boolean>
        ): GameAnalysis {
            // Neslaganje duzina je greska pozivaoca. Vracamo praznu analizu umesto
            // da indeksiramo van granica — pad ovde bi srusio aplikaciju posle
            // partije, u trenutku kad korisnik nista nije ni trazio osim rezultata.
            if (scores.size != notations.size + 1 ||
                engineBestMatched.size != notations.size ||
                notations.isEmpty()
            ) {
                return GameAnalysis(moves = emptyList(), whiteAccuracy = 100.0, blackAccuracy = 100.0)
            }

            val moves = ArrayList<AnalyzedMove>(notations.size)
            for (i in notations.indices) {
                val before = scores[i]
                val after = scores[i + 1]
                val loss = cpLoss(before, after)
                moves.add(
                    AnalyzedMove(
                        ply = i,
                        notation = notations[i],
                        byWhite = i % 2 == 0,
                        cpLoss = loss,
                        moveClass = MoveClass.classify(loss, engineBestMatched[i]),
                        scoreBefore = before,
                        scoreAfter = after
                    )
                )
            }

            val weights = volatilityWeights(scores)

            // Tacnost strane = Lichess `AccuracyPercent.gameAccuracy`: polovina je
            // sredina ponderisana nestabilnoscu pozicije, polovina HARMONIJSKA
            // sredina. Harmonijska je ta koja ne da jednom promasaju da se razvodni
            // kroz mnogo dobrih poteza — do 2026-10 ovde je bila aritmeticka sredina
            // i ostavljena dama u partiji od 9 poteza davala je ~89 % (sada ~44 %).
            // iOS: isto mesto u `GameAnalysis.build`.
            fun accuracyFor(white: Boolean): Double {
                val side = moves.filter { it.byWhite == white }
                if (side.isEmpty()) return 100.0
                val accuracies = side.map { accuracy(winPercentLoss(it.scoreBefore, it.scoreAfter)) }
                val sideWeights = side.map { weights[it.ply] }
                val weighted = accuracies.indices.sumOf { accuracies[it] * sideWeights[it] } / sideWeights.sum()
                // Lichess `Maths.harmonicMean` racuna sa najmanje 1, da potez sa 0 %
                // ne da deljenje nulom.
                val harmonic = accuracies.size / accuracies.sumOf { 1.0 / max(1.0, it) }
                return (weighted + harmonic) / 2
            }

            return GameAnalysis(
                moves = moves,
                whiteAccuracy = accuracyFor(true),
                blackAccuracy = accuracyFor(false)
            )
        }
    }

    /** Broj poteza jedne strane. */
    fun moveCount(white: Boolean): Int = moves.count { it.byWhite == white }

    /**
     * Tacnost za prikaz: `null` kad strana ima manje od `MIN_MOVES_FOR_ACCURACY`
     * sopstvenih poteza (ekran tada pokazuje „—").
     */
    fun reliableAccuracy(white: Boolean): Double? {
        if (moveCount(white) < MIN_MOVES_FOR_ACCURACY) return null
        return if (white) whiteAccuracy else blackAccuracy
    }

    /**
     * `.best` se ISKLJUCUJE. `classify` daje `.best` cim je odigran potez
     * motora, bez obzira na izmeren gubitak — a gubitak ume da ispadne
     * veliki i za potez motora, jer se pozicija pre poteza i pozicija posle
     * njega pretrazuju nezavisno, iz razlicitih cvorova. Bez ovog filtera
     * kartica ume da kaze „Prelomni potez 15…Sxd7 (−1000)" i da je pritom
     * oboji akcentom, kao najbolji potez u partiji. Potez koji bi i motor
     * odigrao nije prelomni potez, ma sta merenje reklo.
     */
    val turningPoint: AnalyzedMove?
        get() {
            val worst = moves.filter { it.moveClass != MoveClass.BEST }
                .maxByOrNull { it.cpLoss } ?: return null
            return if (worst.cpLoss >= TURNING_POINT_MIN_LOSS) worst else null
        }
}
