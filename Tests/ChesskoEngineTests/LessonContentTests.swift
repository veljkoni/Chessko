import Testing
import Foundation
@testable import ChesskoEngine

@Test func decodesAllBlockTypesFromJSON() throws {
    let json = """
    {
      "id": "test-lesson",
      "language": "sr",
      "title": "Naslov",
      "subtitle": "Podnaslov",
      "icon": "book.fill",
      "blocks": [
        { "type": "heading", "text": "Sekcija", "icon": "star.fill" },
        { "type": "paragraph", "text": "Tekst sa **podebljanim**." },
        { "type": "bullets", "items": [
            { "icon": "checkmark", "title": "Prvo", "text": "Opis prvog" }
        ] },
        { "type": "box", "style": "rule", "icon": "lightbulb", "title": "Pravilo", "text": "Telo" },
        { "type": "quote", "text": "Citat", "author": "Kapablanka" },
        { "type": "pieceRow", "piece": "knight", "name": "Skakač", "count": "2 komada" },
        { "type": "numberedRule", "number": 1, "title": "Razvoj", "text": "Razvijaj figure" },
        { "type": "pieceValueTable", "rows": [
            { "piece": "pawn", "name": "Pion", "value": 1 }
        ] },
        { "type": "board", "fen": "8/8/8/8/8/8/8/R3K2R w KQ - 0 1", "caption": "Rokada", "interactive": false },
        { "type": "explorer" },
        { "type": "exercise", "kind": "scripted", "title": "Španska",
          "hint": "Odigraj e4", "icon": "flame.fill",
          "uciMoves": ["e2e4", "e7e5"], "startFEN": null,
          "solvedMessage": "Bravo!", "wrongMessage": "Pogrešno.",
          "playingPrompt": null, "mateIn": null },
        { "type": "exercise", "kind": "vsEngine", "title": "Kralj + Top",
          "hint": "Oteraj kralja", "icon": "rectangle.portrait.fill",
          "uciMoves": null, "startFEN": "8/8/4k3/8/4K3/8/8/R7 w - - 0 1",
          "solvedMessage": null, "wrongMessage": null,
          "playingPrompt": null, "mateIn": null }
      ]
    }
    """
    let doc = try JSONDecoder().decode(LessonDocument.self, from: Data(json.utf8))

    #expect(doc.id == "test-lesson")
    #expect(doc.language == "sr")
    #expect(doc.title == "Naslov")
    #expect(doc.blocks.count == 12)

    guard case .heading(let text, let icon) = doc.blocks[0] else {
        Issue.record("blok 0 nije heading"); return
    }
    #expect(text == "Sekcija")
    #expect(icon == "star.fill")

    guard case .bullets(let items) = doc.blocks[2] else {
        Issue.record("blok 2 nije bullets"); return
    }
    #expect(items.count == 1)
    #expect(items[0].title == "Prvo")

    guard case .box(let style, _, _, _) = doc.blocks[3] else {
        Issue.record("blok 3 nije box"); return
    }
    #expect(style == .rule)

    guard case .exercise(let scripted) = doc.blocks[10] else {
        Issue.record("blok 10 nije exercise"); return
    }
    #expect(scripted.kind == .scripted)
    #expect(scripted.uciMoves == ["e2e4", "e7e5"])

    guard case .exercise(let vsEngine) = doc.blocks[11] else {
        Issue.record("blok 11 nije exercise"); return
    }
    #expect(vsEngine.kind == .vsEngine)
    #expect(vsEngine.startFEN == "8/8/4k3/8/4K3/8/8/R7 w - - 0 1")
}

@Test func unknownBlockTypeFailsLoudlyInsteadOfBeingSkipped() {
    let json = """
    { "id": "x", "language": "sr", "title": "T", "subtitle": "S", "icon": "book.fill",
      "blocks": [ { "type": "izmisljeni-tip", "text": "nešto" } ] }
    """
    #expect(throws: (any Error).self) {
        try JSONDecoder().decode(LessonDocument.self, from: Data(json.utf8))
    }
}
