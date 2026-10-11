import SwiftUI

// MARK: - Lesson Detail View
//
// Od Faze 3 ovaj ekran vise ne zna sadrzaj lekcije. Uzima dokument iz
// `LessonRepository`-ja i predaje blokove `LessonRenderer`-u; njemu ostaje
// samo zaglavlje i okvir za skrolovanje.
//
// `GeometryReader` + `.frame(width:)` nisu ukras: bez njih se sadrzaj lekcije
// preliva vodoravno (popravka od 2026-06-28).

struct LessonDetailView: View {

    let lessonId: String

    /// Id koraka Puta iz kojeg je lekcija otvorena, ili `nil` ako je otvorena
    /// iz liste lekcija (tab Učenje).
    ///
    /// Od ovoga zavisi SAMO dugme na dnu: korak se zavrsava tek kad korisnik
    /// dodje do kraja i POTVRDI (spec 5.1). Da se `ProgressStore` zove bez ovog
    /// razlikovanja, obicno listanje lekcija bi tiho zavrsavalo korake Puta —
    /// napredak koji korisnik nije zaradio.
    var stepId: String? = nil

    @Environment(\.dismiss) private var dismiss

    /// Citanje `effectiveCode` unutar `body`-ja je i pretplata: `@Observable`
    /// belezi pristup i bez uskladistene reference, pa ekran prati promenu
    /// jezika i kad je gurnut na navigacioni stek.
    private var document: LessonDocument? {
        LessonRepository.shared.lesson(id: lessonId,
                                       language: LocalizationManager.shared.effectiveCode)
    }

    /// Redni broj u naslovu (,,Lekcija 3") dolazi iz redosleda u repozitorijumu,
    /// jer `id` je od Faze 3 string a ne broj.
    /// `nil` za lekciju koja nije u poznatom redosledu. Ranije je `?? 0` takvoj
    /// lekciji davao broj 1 — dakle tudji broj, sto je gore od nikakvog.
    private var lessonNumber: Int? {
        LessonRepository.lessonOrder.firstIndex(of: lessonId).map { $0 + 1 }
    }

    @Environment(\.horizontalSizeClass) private var hSize
    /// Položaj oznaka blokova sa tablom (iPad pejzaž) u prostoru skrola.
    @State private var markerY: [Int: CGFloat] = [:]

