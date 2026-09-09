import SwiftUI

// MARK: - Path View (ekran „Put")
//
// Okosnica v2: kurikulum iz `Content/curriculum.json` + napredak iz
// `ProgressStore`-a, spojeni u jedan spisak koraka koji se ide redom.
//
// Ekran NE drzi sopstveno stanje napretka. Sve tri sekcije (nastavak, streak,
// poglavlja) citaju isti recnik `states`, pa ne mogu da se raziđu: nemoguce je
// da kartica „Nastavi" pokazuje jedan korak a lista drugi.
//
// `ProgressStore` je `@Observable`, pa se ekran sam prekrsti kad se korak
// zavrsi u lekciji — koja je u tom trenutku gurnuta na navigacioni stek iznad
// ovog ekrana.
//
// Bez bedzeva i konfeta (spec 5.4): streak i procenat poglavlja su podatak,
// ne nagrada.

struct PathView: View {

    /// Model istrazivaca figura. Ne koristi ga ovaj ekran nego
    /// `LessonPieceExplorer` duboko u rendereru lekcije; podignut je u
    /// `ContentView`-u da se ne pravi tokom animacije prelaska taba, pa ovde
    /// samo prolazi kroz okruzenje — isto kao na ekranu Učenje.
    var viewModel: LearnViewModel

    private var curriculum: Curriculum? { CurriculumRepository.shared.curriculum }
    private var store: ProgressStore { ProgressStore.shared }

    /// Citanje `effectiveCode` unutar `body`-ja je i pretplata na promenu
    /// jezika (`@Observable` belezi pristup) — vidi `LearnView`.
    private var language: String { LocalizationManager.shared.effectiveCode }

    /// Stanje svakog koraka po id-ju. Jedan izvor za sve tri sekcije ekrana.
    private var states: [String: StepState] {
        guard let curriculum else { return [:] }
        return store.stepStates(for: curriculum)
    }

    /// Prvi dostupan korak — to je „Nastavi". `nil` znaci da je put predjen.
    private func nextStep(_ states: [String: StepState]) -> (index: Int, step: CurriculumStep)? {
        guard let curriculum else { return nil }
        let all = curriculum.chapters.flatMap(\.steps)
        guard let i = all.firstIndex(where: { states[$0.id] == .available }) else { return nil }
        return (i + 1, all[i])   // redni broj je 1-baziran, za „Korak %lld"
    }

    private func completion(of chapter: Chapter, _ states: [String: StepState]) -> Double {
        let done = chapter.steps.filter { states[$0.id] == .completed }.count
        return chapter.steps.isEmpty ? 0 : Double(done) / Double(chapter.steps.count)
    }

    /// Naslovi poglavlja postoje samo na `sr` i `en`, a aplikacija ima 8 jezika.
    /// Zato ekran pada nazad SAM, istim lancem koji `LessonRepository` koristi
    /// za lekcije: trazeni jezik → en → sr. Prazan string je poslednja odbrana
    /// od poglavlja bez ijednog naslova; bolje bezimeno nego „nil".
    private func title(of chapter: Chapter) -> String {
        for candidate in [language, "en", "sr"] {
            if let t = chapter.title[candidate], !t.isEmpty { return t }
        }
        return ""
    }

    private func title(of step: CurriculumStep) -> String {
        switch step.kind {
        case .lesson(let id):
            return LessonRepository.shared.lesson(id: id, language: language)?.title
                ?? Loc("Lekcija nije dostupna")
        case .practice(_, let count, _): return LocF("Vežba · %lld", count)
        case .test(_, let count, _):     return LocF("Test · %lld", count)
        case .game:                      return Loc("Partija")
        }
    }

    private func icon(of step: CurriculumStep) -> String {
        switch step.kind {
        case .lesson:   return "book.fill"
        case .practice: return "puzzlepiece.fill"
        case .test:     return "checkmark.seal.fill"
        case .game:     return "cpu"
        }
    }

