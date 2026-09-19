package com.amehrug.app.model

// Pure Kotlin, no Android import, so it runs under kotlinc and is tested
// before delivery. The PDF writer is the one part that cannot live here,
// since it needs the platform text layout.

enum class ExportFormat(val extension: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    MARKDOWN("md", "text/markdown"),
    HTML("html", "text/html"),
    PDF("pdf", "application/pdf"),
}

/**
 * Notes, written out for something other than this app to read.
 *
 * A run of text and the styles it carries is the unit all three formats are
 * built from, so the three agree on where a style starts and stops. Runs are
 * cut at every line break: markdown markers do not survive a newline, and
 * neither does readable HTML.
 */
object NoteExport {

    /** Text and its style mask, in order, newlines split off on their own. */
    fun runs(body: String, spans: List<TextSpan>): List<Pair<String, Int>> {
        if (body.isEmpty()) return emptyList()
        val masks = TextSpans.explode(spans, body.length)
        val out = ArrayList<Pair<String, Int>>()
        var i = 0
        while (i < body.length) {
            if (body[i] == '\n') {
                out.add("\n" to 0)
                i++
                continue
            }
            val mask = masks[i]
            var j = i
            while (j < body.length && body[j] != '\n' && masks[j] == mask) j++
            out.add(body.substring(i, j) to mask)
            i = j
        }
        return out
    }

    private fun has(mask: Int, style: NoteStyle) = mask and style.bit != 0

    // Plain text keeps no styling. Markers would be markdown, and someone
    // asking for a .txt asked for the words.
    fun text(notes: List<Note>): String {
        val out = StringBuilder()
        for ((index, note) in notes.withIndex()) {
            if (index > 0) out.append("\n").append("-".repeat(40)).append("\n\n")
            if (note.title.isNotBlank()) out.append(note.title).append("\n\n")
            when (note.type) {
                NoteType.NOTE -> if (note.body.isNotBlank()) out.append(note.body).append("\n")
                NoteType.LIST -> for (item in note.items) {
                    out.append("  ".repeat(item.indent))
                    out.append(if (item.checked) "[x] " else "[ ] ")
                    out.append(item.body).append("\n")
                }
            }
            if (note.labels.isNotEmpty()) {
                out.append("\n").append(note.labels.joinToString(", ")).append("\n")
            }
        }
        return out.toString()
    }

    /**
     * Markdown. A run that is monospace comes out as a code span and nothing
     * else, because a code span carries no markup inside it. Text is written
     * as typed rather than escaped: a note is prose, and backslashes in front
     * of every asterisk would be worse than the rare stray emphasis.
     */
    fun markdown(notes: List<Note>): String {
        val out = StringBuilder()
        for ((index, note) in notes.withIndex()) {
            if (index > 0) out.append("\n---\n\n")
            if (note.title.isNotBlank()) out.append("# ").append(note.title).append("\n\n")
            when (note.type) {
                NoteType.NOTE -> if (note.body.isNotBlank()) {
                    for ((run, mask) in runs(note.body, note.spans)) {
                        out.append(markdownRun(run, mask))
                    }
                    out.append("\n")
                }
                NoteType.LIST -> for (item in note.items) {
                    out.append("  ".repeat(item.indent))
                    out.append(if (item.checked) "- [x] " else "- [ ] ")
                    out.append(item.body).append("\n")
                }
            }
            if (note.labels.isNotEmpty()) {
                out.append("\n")
                out.append(note.labels.joinToString(" ") { "#" + it.replace(' ', '-') })
                out.append("\n")
            }
        }
        return out.toString()
    }

    private fun markdownRun(run: String, mask: Int): String {
        if (run == "\n" || mask == 0) return run
        if (has(mask, NoteStyle.MONOSPACE)) return "`$run`"
        var body = run
        if (has(mask, NoteStyle.STRIKETHROUGH)) body = "~~$body~~"
        if (has(mask, NoteStyle.ITALIC)) body = "*$body*"
        if (has(mask, NoteStyle.BOLD)) body = "**$body**"
        return body
    }

