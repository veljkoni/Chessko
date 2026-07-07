import SwiftUI

struct TimeControlPreset: Identifiable, Hashable {
    let id: UUID
    let name: String
    let category: String
    let baseMinutes: Int
    let incrementSeconds: Int
    let hasSecondPhase: Bool
    let secondPhaseTriggerMove: Int
    let secondPhaseAddedMinutes: Int
    let isOfficial: Bool
    let subtitle: String

    init(name: String, category: String, baseMinutes: Int, incrementSeconds: Int, hasSecondPhase: Bool = false, secondPhaseTriggerMove: Int = 0, secondPhaseAddedMinutes: Int = 0, isOfficial: Bool = false, subtitle: String = "") {
        self.id = UUID()
        self.name = name
        self.category = category
        self.baseMinutes = baseMinutes
        self.incrementSeconds = incrementSeconds
        self.hasSecondPhase = hasSecondPhase
        self.secondPhaseTriggerMove = secondPhaseTriggerMove
        self.secondPhaseAddedMinutes = secondPhaseAddedMinutes
        self.isOfficial = isOfficial
        self.subtitle = subtitle
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(name)
        hasher.combine(category)
        hasher.combine(baseMinutes)
        hasher.combine(incrementSeconds)
    }

    static func == (lhs: TimeControlPreset, rhs: TimeControlPreset) -> Bool {
        return lhs.name == rhs.name &&
               lhs.category == rhs.category &&
               lhs.baseMinutes == rhs.baseMinutes &&
               lhs.incrementSeconds == rhs.incrementSeconds
    }
}

struct ChessClockView: View {
    @Environment(\.dismiss) private var dismiss

    // Available presets
    private let presets: [TimeControlPreset] = [
        TimeControlPreset(name: "3 + 2", category: "Blic", baseMinutes: 3, incrementSeconds: 2, isOfficial: true, subtitle: "Zvanični svetski blic"),
        TimeControlPreset(name: "5 + 0", category: "Blic", baseMinutes: 5, incrementSeconds: 0, subtitle: "Stara škola blica"),
        TimeControlPreset(name: "5 + 3", category: "Blic", baseMinutes: 5, incrementSeconds: 3, subtitle: "Turnirski blic"),
        
        TimeControlPreset(name: "15 + 10", category: "Ubrzani šah", baseMinutes: 15, incrementSeconds: 10, isOfficial: true, subtitle: "Zvanični svetski rapid"),
        TimeControlPreset(name: "10 + 5", category: "Ubrzani šah", baseMinutes: 10, incrementSeconds: 5, subtitle: "Popularni online rapid"),
        TimeControlPreset(name: "25 + 10", category: "Ubrzani šah", baseMinutes: 25, incrementSeconds: 10, subtitle: "Lokalni turniri"),
        
        TimeControlPreset(name: "90 + 30 + 30", category: "Klasični šah", baseMinutes: 90, incrementSeconds: 30, hasSecondPhase: true, secondPhaseTriggerMove: 40, secondPhaseAddedMinutes: 30, isOfficial: true, subtitle: "Turniri kandidata / FIDE"),
        TimeControlPreset(name: "90 + 30", category: "Klasični šah", baseMinutes: 90, incrementSeconds: 30, subtitle: "Domaća liga")
    ]

    @State private var selectedPreset: TimeControlPreset = TimeControlPreset(
        name: "3 + 2",
        category: "Blic",
        baseMinutes: 3,
        incrementSeconds: 2,
        hasSecondPhase: false,
        secondPhaseTriggerMove: 0,
        secondPhaseAddedMinutes: 0,
        isOfficial: true,
        subtitle: "Zvanični svetski blic"
    )

    // Timer states
    @State private var p1Time: Double = 180.0 // Player 1 (White, bottom)
    @State private var p2Time: Double = 180.0 // Player 2 (Black, top)
    @State private var p1Moves: Int = 0
    @State private var p2Moves: Int = 0

    // Game state
    @State private var activePlayer: Int? = nil // nil = not started or paused
    @State private var isPaused = false
    @State private var hasStarted = false
    @State private var showInfoSheet = false

