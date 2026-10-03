package com.hereliesaz.illumera.ui.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Adds touch tap/long-press handling without changing keyboard/D-pad behavior.
 * The long-press action fires at the system long-press timeout while the finger is still
 * down, like every other Android app; the release that follows is not a tap.
 */
fun Modifier.touchClick(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier {
    if (!enabled) return this
    return pointerInput(onClick, onLongClick) {
        // detectTapGestures never reports a tap for a press that already fired onLongPress.
        detectTapGestures(
            onTap = { onClick() },
            onLongPress = onLongClick?.let { longClick -> { longClick() } }
        )
    }
}