    fun html(notes: List<Note>, title: String = "Amehrug"): String {
        val out = StringBuilder()
        out.append("<!DOCTYPE html>\n<html>\n<head>\n")
        out.append("<meta charset=\"utf-8\">\n")
        out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        out.append("<title>").append(escape(title)).append("</title>\n")
        out.append("<style>\n").append(CSS).append("</style>\n")
        out.append("</head>\n<body>\n")
        for (note in notes) {
            out.append("<article>\n")
            if (note.title.isNotBlank()) {
                out.append("<h1>").append(escape(note.title)).append("</h1>\n")
            }
            when (note.type) {
                NoteType.NOTE -> if (note.body.isNotBlank()) {
                    out.append("<p>")
                    for ((run, mask) in runs(note.body, note.spans)) {
                        out.append(htmlRun(run, mask))
                    }
                    out.append("</p>\n")
                }
                NoteType.LIST -> {
                    out.append("<ul class=\"checklist\">\n")
                    for (item in note.items) {
                        out.append("<li class=\"indent-")
                        out.append(if (item.indent > 4) 4 else item.indent)
                        out.append("\">")
                        out.append(if (item.checked) "&#9745; " else "&#9744; ")
                        out.append(escape(item.body))
                        out.append("</li>\n")
                    }
                    out.append("</ul>\n")
                }
            }
            if (note.labels.isNotEmpty()) {
                out.append("<p class=\"labels\">")
                out.append(note.labels.joinToString(" ") { escape(it) })
                out.append("</p>\n")
            }
            out.append("</article>\n")
        }
        out.append("</body>\n</html>\n")
        return out.toString()
    }

    private fun htmlRun(run: String, mask: Int): String {
        if (run == "\n") return "<br>\n"
        var body = escape(run)
        if (has(mask, NoteStyle.MONOSPACE)) body = "<code>$body</code>"
        if (has(mask, NoteStyle.STRIKETHROUGH)) body = "<s>$body</s>"
        if (has(mask, NoteStyle.ITALIC)) body = "<em>$body</em>"
        if (has(mask, NoteStyle.BOLD)) body = "<strong>$body</strong>"
        if (has(mask, NoteStyle.LINK)) body = "<u>$body</u>"
        return body
    }

    fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (c in value) {
            when (c) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /**
     * The file a picker is opened with. One note carries its own title, a
     * selection carries the date, and anything that cannot go in a file name
     * on any of the usual file systems is dropped.
     */
    fun fileName(notes: List<Note>, format: ExportFormat, stamp: String): String {
        val single = notes.singleOrNull()
        val base = if (single != null && single.title.isNotBlank()) {
            val cleaned = single.title.map { c ->
                if (c.isLetterOrDigit() || c == ' ' || c == '-' || c == '_') c else '-'
            }.joinToString("").trim().replace(Regex(" +"), " ")
            if (cleaned.isEmpty()) "amehrug-$stamp" else cleaned.take(60)
        } else {
            "amehrug-$stamp"
        }
        return "$base.${format.extension}"
    }

    // Kept small on purpose. The point is a file that opens anywhere and
    // reads well, not a copy of the app's own look.
    private val CSS = """
        body { font-family: system-ui, sans-serif; line-height: 1.55;
               max-width: 44rem; margin: 2rem auto; padding: 0 1rem; }
        article + article { border-top: 1px solid #8884; margin-top: 2rem;
               padding-top: 2rem; }
        h1 { font-size: 1.4rem; }
        code { font-family: ui-monospace, monospace; }
        ul.checklist { list-style: none; padding-left: 0; }
        .indent-1 { padding-left: 1.5rem; }
        .indent-2 { padding-left: 3rem; }
        .indent-3 { padding-left: 4.5rem; }
        .indent-4 { padding-left: 6rem; }
        .labels { opacity: 0.6; font-size: 0.85rem; }
    """.trimIndent()
}
