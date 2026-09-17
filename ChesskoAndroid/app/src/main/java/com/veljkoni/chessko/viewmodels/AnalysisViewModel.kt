package com.veljkoni.chessko.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veljkoni.chessko.logic.PositionEval
import com.veljkoni.chessko.logic.StockfishEngine
import com.veljkoni.chessko.models.ChessMove
import com.veljkoni.chessko.models.GameAnalysis
import com.veljkoni.chessko.models.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - Analiza partije: tok, ne racunica
//
// Prenosi `Chessko/ViewModels/AnalysisViewModel.swift`. Sva racunica je u
// `GameAnalysis` (Task 1, cist Kotlin, JVM-testiran); ovde je samo
// orkestracija: sta se salje motoru, kako se prijavljuje napredak i sta se
// desava kad korisnik ode sa ekrana.

/**
 * Greske analize. NAMERNO bez teksta ovde: loc() kljuceve dodaje ekran
 * analize (Task 5 ove faze) — `LocTest.everyLocCallInTheSourceHasAKeyInTheDictionary`
 * skenira CEO izvor i pada na svaki `loc()`/`locF()` literal bez unosa u
 * recniku, a ovaj fajl (Task 4) namerno ne dodaje nijedan. Ekran mapira svaku
 * vrednost u lokalizovanu poruku.
 */
enum class AnalysisError {
    /** `states`/`notations` se ne poklapaju po ugovoru N+1, ili partija nema poteza. */
    NOT_ENOUGH_MOVES,

    /** `StockfishEngine.engineStarted == false` — motor jos nije pokrenut. */
    ENGINE_UNAVAILABLE,

    /**
     * Motor je pokrenut ali nije potvrdio spremnost (`waitUntilReady()` je
     * istekla). Zasebno od [ENGINE_UNAVAILABLE] zato sto su to razlicite stvari
     * i za korisnika: „nema motora" se ne popravlja cekanjem, „jos nije spreman"
     * se popravlja.
     */
    ENGINE_NOT_READY,

    /** Bar jedna pretraga nije dala ocenu (timeout ili neuspeh motora). */
    SEARCH_FAILED
}

/**
 * Pokrece analizu partije i drzi njen tok.
 *
 * **N+1 pretraga, ne 2N.** Ocena pozicije PRE poteza daje „najbolje sto se
 * moglo", a ocena pozicije POSLE njega, sa obrnutim znakom, daje „sta je
 * odigrano". Svaka pozicija se zato pretrazuje TACNO JEDNOM: partija od 40
 * poteza trazi 81 pretragu, ne 160. `GameAnalysis.build` to zahteva u
 * tipovima (`scores.size == notations.size + 1`) — ako se to ne poklopi,
 * uzrok je u rekonstrukciji pozicija, ne u nedostatku jos jedne pretrage.
 *
 * **Otkazivanje je ovde DRUGACIJE nego na iOS-u — najvazniji deo ove klase.**
 * iOS pravi ZASEBAN primerak motora po analizi. Android to ne moze:
 * `StockfishEngine` je `object` singleton sa JEDNIM `searchMutex`-om. Dok
 * analiza drzi taj mutex, nijedna druga pretraga ne prolazi — ukljucujuci AI
 * potez u zivoj partiji. Zato:
 *   - analiza se pokrece u `viewModelScope` i **otkazuje u `onCleared()`**,
 *     ali se na to NE oslanja kao jedinu zastitu — ekran analize (Task 5)
 *     mora zvati `cancel()` eksplicitno iz `DisposableEffect`-a pri izlasku.
 *     Bez toga bi korisnik koji izadje sa ekrana i ODMAH pocne novu partiju
 *     cekao da se svih ~81 pretraga zavrsi pre nego sto AI uopste odigra
 *     prvi potez — `getBestMove` stoji iza istog mutexa i ceka svoj red.
 *   - otkazivanje usred `StockfishEngine.evaluate()` je bezbedno:
 *     `Mutex.withLock` oslobadja bravu u `finally` cak i kad korutina bude
 *     otkazana dok ceka unutar brave, pa se mutex vraca ODMAH — sledeca
 *     pretraga (npr. AI potez) ne ceka da istekne 81. ciklus.
 */
class AnalysisViewModel : ViewModel() {

