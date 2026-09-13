package com.veljkoni.chessko.viewmodels

import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.logic.locF

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veljkoni.chessko.logic.HapticManager
import com.veljkoni.chessko.logic.MoveGenerator
import com.veljkoni.chessko.logic.ProgressStore
import com.veljkoni.chessko.logic.PuzzleRating
import com.veljkoni.chessko.logic.PuzzleRepository
import com.veljkoni.chessko.logic.SoundManager
import com.veljkoni.chessko.logic.StatsManager
import com.veljkoni.chessko.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class PuzzlePhase {
    LOADING, NETWORK_ERROR, PLAYING, WRONG_MOVE, SOLVED, SHOWING_SOLUTION
}

/**
 * `autoLoadDaily`: `false` za instance koje ekran koraka Puta pravi za
 * `StepPracticeView` (`PuzzleViewModel(app, autoLoadDaily = false)`).
 *
 * Bez ovoga je `init` bezuslovno zvao `loadDailyPuzzle()`, koji radi na
 * `Dispatchers.IO` i zavrsava se pozivom `setupPuzzle()` na glavnoj niti — a
 * `startStepPractice()` iz `StepPracticeView`-a radi ISTO. Dva asinhrona
 * poziva na istoj svezoj instanci se trkaju za `setupPuzzle()`; ko god stigne
 * POSLEDNJI pobedi. Izmereno: dnevni poziv je stigao PRVI (upisao svoj FEN
 * dok je `mode` vec bio `STEP`, jer `startStepPractice()` postavlja `mode`
 * SINHRONO pre sopstvenog IO poziva), a red koraka je zatim ispravno
 * pregazio taj upis — ali redosled NIJE zagarantovan, pa bi obrnut tajming
 * ostavio ekran koraka da prikazuje DNEVNI zadatak dok brojac napretka i
 * dalje govori o redu koraka.
 */
