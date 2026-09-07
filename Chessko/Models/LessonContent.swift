import Foundation

// MARK: - Sadržaj lekcije
//
// Lekcije od Faze 3 žive u `Chessko/Content/lessons/<id>.<lang>.json`, ne u
// Swift kodu. Nova lekcija = nov JSON. Ovaj fajl je JEDINO mesto koje zna
// kako sadržaj izgleda; `LessonRenderer` ga crta, `LessonRepository` učitava.
//
// Namerno NEMA `default` grane pri dekodiranju tipa bloka: nepoznat tip mora
// da baci grešku. Tiho preskakanje bi dalo lekciju sa rupom koju niko ne vidi.

struct LessonDocument: Codable, Equatable {
    let id: String
    let language: String
    let title: String
    let subtitle: String
    let icon: String
    let blocks: [LessonBlock]
}

enum BoxStyle: String, Codable, Equatable {
    case rule      // žuta „zlatno pravilo" kutija
    case warning   // crvena kutija (greške, šah-mat)
    case info      // kutija u akcentu lekcije
}

struct BulletItem: Codable, Equatable {
    let icon: String
    let title: String
    let text: String
}

struct PieceValueRow: Codable, Equatable {
    let piece: String   // "pawn" | "knight" | "bishop" | "rook" | "queen" | "king"
    let name: String
    let value: Int
}

enum ExerciseKind: String, Codable, Equatable {
    /// Protivnik igra po unapred zapisanoj UCI liniji (otvaranja, „mat u N").
    case scripted
    /// Protivnik je motor na lakoj težini (elementarni matovi).
    case vsEngine
}

struct ExerciseSpec: Codable, Equatable {
    let kind: ExerciseKind
    let title: String
    let hint: String
    let icon: String
    /// Samo za `.scripted`. Svi potezi, naizmenično beli/crni.
    let uciMoves: [String]?
    /// `nil` kod `.scripted` znači standardnu početnu poziciju.
    /// Kod `.vsEngine` je obavezan.
    let startFEN: String?
    let solvedMessage: String?
    let wrongMessage: String?
    let playingPrompt: String?
    /// Ako je zadat, kartica dobija bedž „Mat u N".
    let mateIn: Int?
}

enum LessonBlock: Codable, Equatable {
    case heading(text: String, icon: String)
    case paragraph(text: String)
    case bullets(items: [BulletItem])
    case box(style: BoxStyle, icon: String, title: String, text: String)
    case quote(text: String, author: String)
    case pieceRow(piece: String, name: String, count: String)
    case numberedRule(number: Int, title: String, text: String)
    case pieceValueTable(rows: [PieceValueRow])
    case board(fen: String, caption: String, interactive: Bool)
    case explorer
    case exercise(ExerciseSpec)

    private enum CodingKeys: String, CodingKey {
        case type, text, icon, items, style, title, author, piece, name, count
        case number, rows, fen, caption, interactive
        case kind, hint, uciMoves, startFEN, solvedMessage, wrongMessage
        case playingPrompt, mateIn
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let type = try c.decode(String.self, forKey: .type)