    var analysis by mutableStateOf<GameAnalysis?>(null)
        private set
    var progress by mutableStateOf(0f)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var error by mutableStateOf<AnalysisError?>(null)
        private set

    private var job: Job? = null

    /** Dubina je fiksna, isto kao iOS (spec 5.5) — analiza mora trajati predvidivo. */
    private val depth = 12

    /**
     * `states` je `GameViewModel.allHistoryStates`: N+1 pozicija za N poteza,
     * pocev od pocetne. `notations` je `gameState.moveNotations`, duzine N.
     */
    fun start(states: List<Pair<GameState, ChessMove?>>, notations: List<String>) {
        if (isRunning) return

        // Neslaganje duzina nije korisnikov problem i ne sme da srusi ekran.
        if (states.size != notations.size + 1 || notations.isEmpty()) {
            error = AnalysisError.NOT_ENOUGH_MOVES
            return
        }
        if (!StockfishEngine.engineStarted) {
            error = AnalysisError.ENGINE_UNAVAILABLE
            return
        }

        analysis = null
        error = null
        isRunning = true
        progress = 0f

        val positions = states.map { it.first }
        val playedMoves = states.drop(1).map { it.second }
        val total = positions.size

        job = viewModelScope.launch(Dispatchers.Default) {
            // `engineStarted` gore NIJE dokaz spremnosti -- postaje `true`
            // sinhrono u `StockfishEngine.start()`, pre kopiranja ~140 MB NNUE
            // mreza i pre `uci`/`setoption`/`isready`. Pretraga poslata pre toga
            // ne degradira tiho nego GASI CEO PROCES: `engine.cpp:153-155`
            // (`Engine::go` -> `verify_networks()`) -> `nnue/network.cpp:267`
            // (`exit(EXIT_FAILURE)`), bez dijaloga o padu, ista vrsta nestanka
            // kao iOS-ov SIGPIPE. Analiza su nova vrata ka motoru, a u rezimu
            // „Igra sa prijateljem" i JEDINA: prvo pokretanje -> partija u
            // dvoje -> mat u 4 poluteza -> „Analiziraj partiju", bez ijednog
            // ranijeg poziva motoru. Zato se spremnost ovde stvarno ceka.
            if (!StockfishEngine.waitUntilReady()) {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    error = AnalysisError.ENGINE_NOT_READY
                }
                return@launch
            }

            val evals = ArrayList<PositionEval>(total)
            for (i in 0 until total) {
                // Izlaz na PRVOJ neuspeloj poziciji, isto kao iOS
                // (`StockfishBridge.analyze`: `guard let eval else { return nil }`).
                // Ranije se cekalo svih N+1 pretraga pa se tek onda gledalo ima
                // li `null`-a -- korisnik bi odstajao celu analizu da bi mu se
                // reklo da nije uspela.
                val eval = StockfishEngine.evaluate(positions[i].fen, depth)
                if (eval == null) {
                    withContext(Dispatchers.Main) {
                        isRunning = false
                        error = AnalysisError.SEARCH_FAILED
                    }
                    return@launch
                }
                evals.add(eval)
                val done = i + 1
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / total
                }
            }

            val scores = evals.map { it.score }

            // Potez motora (UCI string) iz pozicije `i` prema stvarno odigranom
            // potezu. `ChessMove.fromUCI` treba poziciju PRE poteza da razresi
            // string u konkretan potez (from/to/flag); `ChessMove` je `data class`,
            // pa `==` poredi sve troje — tacno i za promociju i za rokadu.
            val matched = playedMoves.mapIndexed { i, played ->
                val bestUci = evals[i].bestMove
                val best = bestUci?.let { ChessMove.fromUCI(it, positions[i]) }
                played != null && best != null && played == best
            }

            val result = GameAnalysis.build(
                notations = notations,
                scores = scores,
                engineBestMatched = matched
            )
            withContext(Dispatchers.Main) {
                analysis = result
                isRunning = false
            }
        }
    }

    /**
     * Zove se kad korisnik napusti ekran analize (vidi komentar klase). Bez
     * ovoga motor nastavlja da melje preostale pozicije u pozadini iako
     * rezultat vise nema ko da vidi — i drzi `searchMutex` dok to radi.
     */
    fun cancel() {
        job?.cancel()
        job = null
        isRunning = false
    }

    override fun onCleared() {
        super.onCleared()
        cancel()
    }
}
