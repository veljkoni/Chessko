import SwiftUI

// MARK: - Lesson Card

/// Kartica u listi lekcija. Od Faze 3 ne prima vise `LessonInfo` (zakucanu
/// strukturu iz Swift-a) nego polja dokumenta ucitanog iz JSON-a. `accentColor`
/// se namerno ne cita iz sadrzaja — sve cetiri lekcije su `DS.accent` od Faze 1.
struct LessonCard: View {
    let number: Int
    let title: String
    let subtitle: String
    let icon: String

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(DS.accent.opacity(0.18))
                    .frame(width: 54, height: 54)
                Image(systemName: icon)
                    .font(.appFont(.title3).weight(.medium))
                    .foregroundStyle(DS.accent)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(LocF("Lekcija %lld", number))
                    .font(.appFont(.caption).weight(.semibold))
                    .foregroundStyle(DS.accent.opacity(0.9))
                Text(title)
                    .font(.appFont(.subheadline).weight(.semibold))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                Text(subtitle)
                    .font(.appFont(.caption))
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 0)

            Image(systemName: "chevron.right")
                .font(.appFont(.caption).weight(.semibold))
                .foregroundStyle(.secondary.opacity(0.8))
        }
        .padding(16)
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(DS.accent.opacity(0.2), lineWidth: 1)
        )
    }
}

// MARK: - Learn View

struct LearnView: View {

    var viewModel: LearnViewModel   // passed from ContentView — pre-initialized

    var body: some View {
        NavigationStack {
            ZStack {
                AppBackgroundView()

                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {

                        // Naslov ekrana zivi u sadrzaju, a nav bar je sakriven.
                        // Nativni veliki naslov ovde ne radi: ScrollView je umotan
                        // u ZStack sa pozadinom, pa ga nav bar ne prepoznaje kao svoj
                        // skrol i naslov se ne iscrta — ostane samo prazna traka.
                        VStack(alignment: .leading, spacing: 4) {
                            Text(Loc("Nauči šah"))
                                .font(.dsTitle)
                                .foregroundStyle(DS.ink)
                            Text(Loc("4 lekcije od osnova do završnice"))
                                .font(.dsBody)
                                .foregroundStyle(DS.inkMuted)
                        }
                        .padding(.top, 4)

                        // Lista se gradi iz repozitorijuma; redosled i redni broj
                        // dolaze iz `LessonRepository.lessonOrder`.
                        let lessons = LessonRepository.shared.allLessons(
                            language: LocalizationManager.shared.effectiveCode)

                        ForEach(Array(lessons.enumerated()), id: \.element.id) { index, document in
                            NavigationLink {
                                LessonDetailView(lessonId: document.id)
                            } label: {
                                LessonCard(number: index + 1,
                                           title: document.title,
                                           subtitle: document.subtitle,
                                           icon: document.icon)
                            }
                            .buttonStyle(.plain)
                        }

                        Spacer(minLength: 16)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .safeAreaPadding(.bottom, 24)
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        // Model istrazivaca figura ostaje podignut u `ContentView`-u (da se ne
        // pravi tokom animacije prelaska taba). Ide kroz okruzenje jer ga trazi
        // `LessonPieceExplorer` duboko u rendereru, a ni `LessonDetailView` ni
        // `LessonRenderer` nemaju razloga da znaju za njega.
        .environment(viewModel)
    }
}

// MARK: - Preview

#Preview {
    LearnView(viewModel: LearnViewModel())
}
