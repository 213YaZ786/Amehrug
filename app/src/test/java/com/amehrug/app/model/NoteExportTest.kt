package com.amehrug.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a note looks like once it has left the app. */
class NoteExportTest {

    private val body = "hello world"

    private val styled: Note = run {
        var spans = TextSpans.toggle(emptyList(), body.length, 0, 5, NoteStyle.BOLD)
        spans = TextSpans.toggle(spans, body.length, 6, 11, NoteStyle.ITALIC)
        Note(id = 1, title = "A note", body = body, spans = spans, labels = listOf("work"))
    }

    private val list = Note(
        type = NoteType.LIST,
        title = "Shopping",
        items = listOf(ListItem("milk", true), ListItem("bread", false, 1)),
    )

    @Test
    fun `a body splits into one run per style`() {
        assertEquals(3, NoteExport.runs(body, styled.spans).size)
    }

    @Test
    fun `a span reaching past the body cannot break the runs`() {
        assertEquals(1, NoteExport.runs("abc", listOf(TextSpan(0, 500, bold = true))).size)
    }

    @Test
    fun `markdown carries the styles, the title and the labels`() {
        val out = NoteExport.markdown(listOf(styled))
        assertTrue(out.contains("**hello** *world*"))
        assertTrue(out.contains("# A note"))
        assertTrue(out.contains("#work"))
    }

    @Test
    fun `plain text carries no markers`() {
        val out = NoteExport.text(listOf(styled))
        assertTrue(out.contains("hello world"))
        assertFalse(out.contains("**"))
    }

    /** A marker opened on one line and closed on the next is not markdown. */
    @Test
    fun `markers stop at a line break`() {
        val two = "one\ntwo"
        val spans = TextSpans.toggle(emptyList(), two.length, 0, two.length, NoteStyle.BOLD)
        val out = NoteExport.markdown(listOf(Note(body = two, spans = spans)))
        assertTrue(out.contains("**one**\n**two**"))
    }

    /** A code span carries no markup inside it, so monospace wins alone. */
    @Test
    fun `a monospace run comes out as a code span and nothing else`() {
        var spans = TextSpans.toggle(emptyList(), 4, 0, 4, NoteStyle.BOLD)
        spans = TextSpans.toggle(spans, 4, 0, 4, NoteStyle.MONOSPACE)
        val out = NoteExport.markdown(listOf(Note(body = "code", spans = spans)))
        assertTrue(out.contains("`code`"))
        assertFalse(out.contains("**"))
    }

    @Test
    fun `a checklist keeps its boxes and its indents`() {
        assertTrue(NoteExport.text(listOf(list)).contains("[x] milk"))
        assertTrue(NoteExport.markdown(listOf(list)).contains("  - [ ] bread"))
    }

    @Test
    fun `several notes are separated`() {
        assertTrue(NoteExport.markdown(listOf(styled, list)).contains("\n---\n"))
        assertTrue(NoteExport.text(listOf(styled, list)).contains("-".repeat(40)))
    }

    @Test
    fun `an empty selection writes an empty file`() {
        assertEquals("", NoteExport.text(emptyList()))
    }

    @Test
    fun `one note is named after its title`() {
        assertEquals(
            "A note.md",
            NoteExport.fileName(listOf(styled), ExportFormat.MARKDOWN, "20260919"),
        )
    }

    @Test
    fun `several notes are named after the day`() {
        assertEquals(
            "amehrug-20260919.md",
            NoteExport.fileName(listOf(styled, list), ExportFormat.MARKDOWN, "20260919"),
        )
    }

    @Test
    fun `a note with no title is named after the day`() {
        assertEquals(
            "amehrug-20260919.txt",
            NoteExport.fileName(listOf(Note(body = "x")), ExportFormat.TXT, "20260919"),
        )
    }

    @Test
    fun `a title that no file system would take is cleaned`() {
        assertEquals(
            "a-b-c.txt",
            NoteExport.fileName(listOf(Note(title = "a/b:c")), ExportFormat.TXT, "20260919"),
        )
    }
}
