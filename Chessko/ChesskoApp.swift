import SwiftUI

@main
struct ChesskoApp: App {

    init() {
        // ChessKitEngine ne pokrece zaseban proces — Stockfish radi u NASEM, a
        // `EngineMessenger` mu `dup2`-uje `stdout` na sopstveni pipe. `stop()`
        // zatvara citajuci kraj tog pipe-a, dok Stockfish-ova nit ume da ispise
        // jos koji red posle toga; svaki takav upis salje SIGPIPE, cija
        // podrazumevana radnja gasi CEO proces — i to bez crash izvestaja, pa
        // izgleda kao da je aplikacija „samo nestala".
        //
        // IZMERENO na simulatoru, ne pretpostavljeno: ekran analize koji se
        // zatvori usred rada pa odmah otvori (`cancel()` pa `start()`) obarao je
        // aplikaciju u 4 od 8 pokretanja, svaki put sa
        // `RBSProcessExitStatus| domain:signal(2) code:SIGPIPE(13)` u logu
        // simulatora; sa ovom linijom 0 od 14.
        //
        // `write()` u ChessKitEngine-u ionako ne gleda povratnu vrednost, pa se
        // ignorisanjem signala nista ne gubi: upis u zatvoren pipe vrati EPIPE.
        signal(SIGPIPE, SIG_IGN)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
