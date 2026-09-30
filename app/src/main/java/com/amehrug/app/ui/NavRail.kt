package com.amehrug.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amehrug.app.R
import com.amehrug.app.ui.component.FloatingSurface
import com.amehrug.app.ui.glass.LocalGlass
import com.amehrug.app.ui.glass.glassZone

enum class RailItem { NOTES, ARCHIVE, TRASH, LABELS, SETTINGS }

/**
 * The places of the app on a wide screen: a slim pill of glass down the
 * left edge, icons only, instead of a drawer that keeps a third of a tablet
 * for five rows. The labels, which can be many, open from their own button
 * in a small menu.
 */
@Composable
fun NavRail(
    selected: RailItem,
    labels: List<String>,
    activeLabel: String,
    onNotes: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onLabel: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        FloatingSurface(shape = RoundedCornerShape(36.dp)) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RailButton(R.drawable.ic_note, R.string.folder_notes, selected == RailItem.NOTES, onNotes)
                RailButton(R.drawable.ic_archive, R.string.folder_archive, selected == RailItem.ARCHIVE, onArchive)
                RailButton(R.drawable.ic_delete, R.string.folder_trash, selected == RailItem.TRASH, onTrash)
                if (labels.isNotEmpty()) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        RailButton(R.drawable.ic_label, R.string.drawer_labels, selected == RailItem.LABELS) { open = true }
                        LabelMenu(open, labels, activeLabel, onDismiss = { open = false }) { label ->
                            open = false
                            onLabel(label)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                RailButton(R.drawable.ic_settings, R.string.settings_title, selected == RailItem.SETTINGS, onSettings)
            }
        }
    }
}

@Composable
private fun RailButton(icon: Int, label: Int, on: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                when {
                    !on -> Color.Transparent
                    glass != null -> glass.accentTint
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
            tint = when {
                !on -> MaterialTheme.colorScheme.onSurfaceVariant
                glass != null -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

/** The labels, in a small menu of glass beside the rail. */
@Composable
private fun LabelMenu(
    open: Boolean,
    labels: List<String>,
    active: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(20.dp)
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        modifier = if (glass != null) Modifier.glassZone(shape, glass) else Modifier,
        shape = shape,
        containerColor = if (glass != null) Color.Transparent else MenuDefaults.containerColor,
        tonalElevation = if (glass != null) 0.dp else MenuDefaults.TonalElevation,
        shadowElevation = if (glass != null) 0.dp else MenuDefaults.ShadowElevation,
    ) {
        for (label in labels) {
            val on = label == active
            DropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        fontWeight = if (on) FontWeight.SemiBold else null,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_label), contentDescription = null) },
                onClick = { onPick(label) },
            )
        }
    }
}