    /// Lekcijski korak je jedini koji se u ovoj fazi MOZE otvoriti. Pokretaci
    /// za vezbu, test i partiju stizu u zadacima 4 i 5; do tada su ti redovi
    /// vidljivi sa tacnim stanjem, ali bez ikakve akcije.
    private func lessonId(of step: CurriculumStep) -> String? {
        if case .lesson(let id) = step.kind { return id }
        return nil
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            ZStack {
                AppBackgroundView()

                ScrollView {
                    VStack(alignment: .leading, spacing: DS.Space.l) {
                        // Naslov zivi u sadrzaju, a nav bar je sakriven — vidi
                        // isto objasnjenje u `LearnView`.
                        Text(Loc("Put"))
                            .font(.dsTitle)
                            .foregroundStyle(DS.ink)
                            .padding(.top, DS.Space.xs)

                        if curriculum == nil {
                            unavailableCard
                        } else {
                            // `states` se racuna JEDNOM i prosledjuje dalje —
                            // tri sekcije koje bi ga svaka za sebe racunale
                            // mogle bi da se raziđu ako se napredak promeni
                            // usred iscrtavanja.
                            loaded(states)
                        }

                        Spacer(minLength: DS.Space.l)
                    }
                    .padding(.horizontal, DS.Space.l)
                    .padding(.vertical, DS.Space.m)
                }
                .safeAreaPadding(.bottom, 24)
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .environment(viewModel)
    }

    @ViewBuilder
    private func loaded(_ states: [String: StepState]) -> some View {
        continueCard(states)
        streakCard
        chapters(states)
    }

    // MARK: - Nastavak

    @ViewBuilder
    private func continueCard(_ states: [String: StepState]) -> some View {
        if let next = nextStep(states) {
            if let lessonId = lessonId(of: next.step) {
                NavigationLink {
                    LessonDetailView(lessonId: lessonId, stepId: next.step.id)
                } label: {
                    continueLabel(next)
                }
                .buttonStyle(.plain)
            } else {
                // Vezba / test / partija — jos nema pokretac (zadaci 4 i 5).
                continueLabel(next)
            }
        } else {
            HStack(spacing: DS.Space.m) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.dsHeading)
                    .foregroundStyle(DS.success)
                Text(Loc("Prešao si ceo put!"))
                    .font(.dsHeading)
                    .foregroundStyle(DS.ink)
                Spacer(minLength: 0)
            }
            .padding(DS.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.l))
            .overlay(RoundedRectangle(cornerRadius: DS.Radius.l).strokeBorder(DS.line, lineWidth: 1))
        }
    }

    private func continueLabel(_ next: (index: Int, step: CurriculumStep)) -> some View {
        HStack(alignment: .center, spacing: DS.Space.m) {
            VStack(alignment: .leading, spacing: DS.Space.xs) {
                Text(Loc("Nastavi"))
                    .font(.dsCaption.weight(.semibold))
                    .foregroundStyle(DS.onAccent.opacity(0.85))
                Text(LocF("Korak %lld", next.index))
                    .font(.dsCaption)
                    .foregroundStyle(DS.onAccent.opacity(0.85))
                Text(title(of: next.step))
                    .font(.dsHeading.weight(.bold))
                    .foregroundStyle(DS.onAccent)
                    .multilineTextAlignment(.leading)
            }
            Spacer(minLength: 0)
            Image(systemName: icon(of: next.step))
                .font(.dsTitle)
                .foregroundStyle(DS.onAccent.opacity(0.85))
        }
        .padding(DS.Space.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DS.accent, in: RoundedRectangle(cornerRadius: DS.Radius.l))
        .accessibilityElement(children: .combine)
    }

    // MARK: - Streak i dnevni cilj

    private var streakCard: some View {
        let goalMet = store.goalMetToday
        return VStack(alignment: .leading, spacing: DS.Space.s) {
            HStack(alignment: .firstTextBaseline, spacing: DS.Space.s) {
                Text(store.currentStreak.formatted())
                    .font(.dsTitle)
                    .foregroundStyle(DS.ink)
                Text(Loc("Dana zaredom"))
                    .font(.dsBody)
                    .foregroundStyle(DS.inkMuted)
                Spacer(minLength: 0)
            }
            HStack(spacing: DS.Space.xs) {
                Image(systemName: goalMet ? "checkmark.circle.fill" : "circle")
                Text(goalMet ? Loc("Cilj za danas ispunjen") : Loc("Cilj za danas nije ispunjen"))
            }
            .font(.dsCaption)
            .foregroundStyle(goalMet ? DS.success : DS.inkMuted)
        }
        .padding(DS.Space.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.l))
        .overlay(RoundedRectangle(cornerRadius: DS.Radius.l).strokeBorder(DS.line, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }

    // MARK: - Poglavlja

    @ViewBuilder
    private func chapters(_ states: [String: StepState]) -> some View {
        ForEach(curriculum?.chapters ?? [], id: \.id) { chapter in
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    Text(title(of: chapter))
                        .font(.dsHeading)
                        .foregroundStyle(DS.ink)
                    Spacer(minLength: DS.Space.s)
                    Text(completion(of: chapter, states)
                            .formatted(.percent.locale(LocalizationManager.shared.locale)
                                               .precision(.fractionLength(0))))
                        .font(.dsCaption)
                        .foregroundStyle(DS.inkMuted)
                        .monospacedDigit()
                }
                .padding(.horizontal, DS.Space.l)
                .padding(.top, DS.Space.l)
                .padding(.bottom, DS.Space.m)

                // Tanka traka napretka. Nije bedz — ista informacija kao
                // procenat pored naslova, samo citljiva jednim pogledom.
                progressBar(completion(of: chapter, states))
                    .padding(.horizontal, DS.Space.l)
                    .padding(.bottom, DS.Space.s)

                ForEach(Array(chapter.steps.enumerated()), id: \.element.id) { index, step in
                    if index > 0 {
                        Rectangle()
                            .fill(DS.line)
                            .frame(height: 1)
                            .padding(.leading, DS.Space.l + 28 + DS.Space.m)
                    }
                    stepRow(step, state: states[step.id] ?? .locked)
                }
            }
            .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.l))
            .overlay(RoundedRectangle(cornerRadius: DS.Radius.l).strokeBorder(DS.line, lineWidth: 1))
        }
    }

    private func progressBar(_ value: Double) -> some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(DS.fill)
                Capsule().fill(DS.accent)
                    .frame(width: max(0, min(1, value)) * geo.size.width)
            }
        }
        .frame(height: 4)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func stepRow(_ step: CurriculumStep, state: StepState) -> some View {
        if state != .locked, let lessonId = lessonId(of: step) {
            NavigationLink {
                LessonDetailView(lessonId: lessonId, stepId: step.id)
            } label: {
                stepLabel(step, state: state, showsChevron: true)
            }
            .buttonStyle(.plain)
        } else {
            // Zakljucan korak nije dodirljiv; dostupna vezba/test/partija je u
            // ovoj fazi jos bez pokretaca, pa takodje stoji mirno.
            stepLabel(step, state: state, showsChevron: false)
        }
    }

    private func stepLabel(_ step: CurriculumStep, state: StepState, showsChevron: Bool) -> some View {
        HStack(spacing: DS.Space.m) {
            ZStack {
                RoundedRectangle(cornerRadius: DS.Radius.s)
                    .fill(state == .locked ? DS.fill : DS.accent.opacity(0.16))
                    .frame(width: 28, height: 28)
                Image(systemName: icon(of: step))
                    .font(.dsCaption)
                    .foregroundStyle(state == .locked ? DS.inkMuted : DS.accent)
            }

            Text(title(of: step))
                .font(.dsBody)
                .foregroundStyle(state == .locked ? DS.inkMuted : DS.ink)
                .multilineTextAlignment(.leading)

            Spacer(minLength: DS.Space.s)

            switch state {
            case .completed:
                Image(systemName: "checkmark.circle.fill")
                    .font(.dsBody)
                    .foregroundStyle(DS.success)
            case .locked:
                Image(systemName: "lock.fill")
                    .font(.dsCaption)
                    .foregroundStyle(DS.inkMuted)
            case .available:
                if showsChevron {
                    Image(systemName: "chevron.right")
                        .font(.dsCaption.weight(.semibold))
                        .foregroundStyle(DS.inkMuted)
                }
            }
        }
        .padding(.horizontal, DS.Space.l)
        .padding(.vertical, DS.Space.m)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    // MARK: - Put nije dostupan

    private var unavailableCard: some View {
        // Sadrzaj je van koda; ako `curriculum.json` nedostaje iz bundle-a,
        // korisnik mora da vidi zasto, a ne prazan ekran.
        Text(Loc("Put nije dostupan"))
            .font(.dsBody)
            .foregroundStyle(DS.inkMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(DS.Space.l)
            .background(DS.surface, in: RoundedRectangle(cornerRadius: DS.Radius.l))
            .overlay(RoundedRectangle(cornerRadius: DS.Radius.l).strokeBorder(DS.line, lineWidth: 1))
    }
}

// MARK: - Preview

#Preview("Svetla tema") {
    PathView(viewModel: LearnViewModel())
        .preferredColorScheme(.light)
}

#Preview("Tamna tema") {
    PathView(viewModel: LearnViewModel())
        .preferredColorScheme(.dark)
}
