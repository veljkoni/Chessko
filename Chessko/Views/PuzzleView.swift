import SwiftUI

// MARK: - Puzzle View

struct PuzzleView: View {
    var viewModel: PuzzleViewModel

    @State private var showCalendar = false
    @State private var calendarDate: Date = Calendar.current.startOfDay(for: Date())
    @State private var bounceLeft = 0
    @State private var bounceCalendar = 0
    @State private var bounceCheckmark = 0
    @State private var bounceRight = 0

    var body: some View {
        NavigationStack {
            ZStack {
                AppBackgroundView()

                GeometryReader { geo in
                    let isLandscape = geo.size.width > geo.size.height
                    let side = boardSide(in: geo.size, isLandscape: isLandscape)

                    if case .loading = viewModel.phase {
                        VStack {
                            Spacer()
                            loadingView
                            Spacer()
                        }
                        .frame(width: geo.size.width, height: geo.size.height)
                    } else if case .unavailable(let msg) = viewModel.phase {
                        VStack {
                            Spacer()
                            errorView(message: msg)
                            Spacer()
                        }
                        .frame(width: geo.size.width, height: geo.size.height)
                    } else {
                        if isLandscape {
                            // spec § 7: od vrha — tabla levo, desno tagovi →
                            // uputstvo → radnja; ništa centrirano po visini.
                            HStack(alignment: .top, spacing: DS.Space.xl) {
                                board(side: side)

                                ScrollView {
                                    VStack(alignment: .leading, spacing: DS.Space.m) {
                                        if let puzzle = viewModel.currentPuzzle {
                                            puzzleHeader(puzzle: puzzle)
                                        }
                                        instructionBox
                                        actionButtons
                                            .padding(.top, DS.Space.xs)
                                    }
                                    .padding(.vertical, DS.Space.l)
                                }
                                .scrollBounceBehavior(.basedOnSize)
                            }
                            .padding(.horizontal, DS.Space.l)
                            .frame(width: geo.size.width, height: geo.size.height, alignment: .top)
                        } else {
                            ScrollView {
                                puzzleContent(boardSide: side)
                            }
                            .scrollBounceBehavior(.basedOnSize)
                            .safeAreaPadding(.bottom, 24)
                            .frame(width: geo.size.width, height: geo.size.height)
                        }
                    }
                }
            }
            .navigationTitle(Loc("Zadaci"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(DS.ground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    // spec § 7: „‹ Danas ›" — strelice kao ikone-dugmad 44 × 44 u
                    // `accent`; nedostupna strelica (danas → desno) ostaje na
                    // mestu sa 38 %.
                    let loading = viewModel.phase == .loading
                    let canPrev = viewModel.canGoPrevious && !loading
                    let canNext = viewModel.canGoNext && !loading
                    HStack(spacing: 0) {
                        Button {
                            viewModel.goToPrevious()
                        } label: {
                            Image(systemName: "chevron.left")
                                .font(.dsBody.weight(.semibold))
                                .foregroundStyle(DS.accent)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                                .opacity(canPrev ? 1 : 0.38)
                                .symbolEffect(.bounce, value: bounceLeft)
                        }
                        .buttonStyle(DSPressableButtonStyle())
                        .disabled(!canPrev)
                        .accessibilityLabel(Loc("Prethodni dan"))

                        Button {
                            calendarDate = viewModel.selectedDate
                            showCalendar = true
                        } label: {
                            HStack(spacing: 5) {
                                Image(systemName: "calendar")
                                    .font(.dsCaption)
                                    .foregroundStyle(DS.inkMuted)
                                    .symbolEffect(.bounce, value: bounceCalendar)
                                Text(dateTitle)
                                    .font(.dsCaption.weight(.semibold))
                                    .foregroundStyle(DS.ink)
                                if viewModel.isSolved(viewModel.selectedDate) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.dsCaption)
                                        .foregroundStyle(DS.success)
                                        .symbolEffect(.bounce, value: bounceCheckmark)
                                        .accessibilityLabel(Loc("Rešeno"))
                                }
                            }
                            .frame(minHeight: 44)
                        }
                        .disabled(loading)

                        Button {
                            viewModel.goToNext()
                        } label: {
                            Image(systemName: "chevron.right")
                                .font(.dsBody.weight(.semibold))
                                .foregroundStyle(DS.accent)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                                .opacity(canNext ? 1 : 0.38)
                                .symbolEffect(.bounce, value: bounceRight)
                        }
                        .buttonStyle(DSPressableButtonStyle())
                        .disabled(!canNext)
                        .accessibilityLabel(Loc("Sledeći dan"))
                    }
                }
            }
            .sheet(isPresented: $showCalendar) {
                calendarSheet
            }
        }
        .onAppear { viewModel.loadDailyPuzzle() }
        .onChange(of: viewModel.phase) { oldPhase, newPhase in
            // Sekvenca skokova navodi korisnika na strelice za DATUM — u
            // `.practice` rezimu one nemaju veze sa zadatkom koji je upravo
            // resen, pa se ne pokrece.
            if newPhase == .solved && viewModel.mode == .daily {
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                    // Niz se ne moze otkazati; ako je korisnik u medjuvremenu
                    // tapnuo "Sledeći zadatak", cetiri haptika bi pala usred
                    // sledeceg zadatka. Zato se stanje proverava i ovde.
                    guard viewModel.mode == .daily, viewModel.phase == .solved else { return }

                    // 1. Chevron left bounces
                    bounceLeft += 1
                    Haptics.impact(.light)
                    
                    // 2. Calendar bounces after 0.15s
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                        bounceCalendar += 1
                        Haptics.impact(.light)
                        
                        // 3. Checkmark bounces after 0.15s
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                            bounceCheckmark += 1
                            Haptics.impact(.light)
                            
                            // 4. Chevron right bounces after 0.15s
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                                bounceRight += 1
                                Haptics.impact(.light)
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Calendar Sheet

    private var calendarSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {

                    // Date Picker
                    DatePicker(
                        "",
                        selection: $calendarDate,
                        in: viewModel.minSelectableDate...viewModel.maxSelectableDate,
                        displayedComponents: .date
                    )
                    .datePickerStyle(.graphical)
                    .tint(DS.accent)
                    .padding(.horizontal, 8)

                    // Solved dates legend
                    solvedLegend
                        .padding(.horizontal, 16)
                }
                .padding(.bottom, 32)
            }
            .background(DS.ground) // spec § 3: nijedna sistemska podloga
            .navigationTitle(Loc("Izaberi dan"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(Loc("Otkaži")) { showCalendar = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(Loc("Otvori")) {
                        viewModel.load(date: calendarDate)
                        showCalendar = false
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    private var solvedLegend: some View {
        let solved = solvedDatesInRange
        guard !solved.isEmpty else { return AnyView(EmptyView()) }

        return AnyView(
            VStack(alignment: .leading, spacing: 10) {
                Text(Loc("Rešeni zadaci"))
                    .font(.dsSectionHeading) // spec § 3: naslov sekcije bold `ink`
                    .foregroundStyle(DS.ink)

                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 7),
                          spacing: 8) {
                    ForEach(solved, id: \.self) { date in
                        Button {
                            calendarDate = date
                        } label: {
                            VStack(spacing: 2) {
                                Text(dayNumber(date))
                                    .font(.dsCaption.weight(.semibold))
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.dsCaption)
                                    .foregroundStyle(DS.success)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                            .background(
                                calendarDate == date
                                    ? DS.accent.opacity(0.15)
                                    : Color.secondary.opacity(0.08),
                                in: RoundedRectangle(cornerRadius: 8)
                            )
                        }
                        .foregroundStyle(DS.ink)
                    }
                }
            }
        )
    }

    // MARK: - Main Content

    /// Jedna vrednost strane table po ekranu (Task 11 menja samo telo).
    /// Portret: širina (do `DS.maxBoardSide`); pejzaž: visina.
    private func boardSide(in size: CGSize, isLandscape: Bool) -> CGFloat {
        let available = isLandscape
            ? size.height - 2 * DS.Space.l
            : size.width - 2 * DS.Space.l
        return max(200, min(available, DS.maxBoardSide))
    }

    private func board(side: CGFloat) -> some View {
        BoardView(
            board:            viewModel.gameState.board,
            isFlipped:        viewModel.isFlipped,
            selectedPosition: viewModel.selectedPosition,
            legalMoves:       viewModel.legalMovesForSelected,
            lastMove:         viewModel.lastMove,
            animatingPiece:   viewModel.animatingPiece,
            flyingCapture:    viewModel.flyingCapture,
            playerColor:      viewModel.playerColor,
            isPlayerTurn:     viewModel.isPlayerTurn,
            gameStatus:       viewModel.gameState.status,
            onTap:            { viewModel.tap(position: $0) }
        )
        .frame(width: side, height: side)
    }

    /// spec § 7: od vrha — tagovi → uputstvo → tabla → radnja (`Space.l`).
    /// Ništa nije centrirano po visini, ništa nije prikovano za dno.
    private func puzzleContent(boardSide side: CGFloat) -> some View {
        VStack(spacing: DS.Space.m) {
            if let puzzle = viewModel.currentPuzzle {
                puzzleHeader(puzzle: puzzle)
            }

            instructionBox

            board(side: side)
                .frame(maxWidth: .infinity)

            actionButtons
                .padding(.top, DS.Space.l - DS.Space.m)
        }
        .padding(.horizontal, DS.Space.l)
        .padding(.top, DS.Space.l)
        .padding(.bottom, DS.Space.l)
    }

    // MARK: - Puzzle Header

    /// Red tagova (spec § 7): rejting (zvezdica `warning`, broj `ink`, bez
    /// tačke hiljada), teme (`fill` + `inkMuted`) i težina kao neutralan čip
    /// (`fill` + `ink` uz 1–3 crtice — „Teško" nije greška, „Lako" nije uspeh).
    private func puzzleHeader(puzzle: ChessPuzzle) -> some View {
        let themes = Array(puzzle.themeList.map(localizeTheme).filter { !$0.isEmpty }.prefix(2))
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: DS.Space.s) {
                HStack(spacing: DS.Space.xs) {
                    Image(systemName: "star.fill")
                        .font(.dsCaption)
                        .foregroundStyle(DS.warning)
                        .accessibilityHidden(true)
                    // `verbatim`: interpolacija u `Text` ide kroz lokalni format
                    // i daje „1.503" — spec traži „1503".
                    Text(verbatim: String(puzzle.rating))
                        .font(.dsMono.weight(.semibold))
                        .foregroundStyle(DS.ink)
                }
                .modifier(PuzzleTagStyle())
                .accessibilityElement(children: .combine)

                DifficultyChip(label: puzzle.difficultyLabel, level: difficultyLevel(puzzle.rating))

                ForEach(Array(themes.enumerated()), id: \.offset) { _, label in
                    Text(label)
                        .font(.dsCaption)
                        .foregroundStyle(DS.inkMuted)
                        .modifier(PuzzleTagStyle())
                }
            }
        }
        .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
    }

    // MARK: - Uputstvo

    /// spec § 7: uputstvo je okvir (`DSInstructionBox`), ne dugme ni polje.
    private var instructionBox: some View {
        DSInstructionBox(
            text: viewModel.statusMessage,
            sideToMove: viewModel.phase.isSolving ? viewModel.playerColor : nil,
            tone: viewModel.phase.instructionTone
        )
    }

    // MARK: - Action Buttons

    /// spec § 7: jedna radnja. Tokom rešavanja samo „Prikaži rešenje"
    /// (sporedno dugme); posle rešenja „Sledeći zadatak" (primarno).
    @ViewBuilder
    private var actionButtons: some View {
        switch viewModel.phase {
        case .playing, .wrongMove:
            Button {
                viewModel.showSolution()
            } label: {
                Label(Loc("Prikaži rešenje"), systemImage: "eye")
            }
            .buttonStyle(DSSecondaryButtonStyle())

        case .solved:
            VStack(spacing: DS.Space.s) {
                Button {
                    viewModel.nextPuzzle()
                } label: {
                    Label(Loc("Sledeći zadatak"), systemImage: "arrow.triangle.2.circlepath")
                }
                .buttonStyle(DSPrimaryButtonStyle())

                // Sve ispod je vezano za DNEVNI zadatak. U `.practice` rezimu
                // "Sledeći dan" bi korisnika tiho izbacio iz vezbanja u tudji
                // datum, a poruka "Završio si zadatak za danas!" bi lagala —
                // resen vezbovni zadatak ne oznacava dan resenim (kvacica u
                // toolbaru bi na istom ekranu tvrdila suprotno).
                if viewModel.mode == .daily {
                    if viewModel.canGoNext {
                        Button {
                            viewModel.goToNext()
                        } label: {
                            Label(Loc("Sledeći dan"), systemImage: "chevron.right")
                        }
                        .buttonStyle(DSSecondaryButtonStyle())
                    } else {
                        HStack(spacing: DS.Space.s) {
                            Image(systemName: "star.fill")
                                .foregroundStyle(DS.warning)
                            Text(Loc("Završio si zadatak za danas!"))
                                .font(.dsBody.weight(.medium))
                                .foregroundStyle(DS.ink)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, DS.Space.m)
                    }
                }
            }

        default:
            EmptyView()
        }
    }

    // MARK: - Loading / Error

    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
                .tint(DS.inkMuted)
                .scaleEffect(1.4)
            Text(Loc("Učitavam zadatak..."))
                .foregroundStyle(DS.inkMuted)
                .font(.dsBody)
        }
    }

    private func errorView(message: String) -> some View {
        VStack(spacing: 20) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundStyle(DS.inkMuted)
            Text(message)
                .foregroundStyle(DS.ink)
                .font(.dsBody)
                .multilineTextAlignment(.center)
            Button {
                viewModel.loadDailyPuzzle()
            } label: {
                // Ostaje: jedini izlaz iz stanja greške pri učitavanju (spec § 7
                // uklanja „Pokušaj ponovo" samo iz rešavanja).
                Label(Loc("Pokušaj ponovo"), systemImage: "arrow.clockwise")
                    .font(.dsBody.weight(.medium))
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(DS.accent) // spec § 3: primarno dugme `accent` + `onAccent`
                    .foregroundStyle(DS.onAccent)
                    .clipShape(RoundedRectangle(cornerRadius: DS.Radius.m)) // spec § 3: nigde „stadion"
            }
        }
        .padding(32)
    }

    // MARK: - Helpers

    private var dateTitle: String {
        let cal   = Calendar.current
        let today = cal.startOfDay(for: Date())
        let sel   = viewModel.selectedDate

        if sel == today {
            return Loc("Danas")
        } else if sel == cal.date(byAdding: .day, value: -1, to: today) {
            return Loc("Juče")
        } else {
            let f = DateFormatter()
            f.locale = LocalizationManager.shared.locale
            f.setLocalizedDateFormatFromTemplate("dMMM")
            return f.string(from: sel)
        }
    }

    private func dayNumber(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "d.M."
        return f.string(from: date)
    }

    /// All dates in the selectable range that have been solved, newest first.
    private var solvedDatesInRange: [Date] {
        let cal   = Calendar.current
        let today = cal.startOfDay(for: Date())
        let start = viewModel.minSelectableDate
        var dates: [Date] = []
        var d = today
        while d >= start {
            if viewModel.isSolved(d) { dates.append(d) }
            guard let prev = cal.date(byAdding: .day, value: -1, to: d) else { break }
            d = prev
        }
        return dates
    }

    /// 1–3 crtice uz težinu — isti pragovi kao `ChessPuzzle.difficultyLabel`.
    private func difficultyLevel(_ rating: Int) -> Int {
        switch rating {
        case ..<1200: return 1
        case 1200..<1600: return 2
        default: return 3
        }
    }

    private func localizeTheme(_ theme: String) -> String {
        switch theme {
        case "opening":          return Loc("Otvaranje")
        case "middlegame":       return Loc("Srednja igra")
        case "endgame":          return Loc("Završnica")
        case "mate":             return Loc("Mat")
        case "mateIn1":          return Loc("Mat u 1")
        case "mateIn2":          return Loc("Mat u 2")
        case "mateIn3":          return Loc("Mat u 3")
        case "fork":             return Loc("Vilica")
        case "pin":              return Loc("Vezivanje")
        case "skewer":           return Loc("Nabijanje")
        case "sacrifice":        return Loc("Žrtva")
        case "discoveredAttack": return Loc("Otkriveni napad")
        case "deflection":       return Loc("Odvlačenje")
        case "advantage":        return Loc("Prednost")
        case "crushing":         return Loc("Odlučujuće")
        case "short":            return Loc("Kratko")
        case "long":             return Loc("Dugo")
        case "oneMove":          return Loc("Jedan potez")
        case "defensiveMove":    return Loc("Odbrana")
        case "queensideAttack":  return Loc("Napad na damu")
        case "kingsideAttack":   return Loc("Napad na kralja")
        // Nepoznata tema se ne prikazuje — bolje nista nego sirov kljuc
        // tipa "backRankMate". Mapa namerno pokriva 21 od 73 teme koliko ih
        // baza ima: empirijski provereno da od 20 000 isporucenih zadataka
        // nijedan ne ostaje bez ijednog cipa, a samo 184 dobijaju jedan
        // umesto dva. Prosirivanje mape nema merljivu korist.
        default:                 return ""
        }
    }
}

