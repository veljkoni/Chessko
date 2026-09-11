import Foundation
import SQLite3

// MARK: - Puzzle Repository
//
// Cita zadatke iz lokalne, u aplikaciju upakovane SQLite baze
// (Chessko/puzzles.sqlite — 20 000 Lichess zadataka, CC0). Baza se
// otvara ISKLJUCIVO za citanje (SQLITE_OPEN_READONLY); nema mrezne
// zavisnosti niti SPM paketa — `SQLite3` je sistemski modul, dostupan
// i na iOS-u i na macOS-u (SwiftPM test paket).
//
// Sema baze:
//   CREATE TABLE puzzles (id TEXT PRIMARY KEY, fen TEXT, moves TEXT,
//                          rating INTEGER, themes TEXT);
//   CREATE TABLE puzzle_themes (theme TEXT, puzzle_id TEXT);
//
// `puzzles.themes` je denormalizovan (razmakom odvojen) string koji
// direktno popunjava `ChessPuzzle.themes`; `puzzle_themes` je razlozena
// tabela koja postoji SAMO radi brzog filtriranja po temi (JOIN + IN).

/// Swift nema ugradjenu `SQLITE_TRANSIENT` konstantu. Bez nje
/// `sqlite3_bind_text` pamti pokazivac na Swift-ov privremeni bafer
/// koji moze biti oslobodjen pre nego sto se upit izvrsi — ispravno
/// ponasanje zahteva da SQLite ODMAH napravi sopstvenu kopiju stringa.
private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

// `@MainActor`: klasa cuva neizolovan `OpaquePointer` ka SQLite handle-u i
// kesira `count` u `lazy var` — oboje bi bilo trka da im se pristupa sa vise
// niti. Umesto `@unchecked Sendable` (koji samo utisava kompajler), izolacija
// na glavni actor odgovara stvarnom pozivnom grafu (sve ide iz
// `PuzzleViewModel`-a, koji je `@MainActor`) i pretvara svakog buduceg
// pozivaoca sa pozadinske niti u gresku pri kompajliranju umesto u tihu trku.
// Upiti su mikrosekundni nad 20k redova, pa glavna nit nije usko grlo.
@MainActor
final class PuzzleRepository {

    /// `nonisolated(unsafe)` je potreban SAMO zbog `deinit`-a: deinit je uvek
    /// neizolovan, a `OpaquePointer` nije `Sendable`. Bezbedno je jer deinit
    /// po definiciji radi kad vise nijedna referenca na objekat ne postoji,
    /// dakle bez ijednog konkurentnog citaoca. Svaki drugi pristup ide kroz
    /// `@MainActor` izolaciju klase.
    nonisolated(unsafe) private let db: OpaquePointer

    /// Baza upakovana u glavni bundle aplikacije (`puzzles.sqlite`).
    /// `nil` ako resurs nije registrovan/nedostupan — pozivaoci moraju
    /// da se ponasaju korektno kad zadaci nisu dostupni.
    static let bundled: PuzzleRepository? = {
        guard let url = Bundle.main.url(forResource: "puzzles", withExtension: "sqlite") else {
            return nil
        }
        return PuzzleRepository(databaseURL: url)
    }()

    /// Otvara bazu na datoj putanji, isključivo za čitanje.
    /// Vraća `nil` ako fajl ne postoji ili se ne može otvoriti kao SQLite baza.
    init?(databaseURL: URL) {
        guard FileManager.default.fileExists(atPath: databaseURL.path) else { return nil }

        var handle: OpaquePointer?
        let rc = sqlite3_open_v2(databaseURL.path, &handle, SQLITE_OPEN_READONLY, nil)
        guard rc == SQLITE_OK, let handle else {
            if let handle { sqlite3_close(handle) }
            return nil
        }
        self.db = handle
    }

    deinit {
        sqlite3_close(db)
    }

    // MARK: - Broj zadataka

    /// Ukupan broj zadataka u bazi. Racuna se lenjo i kesira — upit je
    /// jeftin (COUNT preko primary key indeksa) ali nema razloga da se
    /// ponavlja na svaki pristup.
    private(set) lazy var count: Int = {
        let sql = "SELECT COUNT(*) FROM puzzles;"
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK,
              sqlite3_step(stmt) == SQLITE_ROW else {
            return 0
        }
        return Int(sqlite3_column_int(stmt, 0))
    }()

    // MARK: - Pojedinacni zadatak po ID-ju

