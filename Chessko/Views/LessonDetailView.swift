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

    var body: some View {
        GeometryReader { geo in
            ZStack {
                AppBackgroundView()

                ScrollView(.vertical, showsIndicators: true) {
                    VStack(alignment: .leading, spacing: 0) {
                        if let document {
                            lessonHeader(document)
                            LessonRenderer(blocks: document.blocks)
                        } else {
                            // Sadrzaj je van koda; ako fajl nedostaje iz bundle-a
                            // korisnik mora da vidi zasto, a ne prazan ekran.
                            Text(Loc("Lekcija nije dostupna"))
                                .font(.dsBody)
                                .foregroundStyle(DS.inkMuted)
                                .padding(40)
                        }

                        if document != nil, let stepId {
                            stepFooter(stepId)
                        }
                    }
                    .frame(width: geo.size.width)
                    .padding(.bottom, 40)
                }
                .safeAreaPadding(.bottom, 24)
            }
        }
        .navigationTitle(lessonNumber.map { LocF("Lekcija %lld", $0) }
                         ?? (document?.title ?? ""))
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.appBackground, for: .navigationBar)
    }

    // MARK: - Zavrsetak koraka Puta

    /// Vidi se samo kad je lekcija otvorena iz Puta. Vec zavrsen korak ne nudi
    /// dugme nego samo potvrdu — `completeStep` je ionako idempotentan, ali
    /// dugme koje ne menja nista je gore od nikakvog dugmeta.
    @ViewBuilder
    private func stepFooter(_ stepId: String) -> some View {
        let done = ProgressStore.shared.snapshot.completedSteps.contains(stepId)

        VStack(spacing: DS.Space.m) {
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
                .padding(.horizontal, DS.Space.xl)
            } else {
                Button {
                    ProgressStore.shared.completeStep(stepId)
                    Haptics.notification(.success)
                    dismiss()
                } label: {
                    Text(Loc("Završi korak"))
                        .font(.dsHeading.weight(.semibold))
                        .foregroundStyle(DS.onAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, DS.Space.m)
                        .background(DS.accent, in: RoundedRectangle(cornerRadius: DS.Radius.m))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, DS.Space.xl)
            }
        }
        .padding(.top, DS.Space.xl)
    }

    // MARK: - Lesson Header

    private func lessonHeader(_ document: LessonDocument) -> some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(DS.accent.opacity(0.18))
                    .frame(width: 60, height: 60)
                Image(systemName: document.icon)
                    .font(.dsTitle.weight(.medium))
                    .foregroundStyle(DS.accent)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(document.title)
                    .font(.dsHeading.weight(.bold))
                    .foregroundStyle(.primary)
                Text(document.subtitle)
                    .font(.dsBody)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 20)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        LessonDetailView(lessonId: "board-and-pieces")
    }
    // `LessonPieceExplorer` cita model iz okruzenja — vidi LearnView.
    .environment(LearnViewModel())
}
