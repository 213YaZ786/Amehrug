package com.amehrug.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/**
 * What a bar drawn at the very top of the window has to keep clear: the
 * status bar, and the sides, which carry the cutout once the phone is turned.
 *
 * Material's own TopAppBar applies this on its own. A bar built from a
 * Surface, like the search pill, applies nothing, so it draws under the
 * status bar until this is added by hand. That was the bug.
 *
 * safeDrawing rather than systemBars, because systemBars leaves the display
 * cutout out, and a bar under a cutout is a bar nobody can read.
 */
val topBarInsets: WindowInsets
    @Composable
    get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
