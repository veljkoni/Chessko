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

    /// Naziv za prikaz: neprelomni razmak (U+00A0) oko „+" — „90 + 30 + 30" se ne
    /// lomi u dva reda (spec § 14). U podacima ostaje običan razmak (jednakost).
    var displayName: String { TimeControlPreset.nonBreaking(name) }

    static func nonBreaking(_ value: String) -> String {
        value.replacingOccurrences(of: " + ", with: "\u{00A0}+\u{00A0}")
    }
}

/// Fiksno tamna traka između polovina sata (spec § 14), u OBE teme — iste vrednosti
/// kao Android `ClockBarBackground` / `ClockBarWell` / `ClockBarAccent` i `DarkColors`
/// članovi (`ChesskoAndroid/.../ui/ChessClockView.kt`). Traka dodiruje crnu polovinu
/// odozgo i belu odozdo, pa prati STRANU sata, ne temu: sistemski materijal je u
/// svetloj temi bio `#F4F5F5`, skoro ista boja kao bela polovina ispod.
/// Pravilo „fiksna površina dobija fiksne članove": ni jedan `DS.*` token na traci —
/// `DS.ink` svetle teme na `#1E293B` bi dao 1,19 : 1. Mereno (Android
/// `ContrastTest.clockControlBarIsReadableOverFixedHalves`): ink na traci 12,93,
/// accent 5,63, onAccent na accent 6,71, ink na krugu 8,87, inkMuted na krugu 3,26.
private enum ClockBar {
    static let background = Color(hex: "#1E293B")
    static let well       = Color.white.opacity(0.12)
    static let accent     = Color(hex: "#7EA0E8") // DarkColors.accent
    static let ink        = Color(hex: "#EEF1F7") // DarkColors.ink
    static let inkMuted   = Color(hex: "#8B93A7") // DarkColors.inkMuted
    static let onAccent   = Color(hex: "#161A22") // DarkColors.onAccent
}

struct ChessClockView: View {
    @Environment(\.dismiss) private var dismiss

    /// Kategorije su ključevi prevoda (prikaz kroz `Loc`).
    static let categories = ["Blic", "Ubrzani šah", "Klasični šah"]

    // Available presets
    static let presets: [TimeControlPreset] = [
        TimeControlPreset(name: "3 + 2", category: "Blic", baseMinutes: 3, incrementSeconds: 2, isOfficial: true, subtitle: "Zvanični svetski blic"),
        TimeControlPreset(name: "5 + 0", category: "Blic", baseMinutes: 5, incrementSeconds: 0, subtitle: "Stara škola blica"),
        TimeControlPreset(name: "5 + 3", category: "Blic", baseMinutes: 5, incrementSeconds: 3, subtitle: "Turnirski blic"),

        TimeControlPreset(name: "15 + 10", category: "Ubrzani šah", baseMinutes: 15, incrementSeconds: 10, isOfficial: true, subtitle: "Zvanični svetski rapid"),
        TimeControlPreset(name: "10 + 5", category: "Ubrzani šah", baseMinutes: 10, incrementSeconds: 5, subtitle: "Popularni online rapid"),
        TimeControlPreset(name: "25 + 10", category: "Ubrzani šah", baseMinutes: 25, incrementSeconds: 10, subtitle: "Lokalni turniri"),

        TimeControlPreset(name: "90 + 30 + 30", category: "Klasični šah", baseMinutes: 90, incrementSeconds: 30, hasSecondPhase: true, secondPhaseTriggerMove: 40, secondPhaseAddedMinutes: 30, isOfficial: true, subtitle: "Turniri kandidata / FIDE"),
        TimeControlPreset(name: "90 + 30", category: "Klasični šah", baseMinutes: 90, incrementSeconds: 30, subtitle: "Domaća liga")
    ]

    @State private var selectedPreset: TimeControlPreset = ChessClockView.presets[0]

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
    @State private var showPresetSheet = false

