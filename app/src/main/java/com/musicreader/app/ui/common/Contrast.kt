package com.musicreader.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Black or white, whichever reads better on [fill]. Used for the finger number inside a colored dot,
 * where the dot's color is the user's setting and the number must stay readable on any of them.
 */
fun numberColorOn(fill: Color): Color = if (fill.luminance() > 0.45f) Color.Black else Color.White
