package com.amehrug.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette, used when dynamic color is switched off.
// minSdk is 31, so dynamic color needs no version check.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6A4F),
    secondary = Color(0xFF4D6357),
    tertiary = Color(0xFF3C6472),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D4B3),
    secondary = Color(0xFFB4CCBD),
    tertiary = Color(0xFFA4CDDD),
)

// MaterialExpressiveTheme is internal in material3 1.4.0, the stable release
// on 2026-09-17. It is not used until a stable release exposes it.
@Composable
fun AmehrugTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (dynamicColor) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
