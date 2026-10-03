package com.hereliesaz.illumera.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle

private const val ACTION_PIP_CONTROL = "com.hereliesaz.illumera.PIP_CONTROL"
private const val EXTRA_PIP_COMMAND = "command"
private const val CMD_TOGGLE = 1
private const val CMD_REWIND = 2
private const val CMD_FORWARD = 3
private const val PIP_SEEK_MS = 10_000L

// Android rejects PiP aspect ratios outside 1:2.39 .. 2.39:1.
private val MIN_PIP_RATIO = Rational(100, 239)
private val MAX_PIP_RATIO = Rational(239, 100)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** PiP is a phone/tablet feature; TV has no use for it and some boxes advertise it badly. */
fun Context.supportsPictureInPicture(): Boolean {
    val pm = packageManager
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
        !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

internal fun clampPipRatio(width: Int, height: Int): Rational? {
    if (width <= 0 || height <= 0) return null
    val ratio = Rational(width, height)
    return when {
        ratio < MIN_PIP_RATIO -> MIN_PIP_RATIO
        ratio > MAX_PIP_RATIO -> MAX_PIP_RATIO
        else -> ratio
    }
}

/**
 * Wires the hosting activity's picture-in-picture for the player: auto-enter while playing
 * (setAutoEnterEnabled on 31+, onUserLeaveHint on 26-30), play/pause and skip remote actions,
 * and pausing once the PiP window is dismissed. Returns whether the activity is in PiP so the
 * caller can drop everything but the video.
 */
@Composable
fun rememberPictureInPictureState(
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    sourceRectHint: Rect?,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPipDismissed: () -> Unit
): State<Boolean> {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    val enabled = remember(context) { context.supportsPictureInPicture() }
    val inPip = remember { mutableStateOf(false) }
    if (activity == null || !enabled) return inPip

    val currentToggle by rememberUpdatedState(onTogglePlayPause)
    val currentSeek by rememberUpdatedState(onSeekBy)
    val currentDismissed by rememberUpdatedState(onPipDismissed)
    val currentPlaying by rememberUpdatedState(isPlaying)

    fun buildParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder()
            .setActions(buildActions(activity, isPlaying))
        clampPipRatio(videoWidth, videoHeight)?.let(builder::setAspectRatio)
        sourceRectHint?.takeIf { !it.isEmpty }?.let(builder::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    val params = buildParams()
    LaunchedEffect(params) {
        if (params != null) runCatching { activity.setPictureInPictureParams(params) }
    }
    val currentParams by rememberUpdatedState(params)

    DisposableEffect(activity) {
        inPip.value = activity.isInPictureInPictureMode
        val modeListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip.value = info.isInPictureInPictureMode
            // Closing the PiP window stops the activity without going through the player's
            // own ON_STOP path (it was skipped while in PiP), so pause here.
            if (!info.isInPictureInPictureMode &&
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                currentDismissed()
            }
        }
        val leaveHintListener = Runnable {
            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S &&
                currentPlaying && !activity.isInPictureInPictureMode
            ) {
                currentParams?.let { runCatching { activity.enterPictureInPictureMode(it) } }
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.getIntExtra(EXTRA_PIP_COMMAND, 0)) {
                    CMD_TOGGLE -> currentToggle()
                    CMD_REWIND -> currentSeek(-PIP_SEEK_MS)
                    CMD_FORWARD -> currentSeek(PIP_SEEK_MS)
                }
            }
        }
        activity.addOnPictureInPictureModeChangedListener(modeListener)
        activity.addOnUserLeaveHintListener(leaveHintListener)
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(modeListener)
            activity.removeOnUserLeaveHintListener(leaveHintListener)
            runCatching { activity.unregisterReceiver(receiver) }
            // Leaving the player must not leave auto-enter armed for the browse screens.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    activity.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
                    )
                }
            }
        }
    }
    return inPip
}

private fun buildActions(activity: Activity, isPlaying: Boolean): List<RemoteAction> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
    fun action(command: Int, icon: Int, label: String): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL)
            .setPackage(activity.packageName)
            .putExtra(EXTRA_PIP_COMMAND, command)
        val pending = PendingIntent.getBroadcast(
            activity,
            command,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(Icon.createWithResource(activity, icon), label, label, pending)
    }
    return listOf(
        action(CMD_REWIND, android.R.drawable.ic_media_rew, "Rewind 10 seconds"),
        if (isPlaying) {
            action(CMD_TOGGLE, android.R.drawable.ic_media_pause, "Pause")
        } else {
            action(CMD_TOGGLE, android.R.drawable.ic_media_play, "Play")
        },
        action(CMD_FORWARD, android.R.drawable.ic_media_ff, "Forward 10 seconds")
    )
}
