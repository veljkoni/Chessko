package com.veljkoni.chessko.logic

import android.content.Context
import android.util.Log
import com.veljkoni.chessko.models.EngineScore
import com.veljkoni.chessko.models.GameState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ocena jedne pozicije + potez koji bi motor odigrao (`bestMove`, UCI string ili
 * `null` za terminalnu poziciju). `bestMove` je ovde potreban da bi se posle, u
 * analizi partije, moglo utvrditi da li je IGRAC odigrao bas potez motora
 * (`engineBestMatched` u `GameAnalysis.build`).
 */
data class PositionEval(val score: EngineScore, val bestMove: String?)

object StockfishEngine {
    private val inputChannel = Channel<String>(Channel.UNLIMITED)
    private val outputChannel = Channel<String>(Channel.UNLIMITED)
    var engineStarted = false
        private set

    private val searchMutex = Mutex()

    /**
     * Postaje `true` cim [listenOutput] vidi "readyok" -- vidi doc-komentar
     * [waitUntilReady] za razlog zasto se postavlja BAS tamo, a ne u samoj
     * [waitUntilReady].
     */
    @Volatile private var readyObserved = false

    // Gornje granice za `for (line in outputChannel)` petlje ispod. Bez njih,
    // ako native motor umre ili "go"/"position" bude progutana, petlja visi
    // ZAUVEK -- unutar `searchMutex.withLock`. `StockfishEngine` je `object`
    // singleton sa JEDNIM mutexom, pa jedna zaglavljena pretraga trajno
    // blokira SVAKI naredni poziv (`getBestMove` -- dakle i zivu partiju
    // protiv racunara -- i `evaluate`), do restarta procesa. Ovaj task
    // uvodi `evaluate` koji ce u analizi partije (Task 4) biti pozivan ~81
    // puta zaredom kroz isti mutex -- izlozenost istoj klasi greske raste za
    // dva reda velicine u odnosu na jedan poziv `getBestMove` po potezu.
    //
    // Vrednosti su PROCENA sa realnom donjom granicom, ne merenje ove tacne
    // stvari (koliko traje TIMEOUT), nego merenje koliko traje NORMALNA
    // pretraga -- iz stvarno uhvacenog izlaza na ovom istom emulatoru
    // (Task 2 ove faze, "P3(deep)" dijagnostika):
    //   `go depth 20` -> "info depth 20 ... time 29315" (~29,3s)
    //   `go depth 22` -> "info depth 22 ... time 43818" (~43,8s)
    // `StockfishLevel.MAXIMUM` (NAJVISI od sest nivoa koje korisnik bira --
    // `SettingsManager.kt:178-184`, dubine 1/3/6/10/14/20; raniji tekst je
    // tvrdio da je jedini, sto nije tacno) trazi dubinu 20, dakle ~29s je
    // gornja granica NORMALNOG toka za koju imamo merenje. 120s je >4x ta vrednost -- dovoljna rezerva za
    // sporiju poziciju ili sporiji uredjaj, a i dalje konacna. `evaluate` se
    // poziva na fiksnoj dubini 12, gde je isti capture izmerio ~1,0s
    // ("info depth 12 ... time 1040"); 30s je >25x ta vrednost. Marginu (4x,
    // 25x) biram procenom, ne merenjem -- Task 6 (jedino preostalo dizanje
    // emulatora u fazi) je proverava na zivom uredjaju; ako se pokaze da
    // normalna pretraga ikad priblizi ovim brojevima, vrednosti treba
    // povecati, ne obrnuto.
    private const val BEST_MOVE_TIMEOUT_MS = 120_000L
    private const val EVALUATE_TIMEOUT_MS = 30_000L

