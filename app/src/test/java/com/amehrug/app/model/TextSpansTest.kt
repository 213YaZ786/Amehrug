package com.amehrug.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The span algebra, which is what decides where a style starts and stops
 * after every keystroke. Bold is written as an uppercase letter in these
 * tests, so a run is readable at a glance instead of being a list of index
 * pairs.
 */
class TextSpansTest {

    private fun bolded(spans: List<TextSpan>, text: String): String {
        val masks = TextSpans.explode(spans, text.length)
        return text.mapIndexed { index, c ->
            if (masks[index] and NoteStyle.BOLD.bit != 0) c.uppercaseChar() else c
        }.joinToString("")
    }

    @Test
    fun `normalize clips a span that reaches past the text`() {
        val messy = listOf(TextSpan(0, 99, bold = true))
        assertEquals(6, TextSpans.normalize(messy, 6).last().end)
    }

    @Test
    fun `normalize drops a run that carries no style`() {
        assertEquals(0, TextSpans.normalize(listOf(TextSpan(0, 4)), 4).size)
    }

    @Test
    fun `toggle turns a style on and off over the same range`() {
        var spans = TextSpans.toggle(emptyList(), 11, 0, 5, NoteStyle.BOLD)
        assertEquals("HELLO world", bolded(spans, "hello world"))
        spans = TextSpans.toggle(spans, 11, 0, 5, NoteStyle.BOLD)
        assertEquals("hello world", bolded(spans, "hello world"))
    }

    @Test
    fun `toggle turns on when only part of the range carries the style`() {
        var spans = TextSpans.toggle(emptyList(), 11, 0, 5, NoteStyle.BOLD)
        spans = TextSpans.toggle(spans, 11, 3, 8, NoteStyle.BOLD)
        assertEquals("HELLO WOrld", bolded(spans, "hello world"))
    }

    @Test
    fun `toggle over an empty range changes nothing`() {
        val spans = TextSpans.toggle(emptyList(), 11, 0, 5, NoteStyle.BOLD)
        assertEquals(spans, TextSpans.toggle(spans, 11, 4, 4, NoteStyle.BOLD))
    }

    @Test
    fun `covers at a cursor answers for the character on the left`() {
        val spans = TextSpans.toggle(emptyList(), 11, 0, 5, NoteStyle.BOLD)
        assertTrue(TextSpans.covers(spans, 11, 3, 3, NoteStyle.BOLD))
        assertFalse(TextSpans.covers(spans, 11, 0, 0, NoteStyle.BOLD))
    }

    @Test
    fun `two styles on the same characters stay one run`() {
        var spans = TextSpans.toggle(emptyList(), 4, 0, 4, NoteStyle.BOLD)
        spans = TextSpans.toggle(spans, 4, 0, 4, NoteStyle.ITALIC)
        assertEquals(1, spans.size)
        assertTrue(spans[0].bold && spans[0].italic)
    }

    @Test
    fun `typing inside a styled word stays styled`() {
        var spans = TextSpans.toggle(emptyList(), 5, 0, 5, NoteStyle.BOLD)
        spans = TextSpans.afterEdit(spans, "hello", "heXllo")
        assertEquals("HEXLLO", bolded(spans, "heXllo"))
    }

    @Test
    fun `typing after a styled run continues it`() {
        var spans = TextSpans.toggle(emptyList(), 11, 0, 5, NoteStyle.BOLD)
        spans = TextSpans.afterEdit(spans, "hello world", "hello! world")
        assertEquals("HELLO! world", bolded(spans, "hello! world"))
    }

    @Test
    fun `typing before a styled run does not join it`() {
        var spans = TextSpans.toggle(emptyList(), 11, 6, 11, NoteStyle.BOLD)
        spans = TextSpans.afterEdit(spans, "hello world", "hello xworld")
        assertEquals("hello xWORLD", bolded(spans, "hello xworld"))
    }

    /**
     * Inserting the same letter that already starts a run cannot be told
     * apart from inserting after it, from the two strings alone. The common
     * prefix takes the later reading, so the letter lands inside the run.
     */
    @Test
    fun `an ambiguous insert joins the run`() {
        var spans = TextSpans.toggle(emptyList(), 11, 6, 11, NoteStyle.BOLD)
        spans = TextSpans.afterEdit(spans, "hello world", "hello wworld")
        assertEquals("hello WWORLD", bolded(spans, "hello wworld"))
    }

    @Test
    fun `deleting inside a run shortens it`() {
        var spans = TextSpans.toggle(emptyList(), 11, 0, 5, NoteStyle.BOLD)
        spans = TextSpans.afterEdit(spans, "hello world", "helo world")
        assertEquals("HELO world", bolded(spans, "helo world"))
    }

    @Test
    fun `deleting across a boundary keeps what is left`() {
        var spans = TextSpans.toggle(emptyList(), 11, 0, 5, NoteStyle.BOLD)
        spans = TextSpans.afterEdit(spans, "hello world", "helorld")
        assertEquals("HELorld", bolded(spans, "helorld"))
    }

    @Test
    fun `replacing a selection takes the style on its left`() {
        var spans = TextSpans.toggle(emptyList(), 11, 0, 5, NoteStyle.BOLD)
        spans = TextSpans.afterEdit(spans, "hello world", "hello there")
        assertEquals("HELLO there", bolded(spans, "hello there"))
    }

    @Test
    fun `a pending style applies to what is typed next`() {
        val spans = TextSpans.afterEdit(emptyList(), "ab", "abc", pending = NoteStyle.BOLD.bit)
        assertEquals("abC", bolded(spans, "abc"))
    }

    @Test
    fun `deleting everything leaves no run`() {
        val spans = TextSpans.toggle(emptyList(), 3, 0, 3, NoteStyle.BOLD)
        assertEquals(0, TextSpans.afterEdit(spans, "abc", "").size)
    }

    @Test
    fun `typing into an empty note carries no style`() {
        assertEquals(0, TextSpans.afterEdit(emptyList(), "", "hi").size)
    }

    @Test
    fun `appending after a run that ends at the end of the text continues it`() {
        var spans = TextSpans.toggle(emptyList(), 5, 2, 5, NoteStyle.BOLD)
        spans = TextSpans.afterEdit(spans, "abcde", "abcdef")
        assertEquals("abCDEF", bolded(spans, "abcdef"))
    }
}