class PuzzleViewModel(
    application: Application,
    private val autoLoadDaily: Boolean = true
) : AndroidViewModel(application) {

    private val soundManager = SoundManager(application)
    private val hapticManager = HapticManager(application)
    private val sharedPrefs = application.getSharedPreferences(StatsManager.PUZZLE_PREFS_NAME, Context.MODE_PRIVATE)
    private val statsManager = StatsManager.getInstance(application)
    private val progressStore = ProgressStore.getInstance(application)
    private var puzzleHadError = false

    // `by lazy`: prva upotreba kopira 7 MB iz `assets` u `filesDir`, pa se to
    // ne radi u konstruktoru ViewModel-a (glavna nit pri otvaranju taba).
    private val repository by lazy { PuzzleRepository(getApplication()) }

    // Skup id-jeva vec resenih zadataka (nezavisno od kalendara - vidi
    // `solvedDates`). Ucitava se JEDNOM iz `SharedPreferences`; upisuje se tek
    // kad je zadatak STVARNO resen (prikaz resenja ga ne upisuje), u oba
    // rezima (DAILY i PRACTICE) - koristi se za iskljucivanje iz `nextPuzzle()`.
    private val solvedPuzzleIds: MutableSet<String> =
        sharedPrefs.getStringSet(SOLVED_PUZZLE_IDS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun persistSolvedPuzzleIds() {
        sharedPrefs.edit().putStringSet(SOLVED_PUZZLE_IDS_KEY, solvedPuzzleIds).apply()
    }

    // Observable states
    var gameState by mutableStateOf(GameState.initial())
        private set
    var playerColor by mutableStateOf(PieceColor.WHITE)
        private set
    var selectedPosition by mutableStateOf<Position?>(null)
        private set
    var legalMovesForSelected by mutableStateOf<List<ChessMove>>(emptyList())
        private set
    var lastMove by mutableStateOf<ChessMove?>(null)
        private set

    var currentPuzzle by mutableStateOf<ChessPuzzle?>(null)
        private set
    var phase by mutableStateOf(PuzzlePhase.LOADING)
        private set
    var networkErrorMessage by mutableStateOf("")
        private set

    /// Korak Puta (`STEP`) je fiksan red zadataka umesto jednog — `practice`
    /// i `test` se razlikuju SAMO po `stepRequiresFlawless`, pa je to
    /// zastavica a ne cetvrti tok.
    enum class PuzzleMode { DAILY, PRACTICE, STEP }

    var mode by mutableStateOf(PuzzleMode.DAILY)
        private set

    var selectedDate by mutableStateOf(LocalDate.now())
        private set
    var solvedDates by mutableStateOf(setOf<String>())
        private set

    // MARK: - Korak Puta

    /// Red zadataka za tekuci korak i koliko ih je reseno. Puni se JEDNOM, u
    /// `startStepPractice()`: bez toga traka napretka ne bi imala ukupan
    /// broj, a `test` koji krece ispocetka ne bi mogao da garantuje da su
    /// zadaci NOVI.
    var stepQueue by mutableStateOf<List<ChessPuzzle>>(emptyList())
        private set
    var stepSolved by mutableStateOf(0)
        private set

    /// Prva greska u `test` koraku obara ceo korak. Dok je `true`, tabla ne
    /// prima poteze (`isPlayerTurn`) — ekran ce se za ~1,4s sam vratiti na
    /// pocetak koraka sa NOVIM zadacima.
    var stepFailed by mutableStateOf(false)
        private set

    /// Korak iz kog je red napunjen — cuva se da se `test` moze ponoviti sa
    /// novim zadacima bez pomoci ekrana.
    private var currentStep: CurriculumStep? = null

    /// `true` samo za `test`; `practice` toleriše grešku.
    var stepRequiresFlawless by mutableStateOf(false)
        private set

    val stepProgress: Pair<Int, Int>
        get() = stepSolved to stepQueue.size

    private fun clearStepState() {
        stepQueue = emptyList()
        stepSolved = 0
        stepFailed = false
        currentStep = null
        stepRequiresFlawless = false
    }

    private data class StepPlan(
        val themes: List<String>, val count: Int, val range: IntRange, val requireFlawless: Boolean
    )

    private fun planFor(step: CurriculumStep): StepPlan? = when (val k = step.kind) {
        is StepKind.Practice -> StepPlan(k.themes, k.count, k.ratingRange, false)
        is StepKind.Test -> StepPlan(k.themes, k.count, k.ratingRange, true)
        else -> null
    }

    private var rawMoves = listOf<String>()
    private var movePointer = 0

    /// `true` dok traje skriptovana pauza (600 ms) izmedju igracevog TACNOG
    /// poteza i protivnickog odgovora. Bez ovoga je `isPlayerTurn` u tom
    /// prozoru `true`, pa brz dodir stize u `attempt()` i poredi se sa
    /// PROTIVNICKIM potezom iz `rawMoves` — greska koju korisnik nije napravio,
    /// a kosta ga niza (`currentPuzzleStreak` na 0) i Elo rejtinga, i trajno
    /// gasi kredit za zadatak (`puzzleHadError`). Izmereno: cetiri dodira za
    /// 101 ms obore niz, a ekran i dalje cestita kad se zadatak zavrsi tacno.
    ///
    /// `PuzzlePhase.LOADING` se ovde NE sme koristiti kao gejt: `PuzzleView` u
    /// toj fazi crta ekran ucitavanja umesto table, pa bi tabla treptala posle
    /// svakog tacnog poteza.
    private var awaitingOpponent = false

    /// Raste pri SVAKOM ucitavanju zadatka. Svaka odlozena korutina (prvi
    /// protivnikov potez, odgovor posle tacnog poteza, reprodukcija resenja)
    /// upamti vrednost pre `delay` i odustane ako se u medjuvremenu promenila.
    ///
    /// Bez toga zaostala korutina odigra potez nad NOVIM zadatkom: `rawMoves`
    /// je vec zamenjen, pa se odigra tudji potez i `movePointer` odmakne za
    /// jedan. Izmereno: tacan potez pa odmah strelica za datum — aplikacija je
    /// sama odigrala IGRACEV potez novog zadatka, `movePointer` je ostao na
    /// protivnickom potezu, i zadatak je postao NERESIV (jedini izlaz
    /// „Prikazi resenje"). Strelice za datum su zive tokom svih tih pauza, pa
    /// se prozor ne moze zatvoriti fazom.
    private var loadGeneration = 0

    val isFlipped: Boolean
        get() = playerColor == PieceColor.BLACK

    val isPlayerTurn: Boolean
        // `!stepFailed`: kad test padne, restart stize tek posle 1.4s. Bez ovoga
        // bi tabla u tom prozoru i dalje primala poteze, a resen zadatak unutar
        // njega bi SAM otkazao restart koji ga je cekao (podize loadGeneration).
        get() = !awaitingOpponent && !stepFailed &&
            (phase == PuzzlePhase.PLAYING || phase == PuzzlePhase.WRONG_MOVE)

    val canGoPrevious: Boolean
        get() = true // Limit dates if desired

    val canGoNext: Boolean
        get() = selectedDate.isBefore(LocalDate.now())

    val statusMessage: String
        get() = when (phase) {
            PuzzlePhase.LOADING -> loc("Učitavam zadatak...")
            // Prevodi se SAMO okvir; `networkErrorMessage` je vec preveden na
            // svom mestu nastanka. Prevod celog sklopa bi trazio kljuc po
            // svakom razlogu greske.
            PuzzlePhase.NETWORK_ERROR -> "${loc("Greška pri učitavanju")}: $networkErrorMessage"
            PuzzlePhase.PLAYING -> if (playerColor == PieceColor.WHITE) loc("Pronađi pravi potez za bele") else loc("Pronađi pravi potez za crne")
            // U testu prva greska nije "pokusaj ponovo" nego kraj pokusaja —
            // poruka mora da kaze sta se upravo desilo, jer se tabla za koji
            // trenutak sama zameni novim zadacima.
            PuzzlePhase.WRONG_MOVE -> if (stepFailed) loc("Greška — test kreće ispočetka") else loc("Pogrešno. Pokušaj ponovo.")
            PuzzlePhase.SOLVED -> loc("Odlično! Zadatak rešen! 🎉")
            PuzzlePhase.SHOWING_SOLUTION -> loc("Rešenje...")
        }

    init {
        // `loadSolvedDates()` je ovde nekad stajao zasebno; sada je suvisan jer
        // `loadDailyPuzzle()` odmah zove `reloadPersistedProgress()`, koji ga
        // ionako zove. Rezultat bi se prepisao u istom dahu.
        //
        // `autoLoadDaily == false` za instance koje pravi `StepPracticeView`:
        // taj ekran zove `startStepPractice()` sam, i ne sme da se trka sa
        // dnevnim ucitavanjem koje mu ovde nista ne znaci.
        if (autoLoadDaily) loadDailyPuzzle()
    }

    fun goToPrevious() {
        val prevDate = selectedDate.minusDays(1)
        loadDate(prevDate)
    }

    fun goToNext() {
        if (canGoNext) {
            val nextDate = selectedDate.plusDays(1)
            loadDate(nextDate)
        }
    }

    private fun loadDate(date: LocalDate) {
        selectedDate = date
        loadDailyPuzzle()
    }

    fun isSolved(date: LocalDate): Boolean {
        return solvedDates.contains(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    private fun markCurrentSolved() {
        val dateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val updated = solvedDates.toMutableSet().apply { add(dateKey) }
        solvedDates = updated
        sharedPrefs.edit().putStringSet(StatsManager.SOLVED_DATES_KEY, updated).apply()
    }

    /// Javna tacka za osvezavanje SAMO kesa napretka, bez diranja zadatka.
    ///
    /// Zove se kad se ekran Zadataka ponovo prikaze. Namerno NE zove
    /// `loadDailyPuzzle()`: taj bi restartovao zadatak u toku, a bas to je vec
    /// jednom bio bug (iOS Faza 2: `.onAppear` je bezuslovno restartovao
    /// napola resen zadatak). Ovde treba osvezi samo ono sto je „Resetuj
    /// statistiku" moglo da promeni sa strane — kvacicu i skup iskljucenih.
    fun refreshPersistedProgress() = reloadPersistedProgress()

    /// Ponovo cita napredak sa diska u kes.
    ///
    /// `solvedPuzzleIds` i `solvedDates` se ucitavaju JEDNOM, pri stvaranju
    /// ViewModel-a, a `MainActivity` ga drzi kroz `remember` — instanca
    /// prezivljava prelaske izmedju tabova i otvaranje podesavanja. Kad
    /// „Resetuj statistiku" obrise `chessko_puzzle_prefs` NA DISKU, kes toga ne
    /// zna: kvacica pored datuma ostaje, a vec reseni zadaci ostaju iskljuceni
    /// iz vezbanja — sve do ubijanja procesa.
    ///
    /// Zato se zove na SVAKOM ulasku u nov zadatak, u oba rezima, I iz
    /// `refreshPersistedProgress()` kad se ekran ponovo prikaze.
    ///
    /// iOS ima istoimenu funkciju, ali NEMA ovo reseno do kraja: njegov
    /// `loadDailyPuzzle()` nosi `guard currentPuzzle == nil || isUnavailable`,
    /// pa `.onAppear` na vec ucitanom zadatku ne stigne do osvezavanja. Isti
    /// propust dakle postoji i tamo; ovde je zatvoren razdvajanjem osvezavanja
    /// kesa od ucitavanja zadatka. Ne prepisivati ovo nazad na „iOS to ima
    /// reseno" — provereno da nema.
    private fun reloadPersistedProgress() {
        solvedPuzzleIds.clear()
        sharedPrefs.getStringSet(SOLVED_PUZZLE_IDS_KEY, emptySet())?.let { solvedPuzzleIds.addAll(it) }
        loadSolvedDates()
    }

    private fun loadSolvedDates() {
        solvedDates = sharedPrefs.getStringSet(StatsManager.SOLVED_DATES_KEY, emptySet()) ?: emptySet()
    }

    fun loadDailyPuzzle() {
        reloadPersistedProgress()
        clearStepState()
        // Reset na DAILY: bez ovoga bi vezbanje (PRACTICE) ostalo "zaglavljeno"
        // posle povratka na zadatak dana (retry dugme, promena datuma), pa bi
        // UI (traka za datum, poruke) i dalje gejtovao na pogresan rezim.
        mode = PuzzleMode.DAILY
        phase = PuzzlePhase.LOADING
        currentPuzzle = null
        puzzleHadError = false
        awaitingOpponent = false
        loadGeneration++

        val epochStart = LocalDate.of(1970, 1, 1)
        val dayIndex = ChronoUnit.DAYS.between(epochStart, selectedDate)

        viewModelScope.launch(Dispatchers.IO) {
            // Citanje iz lokalne baze je brzo, ali ostaje na IO niti: prvo
            // pokretanje kopira 7 MB iz `assets` u `filesDir`.
            val puzzle = repository.dailyPuzzle(dayIndex)
            withContext(Dispatchers.Main) {
                if (puzzle != null) setupPuzzle(puzzle) else showUnavailable()
            }
        }
    }

    /// Vezbanje bez kraja: bira zadatak po rejtingu igraca, iskljucujuci vec
    /// resene. Prozor se progresivno siri — inace bi korisnik koji je resio sve
    /// u svom opsegu dobio prazan ekran bez objasnjenja.
    fun nextPuzzle() {
        reloadPersistedProgress()
        clearStepState()
        mode = PuzzleMode.PRACTICE
        // Bez ovoga bi `puzzleHadError` iz PRETHODNOG zadatka (dnevnog ili
        // vezbovnog) ostao `true` i tiho progutao snimanje Elo rejtinga i
        // streaka za OVAJ, potpuno nov zadatak (i tacan i pogresan potez u
        // `attempt()` proveravaju bas ovaj flag).
        puzzleHadError = false
        awaitingOpponent = false
        loadGeneration++
        viewModelScope.launch(Dispatchers.IO) {
            val r = statsManager.puzzleRating
            val windows = listOf(
                PuzzleRating.practiceWindow(r),
                maxOf(PuzzleRating.MIN, r - 400)..minOf(PuzzleRating.MAX, r + 400),
                PuzzleRating.MIN..PuzzleRating.MAX
            )
            var found = windows.firstNotNullOfOrNull {
                repository.randomPuzzle(it, solvedPuzzleIds)
            }
            // Poslednje pribeziste: korisnik je resio sve. Bolje ponovljen
            // zadatak nego prazan ekran.
            if (found == null) found = repository.randomPuzzle(PuzzleRating.MIN..PuzzleRating.MAX, emptySet())
            withContext(Dispatchers.Main) {
                if (found != null) setupPuzzle(found) else showUnavailable()
            }
        }
    }

    /// Repozitorijum ne vraca zadatak (isporucena baza od 20.000 redova ovo
    /// prakticno cini nedostizivim, ali `randomPuzzle`/`dailyPuzzle` su
    /// nullable pa se mora pokriti). Ista poruka i faza koje je ranije
    /// koristio mrezni put za "nema rezultata".
    private fun showUnavailable() {
        networkErrorMessage = loc("Nema dostupnih zadataka")
        phase = PuzzlePhase.NETWORK_ERROR
    }

    // MARK: - Korak Puta (pokretac `practice` i `test`)

    /// Puni red zadataka za korak Puta i pokrece prvi. Poziva se i pri ulasku
    /// na ekran i pri ponovnom pokretanju palog `test`-a — u oba slucaja su
    /// zadaci NOVI (upit je `ORDER BY RANDOM()`), sto je i smisao "krece
    /// ispocetka".
    fun startStepPractice(step: CurriculumStep) {
        val plan = planFor(step)
        if (plan == null) {
            // Lekcija i partija imaju sopstvene ekrane; da neko ovde dovede
            // takav korak, prazan ekran bi bio gori od poruke.
            networkErrorMessage = loc("Korak nije dostupan")
            phase = PuzzlePhase.NETWORK_ERROR
            return
        }

        reloadPersistedProgress()
        clearStepState()
        currentStep = step
        stepRequiresFlawless = plan.requireFlawless
        mode = PuzzleMode.STEP
        phase = PuzzlePhase.LOADING
        currentPuzzle = null
        puzzleHadError = false
        awaitingOpponent = false
        loadGeneration++

        viewModelScope.launch(Dispatchers.IO) {
            val playerRating = statsManager.puzzleRating
            // Prozor rejtinga presecen sa opsegom koraka; kad je presek prazan,
            // prednost ima opseg koraka (`PuzzleRating.stepWindow`).
            val window = PuzzleRating.stepWindow(playerRating, plan.range)

            // Popustanje ide redom: prvo uzi prozor bez vec resenih, pa ceo
            // opseg koraka, pa isto to SA vec resenim. Tema se ne popusta ni u
            // jednom koraku — ona je ono sto korak uci; radije ponovljen
            // zadatak na pravu temu nego nov na pogresnu.
            val attempts: List<Pair<IntRange, Set<String>>> = listOf(
                window to solvedPuzzleIds,
                plan.range to solvedPuzzleIds,
                window to emptySet(),
                plan.range to emptySet()
            )
            var best = emptyList<ChessPuzzle>()
            for ((range, excl) in attempts) {
                val found = repository.puzzlesForStep(plan.themes, range, excl, plan.count)
                if (found.size > best.size) best = found
                if (best.size >= plan.count) break
            }

            withContext(Dispatchers.Main) {
                // Kraci red od trazenog je prihvatljiv (korak se zavrsava kad
                // se resi sve sto je u redu); PRAZAN nije — to je ekran bez
                // zadatka.
                if (best.isEmpty()) {
                    showUnavailable()
                } else {
                    stepQueue = best
                    setupPuzzle(best[0])
                }
            }
        }
    }

    /// Ucitava sledeci zadatak iz reda. Isti reset kao `loadDailyPuzzle()`
    /// (nov `loadGeneration` gasi zaostale odlozene poteze prethodnog
    /// zadatka), ali NE dira red ni brojac resenih.
    private fun loadStepPuzzle(index: Int) {
        if (index < 0 || index >= stepQueue.size) return
        phase = PuzzlePhase.LOADING
        currentPuzzle = null
        puzzleHadError = false
        awaitingOpponent = false
        loadGeneration++
        setupPuzzle(stepQueue[index])
    }

    /// Zove se posle svakog resenog zadatka u koraku. U `test`-u greska korak
    /// vec obara pre ovoga (`failStepIfTest`), pa je ovde dovoljno brojati.
    private fun advanceStepAfterSolve() {
        if (mode != PuzzleMode.STEP) return
        val step = currentStep ?: return

        stepSolved++

        if (stepSolved < stepQueue.size) {
            // Jos ima zadataka: kratka pauza da korisnik vidi da je resio, pa
            // sledeci. Odlozena korutina se, kao i sve ostale, gasi ako se u
            // medjuvremenu ucita nesto drugo.
            val generation = loadGeneration
            val next = stepSolved
            viewModelScope.launch {
                delay(900)
                if (generation != loadGeneration) return@launch
                loadStepPuzzle(next)
            }
            return
        }

        // `stepRequiresFlawless && stepFailed` je ovde nedostizno (pao test se
        // restartuje, a restart nulira `stepFailed`), ali stoji da bi pravilo
        // "test se zavrsava SAMO bez greske" bilo iskazano na mestu gde se
        // korak zaista zavrsava.
        if (stepRequiresFlawless && stepFailed) return
        progressStore.completeStep(step.id)
    }

    /// Prva greska u `test` koraku obara ceo korak. Tabla ostaje na mestu
    /// ~1,4s (status kaze zasto), pa se korak pokrece iznova sa NOVIM
    /// zadacima.
    private fun failStepIfTest() {
        if (mode != PuzzleMode.STEP || !stepRequiresFlawless || stepFailed) return
        stepFailed = true

        val generation = loadGeneration
        val step = currentStep
        viewModelScope.launch {
            delay(1400)
            if (generation != loadGeneration || step == null) return@launch
            startStepPractice(step)
        }
    }

    private fun setupPuzzle(puzzle: ChessPuzzle) {
        // Ucitavanje ide preko IO korutine, pa dva brza zahteva (dve strelice
        // za datum zaredom) mogu da stignu dovde OBA. Bez inkrementa bas ovde
        // bi prvi `setupPuzzle` ostavio svoju odlozenu korutinu na istoj
        // generaciji kao drugi, pa bi ona odigrala potez nad zadatkom koji je
        // drugi u medjuvremenu postavio.
        loadGeneration++
        awaitingOpponent = false

        val state = GameState.fromFEN(puzzle.fen)
        if (state == null) {
            networkErrorMessage = loc("Neispravan FEN u zadatku")
            phase = PuzzlePhase.NETWORK_ERROR
            return
        }

        currentPuzzle = puzzle
        rawMoves = puzzle.uciMoves
        movePointer = 0
        selectedPosition = null
        legalMovesForSelected = emptyList()
        lastMove = null

        playerColor = state.currentTurn.opposite
        gameState = state

        val generation = loadGeneration
        viewModelScope.launch {
            delay(400)
            if (generation != loadGeneration) return@launch
            applyNextComputerMove()
        }
    }

    fun tap(position: Position) {
        if (!isPlayerTurn) return

        val selected = selectedPosition
        if (selected != null) {
            val move = legalMovesForSelected.firstOrNull { it.to == position }
            if (move != null) {
                attempt(move)
                return
            }
        }

        val piece = gameState.board[position.row][position.col]
        if (piece != null && piece.color == playerColor) {
            hapticManager.selection()
            selectedPosition = position
            legalMovesForSelected = MoveGenerator.legalMoves(playerColor, gameState)
                .filter { it.from == position }
            if (phase == PuzzlePhase.WRONG_MOVE) {
                phase = PuzzlePhase.PLAYING
            }
        } else {
            selectedPosition = null
            legalMovesForSelected = emptyList()
        }
    }

    private fun attempt(move: ChessMove) {
        if (movePointer >= rawMoves.size) return
        val expected = ChessMove.fromUCI(rawMoves[movePointer], gameState) ?: return

        if (move.from != expected.from || move.to != expected.to) {
            hapticManager.warning()
            phase = PuzzlePhase.WRONG_MOVE
            selectedPosition = null
            legalMovesForSelected = emptyList()
            if (!puzzleHadError) {
                puzzleHadError = true
                statsManager.recordPuzzleFailed()
                statsManager.applyPuzzleResult(currentPuzzle?.rating ?: return, solved = false)
            }
            failStepIfTest()
            return
        }

        hapticManager.mediumImpact()
        applyMove(move)
        movePointer++

        if (movePointer >= rawMoves.size) {
            phase = PuzzlePhase.SOLVED
            hapticManager.success()
            currentPuzzle?.let { puzzle ->
                if (solvedPuzzleIds.add(puzzle.puzzleId)) persistSolvedPuzzleIds()
            }
            // Kalendarski dan se oznacava resenim SAMO u DAILY rezimu —
            // vezbovni zadatak nije vezan ni za jedan datum.
            if (mode == PuzzleMode.DAILY) {
                markCurrentSolved()
            }
            if (!puzzleHadError) {
                statsManager.recordPuzzleSolved()
                statsManager.applyPuzzleResult(currentPuzzle?.rating ?: return, solved = true)
            }
            advanceStepAfterSolve()
            return
        }

        phase = PuzzlePhase.PLAYING
        // Prozor u kome tabla NE sme da prima poteze — zatvara ga
        // `applyNextComputerMove()` na svakom svom izlazu.
        awaitingOpponent = true
        val generation = loadGeneration
        viewModelScope.launch {
            delay(600)
            if (generation != loadGeneration) return@launch
            applyNextComputerMove()
        }
    }

    private fun applyNextComputerMove() {
        try {
            if (movePointer >= rawMoves.size) return
            val move = ChessMove.fromUCI(rawMoves[movePointer], gameState) ?: return

            applyMove(move)
            movePointer++
            phase = PuzzlePhase.PLAYING
        } finally {
            // `finally`, ne jedan red na kraju: funkcija ima tri izlaza
            // (iscrpljena lista, nerazresiv potez, normalan kraj) i svaki mora
            // da otvori tablu nazad. Inace bi zadatak ostao zamrznut.
            awaitingOpponent = false
        }
    }

    fun showSolution() {
        // `!awaitingOpponent`: dok se ceka protivnikov odgovor faza je PLAYING,
        // pa je dugme „Prikazi resenje" vidljivo. Bez ovog gejta bi odlozeni
        // potez stigao usred reprodukcije, pregazio SHOWING_SOLUTION nazad u
        // PLAYING i dvaput odmakao `movePointer`.
        if (awaitingOpponent) return
        if (phase != PuzzlePhase.PLAYING && phase != PuzzlePhase.WRONG_MOVE) return
        phase = PuzzlePhase.SHOWING_SOLUTION
        if (!puzzleHadError) {
            puzzleHadError = true
            statsManager.recordPuzzleFailed()
            currentPuzzle?.let { statsManager.applyPuzzleResult(it.rating, solved = false) }
        }

        val generation = loadGeneration
        viewModelScope.launch {
            while (movePointer < rawMoves.size) {
                // Reprodukcija traje ~700 ms po potezu; za to vreme strelice za
                // datum nisu onemogucene (gase se samo na LOADING). Bez ove
                // provere bi petlja nastavila da igra poteze NOVOG zadatka.
                if (generation != loadGeneration) return@launch
                val move = ChessMove.fromUCI(rawMoves[movePointer], gameState) ?: break
                applyMove(move)
                movePointer++
                delay(700)
            }
            if (generation != loadGeneration) return@launch
            phase = PuzzlePhase.SOLVED
        }
    }

    private fun applyMove(move: ChessMove) {
        val captured = gameState.board[move.to.row][move.to.col] != null || move.flag is MoveFlag.EnPassant
        if (captured) {
            soundManager.playCapture()
        } else {
            soundManager.playMove()
        }

        gameState = gameState.applyingForSearch(move)
        lastMove = move
        selectedPosition = null
        legalMovesForSelected = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
    }

    companion object {
        // Kljucevi zive na `StatsManager`-u jer ih i `resetStats()` mora znati.
        // Dupliran literal bi znacio da izmena na jednom mestu tiho ugasi reset.
        private const val SOLVED_PUZZLE_IDS_KEY = StatsManager.SOLVED_PUZZLE_IDS_KEY
    }
}