// MARK: - Tagovi

/// Čip u redu tagova: `fill`, `Radius.s`, bez ivice (spec § 7 „tagovi u tokenima").
private struct PuzzleTagStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.s, style: .continuous))
    }
}

/// Težina kao neutralan čip: `fill` + `ink` uz 1–3 crtice (spec § 7).
private struct DifficultyChip: View {
    let label: String
    let level: Int

    var body: some View {
        HStack(spacing: DS.Space.s) {
            Text(label)
                .font(.dsCaption.weight(.medium))
                .foregroundStyle(DS.ink)
            HStack(spacing: 2) {
                ForEach(0..<3, id: \.self) { i in
                    Capsule()
                        .fill(i < level ? DS.ink : DS.ink.opacity(0.2))
                        .frame(width: 8, height: 3)
                }
            }
            .accessibilityHidden(true)
        }
        .modifier(PuzzleTagStyle())
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Faza → okvir uputstva (deli se sa `StepPracticeView`)

extension PuzzlePhase {
    /// Igrač je na potezu u zadatku (krug strane se prikazuje samo tad).
    var isSolving: Bool {
        switch self {
        case .playing, .wrongMove: return true
        default: return false
        }
    }

    var instructionTone: DSInstructionTone {
        switch self {
        case .wrongMove:       return .wrong
        case .solved:          return .solved
        case .showingSolution: return .solution
        default:               return .hint
        }
    }
}

#Preview {
    PuzzleView(viewModel: PuzzleViewModel())
}
