package com.amehrug.app.model

// Pure Kotlin, no Android import, so it runs under kotlinc and is tested
// before delivery. The PDF writer is the one part that cannot live here,
// since it needs the platform text layout.

/**
 * Two formats, both plain text.
 *
 * PDF and HTML were here and are gone. A note taking app that writes a
 * paginated document is an office suite with extra steps, and neither format
 * was worth the code that laid it out. Text and markdown open everywhere,
 * read as themselves, and carry the styles as far as they can.
 *
 * Neither carries the pictures attached to a note. They stay in the app.
 */
enum class ExportFormat(val extension: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    MARKDOWN("md", "text/markdown"),
}

/**
 * Notes, written out for something other than this app to read.
 *
 * A run of text and the styles it carries is the unit both formats are built
 * from, so the two agree on where a style starts and stops. Runs are cut at
 * every line break, because markdown markers do not survive one.
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
}
