import SwiftUI

// MARK: - Lesson Info

struct LessonInfo: Identifiable {
    let id: Int
    let title: String
    let subtitle: String
    let systemIcon: String
    let accentColor: Color

    static let all: [LessonInfo] = [
        LessonInfo(id: 1,
                   title: "Tabla, figure i kretanje",
                   subtitle: "Osnove šaha za početnike",
                   systemIcon: "square.grid.3x3.fill",
                   accentColor: .blue),
        LessonInfo(id: 2,
                   title: "Početak igre (Otvaranja)",
                   subtitle: "Zlatna pravila i poznata otvaranja",
                   systemIcon: "flag.fill",
                   accentColor: .green),
        LessonInfo(id: 3,
                   title: "Središnjica",
                   subtitle: "Taktika i srce bitke",
                   systemIcon: "bolt.fill",
                   accentColor: .orange),
        LessonInfo(id: 4,
                   title: "Završnica",
                   subtitle: "Šah-mat, pat i remi",
                   systemIcon: "flag.checkered",
                   accentColor: .red),
    ]
}

// MARK: - Lesson Card

struct LessonCard: View {
    let info: LessonInfo

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(info.accentColor.opacity(0.18))
                    .frame(width: 54, height: 54)
                Image(systemName: info.systemIcon)
                    .font(.title3.weight(.medium))
                    .foregroundStyle(info.accentColor)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Lekcija \(info.id)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(info.accentColor.opacity(0.9))
                Text(info.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.leading)
                Text(info.subtitle)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.55))
            }

            Spacer(minLength: 0)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.3))
        }
        .padding(16)
        .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(info.accentColor.opacity(0.3), lineWidth: 1)
        )
    }
}

// MARK: - Learn View

struct LearnView: View {

    var viewModel: LearnViewModel   // passed from ContentView — pre-initialized

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.09, green: 0.14, blue: 0.31)
                    .ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {

                        // Header
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Nauči šah")
                                .font(.title2.weight(.bold))
                                .foregroundStyle(.white)
                            Text("4 lekcije od osnova do završnice")
                                .font(.subheadline)
                                .foregroundStyle(.white.opacity(0.55))
                        }
                        .padding(.top, 4)

                        // Lesson cards
                        ForEach(LessonInfo.all) { lesson in
                            NavigationLink {
                                LessonDetailView(lesson: lesson, pieceExplorer: viewModel)
                            } label: {
                                LessonCard(info: lesson)
                            }
                            .buttonStyle(.plain)
                        }

                        Spacer(minLength: 16)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
            }
            .navigationTitle("Učenje")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color(red: 0.09, green: 0.14, blue: 0.31), for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}

// MARK: - Preview

#Preview {
    LearnView(viewModel: LearnViewModel())
}
