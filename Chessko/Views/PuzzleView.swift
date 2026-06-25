import SwiftUI

// MARK: - Puzzle View

struct PuzzleView: View {
    var viewModel: PuzzleViewModel

    @State private var showCalendar = false
    @State private var calendarDate: Date = Calendar.current.startOfDay(for: Date())

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.09, green: 0.14, blue: 0.31)
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    // Date navigation bar — always visible
                    dateNavBar
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                        .padding(.bottom, 12)

                    if case .loading = viewModel.phase {
                        Spacer()
                        loadingView
                        Spacer()
                    } else if case .networkError(let msg) = viewModel.phase {
                        Spacer()
                        errorView(message: msg)
                        Spacer()
                    } else {
                        puzzleContent
                    }
                }
            }
            .navigationTitle("Zadaci")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color(red: 0.09, green: 0.14, blue: 0.31), for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .sheet(isPresented: $showCalendar) {
                calendarSheet
            }
        }
        .task { await viewModel.loadDailyPuzzle() }
    }

    // MARK: - Date Navigation Bar

    private var dateNavBar: some View {
        HStack(spacing: 0) {
            Button {
                viewModel.goToPrevious()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.title3.weight(.semibold))
                    .frame(width: 44, height: 36)
                    .foregroundStyle(viewModel.canGoPrevious ? .white : .white.opacity(0.25))
            }
            .disabled(!viewModel.canGoPrevious)

            Spacer()

            // Date label + solved badge — tap to open calendar
            Button {
                calendarDate = viewModel.selectedDate
                showCalendar = true
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "calendar")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.7))
                    Text(dateTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                    if viewModel.isSolved(viewModel.selectedDate) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption)
                            .foregroundStyle(.green)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(.white.opacity(0.1), in: Capsule())
            }

            Spacer()

            Button {
                viewModel.goToNext()
            } label: {
                Image(systemName: "chevron.right")
                    .font(.title3.weight(.semibold))
                    .frame(width: 44, height: 36)
                    .foregroundStyle(viewModel.canGoNext ? .white : .white.opacity(0.25))
            }
            .disabled(!viewModel.canGoNext)
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
                    .tint(.blue)
                    .padding(.horizontal, 8)

                    // Solved dates legend
                    solvedLegend
                        .padding(.horizontal, 16)
                }
                .padding(.bottom, 32)
            }
            .navigationTitle("Izaberi dan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Otkaži") { showCalendar = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Otvori") {
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
                Text("Rešeni zadaci")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)

                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 7),
                          spacing: 8) {
                    ForEach(solved, id: \.self) { date in
                        Button {
                            calendarDate = date
                        } label: {
                            VStack(spacing: 2) {
                                Text(dayNumber(date))
                                    .font(.caption2.weight(.semibold))
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.caption2)
                                    .foregroundStyle(.green)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                            .background(
                                calendarDate == date
                                    ? Color.blue.opacity(0.15)
                                    : Color.secondary.opacity(0.08),
                                in: RoundedRectangle(cornerRadius: 8)
                            )
                        }
                        .foregroundStyle(.primary)
                    }
                }
            }
        )
    }

    // MARK: - Main Content

    private var puzzleContent: some View {
        VStack(spacing: 14) {

            if let puzzle = viewModel.currentPuzzle {
                puzzleHeader(puzzle: puzzle)
            }

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
                onTap:            { viewModel.tap(position: $0) }
            )
            .padding(.horizontal, 4)

            statusCard

            actionButtons

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
    }

    // MARK: - Puzzle Header

    private func puzzleHeader(puzzle: ChessPuzzle) -> some View {
        HStack(spacing: 12) {
            HStack(spacing: 4) {
                Image(systemName: "star.fill")
                    .font(.caption2)
                Text("\(puzzle.rating)")
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(ratingColor(puzzle.rating))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(ratingColor(puzzle.rating).opacity(0.18), in: Capsule())

            let themes = puzzle.themeList.prefix(2)
            ForEach(Array(themes), id: \.self) { theme in
                Text(localizeTheme(theme))
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.75))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(.white.opacity(0.1), in: Capsule())
            }

            Spacer()
        }
    }

    // MARK: - Status Card

    private var statusCard: some View {
        HStack(spacing: 10) {
            Image(systemName: statusIcon)
                .font(.subheadline)
                .foregroundStyle(statusColor)
            Text(viewModel.statusMessage)
                .font(.subheadline)
                .foregroundStyle(statusColor)
                .animation(.default, value: viewModel.statusMessage)
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(statusColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(statusColor.opacity(0.3), lineWidth: 1)
        )
    }

    private var statusIcon: String {
        switch viewModel.phase {
        case .loading:         return "arrow.clockwise"
        case .networkError:    return "wifi.slash"
        case .playing:         return "lightbulb"
        case .wrongMove:       return "xmark.circle.fill"
        case .solved:          return "checkmark.seal.fill"
        case .showingSolution: return "eye.fill"
        }
    }

    private var statusColor: Color {
        switch viewModel.phase {
        case .wrongMove:       return .red
        case .solved:          return .green
        case .showingSolution: return .orange
        default:               return .white.opacity(0.9)
        }
    }

    // MARK: - Action Buttons

    @ViewBuilder
    private var actionButtons: some View {
        switch viewModel.phase {
        case .playing, .wrongMove:
            Button {
                viewModel.showSolution()
            } label: {
                Label("Prikaži rešenje", systemImage: "eye")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white.opacity(0.8))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
            }

        case .solved:
            if viewModel.canGoNext {
                Button {
                    viewModel.goToNext()
                } label: {
                    Label("Sledeći dan", systemImage: "chevron.right")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(.white, in: RoundedRectangle(cornerRadius: 12))
                }
            } else {
                // Today's puzzle solved — show completion
                HStack(spacing: 8) {
                    Image(systemName: "star.fill")
                        .foregroundStyle(.yellow)
                    Text("Završio si zadatak za danas!")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.85))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
            }

        default:
            EmptyView()
        }
    }

    // MARK: - Loading / Error

    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
                .tint(.white)
                .scaleEffect(1.4)
            Text("Učitavam zadatak...")
                .foregroundStyle(.white.opacity(0.7))
                .font(.subheadline)
        }
    }

    private func errorView(message: String) -> some View {
        VStack(spacing: 20) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 48))
                .foregroundStyle(.white.opacity(0.5))
            Text(message)
                .foregroundStyle(.white.opacity(0.8))
                .font(.subheadline)
                .multilineTextAlignment(.center)
            Button {
                Task { await viewModel.loadDailyPuzzle() }
            } label: {
                Label("Pokušaj ponovo", systemImage: "arrow.clockwise")
                    .font(.subheadline.weight(.medium))
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(.white)
                    .foregroundStyle(.black)
                    .clipShape(Capsule())
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
            return "Danas"
        } else if sel == cal.date(byAdding: .day, value: -1, to: today) {
            return "Juče"
        } else {
            let f = DateFormatter()
            f.locale = Locale(identifier: "sr_Latn_RS")
            f.dateFormat = "d. MMM"
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

    private func ratingColor(_ rating: Int) -> Color {
        switch rating {
        case ..<1200: return .green
        case 1200..<1600: return .yellow
        default: return .red
        }
    }

    private func localizeTheme(_ theme: String) -> String {
        switch theme {
        case "opening":          return "Otvaranje"
        case "middlegame":       return "Srednja igra"
        case "endgame":          return "Završnica"
        case "mate":             return "Mat"
        case "mateIn1":          return "Mat u 1"
        case "mateIn2":          return "Mat u 2"
        case "mateIn3":          return "Mat u 3"
        case "fork":             return "Vilica"
        case "pin":              return "Vezivanje"
        case "skewer":           return "Nabijanje"
        case "sacrifice":        return "Žrtva"
        case "discoveredAttack": return "Otkriveni napad"
        case "deflection":       return "Odvlačenje"
        case "advantage":        return "Prednost"
        case "crushing":         return "Odlučujuće"
        case "short":            return "Kratko"
        case "long":             return "Dugo"
        case "oneMove":          return "Jedan potez"
        case "defensiveMove":    return "Odbrana"
        case "queensideAttack":  return "Napad na dam"
        case "kingsideAttack":   return "Napad na kral"
        default:                 return theme
        }
    }
}

#Preview {
    PuzzleView(viewModel: PuzzleViewModel())
}