    private let timer = Timer.publish(every: 0.1, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            // Background
            Color(uiColor: .systemBackground)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Player (Black) - Rotated 180
                playerArea(
                    playerNumber: 2,
                    timeLeft: p2Time,
                    moves: p2Moves,
                    isActive: activePlayer == 2,
                    isRotated: true
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    handleTap(playerNumber: 2)
                }

                // Center Control Bar
                controlBar

                // Bottom Player (White)
                playerArea(
                    playerNumber: 1,
                    timeLeft: p1Time,
                    moves: p1Moves,
                    isActive: activePlayer == 1,
                    isRotated: false
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    handleTap(playerNumber: 1)
                }
            }
        }
        .onReceive(timer) { _ in
            tick()
        }
        .onAppear {
            resetClock()
        }
        .statusBarHidden(true)
        .sheet(isPresented: $showInfoSheet) {
            ChessClockInfoView()
        }
    }

    // MARK: - Player Area Builder

    private func playerArea(playerNumber: Int, timeLeft: Double, moves: Int, isActive: Bool, isRotated: Bool) -> some View {
        let isTimeOut = timeLeft <= 0

        // Determine background and text colors based on player number and state
        let bgColor: Color
        let textColor: Color
        let subtextColor: Color

        if playerNumber == 2 {
            // Player 2 (Black) - rotated, top half
            if isTimeOut {
                bgColor = Color(hex: "#8C2525")
                textColor = .white
                subtextColor = .white.opacity(0.7)
            } else if isActive {
                bgColor = Color(hex: "#121212")
                textColor = .white
                subtextColor = .white.opacity(0.7)
            } else {
                bgColor = Color(hex: "#2C2C2E")
                textColor = .white.opacity(0.5)
                subtextColor = .white.opacity(0.3)
            }
        } else {
            // Player 1 (White) - bottom half
            if isTimeOut {
                bgColor = Color(hex: "#FADAD8")
                textColor = Color(uiColor: .systemRed)
                subtextColor = Color(uiColor: .systemRed).opacity(0.7)
            } else if isActive {
                bgColor = .white
                textColor = .black
                subtextColor = .black.opacity(0.6)
            } else {
                bgColor = Color(hex: "#E5E5EA")
                textColor = .black.opacity(0.5)
                subtextColor = .black.opacity(0.3)
            }
        }

        return ZStack {
            bgColor
                .animation(.easeInOut(duration: 0.2), value: isActive)
                .animation(.easeInOut(duration: 0.2), value: isTimeOut)

            VStack(spacing: 12) {
                if isTimeOut {
                    Text(LocalizedStringKey("Vreme je isteklo!"))
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(textColor)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 6)
                        .background(textColor.opacity(0.1), in: Capsule())
                } else {
                    if !hasStarted {
                        Text(LocalizedStringKey(selectedPreset.subtitle))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(subtextColor)
                    } else {
                        Text(String(format: NSLocalizedString("Poteza: %d", comment: ""), moves))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(subtextColor)
                    }
                }

                Text(formatTime(timeLeft))
                    .font(.system(size: 86, weight: .bold, design: .monospaced))
                    .foregroundStyle(textColor)
            }
            .rotationEffect(.degrees(isRotated ? 180 : 0))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Control Bar

    private var controlBar: some View {
        HStack(spacing: 24) {
            // Close Button
            Button {
                Haptics.impact(.light)
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.title2.weight(.bold))
                    .padding(12)
                    .background(Color.primary.opacity(0.05), in: Circle())
            }

            Spacer()

            // Time Selection Menu (only if game hasn't started)
            if !hasStarted {
                HStack(spacing: 8) {
                    Menu {
                        ForEach(["Blic", "Ubrzani šah", "Klasični šah"], id: \.self) { category in
                            Menu {
                                ForEach(presets.filter { $0.category == category }) { preset in
                                    Button {
                                        Haptics.impact(.light)
                                        selectedPreset = preset
                                        resetClock()
                                    } label: {
                                        VStack(alignment: .leading) {
                                            HStack {
                                                Text(preset.name)
                                                if preset.isOfficial {
                                                    Image(systemName: "star.fill")
                                                }
                                            }
                                            Text(LocalizedStringKey(preset.subtitle))
                                                .font(.caption)
                                        }
                                    }
                                }
                            } label: {
                                Text(LocalizedStringKey(category))
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "timer")
                            Text(selectedPreset.name)
                                .fontWeight(.semibold)
                        }
                        .font(.headline)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.accentColor.opacity(0.12), in: Capsule())
                    }

                    Button {
                        Haptics.impact(.light)
                        showInfoSheet = true
                    } label: {
                        Image(systemName: "info.circle")
                            .font(.title2)
                            .foregroundColor(.accentColor)
                    }
                }
            } else {
                // Play / Pause Button (when game has started)
                Button {
                    Haptics.impact(.medium)
                    togglePause()
                } label: {
                    Image(systemName: isPaused ? "play.fill" : "pause.fill")
                        .font(.title)
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(Color.accentColor, in: Circle())
                        .shadow(color: Color.accentColor.opacity(0.3), radius: 6, y: 3)
                }
            }

            Spacer()

            // Reset Button
            Button {
                Haptics.impact(.medium)
                resetClock()
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.title2.weight(.bold))
                    .padding(12)
                    .background(Color.primary.opacity(0.05), in: Circle())
            }
            .disabled(!hasStarted && p1Time == Double(selectedPreset.baseMinutes * 60))
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 14)
        .background(.ultraThinMaterial)
        .overlay(
            Rectangle()
                .fill(Color.primary.opacity(0.08))
                .frame(height: 1),
            alignment: .top
        )
        .overlay(
            Rectangle()
                .fill(Color.primary.opacity(0.08))
                .frame(height: 1),
            alignment: .bottom
        )
    }

    // MARK: - Logic Helpers

    private func handleTap(playerNumber: Int) {
        // If time is up, ignore taps
        guard p1Time > 0 && p2Time > 0 else { return }

        // Start game if not started yet
        if !hasStarted {
            hasStarted = true
            isPaused = false
            // Tapping one side starts the opponent's timer
            activePlayer = playerNumber == 1 ? 2 : 1
            SoundManager.shared.playMove()
            Haptics.impact(.medium)
            return
        }

        // Ignore taps if paused
        guard !isPaused else { return }

        // Change turns
        if playerNumber == 1 && activePlayer == 1 {
            p1Moves += 1
            p1Time += Double(selectedPreset.incrementSeconds)
            if selectedPreset.hasSecondPhase && p1Moves == selectedPreset.secondPhaseTriggerMove {
                p1Time += Double(selectedPreset.secondPhaseAddedMinutes * 60)
            }
            activePlayer = 2
            SoundManager.shared.playMove()
            Haptics.impact(.medium)
        } else if playerNumber == 2 && activePlayer == 2 {
            p2Moves += 1
            p2Time += Double(selectedPreset.incrementSeconds)
            if selectedPreset.hasSecondPhase && p2Moves == selectedPreset.secondPhaseTriggerMove {
                p2Time += Double(selectedPreset.secondPhaseAddedMinutes * 60)
            }
            activePlayer = 1
            SoundManager.shared.playMove()
            Haptics.impact(.medium)
        }
    }

    private func togglePause() {
        if isPaused {
            // Resume
            isPaused = false
            // If no active player (e.g. paused at start), default to White (Player 1)
            if activePlayer == nil {
                activePlayer = 1
            }
        } else {
            // Pause
            isPaused = true
        }
    }

    private func resetClock() {
        p1Time = Double(selectedPreset.baseMinutes * 60)
        p2Time = Double(selectedPreset.baseMinutes * 60)
        p1Moves = 0
        p2Moves = 0
        activePlayer = nil
        isPaused = false
        hasStarted = false
    }

    private func tick() {
        guard hasStarted && !isPaused else { return }

        if activePlayer == 1 {
            p1Time = max(0, p1Time - 0.1)
            if p1Time <= 0 {
                timeOut()
            }
        } else if activePlayer == 2 {
            p2Time = max(0, p2Time - 0.1)
            if p2Time <= 0 {
                timeOut()
            }
        }
    }

    private func timeOut() {
        activePlayer = nil
        Haptics.notification(.error)
    }

    private func formatTime(_ seconds: Double) -> String {
        if seconds <= 0 {
            return "00:00"
        }
        let totalMins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        
        if totalMins >= 60 {
            let hours = totalMins / 60
            let mins = totalMins % 60
            return String(format: "%d:%02d:%02d", hours, mins, secs)
        } else if seconds < 10 {
            // Show tenths of a second if under 10 seconds for dramatic precision
            let tenths = Int((seconds - Double(Int(seconds))) * 10)
            return String(format: "%02d:%02d.%d", totalMins, secs, tenths)
        } else {
            return String(format: "%02d:%02d", totalMins, secs)
        }
    }
}

