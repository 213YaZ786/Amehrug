package com.amehrug.app.export

import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteType
import com.amehrug.app.model.TextSpans
import java.io.OutputStream

/**
 * Notes as a PDF.
 *
 * This is the one exporter that cannot be pure Kotlin: laying out wrapped
 * text needs the platform, and reimplementing line breaking to avoid it
 * would be worse in every way. The styles come across, since a StaticLayout
 * reads the same spans the editor writes.
 *
 * A4 at 72 points to the inch, which is what PdfDocument counts in.
 */
object PdfExport {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48
    private const val TEXT_SIZE = 12f

    fun write(notes: List<Note>, out: OutputStream) {
        val document = PdfDocument()
        try {
            var number = 1
            if (notes.isEmpty()) {
                // A file with no page at all is not a valid PDF, and some
                // readers refuse to open it.
                number = writeText(document, SpannableStringBuilder(), number)
            } else {
                // Every note starts its own page. Running them together
                // would save paper and lose the boundary.
                for (note in notes) {
                    number = writeText(document, spanned(note), number)
                }
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun writeText(
        document: PdfDocument,
        text: SpannableStringBuilder,
        firstNumber: Int,
    ): Int {
        val paint = TextPaint().apply {
            textSize = TEXT_SIZE
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }
        val width = PAGE_WIDTH - 2 * MARGIN
        val height = PAGE_HEIGHT - 2 * MARGIN
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .build()

        var number = firstNumber
        var line = 0
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            // The first line of a page is always taken, even when it is
            // taller than the page itself. Without that this loop could
            // never move forward.
            var last = line
            while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= height) {
                last++
            }
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
            val page = document.startPage(info)
            val canvas = page.canvas
            canvas.save()
            canvas.clipRect(MARGIN, MARGIN, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - MARGIN)
            canvas.translate(MARGIN.toFloat(), (MARGIN - top).toFloat())
            layout.draw(canvas)
            canvas.restore()
            document.finishPage(page)
            number++
            line = last + 1
        }
        return number
    }

    private fun spanned(note: Note): SpannableStringBuilder {
        val out = SpannableStringBuilder()
        if (note.title.isNotBlank()) {
            val start = out.length
            out.append(note.title)
            mark(out, StyleSpan(Typeface.BOLD), start, out.length)
            mark(out, RelativeSizeSpan(1.4f), start, out.length)
            out.append("\n\n")
        }
        when (note.type) {
            NoteType.NOTE -> {
                val offset = out.length
                out.append(note.body)
                for (run in TextSpans.normalize(note.spans, note.body.length)) {
                    val from = offset + run.start
                    val to = offset + run.end
                    // Bold and italic are one span with two faces, not two
                    // spans: the second would replace the first.
                    val face = when {
                        run.bold && run.italic -> Typeface.BOLD_ITALIC
                        run.bold -> Typeface.BOLD
                        run.italic -> Typeface.ITALIC
                        else -> null
                    }
                    if (face != null) mark(out, StyleSpan(face), from, to)
                    if (run.monospace) mark(out, TypefaceSpan("monospace"), from, to)
                    if (run.strikethrough) mark(out, StrikethroughSpan(), from, to)
                    if (run.link) mark(out, UnderlineSpan(), from, to)
                }
            }
            NoteType.LIST -> for (item in note.items) {
                out.append("    ".repeat(item.indent))
                out.append(if (item.checked) "\u2611 " else "\u2610 ")
                out.append(item.body).append("\n")
            }
        }
        if (note.labels.isNotEmpty()) {
            out.append("\n\n").append(note.labels.joinToString("   "))
        }
        return out
    }

    private fun mark(target: SpannableStringBuilder, span: Any, from: Int, to: Int) {
        if (to <= from) return
        target.setSpan(span, from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