        switch type {
        case "heading":
            self = .heading(text: try c.decode(String.self, forKey: .text),
                            icon: try c.decode(String.self, forKey: .icon))
        case "paragraph":
            self = .paragraph(text: try c.decode(String.self, forKey: .text))
        case "bullets":
            self = .bullets(items: try c.decode([BulletItem].self, forKey: .items))
        case "box":
            self = .box(style: try c.decode(BoxStyle.self, forKey: .style),
                        icon: try c.decode(String.self, forKey: .icon),
                        title: try c.decode(String.self, forKey: .title),
                        text: try c.decode(String.self, forKey: .text))
        case "quote":
            self = .quote(text: try c.decode(String.self, forKey: .text),
                          author: try c.decode(String.self, forKey: .author))
        case "pieceRow":
            self = .pieceRow(piece: try c.decode(String.self, forKey: .piece),
                             name: try c.decode(String.self, forKey: .name),
                             count: try c.decode(String.self, forKey: .count))
        case "numberedRule":
            self = .numberedRule(number: try c.decode(Int.self, forKey: .number),
                                 title: try c.decode(String.self, forKey: .title),
                                 text: try c.decode(String.self, forKey: .text))
        case "pieceValueTable":
            self = .pieceValueTable(rows: try c.decode([PieceValueRow].self, forKey: .rows))
        case "board":
            self = .board(fen: try c.decode(String.self, forKey: .fen),
                          caption: try c.decode(String.self, forKey: .caption),
                          interactive: try c.decode(Bool.self, forKey: .interactive))
        case "explorer":
            self = .explorer
        case "exercise":
            self = .exercise(ExerciseSpec(
                kind:          try c.decode(ExerciseKind.self, forKey: .kind),
                title:         try c.decode(String.self, forKey: .title),
                hint:          try c.decode(String.self, forKey: .hint),
                icon:          try c.decode(String.self, forKey: .icon),
                uciMoves:      try c.decodeIfPresent([String].self, forKey: .uciMoves),
                startFEN:      try c.decodeIfPresent(String.self, forKey: .startFEN),
                solvedMessage: try c.decodeIfPresent(String.self, forKey: .solvedMessage),
                wrongMessage:  try c.decodeIfPresent(String.self, forKey: .wrongMessage),
                playingPrompt: try c.decodeIfPresent(String.self, forKey: .playingPrompt),
                mateIn:        try c.decodeIfPresent(Int.self, forKey: .mateIn)))
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type, in: c,
                debugDescription: "Nepoznat tip bloka '\(type)'. Dodaj ga u LessonBlock ili ispravi JSON.")
        }
    }

    func encode(to encoder: any Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .heading(let text, let icon):
            try c.encode("heading", forKey: .type)
            try c.encode(text, forKey: .text); try c.encode(icon, forKey: .icon)
        case .paragraph(let text):
            try c.encode("paragraph", forKey: .type); try c.encode(text, forKey: .text)
        case .bullets(let items):
            try c.encode("bullets", forKey: .type); try c.encode(items, forKey: .items)
        case .box(let style, let icon, let title, let text):
            try c.encode("box", forKey: .type); try c.encode(style, forKey: .style)
            try c.encode(icon, forKey: .icon); try c.encode(title, forKey: .title)
            try c.encode(text, forKey: .text)
        case .quote(let text, let author):
            try c.encode("quote", forKey: .type); try c.encode(text, forKey: .text)
            try c.encode(author, forKey: .author)
        case .pieceRow(let piece, let name, let count):
            try c.encode("pieceRow", forKey: .type); try c.encode(piece, forKey: .piece)
            try c.encode(name, forKey: .name); try c.encode(count, forKey: .count)
        case .numberedRule(let number, let title, let text):
            try c.encode("numberedRule", forKey: .type); try c.encode(number, forKey: .number)
            try c.encode(title, forKey: .title); try c.encode(text, forKey: .text)
        case .pieceValueTable(let rows):
            try c.encode("pieceValueTable", forKey: .type); try c.encode(rows, forKey: .rows)
        case .board(let fen, let caption, let interactive):
            try c.encode("board", forKey: .type); try c.encode(fen, forKey: .fen)
            try c.encode(caption, forKey: .caption); try c.encode(interactive, forKey: .interactive)
        case .explorer:
            try c.encode("explorer", forKey: .type)
        case .exercise(let spec):
            try c.encode("exercise", forKey: .type)
            try c.encode(spec.kind, forKey: .kind); try c.encode(spec.title, forKey: .title)
            try c.encode(spec.hint, forKey: .hint); try c.encode(spec.icon, forKey: .icon)
            try c.encodeIfPresent(spec.uciMoves, forKey: .uciMoves)
            try c.encodeIfPresent(spec.startFEN, forKey: .startFEN)
            try c.encodeIfPresent(spec.solvedMessage, forKey: .solvedMessage)
            try c.encodeIfPresent(spec.wrongMessage, forKey: .wrongMessage)
            try c.encodeIfPresent(spec.playingPrompt, forKey: .playingPrompt)
            try c.encodeIfPresent(spec.mateIn, forKey: .mateIn)
        }
    }
}
