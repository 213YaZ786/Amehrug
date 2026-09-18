package com.amehrug.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.amehrug.app.model.NoteColor

/**
 * A note colour is a tint over the theme, not a fixed paint.
 *
 * DEFAULT takes the wallpaper palette of the phone, so most notes belong to
 * the Material You scheme. The eleven named colours keep Notally's names, so
 * its backups map one to one, but their values are muted tones that sit next
 * to a dynamic palette without fighting it, with a darker set for dark mode.
 */
private val lightTints = mapOf(
    NoteColor.CORAL to Color(0xFFFFDAD6),
    NoteColor.ORANGE to Color(0xFFFFDCC2),
    NoteColor.SAND to Color(0xFFF6E3BE),
    NoteColor.STORM to Color(0xFFD7E3F7),
    NoteColor.FOG to Color(0xFFE1E3E8),
    NoteColor.SAGE to Color(0xFFD8E8D5),
    NoteColor.MINT to Color(0xFFC9EBE1),
    NoteColor.DUSK to Color(0xFFDDDCF5),
    NoteColor.FLOWER to Color(0xFFF2D9F2),
    NoteColor.BLOSSOM to Color(0xFFFAD8E4),
    NoteColor.CLAY to Color(0xFFE8DDD3),
)

private val darkTints = mapOf(
    NoteColor.CORAL to Color(0xFF5B3A36),
    NoteColor.ORANGE to Color(0xFF5A3D27),
    NoteColor.SAND to Color(0xFF524524),
    NoteColor.STORM to Color(0xFF2F4258),
    NoteColor.FOG to Color(0xFF3B3E43),
    NoteColor.SAGE to Color(0xFF344734),
    NoteColor.MINT to Color(0xFF204840),
    NoteColor.DUSK to Color(0xFF3A3A55),
    NoteColor.FLOWER to Color(0xFF4D3550),
    NoteColor.BLOSSOM to Color(0xFF53303E),
    NoteColor.CLAY to Color(0xFF453B32),
)

@Composable
@ReadOnlyComposable
fun noteContainerColor(color: NoteColor): Color {
    if (color == NoteColor.DEFAULT) return MaterialTheme.colorScheme.surfaceContainerLow
    val tints = if (isSystemInDarkTheme()) darkTints else lightTints
    return tints[color] ?: MaterialTheme.colorScheme.surfaceContainerLow
}

/** Text stays readable on every tint, so it follows the surface, not the tint. */
@Composable
@ReadOnlyComposable
fun noteContentColor(): Color = MaterialTheme.colorScheme.onSurface
