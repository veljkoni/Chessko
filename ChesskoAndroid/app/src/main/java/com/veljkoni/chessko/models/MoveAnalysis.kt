package com.veljkoni.chessko.models

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

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
         * Propusten mat daje razliku od ~20.000 centipiona. Bez ove granice bi
         * jedan takav potez sam odredio prosek cele partije i tacnost bi pala na
         * ~0 iako je ostatak partije bio solidan. 1000 je trostruko iznad praga
         * za promasaj (300), pa ne sakriva nijednu gresku.
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

        /** Lichess formula iz spec-a 5.5, ogranicena na 0…100. */
        fun accuracy(avgCpLoss: Double): Double {
            val raw = 103.1668 * exp(-0.04354 * avgCpLoss) - 3.1669
            return min(100.0, max(0.0, raw))
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

            fun accuracyFor(white: Boolean): Double {
                val side = moves.filter { it.byWhite == white }
                if (side.isEmpty()) return 100.0
                val avg = side.sumOf { it.cpLoss }.toDouble() / side.size
                return accuracy(avg)
            }

            return GameAnalysis(
                moves = moves,
                whiteAccuracy = accuracyFor(true),
                blackAccuracy = accuracyFor(false)
            )
        }
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