    // Koliko se ceka `bestmove` koji izazove NAS SOPSTVENI "stop" -- vidi
    // [drainUntilBestMove]. Nije isto sto i gornje dve granice: one pokrivaju
    // PRETRAGU (motor racuna), ova pokriva samo REAKCIJU motora na "stop".
    // `engine.cpp:159` -- `stop()` postavlja `threads.stop = true`, atomsku
    // zastavicu koju niti pretrage citaju u svojoj petlji po cvorovima; posle
    // toga motor samo ispise vec pronadjen najbolji potez, bez ijednog novog
    // cvora. IZMERENO na emulatoru (test
    // `cancelledDeepSearchDoesNotPoisonTheNextEvaluation`, logcat 18:01:10.948):
    // ceo put otkazivanje -> "stop" -> procitan `bestmove` nad `go depth 30`
    // koji traje vec 1,5 s -- **1 ms**. Nije iznenadjenje: motor radi u NASEM
    // procesu (JNI), pa nema ni pipe-a ni IPC-a izmedju komande i odgovora.
    // 2000 ms je tri reda velicine iznad izmerenog -- rezerva za sporiji
    // uredjaj i za poziciju sa vise niti, a i dalje konacna i mnogo kraca od
    // bilo koje pretrage. Ako istekne, gubi se samo garancija ciste linije za
    // sledecu pretragu, ne i sam poziv.
    private const val STOP_DRAIN_TIMEOUT_MS = 2_000L

    private external fun startEngine()
    private external fun sendCommand(cmd: String)
    private external fun readOutput(): String

