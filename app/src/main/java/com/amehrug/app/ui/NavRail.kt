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

/** The labels, in a small menu of glass under their round pane at the top. */
@Composable
internal fun LabelMenu(
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
