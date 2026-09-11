import Foundation

// MARK: - Lesson Repository
//
// Ucitava lekcije iz `Content/lessons/<id>.<lang>.json` u bundle-u. `Content`
// je u projektu FOLDER-REFERENCA, pa se cela struktura direktorijuma prenosi u
// .app — nova lekcija ne trazi izmenu `project.pbxproj`.
//
// `@MainActor` iz istog razloga kao `PuzzleRepository`: kes nije zasticen, a
// ceo pozivni graf je ionako na glavnoj niti. Ovako je pozadinski pozivalac
// greska pri kompajliranju, a ne tiha trka.

@MainActor
final class LessonRepository {
    static let shared = LessonRepository()

    /// Redosled kojim se poznate lekcije stoje na ekranu Učenje. NIJE spisak
    /// postojecih lekcija — samo kljuc za sortiranje. Lekcija koja nije ovde
    /// i dalje se prikazuje, na kraju liste; inace bi nova lekcija ubacena u
    /// `Content/lessons/` bila NEVIDLJIVA bez ijedne poruke, sto je tacno ona
    /// tiha rupa koju ostatak ove faze uklanja. Pun redosled dobija
    /// `curriculum.json` u Fazi 4.
    static let lessonOrder = ["board-and-pieces", "openings", "middlegame", "endgame"]

    /// Jezici koje aplikacija isporucuje. Stoji ovde, a ne cita se iz
    /// `LocalizationManager`-a, jer taj uvozi SwiftUI a ovaj fajl mora da ostane
    /// na nivou Foundation-a zbog testnog paketa.
    static let supportedLanguages: Set<String> = ["sr", "en", "fr", "de", "it", "ru", "zh-Hans", "hi"]

    private var cache: [String: LessonDocument] = [:]

    /// Lekcija na trazenom jeziku. Ako tog jezika nema (nove lekcije idu samo
    /// sr+en), pada na engleski pa na srpski — bolje lekcija na drugom jeziku
    /// nego prazan ekran.
    func lesson(id: String, language: String) -> LessonDocument? {
        for candidate in [language, "en", "sr"] {
            let key = "\(id).\(candidate)"
            if let cached = cache[key] { return cached }
            if let doc = load(key) {
                cache[key] = doc
                return doc
            }
        }
        return nil
    }

    /// Razdvaja DVA slucaja koja se lako slepe u jedan `try?`:
    ///
    /// - fajla nema  → ocekivano; nova lekcija ide samo na sr+en, pa se trazeni
    ///   jezik uredno preskace i lanac pada na sledeci kandidata.
    /// - fajl POSTOJI ali se ne dekodira → greska koja ne sme da se izgubi.
    ///   `LessonBlock` namerno BACA na nepoznat tip bloka, bas da pokvarena
    ///   lekcija ne bi prosla nezapazeno; ako bi je ovde `try?` progutao, ta
    ///   namera bi bila ponistena — korisnik bi tiho dobio drugi jezik, a niko
    ///   ne bi saznao da je JSON pokvaren. Sadrzaj je od Faze 3 van dometa
    ///   kompajlera, pa je ovo jedino mesto koje moze da vikne.
    private func load(_ key: String) -> LessonDocument? {
        guard let url = Bundle.main.url(forResource: key, withExtension: "json",
                                        subdirectory: "Content/lessons") else {
            return nil
        }
        do {
            return try JSONDecoder().decode(LessonDocument.self, from: Data(contentsOf: url))
        } catch {
            // `print` PRE `assertionFailure`: u debug build-u assertion obara
            // proces, pa bi poruka posle njega bila nedostizna bas kad je
            // najpotrebnija.
            print("[Chessko] GRESKA: \(key).json se ne dekodira: \(error)")
            assertionFailure("Lekcija \(key).json postoji ali se ne dekodira: \(error)")
            return nil
        }
    }

    /// Id-jevi svih lekcija koje stvarno postoje u bundle-u, otkriveni iz imena
    /// fajlova (`<id>.<lang>.json`). Time nova lekcija = nov JSON, sto je i bila
    /// namera cele faze.
    private func discoveredLessonIds() -> [String] {
        let urls = Bundle.main.urls(forResourcesWithExtension: "json",
                                    subdirectory: "Content/lessons") ?? []
        let ids = Set(urls.compactMap { url -> String? in
            // "openings.sr.json" -> "openings". Poslednji deo MORA biti podrzan
            // jezik: bez te provere bi zalutali "openings.sr.backup.json" dao
            // fantomsku lekciju "openings.sr", koja se onda ne razresi ni na
            // jednom jeziku i zavrsila bi kao prijavljena greska o lekciji
            // koja "nedostaje", a nikad je nije ni bilo.
            let stem = url.deletingPathExtension().lastPathComponent
            guard let dot = stem.lastIndex(of: "."),
                  Self.supportedLanguages.contains(String(stem[stem.index(after: dot)...]))
            else { return nil }
            return String(stem[stem.startIndex..<dot])
        })
        // Poznate prvo, propisanim redom; nepoznate azbucno na kraj.
        let known = Self.lessonOrder.filter(ids.contains)
        let extra = ids.subtracting(Self.lessonOrder).sorted()
        return known + extra
    }

    /// Sve lekcije redom, na trazenom jeziku.
    ///
    /// Od Faze 4a NEMA pozivaoca: ekran Ucenja je obrisan, a Put ne prikazuje
    /// spisak lekcija nego korake iz kurikuluma. Zadrzano jer Faza 4b pise nove
    /// lekcije i verovatno ce joj trebati pregled svih; ako se pokaze da nece,
    /// brise se bez posledica.
    func allLessons(language: String) -> [LessonDocument] {
        let ids = discoveredLessonIds()
        let docs = ids.compactMap { lesson(id: $0, language: language) }
        if docs.count != ids.count {
            let missing = Set(ids).subtracting(docs.map(\.id))
            print("[Chessko] GRESKA: nedostaju lekcije: \(missing.sorted())")
        }
        return docs
    }
}

// MARK: - Curriculum Repository
//
// Isti sloj i isti obrazac kao `LessonRepository`: Put zivi u
// `Content/curriculum.json`, van koda, i ucitava se jednom pa kesira.
//
// Za razliku od lekcija, kurikulum je JEDAN fajl bez varijanti po jeziku —
// naslovi poglavlja stoje u samom JSON-u (`Chapter.title`), pa jezicki lanac
// pada na potrosaca (`PathView`), ne ovde.

@MainActor
final class CurriculumRepository {
    static let shared = CurriculumRepository()

    /// `nil` znaci da `curriculum.json` nedostaje iz bundle-a ili se ne
    /// dekodira — ekran Puta to mora da PRIKAZE, ne da ostane prazan. Ista
    /// namera kao kod lekcija: sadrzaj je van dometa kompajlera od Faze 3, pa
    /// je ovo jedino mesto koje moze da vikne.
    private(set) lazy var curriculum: Curriculum? = {
        guard let url = Bundle.main.url(forResource: "curriculum", withExtension: "json",
                                        subdirectory: "Content") else {
            print("[Chessko] GRESKA: curriculum.json nije u bundle-u.")
            assertionFailure("curriculum.json nije u bundle-u.")
            return nil
        }
        do {
            return try JSONDecoder().decode(Curriculum.self, from: Data(contentsOf: url))
        } catch {
            print("[Chessko] GRESKA: curriculum.json se ne dekodira: \(error)")
            assertionFailure("curriculum.json se ne dekodira: \(error)")
            return nil
        }
    }()
}
