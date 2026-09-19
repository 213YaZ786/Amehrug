package com.amehrug.app.model

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading a typed date back. Half typed text arrives here on every
 * keystroke, so answering "not a date" is the normal case and most of these
 * tests are about answering it rather than guessing.
 */
class NoteTimestampsTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val locale = Locale.UK

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val base = at(2026, 9, 19, 16, 45)

    @Test
    fun `the editable form is the plain one`() {
        assertEquals("19/09/2026 16:45", NoteTimestamps.editable(base, zone, locale))
    }

    @Test
    fun `the editable form reads back as the same moment`() {
        assertEquals(base, NoteTimestamps.parse("19/09/2026 16:45", 0L, zone))
    }

    /** Fixing the day of a note must not send it to midnight. */
    @Test
    fun `a date with no time keeps the time the note had`() {
        assertEquals(at(2026, 9, 20, 16, 45), NoteTimestamps.parse("20/09/2026", base, zone))
    }

    @Test
    fun `dots and dashes separate as well as slashes`() {
        assertEquals(at(2026, 9, 20, 16, 45), NoteTimestamps.parse("20.09.2026", base, zone))
        assertEquals(at(2026, 9, 20, 16, 45), NoteTimestamps.parse("20-09-2026", base, zone))
    }

    @Test
    fun `the year may come first`() {
        assertEquals(at(2026, 9, 20, 16, 45), NoteTimestamps.parse("2026-09-20", base, zone))
    }

    @Test
    fun `single digits are accepted`() {
        assertEquals(at(2026, 2, 1, 16, 45), NoteTimestamps.parse("1/2/2026", base, zone))
    }

    @Test
    fun `text that is not a date yet moves nothing`() {
        assertNull(NoteTimestamps.parse("", base, zone))
        assertNull(NoteTimestamps.parse("19/", base, zone))
        assertNull(NoteTimestamps.parse("19/09", base, zone))
    }

    /** The two display formats that cannot be read back. */
    @Test
    fun `elapsed time and a hijri date are not parsed`() {
        assertNull(NoteTimestamps.parse("3 days ago", base, zone))
        assertNull(NoteTimestamps.parse("7 Rab. I 1448 16:45", base, zone))
    }

    @Test
    fun `a day or a month out of range moves nothing`() {
        assertNull(NoteTimestamps.parse("19/13/2026", base, zone))
        assertNull(NoteTimestamps.parse("32/01/2026", base, zone))
        assertNull(NoteTimestamps.parse("31/09/2026", base, zone))
    }

    @Test
    fun `the 29th of february depends on the year`() {
        assertEquals(at(2024, 2, 29, 16, 45), NoteTimestamps.parse("29/02/2024", base, zone))
        assertNull(NoteTimestamps.parse("29/02/2026", base, zone))
    }

    @Test
    fun `a time out of range moves nothing`() {
        assertNull(NoteTimestamps.parse("19/09/2026 24:00", base, zone))
        assertNull(NoteTimestamps.parse("19/09/2026 12:60", base, zone))
        assertNull(NoteTimestamps.parse("19/09/2026 12", base, zone))
    }

    @Test
    fun `a note with no date shows nothing`() {
        val stamp = NoteTimestamps.format(NoteTimestamp.NUMERIC, 0L, base, zone, locale)
        assertEquals(Stamp.None, stamp)
    }

    @Test
    fun `the date format is off means nothing is shown`() {
        val stamp = NoteTimestamps.format(NoteTimestamp.OFF, base, base, zone, locale)
        assertEquals(Stamp.None, stamp)
    }

    /** A wrong clock or an imported file can date a note in the future. */
    @Test
    fun `a note from the future reads as just now`() {
        val later = at(2026, 9, 19, 18, 0)
        assertEquals(Stamp.JustNow, NoteTimestamps.format(NoteTimestamp.RELATIVE, later, base, zone, locale))
    }

    @Test
    fun `elapsed time is counted in the right unit`() {
        val hours = NoteTimestamps.format(NoteTimestamp.RELATIVE, at(2026, 9, 19, 13, 45), base, zone, locale)
        assertEquals(Stamp.Ago(AgoUnit.HOURS, 3), hours)
        val days = NoteTimestamps.format(NoteTimestamp.RELATIVE, at(2026, 9, 15, 16, 45), base, zone, locale)
        assertEquals(Stamp.Ago(AgoUnit.DAYS, 4), days)
    }
}