    private let timer = Timer.publish(every: 0.1, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            // Background
            DS.ground
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
        .sheet(isPresented: $showPresetSheet) {
            ChessClockPresetSheet(
                presets: ChessClockView.presets,
                selected: selectedPreset
            ) { preset in
                Haptics.impact(.light)
                selectedPreset = preset
                resetClock()
            }
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
                // Istek vremena na crnoj polovini. NAMERNO fiksna, ne token — ista
                // priča kao neaktivna polovina ispod: boja prati STRANU sata, ne temu.
                // U tamnoj temi je DS.danger svetlo-lososi (#F2857A) pa beli tekst
                // pada na ~2.5:1 kontrast — nečitljivo.
                bgColor = Color(hex: "#8C2525")
                textColor = .white
                subtextColor = .white.opacity(0.7)
            } else if isActive {
                bgColor = Color(hex: "#121212")
                textColor = .white
                subtextColor = .white.opacity(0.7)
            } else {
                // Neaktivna crna polovina. NAMERNO fiksna, ne token — DS.fill prati
                // sistemsku temu, a ova boja mora da prati STRANU sata. Sa tokenom
                // bi u svetloj temi postala skoro bela ispod belog teksta.
                bgColor = Color(hex: "#2C2C2E")
                textColor = .white.opacity(0.5)
                subtextColor = .white.opacity(0.3)
            }
        } else {
            // Player 1 (White) - bottom half
            if isTimeOut {
                // Istek vremena na beloj polovini. NAMERNO fiksna, ne token — na
                // 18% providnosti DS.danger komponuje se sa pozadinom iza njega,
                // pa bi u tamnoj temi BELA polovina sata postala tamna (tačno greška
                // koju je revert neaktivnih polovina trebalo da spreči).
                bgColor = Color(hex: "#FADAD8")
                textColor = Color(uiColor: .systemRed)
                subtextColor = Color(uiColor: .systemRed).opacity(0.7)
            } else if isActive {
                bgColor = .white
                textColor = .black
                subtextColor = .black.opacity(0.6)
            } else {
                // Neaktivna bela polovina. Fiksna iz istog razloga kao crna:
                // sa tokenom bi u tamnoj temi postala tamnoplava ispod crnog teksta.
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
                    Text(Loc("Vreme je isteklo!"))
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(textColor)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 6)
                        .background(textColor.opacity(0.1), in: Capsule())
                } else {
                    if !hasStarted {
                        Text(Loc(selectedPreset.subtitle))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(subtextColor)
                    } else {
                        Text(LocF("Poteza: %d", moves))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(subtextColor)
                    }
                }

                // Spec § 14: SF Pro sa `.monospacedDigit()` (tabularne cifre, bez
                // precrtane nule SF Mono-a) — Android isto: Roboto + `tnum`.
                // Boje polovina se ne diraju.
                Text(formatTime(timeLeft))
                    .font(.system(size: 86, weight: .bold).monospacedDigit())
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .foregroundStyle(textColor)
            }
            .rotationEffect(.degrees(isRotated ? 180 : 0))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Control Bar

    private var controlBar: some View {
        let canReset = hasStarted || p1Time != Double(selectedPreset.baseMinutes * 60)
        return HStack(spacing: 24) {
            // Close Button
            Button {
                Haptics.impact(.light)
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(ClockBar.ink)
                    .padding(12)
                    .background(ClockBar.well, in: Circle())
            }
            .accessibilityLabel(Loc("Zatvori"))

            Spacer()

            // Time Selection (only if game hasn't started)
            if !hasStarted {
                HStack(spacing: 8) {
                    Button {
                        Haptics.impact(.light)
                        showPresetSheet = true
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "timer")
                                .accessibilityHidden(true)
                            Text(selectedPreset.displayName)
                                .fontWeight(.semibold)
                                .monospacedDigit()
                        }
                        .font(.headline)
                        .foregroundStyle(ClockBar.accent)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        // 12 %: akcent trake na sopstvenom tintu 4,58 (Android isto).
                        .background(ClockBar.accent.opacity(0.12), in: Capsule())
                    }
                    .accessibilityLabel(Loc("Vremenska kontrola"))
                    .accessibilityValue(selectedPreset.name)

                    Button {
                        Haptics.impact(.light)
                        showInfoSheet = true
                    } label: {
                        Image(systemName: "info.circle")
                            .font(.title2)
                            .foregroundStyle(ClockBar.ink)
                    }
                    .accessibilityLabel(Loc("Objašnjenje pravila"))
                }
            } else {
                // Play / Pause Button (when game has started)
                Button {
                    Haptics.impact(.medium)
                    togglePause()
                } label: {
                    Image(systemName: isPaused ? "play.fill" : "pause.fill")
                        .font(.title)
                        // Fiksni par trake: tamni `onAccent` na svetlom akcentu (6,71).
                        .foregroundStyle(ClockBar.onAccent)
                        .frame(width: 56, height: 56)
                        .background(ClockBar.accent, in: Circle())
                }
                .accessibilityLabel(Loc(isPaused ? "Pusti sat" : "Pauziraj sat"))
            }

            Spacer()

            // Reset Button — onemogućeno stanje kroz boju glifa (inkMuted, 3,26),
            // krug ostaje ceo; bez sistemskog zatamnjenja (isto kao Android).
            Button {
                Haptics.impact(.medium)
                resetClock()
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(canReset ? ClockBar.ink : ClockBar.inkMuted)
                    .padding(12)
                    .background(ClockBar.well, in: Circle())
            }
            .buttonStyle(DSPressableButtonStyle())
            .disabled(!canReset)
            .accessibilityLabel(Loc("Resetuj sat"))
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 14)
        .background(ClockBar.background)
        .environment(\.colorScheme, .dark)
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
                VStack(alignment: .leading, spacing: DS.Space.xl) {
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
                .padding(DS.Space.l)
            }
            .background(DS.ground) // spec § 3: nijedna sistemska podloga
            .navigationTitle(Loc("Objašnjenje vremenskih kontrola"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    // Spec § 14 / § 3: „Zatvori" kao tekst-dugme u `accent`.
                    Button(Loc("Zatvori")) {
                        dismiss()
                    }
                    .foregroundStyle(DS.accent)
                }
            }
        }
    }

    /// Spec § 14: naslov kategorije `Type.heading` bold `ink`; redovi u `surface`
    /// grupi sa `line` razdelnikom; vrednost (`Type.heading`, neprelomni razmak
    /// oko „+") iznad opisa (`Type.body` `ink`).
    private func categorySection(title: String, items: [(String, String)]) -> some View {
        VStack(alignment: .leading, spacing: DS.Space.s) {
            DSSectionHeader(Loc(title))

            VStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element.0) { index, item in
                    if index > 0 {
                        Rectangle()
                            .fill(DS.line)
                            .frame(height: 1)
                    }
                    VStack(alignment: .leading, spacing: DS.Space.xs) {
                        Text(TimeControlPreset.nonBreaking(item.0))
                            .font(.dsHeading)
                            .monospacedDigit()
                            .foregroundStyle(DS.ink)

                        Text(Loc(item.1))
                            .font(.dsBody)
                            .foregroundStyle(DS.ink) // spec § 3: opis je dugi tekst
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(DS.Space.m)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityElement(children: .combine)
                }
            }
            .dsCard()
        }
    }
}

