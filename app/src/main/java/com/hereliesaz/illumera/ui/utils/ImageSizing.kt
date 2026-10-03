package com.hereliesaz.illumera.ui.utils

import android.content.Context
import coil.size.Size

/**
 * Decode size for full-bleed 16:9 backdrops: the screen's longest edge, capped at 1080p, so
 * phones don't decode TV-sized bitmaps. Landscape TVs still get 1920x1080.
 */
fun backdropDecodeSize(context: Context): Size {
    val metrics = context.resources.displayMetrics
    val width = maxOf(metrics.widthPixels, metrics.heightPixels).coerceIn(640, 1920)
    return Size(width, width * 9 / 16)
}