    var body: some View {
        GeometryReader { geo in
            ZStack {
                AppBackgroundView()

                // Spec § 13: tablet = `regular` širina. Dve kolone (spec § 9) samo u
                // pejzažu i uz dovoljno visine — iPhone Max u pejzažu je takođe
                // `regular`, ali bi mu kolona table ispala ~250 pt.
                let isTablet = hSize == .regular
                let twoColumn = isTablet && geo.size.width > geo.size.height
                    && geo.size.height >= 480
                    && (document?.blocks.contains { $0.carriesBoard } ?? false)

                if twoColumn, let document {
                    twoColumnLayout(document, size: geo.size)
                } else {
                    // Jedna kolona: tekst do 640 (spec § 9), centrirana.
                    ScrollView(.vertical, showsIndicators: true) {
                        content(boardMode: .inline)
                            .frame(maxWidth: Self.textMaxWidth)
                            .padding(.horizontal, DS.Space.l)
                            .frame(width: geo.size.width)
                            .padding(.bottom, 40)
                    }
                    .safeAreaPadding(.bottom, 24)
                }
            }
        }
        // Kad je lekcija otvorena IZ KORAKA Puta, naslov je naslov lekcije, a ne
        // "Lekcija N": Put broji korake (lekcija otvaranja je korak 4), a ovaj
        // ekran je brojao lekcije (ista lekcija je lekcija 2). Korisnik bi na
        // dva uzastopna ekrana video dva razlicita broja za istu stvar.
        .navigationTitle(barTitle)
        .navigationBarTitleDisplayMode(.inline)
        // App bar lekcije (spec § 4): naslov `Type.body` medium (sistemski inline
        // naslov je headline 17 semibold), nazad + „Put" u `accent` (tint
        // `TabView`-a; labelu daje naslov `PathView`-a). `navBar` podloga se
        // pojavljuje kad sadrzaj klizi, zajedno sa linijom ispod — iOS na vrhu
        // sadrzaja traku crta providnom (scroll edge), pa je tu podloga `ground`.
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(barTitle)
                    .font(.dsBody.weight(.medium))
                    .foregroundStyle(DS.ink)
                    .lineLimit(1)
            }
        }
        .toolbarBackground(DS.navBar, for: .navigationBar)
    }

    private var barTitle: String {
        stepId != nil
            ? (document?.title ?? "")
            : (lessonNumber.map { LocF("Lekcija %lld", $0) } ?? (document?.title ?? ""))
    }

    // MARK: - Zavrsetak koraka Puta

    /// Vidi se samo kad je lekcija otvorena iz Puta. Vec zavrsen korak ne nudi
    /// dugme nego samo potvrdu — `completeStep` je ionako idempotentan, ali
    /// dugme koje ne menja nista je gore od nikakvog dugmeta.
    @ViewBuilder
    private func stepFooter(_ stepId: String) -> some View {
        let done = ProgressStore.shared.snapshot.completedSteps.contains(stepId)

        VStack(spacing: DS.Space.l) {
            Rectangle()
                .fill(DS.line)
                .frame(height: 1)

            if done {
                HStack(spacing: DS.Space.s) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(DS.success)
                    Text(Loc("Korak je završen"))
                        .foregroundStyle(DS.inkMuted)
                }
                .font(.dsBody)
            } else {
                Button {
                    ProgressStore.shared.completeStep(stepId)
                    Haptics.notification(.success)
                    dismiss()
                } label: {
                    // Spec § 9: primarno dugme `Radius.m` (ne stadion), `accent` + `onAccent`.
                    Text(Loc("Završi korak"))
                }
                .buttonStyle(DSPrimaryButtonStyle())
            }
        }
        .padding(.top, DS.Space.s)
    }

    // MARK: - Sadržaj (isti u jednoj i u dve kolone)

    /// Spec § 9: tekst lekcije na iPad-u u koloni do 640.
    static let textMaxWidth: CGFloat = 640

    @ViewBuilder
    private func content(boardMode: LessonBoardMode) -> some View {
        VStack(alignment: .leading, spacing: DS.Space.l) {
            if let document {
                lessonHeader(document)
                LessonRenderer(blocks: document.blocks, boardMode: boardMode)
            } else {
                // Sadrzaj je van koda; ako fajl nedostaje iz bundle-a korisnik
                // mora da vidi zasto, a ne prazan ekran.
                Text(Loc("Lekcija nije dostupna"))
                    .font(.dsBody)
                    .foregroundStyle(DS.inkMuted)
                    .padding(.vertical, 40)
            }

            if document != nil, let stepId {
                stepFooter(stepId)
            }
        }
    }

    /// iPad pejzaž (spec § 9): tekst 480–520 levo, tabla desno i lepljiva dok
    /// tekst skroluje. Desno je tabla bloka do kog je čitalac stigao — poslednja
    /// oznaka iznad sredine vidnog polja. Sve table ostaju u hijerarhiji (napredak
    /// u vežbi ne nestaje kad čitalac skroluje dalje), neaktivne su skrivene.
    /// TODO(design § 13): širinu kolone table zameniti zajedničkom funkcijom
    /// strane table (Task 11); do tada `lessonBoardMaxSide`.
    private func twoColumnLayout(_ document: LessonDocument, size: CGSize) -> some View {
        let gutter = DS.Space.xl
        let boardCol = max(280, min(size.height - 160, size.width - 480 - gutter * 3, lessonBoardMaxSide))
        let textCol = min(520, max(480, size.width - boardCol - gutter * 3))
        let boardIndices = document.blocks.indices.filter { document.blocks[$0].carriesBoard }
        let viewportMid = size.height / 2
        let active = boardIndices.last { (markerY[$0] ?? .infinity) <= viewportMid } ?? boardIndices.first

        return HStack(alignment: .top, spacing: gutter) {
            ScrollView(.vertical, showsIndicators: true) {
                content(boardMode: .markers(activeIndex: active))
                    .padding(.bottom, 40)
            }
            .coordinateSpace(name: "lessonScroll")
            .onPreferenceChange(LessonMarkerYKey.self) { markerY = $0 }
            .frame(width: textCol)

            ScrollView(.vertical, showsIndicators: false) {
                ZStack(alignment: .top) {
                    ForEach(boardIndices, id: \.self) { i in
                        let isActive = i == active
                        LessonBoardBlock(block: document.blocks[i])
                            .opacity(isActive ? 1 : 0)
                            .allowsHitTesting(isActive)
                            .accessibilityHidden(!isActive)
                    }
                }
                .padding(.vertical, DS.Space.l)
            }
            .frame(width: boardCol)
        }
        .frame(maxWidth: .infinity)
        .safeAreaPadding(.bottom, 24)
    }

    // MARK: - Lesson Header

    /// Spec § 9: naslov lekcije `title` 22 bold `ink`; podnaslov `inkMuted`.
    /// Pločica ikone `fill` + `ink` (bila `accent` tinta — accent je samo za ono
    /// što se tapa, spec § 3).
    private func lessonHeader(_ document: LessonDocument) -> some View {
        HStack(spacing: DS.Space.m) {
            LessonSymbolView(name: document.icon, color: DS.ink, font: .dsTitle.weight(.medium))
                .frame(width: 56, height: 56)
                .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.m))
            VStack(alignment: .leading, spacing: DS.Space.xs) {
                Text(document.title)
                    .font(.dsTitle)
                    .foregroundStyle(DS.ink)
                Text(document.subtitle)
                    .font(.dsBody)
                    .foregroundStyle(DS.inkMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.top, DS.Space.l)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        LessonDetailView(lessonId: "board-and-pieces")
    }
    // `LessonPieceExplorer` cita model iz okruzenja — vidi PathView.
    .environment(LearnViewModel())
}