    func puzzle(id: String) -> ChessPuzzle? {
        let sql = "SELECT id, fen, moves, rating, themes FROM puzzles WHERE id = ? LIMIT 1;"
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }

        sqlite3_bind_text(stmt, 1, id, -1, SQLITE_TRANSIENT)

        guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }
        return puzzleFromRow(stmt)
    }

    // MARK: - Zadatak dana

    /// Deterministicki, stabilan izbor po datumu: redni broj dana od
    /// pocetka ere modulo ukupan broj zadataka daje OFFSET u upitu koji
    /// je EKSPLICITNO sortiran po `id`. Bez `ORDER BY` SQLite ne
    /// garantuje redosled vracenih redova (isti upit moze da vrati
    /// razlicite redove izmedju pokretanja aplikacije ili posle
    /// VACUUM-a), pa bi "zadatak dana" mogao da se promeni bez razloga.
    func dailyPuzzle(for date: Date) -> ChessPuzzle? {
        guard count > 0 else { return nil }
        guard let dayIndex = Calendar.current.ordinality(of: .day, in: .era, for: date) else {
            return nil
        }
        let offset = dayIndex % count

        let sql = "SELECT id, fen, moves, rating, themes FROM puzzles ORDER BY id LIMIT 1 OFFSET ?;"
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }

        sqlite3_bind_int(stmt, 1, Int32(offset))

        guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }
        return puzzleFromRow(stmt)
    }

    // MARK: - Filtriranje po temama/rejtingu

    /// Vraca do `limit` zadataka koji imaju BAR JEDNU od trazenih tema
    /// (razlozena `puzzle_themes` tabela — ne pogadja npr. "mate" na
    /// "mateIn2" jer se poredi ceo string preko IN, ne LIKE), sa
    /// rejtingom u opsegu i bez id-jeva iz `excluding`. Ako `themes` je
    /// prazna lista, filter po temi se preskace (bilo koja tema).
    func puzzles(themes: [String], ratingRange: ClosedRange<Int>, excluding: Set<String>, limit: Int) -> [ChessPuzzle] {
        guard limit > 0 else { return [] }

        var sql = """
        SELECT p.id, p.fen, p.moves, p.rating, p.themes
        FROM puzzles p
        """
        if !themes.isEmpty {
            sql += "\nJOIN puzzle_themes t ON t.puzzle_id = p.id"
        }
        sql += "\nWHERE p.rating BETWEEN ? AND ?"
        if !themes.isEmpty {
            let placeholders = Array(repeating: "?", count: themes.count).joined(separator: ", ")
            sql += "\nAND t.theme IN (\(placeholders))"
        }
        if !excluding.isEmpty {
            let placeholders = Array(repeating: "?", count: excluding.count).joined(separator: ", ")
            sql += "\nAND p.id NOT IN (\(placeholders))"
        }
        sql += "\nGROUP BY p.id\nORDER BY RANDOM()\nLIMIT ?;"

        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }

        var index: Int32 = 1
        sqlite3_bind_int(stmt, index, Int32(ratingRange.lowerBound)); index += 1
        sqlite3_bind_int(stmt, index, Int32(ratingRange.upperBound)); index += 1
        for theme in themes {
            sqlite3_bind_text(stmt, index, theme, -1, SQLITE_TRANSIENT)
            index += 1
        }
        for excludedId in excluding {
            sqlite3_bind_text(stmt, index, excludedId, -1, SQLITE_TRANSIENT)
            index += 1
        }
        sqlite3_bind_int(stmt, index, Int32(limit))

        var results: [ChessPuzzle] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            if let puzzle = puzzleFromRow(stmt) {
                results.append(puzzle)
            }
        }
        return results
    }

    // MARK: - Cela baza (validacija integriteta)

    /// Svi zadaci iz baze, deterministicki sortirani po `id`. Postoji radi
    /// testova integriteta koji moraju da provere SVAKI red (nasumican uzorak
    /// bi propustao los red vecinu pokretanja). Aplikacija ovo nikad ne zove
    /// u toku rada — zadaci se citaju pojedinacno ili u malim serijama.
    func allPuzzlesOrderedById() -> [ChessPuzzle] {
        let sql = "SELECT id, fen, moves, rating, themes FROM puzzles ORDER BY id;"
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }

        var results: [ChessPuzzle] = []
        results.reserveCapacity(count)
        while sqlite3_step(stmt) == SQLITE_ROW {
            if let puzzle = puzzleFromRow(stmt) {
                results.append(puzzle)
            }
        }
        return results
    }

    // MARK: - Nasumican zadatak

    func randomPuzzle(ratingRange: ClosedRange<Int>, excluding: Set<String>) -> ChessPuzzle? {
        puzzles(themes: [], ratingRange: ratingRange, excluding: excluding, limit: 1).first
    }

    // MARK: - Prozor rejtinga za "Sledeći zadatak" (vezbanje bez ogranicenja)

    /// Granice rejtinga zadataka u isporucenoj bazi (`puzzles.sqlite`, 20 000
    /// Lichess zadataka). Javne jer ih `PuzzleViewModel` koristi i za
    /// progresivno prosirenje prozora kad je osnovni prazan (vidi Task 5).
    nonisolated static let minRating = 600
    nonisolated static let maxRating = 2200

    /// Prozor rejtinga za vezbanje bez dnevnog ogranicenja (spec 5.4):
    /// `playerRating - 200 ... playerRating + 100`, ali BEZBEDNO klampovan na
    /// granice baze.
    ///
    /// Bez klampovanja ovo puca: rejting igraca nije ogranicen ni odozgo ni
    /// odozdo (dug niz neuspeha ga vodi ka ~80-100), pa bi npr. za rejting 80
    /// naivan prozor bio `-120...180` (baza pocinje od 600 — prazan rezultat),
    /// a klampovanje SAMO donje granice bi dalo `600...180` — `ClosedRange` sa
    /// donjom granicom vecom od gornje PUCA pri kreiranju (runtime trap), ne
    /// samo vraca prazan niz. Zato se prvo klampuje donja granica, pa se gornja
    /// klampuje na `max(lo, ...)` — rezultat je uvek validan opseg, za svaki
    /// mogud ulaz (probano i na 80 i na 3000 u testovima).
    nonisolated static func practiceRatingWindow(playerRating r: Int) -> ClosedRange<Int> {
        let lo = max(minRating, r - 200)
        let hi = max(lo, min(maxRating, r + 100))
        return lo...hi
    }

    // MARK: - Prozor rejtinga za korak Puta (vezba/test)

    /// Prozor rejtinga za zadatke unutar koraka Puta (spec 5.4): isti
    /// `-200/+100` prozor oko rejtinga igraca, ali PRESECEN sa opsegom koji je
    /// korak propisao.
    ///
    /// Kad je presek prazan, prednost ima OPSEG KORAKA, ne rejting: kurikulum
    /// zna sta se uci, rejting je samo podesavanje. Pocetnik sa rejtingom 600
    /// koji je stigao do koraka za rejting 1500-1800 mora da dobije bas te
    /// zadatke — vracanje praznog preseka (ili prozora oko rejtinga) dalo bi mu
    /// zadatke koji nemaju veze sa lekcijom koju je upravo procitao, ili
    /// nijedan zadatak.
    ///
    /// Nema klampovanja na granice baze kao kod `practiceRatingWindow`: opseg
    /// koraka dolazi iz `curriculum.json`, gde ga `CurriculumStep` vec proverava
    /// (donja <= gornja), pa `ClosedRange` ne moze da pukne pri kreiranju.
    nonisolated static func stepRatingWindow(playerRating r: Int,
                                             stepRange: ClosedRange<Int>) -> ClosedRange<Int> {
        let lo = max(stepRange.lowerBound, r - 200)
        let hi = min(stepRange.upperBound, r + 100)
        return lo <= hi ? lo...hi : stepRange
    }

    // MARK: - Pomocna funkcija

    /// Cita kolone tekuceg reda `SELECT id, fen, moves, rating, themes ...`
    /// u `ChessPuzzle`. Vraca nil ako je `fen`/`moves` prazan (test na
    /// "neprazan fen i bar jedan potez" u repozitorijumu je odgovornost
    /// pozivaoca/testova, ovde samo bezbedno citamo kolone).
    private func puzzleFromRow(_ stmt: OpaquePointer?) -> ChessPuzzle? {
        guard let idCString = sqlite3_column_text(stmt, 0),
              let fenCString = sqlite3_column_text(stmt, 1),
              let movesCString = sqlite3_column_text(stmt, 2),
              let themesCString = sqlite3_column_text(stmt, 4) else {
            return nil
        }
        let id = String(cString: idCString)
        let fen = String(cString: fenCString)
        let moves = String(cString: movesCString)
        let rating = Int(sqlite3_column_int(stmt, 3))
        let themes = String(cString: themesCString)

        return ChessPuzzle(puzzleId: id, fen: fen, moves: moves, rating: rating, themes: themes)
    }
}
