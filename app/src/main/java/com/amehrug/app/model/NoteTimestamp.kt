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
