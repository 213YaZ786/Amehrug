package com.amehrug.app.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What a note card shows about when it was written. */
enum class NoteTimestamp { OFF, DATE_TIME, NUMERIC, RELATIVE, HIJRI }

/** The unit an elapsed time is rounded to, so the words come from resources. */
enum class AgoUnit { MINUTES, HOURS, DAYS, MONTHS, YEARS }

/**
 * What to draw on the card. Absolute formats come back as text. Elapsed time
 * comes back as a number and a unit, never as a sentence, so the wording is a
 * plural resource and the French build reads as French.
 */
sealed interface Stamp {
    data object None : Stamp
    data object JustNow : Stamp
    data class Text(val value: String) : Stamp
    data class Ago(val unit: AgoUnit, val count: Int) : Stamp
}

/**
 * Pure Kotlin, so it runs under kotlinc. java.time only, which Android has
 * from API 26 and minSdk is 31.
 *
 * HIJRI uses the platform Hijrah calendar, Umm al-Qura, the civil calendar of
 * Saudi Arabia. It is arithmetic, so a printed date can sit a day away from a
 * local moon sighting. Good enough to date a note, not a substitute for a
 * mosque announcement.
 */
object NoteTimestamps {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun format(
        format: NoteTimestamp,
        at: Long,
        now: Long,
        zone: ZoneId,
        locale: Locale,
    ): Stamp {
        if (format == NoteTimestamp.OFF || at <= 0L) return Stamp.None
        val moment = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), zone)
        return when (format) {
            NoteTimestamp.OFF -> Stamp.None
            NoteTimestamp.DATE_TIME ->
                Stamp.Text(DateTimeFormatter.ofPattern("EEE MMM d HH:mm", locale).format(moment))
            NoteTimestamp.NUMERIC ->
                Stamp.Text(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale).format(moment))
            NoteTimestamp.HIJRI -> {
                val day = HijrahDate.from(moment.toLocalDate())
                val date = DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(day)
                val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(moment)
                Stamp.Text("$date $time")
            }
            NoteTimestamp.RELATIVE -> relative(at, now, zone)
        }
    }

    /**
     * The date as it is offered for editing, which is not the same thing as
     * the date as it is shown.
     *
     * Two of the display formats cannot be read back. Nobody can type their
     * way from "3 days ago" to a moment, and the Hijri calendar would need
     * its own parser. So the field switches to this plain form the moment it
     * is touched, and whatever the note shows on the wall, editing it always
     * means editing a number of a day, a month and a year.
     */
    fun editable(at: Long, zone: ZoneId, locale: Locale): String {
        val moment = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), zone)
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale).format(moment)
    }

    /**
     * Read a typed date back.
     *
     * Deliberately narrow: day, month and year separated by slashes, dots or
     * dashes, or the same in the year first order, with the time optional. A
     * typed date with no time keeps the time the note already had, so fixing
     * the day of a note does not move it to midnight.
     *
     * @return the moment, or null when the text is not a date yet. Half typed
     *   text arrives here on every keystroke, so null is the normal answer,
     *   not a failure.
     */
    fun parse(text: String, fallback: Long, zone: ZoneId): Long? {
        val cleaned = text.trim().replace('.', '/').replace('-', '/')
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(' ', '\t').filter { it.isNotBlank() }
        val date = parts.firstOrNull() ?: return null
        val fields = date.split('/')
        if (fields.size != 3) return null
        val numbers = fields.map { it.toIntOrNull() ?: return null }
        // Four digits first means the year came first.
        val yearFirst = fields[0].length == 4
        val year = if (yearFirst) numbers[0] else numbers[2]
        val month = numbers[1]
        val day = if (yearFirst) numbers[2] else numbers[0]
        if (year < 1 || month !in 1..12 || day < 1) return null

        val previous = LocalDateTime.ofInstant(Instant.ofEpochMilli(fallback), zone)
        var hour = previous.hour
        var minute = previous.minute
        val time = parts.getOrNull(1)
        if (time != null) {
            val clock = time.split(':')
            if (clock.size < 2) return null
            val typedHour = clock[0].toIntOrNull() ?: return null
            val typedMinute = clock[1].toIntOrNull() ?: return null
            if (typedHour !in 0..23 || typedMinute !in 0..59) return null
            hour = typedHour
            minute = typedMinute
        }
        return try {
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        } catch (e: java.time.DateTimeException) {
            // The 31st of a month with thirty days, and the like.
            null
        }
    }

    /**
     * A note written in the future, which a wrong clock or an imported file
     * can produce, reads as just now rather than as a negative count.
     */
    private fun relative(at: Long, now: Long, zone: ZoneId): Stamp {
        val elapsed = now - at
        if (elapsed < MINUTE) return Stamp.JustNow
        if (elapsed < HOUR) return Stamp.Ago(AgoUnit.MINUTES, (elapsed / MINUTE).toInt())
        if (elapsed < DAY) return Stamp.Ago(AgoUnit.HOURS, (elapsed / HOUR).toInt())
        val from = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val to = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(from, to)
        if (days < 31) return Stamp.Ago(AgoUnit.DAYS, days.toInt())
        val months = ChronoUnit.MONTHS.between(from, to)
        if (months < 12) return Stamp.Ago(AgoUnit.MONTHS, months.toInt().coerceAtLeast(1))
        return Stamp.Ago(AgoUnit.YEARS, ChronoUnit.YEARS.between(from, to).toInt().coerceAtLeast(1))
    }
}
