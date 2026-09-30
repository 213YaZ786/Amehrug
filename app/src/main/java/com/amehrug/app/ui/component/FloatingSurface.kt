package com.amehrug.app.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amehrug.app.ui.glass.LocalGlass
import com.amehrug.app.ui.glass.LocalGlassBackdrop
import com.amehrug.app.ui.glass.glassFloating
import com.amehrug.app.ui.glass.glassZone

/**
 * A control floating above the page: the search pill, the dock, the
 * selection bar. In glass it bends what scrolls under it when the screen
 * gives it a backdrop, and is a pane of glass like the zones otherwise.
 * Without glass it is the Material surface it always was.
 */
@Composable
fun FloatingSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = 3.dp,
    /** A control that carries the accent, like the selection bar. */
    accent: Boolean = false,
    /** How strongly the edges bend what lies under them, 1 being the dock's. */
    lens: Float = 1f,
    /** How much of the tint the glass keeps, 1 being the dock's; less is clearer. */
    clarity: Float = 1f,
    content: @Composable () -> Unit,
) {
    val look = LocalGlass.current
    if (look == null) {
        Surface(modifier, shape, color, contentColor, elevation, elevation, null, content)
        return
    }
    val backdrop = LocalGlassBackdrop.current
    val base = if (accent) look.accentTint else look.floatTint
    val tint = base.copy(alpha = base.alpha * clarity)
    val glass = if (backdrop != null) {
        modifier.glassFloating(backdrop, shape, look, tint, lens)
    } else {
        modifier.glassZone(shape, look, lens = lens)
    }
    Surface(
        modifier = glass,
        shape = shape,
        color = Color.Transparent,
        contentColor = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}
