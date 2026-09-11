// swift-tools-version: 6.0
import PackageDescription

// Testni paket za sahovski motor. Kompajlira POSTOJECE izvorne fajlove po
// putanji — bez kopiranja — pa testovi uvek proveravaju pravi kod.
// Xcode projekat (Chessko.xcodeproj) je potpuno nezavisan od ovog paketa.
let package = Package(
    name: "ChesskoEngine",
    platforms: [.macOS(.v14)],
    targets: [
        .target(
            name: "ChesskoEngine",
            path: "Chessko",
            sources: [
                "Models/Position.swift",
                "Models/ChessPiece.swift",
                "Models/ChessMove.swift",
                "Models/GameState.swift",
                "Models/GameState+FEN.swift",
                "Models/ChessPuzzle.swift",
                "Models/LessonContent.swift",
                "Models/Curriculum.swift",
                "Models/MoveAnalysis.swift",
                "Logic/StatsManager.swift",
                "Logic/ProgressStore.swift",
                "Logic/MoveGenerator.swift",
                "Logic/ZobristTable.swift",
                "Logic/PuzzleRepository.swift",
                "Logic/UCIScoreParser.swift",
                "TestSupport/LocShim.swift",
            ],
            swiftSettings: [.define("CHESSKO_ENGINE_PACKAGE")]
        ),
        .testTarget(
            name: "ChesskoEngineTests",
            dependencies: ["ChesskoEngine"],
            path: "Tests/ChesskoEngineTests"
        ),
    ]
)