struct ChessClockInfoView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    categorySection(
                        title: "Blic",
                        items: [
                            ("3 + 2", "3 minuta + 2 sekunde inkrementa. Zvanični format na Svetskim prvenstvima u blicu."),
                            ("5 + 0", "Čistih 5 minuta bez inkrementa. Stara škola blica."),
                            ("5 + 3", "5 minuta + 3 sekunde inkrementa. Koristi se na jačim online turnirima.")
                        ]
                    )

                    categorySection(
                        title: "Ubrzani šah",
                        items: [
                            ("15 + 10", "15 minuta + 10 sekundi inkrementa. Zvanični FIDE format za Svetska prvenstva."),
                            ("10 + 5", "10 minuta + 5 sekundi inkrementa. Popularan online format, ozbiljniji od blica."),
                            ("25 + 10", "25 minuta + 10 sekundi inkrementa. Čest format na lokalnim turnirima.")
                        ]
                    )

                    categorySection(
                        title: "Klasični šah",
                        items: [
                            ("90 + 30 + 30", "90 minuta za 40 poteza, potom +30 minuta, uz 30s inkrementa. Format za svetske šampionate."),
                            ("90 + 30", "90 minuta za celu partiju uz dodavanje od 30 sekundi po potezu (format domaćih liga).")
                        ]
                    )
                }
                .padding(20)
            }
            .navigationTitle(LocalizedStringKey("Objašnjenje vremenskih kontrola"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(LocalizedStringKey("Zatvori")) {
                        dismiss()
                    }
                }
            }
        }
    }

    private func categorySection(title: String, items: [(String, String)]) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(LocalizedStringKey(title))
                .font(.title3.weight(.bold))
                .padding(.bottom, 4)

            VStack(spacing: 12) {
                ForEach(items, id: \.0) { name, description in
                    HStack(alignment: .top, spacing: 12) {
                        Text(name)
                            .font(.headline.weight(.bold))
                            .frame(width: 100, alignment: .leading)
                            .foregroundColor(.primary)

                        Text(LocalizedStringKey(description))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.primary.opacity(0.03), in: RoundedRectangle(cornerRadius: 8))
                }
            }
        }
    }
}