    fun start(context: Context, evalFileName: String, evalFileSmallName: String) {
        if (engineStarted) return
        try {
            System.loadLibrary("stockfishjni")
            startEngine()
            engineStarted = true
            
            // Start reading standard output in a loop
            listenOutput()
            
            // Start sending standard input commands
            CoroutineScope(Dispatchers.IO).launch {
                sendInput()
            }

            // Copy NNUE files from assets to internal storage and set them up
            CoroutineScope(Dispatchers.IO).launch {
                val evalFilePath = copyNNUEFile(context, evalFileName)
                val evalFileSmallPath = copyNNUEFile(context, evalFileSmallName)
                Log.d("StockfishEngine", "NNUE files copied: $evalFilePath, $evalFileSmallPath")
                inputChannel.send("uci")
                inputChannel.send("setoption name EvalFile value $evalFilePath")
                inputChannel.send("setoption name EvalFileSmall value $evalFileSmallPath")
                inputChannel.send("isready")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e("StockfishEngine", "Failed to load stockfishjni library", e)
        } catch (e: Exception) {
            Log.e("StockfishEngine", "Failed to start Stockfish engine", e)
        }
    }

    private fun sendInput() = CoroutineScope(Dispatchers.IO).launch {
        for (cmd in inputChannel) {
            sendCommand(cmd)
        }
    }

    private fun listenOutput() = CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            val output = readOutput()
            if (output.isNotBlank()) {
                Log.d("StockfishEngine", "Stockfish output: $output")
                // Spremnost se belezi OVDE, na jedinom mestu kroz koje prolazi
                // SVAKA linija motora -- ne u `waitUntilReady`. Vidi njen
                // doc-komentar: "readyok" se pojavi tacno jednom, a bilo koja
                // pretraga pre prvog poziva `waitUntilReady` bi ga pojela
                // (`drain` petlja na ulasku u `getBestMove`/`evaluate`).
                if (output.trim() == "readyok") readyObserved = true
                outputChannel.send(output)
            }
        }
    }

    private fun copyNNUEFile(context: Context, fileName: String): String {
        val outFile = context.filesDir.resolve(fileName)
        if (!outFile.exists()) {
            context.assets.open(fileName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }

    /**
     * Ceka da motor stvarno zavrsi ceo startup pipeline (NNUE kopiranje +
     * `uci`/`setoption`/`isready`), ne samo da native proces krene.
     *
     * `engineStarted` postaje `true` SINHRONO u `start()`, ODMAH posle
     * `startEngine()` -- PRE nego sto kopiranje NNUE fajlova (do ~140MB) i UCI
     * handshake uopste pocnu (oni idu u odvojenoj pozadinskoj korutini, vidi
     * `start()`). Zato `engineStarted` NIJE dokaz spremnosti, samo dokaz da je
     * proces pokrenut -- poziv `getBestMove`/`evaluate` odmah posle `start()`
     * moze da posalje "position"/"go" PRE nego sto motor dobije NNUE mrezu.
     *
     * Ceka se "readyok" -- odgovor na "isready" KOJI VEC SALJE `start()`, na
     * kraju svoje pozadinske korutine, posle oba `setoption`-a. Ne salje se
     * sopstveni "isready": drugi bi mogao da stigne PRE internog niza komandi
     * (ako se `start()`-ova NNUE-kopija jos nije zavrsila) i motor bi odgovorio
     * skoro odmah, sto bi ovu funkciju ucinilo bezvrednom -- istom greskom koju
     * ispravlja u odnosu na `engineStarted`.
     *
     * **Zato se cekanje NE radi citanjem `outputChannel`-a, nego polling-om
     * zastavice [readyObserved] koju postavlja [listenOutput].** Razlog je bug
     * koji bi svaka druga varijanta unela: "readyok" stize tacno jednom u
     * zivotu procesa, a SVAKI pozivalac `getBestMove`/`evaluate` prazni kanal
     * na ulasku (`tryReceive` petlja) -- dakle prva pretraga u aplikaciji
     * POJEDE tu liniju i baci je. Funkcija koja bi je posle toga cekala iz
     * kanala visila bi do [timeoutMs] i vratila `false` iako je motor odavno
     * spreman. To nije teorijski redosled: `start()` se zove iz
     * `MainActivity.onCreate`, a prvi AI potez ili prva analiza dolaze sekundama
     * kasnije. Zastavica u [listenOutput] je jedino mesto kroz koje prolazi
     * svaka linija motora pre nego sto je iko moze pojesti.
     *
     * Iz istog razloga funkcija vise **NE uzima `searchMutex`**: nista ne cita
     * iz kanala, pa nema sta da otme drugoj pretrazi -- a drzati jedini mutex
     * do 30 s bilo bi tacno ona klasa greske protiv koje Taskovi 3 i 4 uvode
     * timeout i `stop` u [getBestMove]/[evaluate].
     *
     * **Istorija, da se ne ponovi:** prva verzija je citala kanal do "readyok"
     * bez ikakvog pamcenja, pa je bila upotrebljiva TACNO JEDNOM po zivotu
     * procesa; drugi poziv je visio 30 s **drzeci mutex**. Nadjeno u Fazi 6e,
     * Task 6 (`StockfishEvaluateTest`, `time="30.019"` u XML-u). Popravka je
     * tada bila zastavica koju postavlja SAMA ova funkcija -- sto resava drugi
     * poziv, ali ne i prvi poziv posle vec obavljene pretrage. Talas ispravki
     * pred spajanje je to dovrsio premestanjem zastavice u [listenOutput].
     */
    suspend fun waitUntilReady(timeoutMs: Long = 30_000L): Boolean {
        if (!engineStarted) return false
        if (readyObserved) return true
        return withTimeoutOrNull(timeoutMs) {
            while (!readyObserved) delay(20)
            true
        } ?: false
    }

    /**
     * Cita `outputChannel` dok ne progura `bestmove` liniju koju je izazvao NAS
     * "stop", ili dok ne istekne [STOP_DRAIN_TIMEOUT_MS]. Zove se ISKLJUCIVO
     * odmah posle poslatog "stop"-a, i UVEK unutar `searchMutex`-a.
     *
     * **Zasto postoji:** "stop" resava UZROK (motor prestaje da racuna) ali
     * pravi POSLEDICU -- pretraga koja se zaustavi obavezno ispise `bestmove`
     * (`search.cpp`, kraj `Search::Worker::start_searching`), i ta linija sleti
     * u isti `outputChannel`. Sledeci pozivalac prazni kanal na ULASKU, pre nego
     * sto zakasneli red stigne, pa ga onda procita kao svoj:
     *   - u [getBestMove]: stari `bestmove` bude odgovor na NOVU poziciju --
     *     `ChessMove.fromUCI` ili vrati `null` (AI ne odigra nista) ili, gore,
     *     potez slucajno bude legalan pa **AI odigra potez izracunat za sasvim
     *     drugu poziciju**, tiho;
     *   - u [evaluate]: `bestmove` bez ijedne `info ... score` linije pre njega
     *     obori tu poziciju na `null`, pa cela analiza zavrsi na
     *     "Analiza nije uspela." (isti simptom koji iOS ima zapisan kao ~2/14
     *     brzih ponovnih pokretanja).
     *
     * Krug ispravki Task-a 4 je dodao samo "stop" i opisao lanac kao zatvoren;
     * bio je suzen, ne zatvoren -- koraci 4 i 5 tog lanca zive u kanalu, ne u
     * motoru. Ovo je drugi kraj iste popravke.
     */
    private suspend fun drainUntilBestMove() {
        withTimeoutOrNull(STOP_DRAIN_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                for (line in outputChannel) {
                    if (line.trim().startsWith("bestmove")) return@withContext
                }
            }
        }
    }

    /**
     * Gets the best move for the given FEN and search depth.
     *
     * Ogranicena sa [BEST_MOVE_TIMEOUT_MS]: bez toga, ako motor umre ili
     * proguta "go", petlja ispod visi zauvek DOK DRZI `searchMutex` --
     * jedina zastita koju `StockfishEngine` (singleton, jedan mutex) inace
     * ima protiv toga je restart procesa. U normalnom toku (depth <= 20,
     * `StockfishLevel.MAXIMUM`) ovo se nikad ne aktivira -- vidi komentar uz
     * konstantu za merenje na kom je zasnovana.
     *
     * **Otkazivanje korutine koja poziva ovu funkciju MORA takodje da posalje
     * "stop", ne samo istek [BEST_MOVE_TIMEOUT_MS].** Bez toga se dogodi
     * ovaj lanac (nadjeno u pregledu Faze 6e, Task 4 -- otkazivanje analize
     * partije je prvi stvaran pozivalac koji otkazuje ovu funkciju usred
     * pretrage, ali isti rizik postoji i za zivu partiju ako korisnik napusti
     * ekran igre dok AI razmislja):
     *   1. Korisnik napusti ekran (npr. analize) usred pretrage --
     *      pozivajuca korutina se otkaze.
     *   2. `Mutex.withLock` je `finally`-zasticen, pa se `searchMutex`
     *      OSLOBODI ODMAH -- ali BEZ da je "stop" ikad poslat, jer je otkazana
     *      korutina preskocila obican `suspend` kod (`inputChannel.send`
     *      unutar `if (move == null)` ispod se NIKAD ne izvrsi kad se
     *      otkazivanje desi za vreme cekanja na `bestmove`).
     *   3. Native motor NASTAVLJA da racuna staru poziciju u pozadini --
     *      niko ga nije zaustavio.
     *   4. Sledeci pozivalac (novi `getBestMove` za AI potez, ili nova
     *      `evaluate` pretraga) uzme SLOBODAN mutex, isprazni kanal
     *      (`tryReceive` petlja) -- ali ta petlja brise SAMO ono sto je VEC
     *      u baferu u tom trenutku, ne ono sto tek stize -- i posalje
     *      sopstveni "position"/"go".
     *   5. Zakasneli "bestmove" STARE pretrage stigne POSLE praznjenja a PRE
     *      novog "bestmove"-a i bude procitan kao odgovor na NOVU poziciju --
     *      `ChessMove.fromUCI` ili vrati `null` (AI ne odigra nista) ili,
     *      gore, potez slucajno bude legalan u novoj poziciji pa **AI odigra
     *      potez izracunat za sasvim drugu poziciju**, tiho, bez ijedne
     *      poruke. Ista klasa greske koju iOS ima zapisanu za analizu
     *      partije ("izlaz starog motora upadne u pipe novog"), samo je
     *      ovde posledica pogresan potez u zivoj partiji, ne neuspela analiza.
     *
     * Zato `finally` ispod salje "stop" i kad je korutina otkazana, u
     * `NonCancellable` kontekstu (obican `suspend` poziv unutar `finally`
     * otkazane korutine bi sam odmah bacio `CancellationException`, pre nego
     * sto bi "stop" uopste stigao u `inputChannel`). Moguce je da se "stop"
     * ovim posalje DVAPUT (jednom iz `if (move == null)`, jednom iz
     * `finally`) ako otkazivanje stigne bas izmedju to dvoje -- bezopasno,
     * provereno u `cpp/stockfish/uci.cpp:105-106`
     * (`if (token == "quit" || token == "stop") engine.stop();`) i
     * `engine.cpp:159` (`void Engine::stop() { threads.stop = true; }`):
     * `stop` samo postavlja `std::atomic_bool` zastavicu, bez provere da li
     * je pretraga uopste u toku -- ponovljen ili "prazan" poziv je no-op.
     *
     * **"stop" sam zatvara korake 1–3, ne i 4–5** -- to je nalaz pregleda pred
     * spajanje, i ispravka prethodnog teksta ovog komentara, koji je lanac
     * proglasio zatvorenim. Zaustavljena pretraga OBAVEZNO ispise `bestmove`
     * (`search.cpp:266-267`, na kraju `start_searching`), pa "stop" ukloni uzrok
     * a sam proizvede posledicu: red koji sleti u kanal tacno u prozoru izmedju
     * praznjenja i prvog reda sledece pretrage. Zato se posle svakog naseg
     * "stop"-a zove [drainUntilBestMove], jos unutar `searchMutex`-a -- tek
     * tada je lanac stvarno zatvoren.
     */
    suspend fun getBestMove(fen: String, depth: Int): String? {
        if (!engineStarted) return null
        // Gejt spremnosti -- vidi [waitUntilReady]. NAMERNO pre `searchMutex`-a:
        // cekanje unutar jedinog mutexa je klasa greske koju ovaj fajl vec dvaput
        // lecio. U normalnom toku se vraca odmah (`readyObserved` je davno `true`).
        if (!waitUntilReady()) return null

        return searchMutex.withLock {
            // Drain output channel to remove old stale messages
            while (true) {
                val result = outputChannel.tryReceive()
                if (result.isFailure || result.isClosed) break
            }

            // Send search commands
            inputChannel.send("position fen $fen")
            inputChannel.send("go depth $depth")

            var completedNormally = false
            // Da li je `bestmove` linija VEC procitana iz kanala. Razlikuje
            // pretragu koja je uredno zavrsila (nema sta da se cisti) od one
            // koju tek zaustavljamo (`bestmove` tek treba da stigne) -- bez
            // toga bi [drainUntilBestMove] cekao liniju koja nikad nece doci.
            var sawBestMove = false
            try {
                // Wait for the bestmove output
                val move = withTimeoutOrNull(BEST_MOVE_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        for (line in outputChannel) {
                            val parts = line.split(" ")
                            if (parts.isNotEmpty() && parts[0] == "bestmove") {
                                sawBestMove = true
                                if (parts.size >= 2) {
                                    val candidate = parts[1]
                                    if (candidate != "(none)") {
                                        return@withContext candidate
                                    }
                                }
                                break
                            }
                        }
                        null
                    }
                }
                if (move == null && !sawBestMove) {
                    // Istekao je TIMEOUT (motor zaglavljen) ili je kanal
                    // zatvoren. "stop" zaustavlja pretragu koja jos traje, a
                    // [drainUntilBestMove] pojede `bestmove` koji ce taj isti
                    // "stop" izazvati -- inace bi ta linija zavrsila u kanalu
                    // SLEDECE pretrage i bila procitana kao odgovor na drugu
                    // poziciju. Ako je pretraga uredno zavrsila (`sawBestMove`,
                    // npr. "(none)"), nema ni sta da se zaustavi ni sta da se
                    // pojede.
                    inputChannel.send("stop")
                    drainUntilBestMove()
                }
                completedNormally = true
                move
            } finally {
                if (!completedNormally) {
                    // Otkazivanje -- vidi dugi komentar iznad funkcije. Mora ici
                    // kroz NonCancellable, jer je korutina vec otkazana i obican
                    // `suspend` poziv bi ovde odmah bacio CancellationException
                    // umesto da posalje "stop". Isti razlog vazi i za citanje
                    // `bestmove`-a koji taj "stop" izaziva.
                    withContext(NonCancellable) {
                        inputChannel.send("stop")
                        if (!sawBestMove) drainUntilBestMove()
                    }
                }
            }
        }
    }

    /**
     * Ocena jedne pozicije za analizu partije. Za razliku od [getBestMove], koji
     * cita SAMO `bestmove` liniju i baca sve `info` linije, ova funkcija cita
     * `info` linije kroz [UCIScoreParser.parse] da izvuce ocenu.
     *
     * Uzima se POSLEDNJA vidjena ocena PRE `bestmove` -- motor tokom
     * produbljivanja salje ocenu za svaku dubinu (`depth 1`, `depth 2`, ...), a
     * zanima nas ona sa pune dubine, ne prva koju je prijavio.
     *
     * Terminalna pozicija (bez legalnih poteza -- mat ili pat) se NE salje
     * motoru, nego joj se ocena izvodi iz PRAVILA, preko generisanja poteza --
     * NE preko `GameState.status`, koji `GameState.fromFEN` ostavlja na
     * `Playing` cak i za mat (konstruktor se poziva bez `status`, podrazumevana
     * vrednost je `Playing` -- `models/GameState.kt`). Nema legalnih poteza:
     * sah -> `Mate(0)`, inace -> `Cp(0)`.
     *
     * **Razlog NIJE isti kao na iOS-u, i to je ispravka ranijeg teksta ovog
     * komentara.** Tamo (`ChessKitEngine`) motor za poziciju bez poteza ne
     * posalje nijednu `score` liniju, pa bi analiza vracala `null` za svaku
     * odigranu partiju. Native Stockfish koji se kompajlira u OVAJ APK to
     * radi drugacije -- provereno u isporucenom izvoru:
     *   `search.cpp:212-216`  rootMoves prazan -> `onUpdateNoMoves({0, {checkers() ? -VALUE_MATE : VALUE_DRAW, pos}})`
     *   `uci.cpp:620-621`     -> `sync_cout << "info depth " << info.depth << " score " << format_score(...)`
     *   `uci.cpp:541`/`:548`  -> `-VALUE_MATE` daje "mate 0", `VALUE_DRAW` daje "cp 0"
     * Dakle motor bi ispisao `info depth 0 score mate 0` (odnosno `cp 0`) i
     * [UCIScoreParser] bi procitao TACNO onu vrednost koju terminalna grana
     * vraca. Grana svejedno ostaje, iz dva razloga koja ne zavise od motora:
     * ne troši ni jednu UCI komandu ni red u `searchMutex`-u (a analiza ima
     * bar jednu takvu poziciju u svakoj odigranoj partiji), i daje isti
     * rezultat i kad motor uopste nije spreman ili je zaglavljen.
     *
     * Pretraga (ne terminalna grana) je ogranicena sa [EVALUATE_TIMEOUT_MS] --
     * isti razlog kao kod [getBestMove], samo izlozeniji: analiza partije
     * (Task 4) zove ovo do ~81 puta zaredom kroz ISTI `searchMutex`, pa bi
     * jedna zaglavljena pozicija bez timeout-a trajno blokirala i analizu i
     * svaku sledecu partiju protiv racunara u istom procesu.
     *
     * **Otkazivanje ove funkcije usred pretrage takodje mora da posalje
     * "stop"** -- isti petostepeni lanac kao kod [getBestMove] (vidi njegov
     * doc-komentar za pun tekst), primenjen ovde na `AnalysisViewModel`, koji
     * je STVARAN pozivalac koji ovo otkazuje: korisnik napusti ekran analize
     * usred jedne od ~81 pretrage -> `Mutex.withLock` oslobodi `searchMutex`
     * ODMAH, ali bez da je "stop" ikad poslat -> native motor nastavi da
     * racuna staru poziciju u pozadini -> SLEDECI pozivalac (nov `evaluate`
     * u kasnijoj analizi, ili `getBestMove` za AI potez u partiji koju
     * korisnik odmah zatim zapocne) isprazni SAMO ono sto je vec u baferu i
     * posalje sopstveni "position"/"go" -> zakasneli izlaz stare pretrage
     * stigne izmedju praznjenja i novog "bestmove"-a i bude procitan kao
     * ocena/potez ZA NOVU poziciju. Kod `evaluate` je to jos podmuklije nego
     * kod `getBestMove`: nema pada, nema "null"-a koji bi nesto upozorio --
     * `GameAnalysis` samo dobije pogresan `cpLoss`/klasu za taj potez, tiho.
     *
     * I ovde, kao u [getBestMove], sam "stop" NIJE dovoljan: on zaustavi motor
     * ali time proizvede `bestmove` red koji tek treba pojesti. Zato posle
     * svakog naseg "stop"-a ide [drainUntilBestMove], jos unutar mutexa.
     */
    suspend fun evaluate(fen: String, depth: Int = 12): PositionEval? {
        // Terminalna pozicija se prepoznaje PRE bilo kakvog dodira sa motorom --
        // ni `searchMutex`, ni provera `engineStarted`, ni ijedna UCI komanda.
        // Isti redosled kao iOS `evaluate(state:depth:)`, koji `terminalEval`
        // proverava pre nego sto uopste napravi motor.
        val state = GameState.fromFEN(fen)
        if (state != null) {
            val legalMoves = MoveGenerator.legalMoves(state.currentTurn, state)
            if (legalMoves.isEmpty()) {
                val inCheck = MoveGenerator.isInCheck(state.currentTurn, state)
                return PositionEval(
                    score = if (inCheck) EngineScore.Mate(0) else EngineScore.Cp(0),
                    bestMove = null
                )
            }
        }

        if (!engineStarted) return null
        // Gejt spremnosti, isti kao u [getBestMove] i iz istog razloga (motor
        // koji dobije "go" pre ucitane mreze gasi CEO proces --
        // `engine.cpp:153-155` -> `nnue/network.cpp:267 exit(EXIT_FAILURE)`).
        // Pre `searchMutex`-a, da cekanje ne drzi jedinu bravu.
        if (!waitUntilReady()) return null

        return searchMutex.withLock {
            // Drain output channel to remove old stale messages
            while (true) {
                val result = outputChannel.tryReceive()
                if (result.isFailure || result.isClosed) break
            }

            // Send search commands
            inputChannel.send("position fen $fen")
            inputChannel.send("go depth $depth")

            var completedNormally = false
            // Vidi isto polje u [getBestMove]: razlikuje "pretraga je uredno
            // zavrsila" od "pretragu tek zaustavljamo", sto odlucuje da li
            // uopste ima `bestmove` reda koji treba pojesti.
            var sawBestMove = false
            try {
                // Wait for the bestmove output, tracking the last seen score along the way.
                val eval = withTimeoutOrNull(EVALUATE_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        var lastScore: EngineScore? = null
                        for (line in outputChannel) {
                            val parts = line.split(" ")
                            if (parts.isNotEmpty() && parts[0] == "info") {
                                UCIScoreParser.parse(line)?.let { lastScore = it }
                                continue
                            }
                            if (parts.isNotEmpty() && parts[0] == "bestmove") {
                                sawBestMove = true
                                val move = if (parts.size >= 2 && parts[1] != "(none)") parts[1] else null
                                // Bez ijedne ocene nema sta da se vrati -- pozicija bez
                                // ocene bi u racunici prosla kao cp(0), sto je tvrdnja
                                // da je izjednaceno.
                                val score = lastScore ?: return@withContext null
                                return@withContext PositionEval(score, move)
                            }
                        }
                        null
                    }
                }
                if (eval == null && !sawBestMove) {
                    // Isto obrazlozenje kao u `getBestMove`: "stop" zaustavlja
                    // pretragu koja jos traje, a [drainUntilBestMove] pojede
                    // `bestmove` koji ce taj "stop" izazvati -- inace bi ta
                    // linija zavrsila u kanalu SLEDECE pretrage i ucinila je
                    // `null`-om (analiza bi stala na "Analiza nije uspela.").
                    inputChannel.send("stop")
                    drainUntilBestMove()
                }
                completedNormally = true
                eval
            } finally {
                if (!completedNormally) {
                    // Otkazivanje -- vidi dugi komentar iznad funkcije. Mora
                    // ici kroz NonCancellable, isti razlog kao u `getBestMove`:
                    // korutina je vec otkazana, pa bi obican `suspend` poziv
                    // ovde odmah bacio CancellationException umesto da posalje
                    // "stop" i procitao `bestmove` koji "stop" izaziva.
                    withContext(NonCancellable) {
                        inputChannel.send("stop")
                        if (!sawBestMove) drainUntilBestMove()
                    }
                }
            }
        }
    }
}
