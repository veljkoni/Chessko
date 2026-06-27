import SwiftUI

// MARK: - Learn Scenario

enum LearnScenario: String, Equatable {
    case castling
    case enPassant
    case promotion

    var label: String {
        switch self {
        case .castling:  return Loc("Rokada")
        case .enPassant: return "En passant"
        case .promotion: return Loc("Promocija")
        }
    }
}

// MARK: - Learn View Model
//
// Manages an interactive "sandbox" board for the tutorial tab.
// The player can freely tap any square to relocate the selected piece
// and see its reachable squares highlighted.

@Observable
@MainActor
final class LearnViewModel {

    // MARK: - State

    var selectedPieceType: PieceType = .queen
    var piecePosition: Position = Position(row: 4, col: 3)   // d4 — good center default
    var activeScenario: LearnScenario? = nil

    // Derived board + moves (refreshed on every state change)
    private(set) var board: [[ChessPiece?]] = emptyBoard()
    private(set) var legalMoves: [ChessMove] = []

    // BoardView compatibility (animation stubs — tutorial doesn't animate)
    var animatingPiece: AnimatingPiece? = nil
    var flyingCapture:  FlyingCapture?  = nil
    var lastMove:       ChessMove?      = nil

    // BoardView reads this to draw the gray "selected" highlight
    var selectedPosition: Position? { piecePosition }
    var isPlayerTurn: Bool { true }

    // MARK: - Init

    init() { refresh() }

    // MARK: - Piece Selection

    func select(piece: PieceType) {
        selectedPieceType = piece
        activeScenario    = nil
        piecePosition     = defaultPosition(for: piece)
        refresh()
    }

    // MARK: - Scenario

    /// Available extra-scenarios for the currently selected piece.
    var availableScenarios: [LearnScenario] {
        switch selectedPieceType {
        case .king:  return [.castling]
        case .pawn:  return [.enPassant, .promotion]
        default:     return []
        }
    }

    func toggleScenario(_ scenario: LearnScenario) {
        if activeScenario == scenario {
            // Deactivate → go back to normal piece view
            activeScenario = nil
            piecePosition  = defaultPosition(for: selectedPieceType)
        } else {
            activeScenario = scenario
            piecePosition  = scenarioStartPosition(scenario)
        }
        refresh()
    }

    // MARK: - Tap Handler

    /// User tapped a square on the board.
    /// • Tapping the piece's own square → no-op
    /// • Tapping anything else → relocate piece (free placement)
    func tap(position: Position) {
        guard position != piecePosition else { return }
        // Exit special scenario when user freely moves the piece
        if activeScenario != nil { activeScenario = nil }
        piecePosition = position
        refresh()
    }

    // MARK: - Info text

    var infoTitle: String {
        switch activeScenario {
        case .none:        return selectedPieceType.srbName
        case .castling:    return Loc("Rokada")
        case .enPassant:   return "En passant"
        case .promotion:   return Loc("Promocija")
        }
    }

    var infoText: String {
        switch activeScenario {
        case .none:
            switch selectedPieceType {
            case .pawn:
                return Loc("Kreće se jedno polje napred. Uzima figuru dijagonalno ispred sebe. Sa startne pozicije može da skoči i dva polja odjednom.")
            case .rook:
                return Loc("Kreće se horizontalno ili vertikalno, koliko god polja želi. Ne može preskakati figure.")
            case .knight:
                return Loc("Kreće se u obliku slova L: dva polja u jednom pravcu pa jedno bočno. Jedina figura koja može preskočiti druge.")
            case .bishop:
                return Loc("Kreće se dijagonalno, koliko god polja želi. Uvek ostaje na istoj boji polja.")
            case .queen:
                return Loc("Najjača figura na tabli. Kombinuje kretanje topa i lovca — horizontalno, vertikalno i dijagonalno.")
            case .king:
                return Loc("Kreće se jedno polje u bilo kom smeru. Ne sme stati na polje koje napada protivnik. Zaštiti ga!")
            }
        case .castling:
            return Loc("Poseban potez: ako kralj i top nisu se još pomerali i između njih nema figura, kralj skoči dva polja ka topu, a top preskoči kralja. Tapni g1 (kratka rokada) ili c1 (duga rokada).")
        case .enPassant:
            return Loc("Posebno uzimanje pešakom: ako protivnički pešak skoči dva polja i nađe se pored tvojeg pešaka, možeš ga uzeti 'u prolazu' — kao da se pomerio samo jedno polje. Tapni d6.")
        case .promotion:
            return Loc("Kad beli pešak stigne do osmog reda (redovi 8), može se pretvoriti u bilo koju figuru — gotovo uvek u damu. Tapni e8.")
        }
    }