/// Izbor vremenske kontrole (spec § 14) — umesto sistemskog ugnežđenog `Menu`-ja,
/// isti sadržaj kao Android `PresetChooserDialog`: segmenti kategorija (do 2 reda,
/// `Type.label`), ceo red je dugme, izabrano = kvačica `accent` + ivica 2 pt `accent`,
/// ★ u `inkMuted` sakrivena od čitača ekrana, „Zatvori" tekst-dugme `accent`.
struct ChessClockPresetSheet: View {
    let presets: [TimeControlPreset]
    let selected: TimeControlPreset
    let onSelect: (TimeControlPreset) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var category: String

    init(presets: [TimeControlPreset], selected: TimeControlPreset, onSelect: @escaping (TimeControlPreset) -> Void) {
        self.presets = presets
        self.selected = selected
        self.onSelect = onSelect
        _category = State(initialValue: selected.category)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DS.Space.l) {
                    segments

                    VStack(spacing: DS.Space.s) {
                        ForEach(presets.filter { $0.category == category }) { preset in
                            row(preset)
                        }
                    }
                }
                .padding(DS.Space.l)
            }
            .background(DS.ground)
            .navigationTitle(Loc("Vremenska kontrola"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(Loc("Zatvori")) {
                        dismiss()
                    }
                    .foregroundStyle(DS.accent)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    /// Segmenti: jednake širine, do 2 reda, bez lomljenja usred reči (reč se
    /// prelama samo na razmaku; najduža reč u 8 jezika staje u trećinu).
    private var segments: some View {
        HStack(spacing: DS.Space.xs) {
            ForEach(ChessClockView.categories, id: \.self) { item in
                let isSelected = item == category
                Button {
                    category = item
                } label: {
                    Text(Loc(item))
                        .font(.dsLabel.weight(.bold))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                        .foregroundStyle(isSelected ? DS.onAccent : DS.ink)
                        .padding(.vertical, DS.Space.s)
                        .padding(.horizontal, DS.Space.xs)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(isSelected ? DS.accent : DS.fill,
                                    in: RoundedRectangle(cornerRadius: DS.Radius.s, style: .continuous))
                        .contentShape(Rectangle())
                }
                .buttonStyle(DSPressableButtonStyle())
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private func row(_ preset: TimeControlPreset) -> some View {
        let isSelected = preset == selected
        let shape = RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous)
        return Button {
            onSelect(preset)
            dismiss()
        } label: {
            HStack(spacing: DS.Space.m) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(preset.displayName)
                            .font(.dsBody.weight(.bold))
                            .monospacedDigit()
                            .foregroundStyle(DS.ink)
                        if preset.isOfficial {
                            // Zvanični FIDE format — podnaslov to već kaže rečima,
                            // pa je ★ za čitač ekrana suvišna (spec § 14).
                            Image(systemName: "star.fill")
                                .font(.caption2)
                                .foregroundStyle(DS.inkMuted)
                                .accessibilityHidden(true)
                        }
                    }
                    Text(Loc(preset.subtitle))
                        .font(.dsCaption)
                        .foregroundStyle(DS.inkMuted)
                }
                Spacer(minLength: 0)
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.dsBody.weight(.semibold))
                        .foregroundStyle(DS.accent)
                        .accessibilityHidden(true)
                }
            }
            .padding(DS.Space.m)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(DS.surface, in: shape)
            .overlay(shape.strokeBorder(isSelected ? DS.accent : DS.line, lineWidth: isSelected ? 2 : 1))
            .contentShape(shape)
        }
        .buttonStyle(DSPressableButtonStyle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
