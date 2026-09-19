package com.amehrug.app.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.amehrug.app.R
import com.amehrug.app.model.AgoUnit
import com.amehrug.app.model.NoteTimestamp
import com.amehrug.app.model.NoteTimestamps
import com.amehrug.app.model.Stamp
import java.time.ZoneId

/**
 * The creation date as a line of text, or null when there is nothing to
 * show. Shared by the card and the editor so a note reads the same in both.
 *
 * Elapsed time arrives as a number and a unit rather than a sentence, and is
 * turned into words here, through plural resources.
 */
@Composable
fun stampText(at: Long, format: NoteTimestamp): String? {
    // Read through the context rather than LocalConfiguration, which has
    // been moving around in recent Compose releases.
    val locale = LocalContext.current.resources.configuration.locales[0]
    return when (
        val stamp = NoteTimestamps.format(
            format = format,
            at = at,
            now = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
            locale = locale,
        )
    ) {
        Stamp.None -> null
        Stamp.JustNow -> stringResource(R.string.stamp_just_now)
        is Stamp.Text -> stamp.value
        is Stamp.Ago -> pluralStringResource(
            when (stamp.unit) {
                AgoUnit.MINUTES -> R.plurals.stamp_minutes
                AgoUnit.HOURS -> R.plurals.stamp_hours
                AgoUnit.DAYS -> R.plurals.stamp_days
                AgoUnit.MONTHS -> R.plurals.stamp_months
                AgoUnit.YEARS -> R.plurals.stamp_years
            },
            stamp.count,
            stamp.count,
        )
    }
}
