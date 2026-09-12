package com.veljkoni.chessko.models

import org.json.JSONArray
import org.json.JSONObject

// MARK: - Sadrzaj lekcija
//
// Sema + parser. NAMERNO bez ijednog `android.*` uvoza — ovaj fajl je cist
// Kotlin/JVM kod. `org.json` (koji koristi) JESTE deo Android SDK-a, ali ga
// `testDebugUnitTest` (obican JVM jedinicni test) obezbedjuje kroz STUB koji
// baca RuntimeException("... not mocked ...") na svaki poziv (dokazano pri
// implementaciji ovog taska). `isReturnDefaultValues = true` to ne resava.
// Zato testovi za ovaj fajl zive u `androidTest` (LessonContentTest.kt) —
// rade na emulatoru/uredjaju gde postoji PRAVI `org.json`. Odsustvo
// `android.*` uvoza ovde i dalje ima vrednost: sam parser se moze pozivati i
// iz koda koji inace nema Android zavisnosti (npr. buduci desktop/CLI alat).

enum class BoxStyle { RULE, WARNING, INFO }

enum class ExerciseKind { SCRIPTED, VS_ENGINE }

data class BulletItem(
    val icon: String,
    val title: String,
    val text: String,
    /// `null` = koristi akcent lekcije. Postoji da bi pojedinacna stavka mogla
    /// da nosi upozorenje bez posebnog tipa bloka.
    val style: BoxStyle? = null
)

data class PieceValueRow(
    val piece: String,
    val name: String,
    /// STRING, ne Int: kralj nosi „∞". Da je broj, ceo blok bi pukao na kralju
    /// i lekcija bi nestala.
    val value: String,
    val valueLabel: String? = null
)

data class ExerciseSpec(
    val kind: ExerciseKind,
    val title: String,
    val hint: String,
    val icon: String,
    val uciMoves: List<String>? = null,
    val startFEN: String? = null,
    val solvedMessage: String? = null,
    val wrongMessage: String? = null,
    val playingPrompt: String? = null,
    val mateIn: Int? = null
)

sealed class LessonBlock {
    data class Heading(val text: String, val icon: String) : LessonBlock()
    data class Paragraph(val text: String) : LessonBlock()
    data class Bullets(val items: List<BulletItem>) : LessonBlock()
    data class Box(val style: BoxStyle, val icon: String, val title: String, val text: String) : LessonBlock()
    data class Quote(val text: String, val author: String) : LessonBlock()
    data class PieceRow(val piece: String, val name: String, val count: String) : LessonBlock()
    data class NumberedRule(val number: Int, val title: String, val text: String) : LessonBlock()
    data class PieceValueTable(val rows: List<PieceValueRow>) : LessonBlock()
    data class Board(val fen: String, val caption: String, val interactive: Boolean) : LessonBlock()
    data object Explorer : LessonBlock()
    data class Exercise(val spec: ExerciseSpec) : LessonBlock()
    data object Divider : LessonBlock()
}

data class LessonDocument(
    val id: String,
    val language: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val blocks: List<LessonBlock>
)

object LessonParser {

    fun parse(json: String): LessonDocument {
        val root = JSONObject(json)
        val arr = root.getJSONArray("blocks")
        val blocks = ArrayList<LessonBlock>(arr.length())
        for (i in 0 until arr.length()) blocks.add(block(arr.getJSONObject(i)))
        return LessonDocument(
            id = root.getString("id"),
            language = root.getString("language"),
            title = root.getString("title"),
            subtitle = root.getString("subtitle"),
            icon = root.getString("icon"),
            blocks = blocks
        )
    }

    private fun block(o: JSONObject): LessonBlock = when (val t = o.getString("type")) {
        "heading" -> LessonBlock.Heading(o.getString("text"), o.getString("icon"))
        "paragraph" -> LessonBlock.Paragraph(o.getString("text"))
        "bullets" -> LessonBlock.Bullets(o.getJSONArray("items").map {
            BulletItem(it.getString("icon"), it.getString("title"), it.getString("text"),
                       it.optStyle("style"))
        })
        "box" -> LessonBlock.Box(style(o.getString("style")), o.getString("icon"),
                                 o.getString("title"), o.getString("text"))
        "quote" -> LessonBlock.Quote(o.getString("text"), o.getString("author"))
        "pieceRow" -> LessonBlock.PieceRow(o.getString("piece"), o.getString("name"),
                                           o.getString("count"))
        "numberedRule" -> LessonBlock.NumberedRule(o.getInt("number"), o.getString("title"),
                                                   o.getString("text"))
        "pieceValueTable" -> LessonBlock.PieceValueTable(o.getJSONArray("rows").map {
            PieceValueRow(it.getString("piece"), it.getString("name"), it.getString("value"),
                          it.optStringOrNull("valueLabel"))
        })
        "board" -> LessonBlock.Board(o.getString("fen"), o.getString("caption"),
                                     o.optBoolean("interactive", false))
        "explorer" -> LessonBlock.Explorer
        "exercise" -> LessonBlock.Exercise(exercise(o))
        "divider" -> LessonBlock.Divider
        // Tih preskok bi dao lekciju sa rupom koju niko ne primeti.
        else -> throw IllegalArgumentException("Nepoznat tip bloka u lekciji: \"$t\"")
    }

    private fun exercise(o: JSONObject) = ExerciseSpec(
        kind = when (val k = o.getString("kind")) {
            "scripted" -> ExerciseKind.SCRIPTED
            "vsEngine" -> ExerciseKind.VS_ENGINE
            else -> throw IllegalArgumentException("Nepoznata vrsta vezbe: \"$k\"")
        },
        title = o.getString("title"),
        hint = o.getString("hint"),
        icon = o.getString("icon"),
        uciMoves = o.optJSONArray("uciMoves")?.let { a -> (0 until a.length()).map { a.getString(it) } },
        startFEN = o.optStringOrNull("startFEN"),
        solvedMessage = o.optStringOrNull("solvedMessage"),
        wrongMessage = o.optStringOrNull("wrongMessage"),
        playingPrompt = o.optStringOrNull("playingPrompt"),
        mateIn = if (o.isNull("mateIn")) null else o.optInt("mateIn").takeIf { o.has("mateIn") }
    )

    private fun style(raw: String) = when (raw) {
        "rule" -> BoxStyle.RULE
        "warning" -> BoxStyle.WARNING
        "info" -> BoxStyle.INFO
        else -> throw IllegalArgumentException("Nepoznat stil kutije: \"$raw\"")
    }

    /// `optString` vraca PRAZAN STRING kad kljuca nema, ne `null` — zbog toga
    /// bi opciona polja postala `""` i renderer bi crtao prazne redove umesto
    /// da ih preskoci.
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optStyle(key: String): BoxStyle? =
        optStringOrNull(key)?.let { style(it) }

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