    var movesCountLabel: String {
        let n = legalMoves.count
        switch n {
        case 0:  return Loc("Nema mogućih poteza")
        case 1:  return Loc("1 mogući potez")
        default: return LocF("%lld mogućih poteza", n)
        }
    }

    // MARK: - Private

    private func refresh() {
        let (b, state) = buildBoardAndState()
        board      = b
        legalMoves = MoveGenerator.legalMoves(for: .white, in: state)
            .filter { $0.from == piecePosition }
    }

    private func buildBoardAndState() -> ([[ChessPiece?]], GameState) {
        var b = Self.emptyBoard()
        var castleKS = false
        var castleQS = false
        var epTarget: Position? = nil

        switch activeScenario {

        case .none:
            // Place the tutorial piece
            b[piecePosition.row][piecePosition.col] = ChessPiece(type: selectedPieceType, color: .white)
            // For pawns, add dummy enemy pieces on the capture diagonals so captures are visible
            if selectedPieceType == .pawn {
                let capRow = piecePosition.row - 1   // white pawn moves up (row decreases)
                if capRow >= 0 {
                    if piecePosition.col > 0 {
                        b[capRow][piecePosition.col - 1] = ChessPiece(type: .pawn, color: .black)
                    }
                    if piecePosition.col < 7 {
                        b[capRow][piecePosition.col + 1] = ChessPiece(type: .pawn, color: .black)
                    }
                }
            }

        case .castling:
            // King on e1 + both rooks — both castling rights active
            b[7][4] = ChessPiece(type: .king, color: .white)   // e1
            b[7][0] = ChessPiece(type: .rook, color: .white)   // a1
            b[7][7] = ChessPiece(type: .rook, color: .white)   // h1
            castleKS = true
            castleQS = true

        case .enPassant:
            // White pawn on e5; black pawn on d5 that "just moved" two squares
            b[3][4] = ChessPiece(type: .pawn, color: .white)   // e5
            b[3][3] = ChessPiece(type: .pawn, color: .black)   // d5
            epTarget = Position(row: 2, col: 3)                 // d6 — en passant target square

        case .promotion:
            // White pawn one step from queening
            b[1][4] = ChessPiece(type: .pawn, color: .white)   // e7
        }

        let state = GameState(
            board: b,
            currentTurn: .white,
            whiteCanCastleKingside:  castleKS,
            whiteCanCastleQueenside: castleQS,
            blackCanCastleKingside:  false,
            blackCanCastleQueenside: false,
            enPassantTarget: epTarget,
            moveHistory: [],
            capturedByWhite: [],
            capturedByBlack: [],
            status: .playing
        )
        return (b, state)
    }

    // MARK: - Helpers

    private func defaultPosition(for piece: PieceType) -> Position {
        switch piece {
        case .pawn:  return Position(row: 6, col: 4)   // e2 — shows double-move + captures
        case .king:  return Position(row: 4, col: 4)   // e4
        default:     return Position(row: 4, col: 3)   // d4 — nice centre for max mobility
        }
    }

    private func scenarioStartPosition(_ scenario: LearnScenario) -> Position {
        switch scenario {
        case .castling:  return Position(row: 7, col: 4)   // e1
        case .enPassant: return Position(row: 3, col: 4)   // e5
        case .promotion: return Position(row: 1, col: 4)   // e7
        }
    }

    private static func emptyBoard() -> [[ChessPiece?]] {
        Array(repeating: Array(repeating: nil, count: 8), count: 8)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        LessonDetailView(lesson: LessonInfo.all[0], pieceExplorer: LearnViewModel())
    }
}
