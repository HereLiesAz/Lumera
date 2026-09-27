package com.hereliesaz.illumera

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.hilt.navigation.compose.hiltViewModel
import com.hereliesaz.illumera.data.update.AppUpdateManager
import com.hereliesaz.illumera.data.update.UpdateInfo
import com.hereliesaz.illumera.data.update.UpdateState
import com.hereliesaz.illumera.data.player.PlaybackTrackSelectionStore
import com.hereliesaz.illumera.data.player.SourceSelectionStore
import com.hereliesaz.illumera.ui.util.rememberDialogWidth
import com.hereliesaz.illumera.ui.MainViewModel
import com.hereliesaz.illumera.ui.components.LumeraBackground
import com.hereliesaz.illumera.ui.details.DetailsScreen
import com.hereliesaz.illumera.ui.home.GridViewScreen
import com.hereliesaz.illumera.ui.home.HomeScreen
import com.hereliesaz.illumera.ui.watchlist.WatchlistScreen
import com.hereliesaz.illumera.ui.queue.QueueScreen
import com.hereliesaz.illumera.data.queue.QueueManager
import com.hereliesaz.illumera.data.queue.QueueItem
import com.hereliesaz.illumera.data.debrid.DebridManager
import com.hereliesaz.illumera.ui.home.HomeViewModel
import com.hereliesaz.illumera.data.model.stremio.MetaItem
import com.hereliesaz.illumera.data.model.stremio.Stream
import com.hereliesaz.illumera.data.model.stremio.MetaVideo
import com.hereliesaz.illumera.data.repository.AddonRepository
import com.hereliesaz.illumera.data.repository.IntroRepository
import com.hereliesaz.illumera.data.repository.SubtitleRepository
import com.hereliesaz.illumera.data.stream.StreamSortingService
import com.hereliesaz.illumera.domain.AddonSubtitle
import com.hereliesaz.illumera.domain.DashboardTab
import com.hereliesaz.illumera.domain.episodeDisplayTitle
import com.hereliesaz.illumera.domain.episodePlaybackId
import com.hereliesaz.illumera.domain.episodeStreamId
import com.hereliesaz.illumera.domain.findNextEpisode
import com.hereliesaz.illumera.ui.navigation.NavDestination
import com.hereliesaz.illumera.ui.navigation.NavDrawer
import com.hereliesaz.illumera.ui.navigation.TopNavigationBar
import com.hereliesaz.illumera.ui.player.PlayerScreen
import com.hereliesaz.illumera.ui.player.PlayerSessionResult
import com.hereliesaz.illumera.ui.player.PlaybackDurationStatus
import com.hereliesaz.illumera.ui.playback.PendingEpisodeSwitch
import com.hereliesaz.illumera.ui.playback.PendingSourceSelection
import com.hereliesaz.illumera.ui.playback.PlaybackSessionViewModel
import com.hereliesaz.illumera.ui.playback.buildPlayerSourceOption
import com.hereliesaz.illumera.ui.playback.buildSourcePayload
import com.hereliesaz.illumera.ui.playback.buildSubtitlePayload
import com.hereliesaz.illumera.ui.playback.handlePlayerSessionEnd
import com.hereliesaz.illumera.ui.playback.requestOrFallback
import com.hereliesaz.illumera.ui.playback.resolvePlayableSourceUrl
import com.hereliesaz.illumera.ui.playback.toPlayerSubtitleSources
import com.hereliesaz.illumera.ui.player.base.NextEpisodeInfo
import com.hereliesaz.illumera.ui.player.base.PlaybackSettings
import com.hereliesaz.illumera.ui.player.base.SkipSegmentInfo
import com.hereliesaz.illumera.ui.profiles.ProfileScreen
import com.hereliesaz.illumera.ui.profiles.ProfileViewModel
import com.hereliesaz.illumera.ui.search.SearchScreen
import com.hereliesaz.illumera.ui.settings.SettingsScreen
import com.hereliesaz.illumera.ui.addons.VoidButton
import com.hereliesaz.illumera.ui.addons.VoidDialog
import com.hereliesaz.illumera.ui.theme.DefaultThemes
import com.hereliesaz.illumera.ui.theme.LocalRoundCorners
import com.hereliesaz.illumera.ui.theme.LocalHubRoundCorners
import com.hereliesaz.illumera.ui.theme.LumeraTheme
import com.hereliesaz.illumera.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.hereliesaz.illumera.data.local.AddonDao
import com.hereliesaz.illumera.data.profile.ProfileConfigurationManager
import kotlinx.coroutines.withContext

import javax.inject.Inject

private const val DOUBLE_BACK_EXIT_WINDOW_MS = 400L
private val SERIES_PLAYBACK_TYPES = setOf("series", "tv", "anime", "episode")
// A next-episode hand-off that hasn't started playback by then is reported and falls back to the source list.
private const val AUTOPLAY_STALL_MS = 45_000L
// Once the next episode is opened: time allowed for its first frame (torrents start slowly).
private const val AUTOPLAY_FIRST_FRAME_MS = 60_000L

private fun launchExternalPlayer(context: android.content.Context, url: String) {
    try {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            Toast.makeText(context, "Unsupported URL scheme", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No external player found", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PlayerChoiceDialog(
    onInternal: () -> Unit,
    onExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(rememberDialogWidth(480))
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Choose Player",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = "Internal Player",
                        onClick = onInternal,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                    VoidButton(
                        text = "External Player",
                        onClick = onExternal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    info: UpdateInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val maxDialogHeight = maxHeight * 0.9f

            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(rememberDialogWidth(480))
                    .heightIn(max = maxDialogHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text(
                    "Update Available",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "v${info.versionName}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            info.changelog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(0.7f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = "Later",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    VoidButton(
                        text = "Update",
                        onClick = onUpdate,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Don't show again",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onDontShowAgain() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun UpdateDownloadingDialog(progress: Float, downloadedMb: Float, totalMb: Float) {
    Dialog(onDismissRequest = {}) {
        Box(
            modifier = Modifier
                .width(rememberDialogWidth(480))
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Downloading Update",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(0.1f),
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (totalMb > 0f) "%.1f MB / %.1f MB".format(downloadedMb, totalMb)
                    else "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun UpdateErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(rememberDialogWidth(480))
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Update Failed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = "Dismiss",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    VoidButton(
                        text = "Retry",
                        onClick = onRetry,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateReadyToInstallDialog(
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(rememberDialogWidth(480))
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Update Ready",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "The update finished downloading. If the install screen didn't open, tap Install.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = "Dismiss",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    VoidButton(
                        text = "Install",
                        onClick = onInstall,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private class GridRestoreState {
    var focusedIndex: Int? = null
    var scrollIndex: Int = 0
    var scrollOffset: Int = 0
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sourceSelectionStore: SourceSelectionStore
    @Inject
    lateinit var playbackTrackSelectionStore: PlaybackTrackSelectionStore
    @Inject
    lateinit var addonRepository: AddonRepository
    @Inject
    lateinit var subtitleRepository: SubtitleRepository
    @Inject
    lateinit var introRepository: IntroRepository
    @Inject
    lateinit var profileConfigurationManager: ProfileConfigurationManager
    @Inject
    lateinit var appUpdateManager: AppUpdateManager
    @Inject
    lateinit var addonDao: AddonDao
    @Inject
    lateinit var streamSortingService: StreamSortingService
    @Inject
    lateinit var debridManager: DebridManager
    @Inject
    lateinit var queueManager: QueueManager

    private var splashOverlay: android.view.View? = null
    private var splashIndicator: android.view.View? = null
    private var splashMinTimeElapsed = false
    private var splashAppReady = false
    private val _splashFinished = mutableStateOf(false)

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch(Dispatchers.IO) {
            profileConfigurationManager.saveActiveRuntimeState()
        }
    }

    override fun onDestroy() {
        dismissSplash()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (splashOverlay == null) {
            outState.putBoolean(KEY_SPLASH_SHOWN, true)
        }
    }

    private fun onSplashAppReady() {
        if (splashAppReady) return
        splashAppReady = true
        if (splashMinTimeElapsed) dismissSplash()
    }

    private fun dismissSplash() {
        splashOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        splashOverlay = null
        splashIndicator = null
        _splashFinished.value = true
    }

    private fun attachSplashOverlay() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val density = resources.displayMetrics.density

        val container = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val logo = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.logo_illumera)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        val logoSize = (140 * density).toInt()
        container.addView(logo, android.widget.FrameLayout.LayoutParams(logoSize, logoSize).apply {
            gravity = android.view.Gravity.CENTER
        })

        val indicator = android.widget.ProgressBar(this).apply {
            indeterminateTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            visibility = android.view.View.GONE
        }
        container.addView(indicator, android.widget.FrameLayout.LayoutParams(
            (36 * density).toInt(), (36 * density).toInt()
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = (80 * density).toInt()
        })
        splashIndicator = indicator

        handler.postDelayed({
            splashMinTimeElapsed = true
            if (splashAppReady) {
                dismissSplash()
            } else {
                indicator.visibility = android.view.View.VISIBLE
            }
        }, SPLASH_PAUSE_MS.toLong())

        addContentView(container, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        splashOverlay = container
    }

    // Torrent streaming and library refresh run as foreground services; on Android 13+
    // their progress notifications only show once this permission is granted.
    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    /** Asks for the notification permission once; the system remembers a refusal. */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("permissions", MODE_PRIVATE)
        if (prefs.getBoolean("notifications_asked", false)) return
        prefs.edit().putBoolean("notifications_asked", true).apply()
        notificationPermission.launch(permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sanitize saved state: R8 can obfuscate Parcelable class names, causing
        // BadParcelableException on process-death restore. Clear the bundle if corrupt.
        val safeState = savedInstanceState?.let { bundle ->
            try {
                bundle.keySet() // forces unparcel — throws if any class is missing
                bundle
            } catch (_: android.os.BadParcelableException) {
                null
            }
        }
        super.onCreate(safeState)
        window.setFormat(android.graphics.PixelFormat.RGBA_8888)

        // Fix sideload launch bug: pressing Home and returning re-creates the activity
        // instead of resuming it when the APK was installed via adb/sideload.
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && Intent.ACTION_MAIN == intent.action) {
            finish()
            return
        }

        requestNotificationPermissionOnce()

        // Default to showing splash; profile's splashEnabled setting is checked async below
        val showSplash = safeState?.getBoolean(KEY_SPLASH_SHOWN) != true
        if (!showSplash) _splashFinished.value = true

        setContent {

            val mainViewModel = hiltViewModel<MainViewModel>()
            val themeManager = hiltViewModel<ThemeManager>()
            val currentProfile by mainViewModel.activeProfile.collectAsState()
            var sessionProfileId by rememberSaveable { mutableStateOf<Int?>(null) }
            var sessionRestoreAttemptedProfileId by rememberSaveable { mutableStateOf<Int?>(null) }
            var activeView by rememberSaveable { mutableStateOf("menu") }
            var selectedMovieId by rememberSaveable { mutableStateOf("") }
            var selectedMovieType by rememberSaveable { mutableStateOf("movie") }
            // Playback state lives in the activity-scoped session, so it outlives a
            // configuration change and TorrentService's callbacks always reach it.
            val session = hiltViewModel<PlaybackSessionViewModel>(viewModelStoreOwner = this@MainActivity)
            var selectedMovieTitle by rememberSaveable { mutableStateOf("") }
            var selectedMoviePoster by rememberSaveable { mutableStateOf("") }
            var selectedMovieLogo by rememberSaveable { mutableStateOf("") }
            var selectedAddonBaseUrl by rememberSaveable { mutableStateOf<String?>(null) }
            var detailsResumePlaybackHint by rememberSaveable { mutableStateOf<String?>(null) }
            var trailerReturnToken by rememberSaveable { mutableStateOf(0) }
            var isTrailerLoading by remember { mutableStateOf(false) }
            var showTrailerError by remember { mutableStateOf(false) }
            var previousView by rememberSaveable { mutableStateOf("menu") }
            var queueAutoPlayId by rememberSaveable { mutableStateOf<String?>(null) }
            LaunchedEffect(activeView) {
                if (activeView != "details" && activeView != "player") session.queueStartPending = false
            }

            // Debrid library items (Watchlist's cloud storage section) are pre-resolved
            // file URLs with no addon Stream/catalog metadata behind them — this plays
            // one through illumera's own player instead of the hardcoded external
            // ACTION_VIEW intent WatchlistScreen previously used, respecting the same
            // playerPreference (internal/ask/external) as every other playback path.
            val onPlayResolvedStream: (id: String, url: String, title: String) -> Unit = { id, url, title ->
                session.stopTorrent()
                session.queueStartPending = false
                session.queuePlaybackActive = false
                session.queueWholeShowActive = false
                session.currentEpisodeList = emptyList()
                session.currentStream = Stream(url = url, title = title)
                session.selectedPlayerSubtitles = emptyList()
                session.selectedPlayerSources = emptyList()
                session.pendingSourceSelection = null
                session.selectedPlaybackId = "debrid_$id"
                session.selectedPlaybackType = "movie"
                session.selectedPlaybackTitle = title
                session.selectedPlaybackPoster = ""
                session.selectedTrailerAudioUrl = ""
                session.selectedVideoUrl = url
                when (currentProfile?.playerPreference) {
                    "external" -> launchExternalPlayer(this@MainActivity, url)
                    "ask" -> session.showPlayerChoiceDialog = true
                    else -> activeView = "player"
                }
            }


            LaunchedEffect(currentProfile?.id) {
                val profileId = currentProfile?.id
                if (profileId != null) {
                    sessionProfileId = profileId
                    sessionRestoreAttemptedProfileId = null
                }
            }

            LaunchedEffect(currentProfile, sessionProfileId, sessionRestoreAttemptedProfileId) {
                if (currentProfile != null) return@LaunchedEffect
                val profileIdToRestore = sessionProfileId ?: return@LaunchedEffect
                if (sessionRestoreAttemptedProfileId == profileIdToRestore) return@LaunchedEffect

                sessionRestoreAttemptedProfileId = profileIdToRestore
                mainViewModel.login(profileIdToRestore)
            }

            // Resolve theme from profile's themeId
            val currentTheme by themeManager.currentTheme.collectAsState()

            // Get round corners setting from profile (default true)
            val roundCorners = currentProfile?.roundCorners ?: true
            val hubRoundCorners = currentProfile?.hubRoundCorners ?: true
            
            // Update theme when profile changes
            LaunchedEffect(currentProfile) {
                currentProfile?.let { profile ->
                    themeManager.setCurrentProfile(profile.id, profile.themeId)
                }
            }

            // Signal native splash to resume once first composition is done
            LaunchedEffect(Unit) { onSplashAppReady() }

            // Auto-check for updates on launch
            val updateState by appUpdateManager.state.collectAsState()
            var updateDismissed by rememberSaveable { mutableStateOf(false) }
            val updateScope = rememberCoroutineScope()
            LaunchedEffect(Unit) { appUpdateManager.checkForUpdate() }

            LumeraTheme(theme = currentTheme) {
                CompositionLocalProvider(
                    LocalRoundCorners provides roundCorners,
                    LocalHubRoundCorners provides hubRoundCorners
                ) {
                LumeraBackground {
                    if (currentProfile == null) {
                        // Double-back-to-exit on profile selection
                        var lastBackPressMs by remember { mutableStateOf(0L) }
                        BackHandler {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastBackPressMs < DOUBLE_BACK_EXIT_WINDOW_MS) {
                                finishAffinity()
                            } else {
                                lastBackPressMs = now
                            }
                        }

                        val isRestoringSession = sessionProfileId != null
                        if (!isRestoringSession) {
                            // PROFILE SELECTION / CREATION
                            // Always use VOID theme for profile selection (black & white)
                            LumeraTheme(theme = DefaultThemes.VOID) {
                                val profileViewModel = hiltViewModel<ProfileViewModel>()
                                val profiles by profileViewModel.profiles.collectAsState()

                                ProfileScreen(
                                    profiles = profiles,
                                    onProfileSelected = {
                                        sessionProfileId = it.id
                                        sessionRestoreAttemptedProfileId = null
                                        mainViewModel.login(it.id)
                                    }
                                )
                            }
                        }
                    } else {
                        // MAIN APP CONTENT
                        var currentNav by remember { mutableStateOf(NavDestination.Home) }
                        // Bumped when Settings is re-selected from the menu while already on
                        // Settings: re-keys SettingsScreen so any open sub-page/section resets
                        // to the Settings root.
                        var settingsResetKey by remember { mutableIntStateOf(0) }
                        // Whether focus is inside Settings' content pane (vs. its section list).
                        var settingsContentFocused by remember { mutableStateOf(false) }
                        
                        // Grid view state
                        var gridViewTitle by rememberSaveable { mutableStateOf("") }
                        var gridViewItems by remember { mutableStateOf<List<MetaItem>>(emptyList()) }
                        var gridViewConfigId by rememberSaveable { mutableStateOf("") }
                        val gridRestoreState = remember { GridRestoreState() }

                        // Search focus restoration
                        val searchMoviesViewMoreRequester = remember { FocusRequester() }
                        val searchSeriesViewMoreRequester = remember { FocusRequester() }
                        val searchResultsRequester = remember { FocusRequester() }
                        var searchFocusTarget by remember { mutableStateOf<String?>(null) }
                        var searchLastFocusedId by remember { mutableStateOf<String?>(null) }

                        // Track where we came from for proper back navigation
                        val uiScope = rememberCoroutineScope()
                        // The playback jobs launched in uiScope die with this composition, while
                        // the session outlives it: drop what those jobs would have settled.
                        DisposableEffect(Unit) {
                            onDispose { session.onUiJobsCancelled() }
                        }

                        // Focus Traffic Control
                        val drawerRequesters = remember { NavDestination.values().associateWith { FocusRequester() } }
                        val homeEntryRequester = remember { FocusRequester() }
                        val searchEntryRequester = remember { FocusRequester() }
                        val settingsEntryRequester = remember { FocusRequester() }
                        val watchlistEntryRequester = remember { FocusRequester() }

                        // STATE CHANGE TRIGGER:
                        LaunchedEffect(currentNav, activeView, settingsResetKey) {
                            if (activeView != "menu") return@LaunchedEffect
                            when(currentNav) {
                                // HomeScreen requests focus itself once data is ready.
                                // Avoid requesting early into the loading placeholder, which can
                                // cause a brief nav -> content -> nav -> content flicker.
                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> Unit
                                NavDestination.Search -> {
                                    delay(200) // Increased for stability
                                    val target = searchFocusTarget
                                    if (target != null) {
                                        searchFocusTarget = null
                                        when (target) {
                                            "movies" -> searchMoviesViewMoreRequester.requestFocus()
                                            "series" -> searchSeriesViewMoreRequester.requestFocus()
                                            "poster" -> searchResultsRequester.requestFocus()
                                        }
                                    } else {
                                        searchEntryRequester.requestFocus()
                                    }
                                }
                                NavDestination.Settings -> {
                                    delay(200) // Increased for stability
                                    settingsEntryRequester.requestFocus()
                                }
                                NavDestination.Watchlist -> {
                                    delay(200)
                                    watchlistEntryRequester.requestFocus()
                                }
                                else -> Unit
                            }
                        }

                        // Focus restoration after navPosition change (Crossfade animation)
                        val navPosition = currentProfile?.navPosition ?: "left"
                        LaunchedEffect(navPosition) {
                            if (activeView == "menu" && currentNav == NavDestination.Settings) {
                                delay(450) // Wait for Crossfade (400ms) + buffer
                                // Re-check after the delay: the user may have navigated away from
                                // Settings while this was pending, unmounting the FocusRequester's
                                // only attachment point and making requestFocus() throw.
                                // Skip if Settings already put focus back on the option that was
                                // changed (see PersonalizationSettings' Menu Position).
                                if (activeView == "menu" && currentNav == NavDestination.Settings && !settingsContentFocused) {
                                    try {
                                        settingsEntryRequester.requestFocus()
                                    } catch (_: IllegalStateException) {
                                    }
                                }
                            }
                        }


                            // CONDITIONAL NAVIGATION RENDERING (no animation)
                            val view = activeView
                            if (view == "menu") {
                                // Double-back-to-exit: two rapid back presses exit the app
                                var lastBackPressMs by remember { mutableStateOf(0L) }

                                // Shared content composable
                                // Shared navigation handler
                                val handleNavigate: (NavDestination) -> Unit = { destination ->
                                    if (destination == NavDestination.Exit) {
                                        finishAffinity()
                                    } else if (currentNav == destination) {
                                        // Re-selecting the current destination is still a navigation:
                                        // return it to its root and put focus into its content (which
                                        // also closes the menu). Settings is the only main destination
                                        // with in-place sub-pages, so it is re-created from scratch; the
                                        // others have no sub-pages inside the menu view and just take
                                        // focus at their entry point.
                                        runCatching {
                                            when (destination) {
                                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> homeEntryRequester.requestFocus()
                                                NavDestination.Search -> searchEntryRequester.requestFocus()
                                                NavDestination.Settings -> {
                                                    settingsContentFocused = false
                                                    settingsResetKey++ // LaunchedEffect above re-focuses Settings' entry
                                                }
                                                NavDestination.Watchlist -> watchlistEntryRequester.requestFocus()
                                                else -> {}
                                            }
                                        }
                                    } else {
                                        if (currentNav == NavDestination.Search) searchFocusTarget = null
                                        if (currentNav == NavDestination.Settings) settingsContentFocused = false
                                        currentNav = destination
                                    }
                                }

                                // Shared enter content handler — runCatching guards against
                                // requestFocus() throwing when the target isn't composed yet
                                // (e.g. focus strays to the drawer mid-transition)
                                val handleEnterContent: () -> Unit = {
                                    runCatching {
                                        when(currentNav) {
                                            NavDestination.Home, NavDestination.Movies, NavDestination.Series -> homeEntryRequester.requestFocus()
                                            NavDestination.Search -> searchEntryRequester.requestFocus()
                                            NavDestination.Settings -> settingsEntryRequester.requestFocus()
                                            NavDestination.Watchlist -> watchlistEntryRequester.requestFocus()
                                            else -> {}
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onPreviewKeyEvent { event ->
                                            if (settingsContentFocused) return@onPreviewKeyEvent false
                                            if (event.key == Key.Back && event.type == KeyEventType.KeyDown) {
                                                val now = SystemClock.uptimeMillis()
                                                if (now - lastBackPressMs < DOUBLE_BACK_EXIT_WINDOW_MS) {
                                                    finishAffinity()
                                                    true
                                                } else {
                                                    lastBackPressMs = now
                                                    false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                ) {
                                Crossfade(targetState = navPosition, animationSpec = tween(400), label = "NavSwitcher") { position ->
                                if (position == "top") {
                                    TopNavigationBar(
                                        currentDestination = currentNav,
                                        currentProfile = currentProfile,
                                        topNavRequesters = drawerRequesters,
                                        onNavigate = handleNavigate,
                                        onEnterContent = handleEnterContent,
                                        onLogout = {
                                            sessionProfileId = null
                                            sessionRestoreAttemptedProfileId = null

                                            activeView = "menu"
                                            themeManager.resetTheme()
                                            mainViewModel.logout()
                                        },
                                        onExit = { finishAffinity() },
                                        content = {
                                            when (currentNav) {
                                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> {
                                                    val vm = hiltViewModel<HomeViewModel>()
                                                    val tab = if(currentNav == NavDestination.Home) "home" else if(currentNav == NavDestination.Movies) "movies" else "series"
                                                    val dashboardTab = DashboardTab.fromString(tab)

                                                    key(tab) {
                                                        LaunchedEffect(tab, currentProfile?.id) { vm.loadScreen(tab, currentProfile) }
                                                        HomeScreen(
                                                            tab = dashboardTab,
                                                            viewModel = vm,
                                                            currentProfile = currentProfile,
                                                            entryRequester = homeEntryRequester,
                                                            drawerRequester = drawerRequesters[currentNav]!!,
                                                            onMovieClick = { movie ->
                                                                selectedMovieId = movie.id
                                                                selectedMovieType = movie.type
                                                                selectedMovieTitle = movie.name
                                                                selectedMoviePoster = movie.poster ?: ""
                                                                selectedMovieLogo = movie.logo ?: ""
                                                                selectedAddonBaseUrl = movie.addonBaseUrl
                                                                detailsResumePlaybackHint = null
                                                                session.selectedPlaybackId = movie.id
                                                                session.selectedPlaybackType = movie.type
                                                                session.selectedPlaybackTitle = movie.name
                                                                session.selectedPlaybackPoster = movie.poster ?: ""
                                                                previousView = "menu"
                                                                activeView = "details"
                                                            },
                                                            onViewMore = { title, items, configId ->
                                                                gridViewTitle = title
                                                                gridViewItems = items
                                                                gridViewConfigId = configId
                                                                activeView = "grid"
                                                            }
                                                        )
                                                    }
                                                }
                                                NavDestination.Search -> {
                                                    val searchHomeVm = hiltViewModel<HomeViewModel>()
                                                    SearchScreen(
                                                        currentProfile = currentProfile,
                                                        watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            session.selectedPlaybackId = movie.id
                                                            session.selectedPlaybackType = movie.type
                                                            session.selectedPlaybackTitle = movie.name
                                                            session.selectedPlaybackPoster = movie.poster ?: ""
                                                            searchFocusTarget = "poster"
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        onViewMore = { title, items ->
                                                            searchFocusTarget = if (title == "Movies") "movies" else "series"
                                                            gridViewTitle = title
                                                            gridViewItems = items
                                                            gridViewConfigId = ""
                                                            activeView = "grid"
                                                        },
                                                        moviesViewMoreRequester = searchMoviesViewMoreRequester,
                                                        seriesViewMoreRequester = searchSeriesViewMoreRequester,
                                                        resultsRequester = searchResultsRequester,
                                                        lastFocusedId = searchLastFocusedId,
                                                        onFocusedIdChange = { searchLastFocusedId = it },
                                                        entryRequester = searchEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Search]!!
                                                    )
                                                }
                                                NavDestination.Profile -> {
                                                    // logout() is a side effect, not a render — run it once via
                                                    // LaunchedEffect rather than inline in the composable body.
                                                    // logout() persists runtime state on IO before flipping
                                                    // currentProfile to null, and nothing here moves currentNav
                                                    // away from Profile in the meantime, so calling it inline
                                                    // re-fired it on every recomposition until that IO completed.
                                                    LaunchedEffect(Unit) {
                                                        sessionProfileId = null
                                                        sessionRestoreAttemptedProfileId = null
                                                        activeView = "menu"
                                                        themeManager.resetTheme()
                                                        mainViewModel.logout()
                                                    }
                                                }
                                                NavDestination.Watchlist -> {
                                                    val watchlistHomeVm = hiltViewModel<HomeViewModel>()
                                                    WatchlistScreen(
                                                        currentProfile = currentProfile,
                                                        entryRequester = watchlistEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Watchlist]!!,
                                                        watchedIds = watchlistHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            session.selectedPlaybackId = movie.id
                                                            session.selectedPlaybackType = movie.type
                                                            session.selectedPlaybackTitle = movie.name
                                                            session.selectedPlaybackPoster = movie.poster ?: ""
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        onPlayResolvedStream = onPlayResolvedStream
                                                    )
                                                }
                                                NavDestination.Settings -> {
                                                    val homeVm = hiltViewModel<HomeViewModel>()
                                                    key(settingsResetKey) {
                                                        SettingsScreen(
                                                            currentProfile = currentProfile,
                                                            onBack = {
                                                                // Leaving Settings is a navigation, not a menu
                                                                // open: Home focuses its own content once loaded.
                                                                currentNav = NavDestination.Home
                                                            },
                                                            entryRequester = settingsEntryRequester,
                                                            drawerRequester = drawerRequesters[NavDestination.Settings]!!,
                                                            onDashboardChanged = { homeVm.invalidate() },
                                                            onContentFocusChanged = { settingsContentFocused = it }
                                                        )
                                                    }
                                                }
                                                NavDestination.Exit -> { /* App closes */ }
                                            }
                                        }
                                    )
                                } else { // position == "left"
                                    NavDrawer(
                                        currentDestination = currentNav,
                                        currentProfile = currentProfile,
                                        drawerRequesters = drawerRequesters,
                                        onNavigate = handleNavigate,
                                        onClose = handleEnterContent,
                                        content = {
                                            when (currentNav) {
                                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> {
                                                    val vm = hiltViewModel<HomeViewModel>()
                                                    val tab = if(currentNav == NavDestination.Home) "home" else if(currentNav == NavDestination.Movies) "movies" else "series"
                                                    val dashboardTab = DashboardTab.fromString(tab)

                                                    key(tab) {
                                                        LaunchedEffect(tab, currentProfile?.id) { vm.loadScreen(tab, currentProfile) }
                                                        HomeScreen(
                                                            tab = dashboardTab,
                                                            viewModel = vm,
                                                            currentProfile = currentProfile,
                                                            entryRequester = homeEntryRequester,
                                                            drawerRequester = drawerRequesters[currentNav]!!,
                                                            onMovieClick = { movie ->
                                                                selectedMovieId = movie.id
                                                                selectedMovieType = movie.type
                                                                selectedMovieTitle = movie.name
                                                                selectedMoviePoster = movie.poster ?: ""
                                                                selectedMovieLogo = movie.logo ?: ""
                                                                selectedAddonBaseUrl = movie.addonBaseUrl
                                                                detailsResumePlaybackHint = null
                                                                session.selectedPlaybackId = movie.id
                                                                session.selectedPlaybackType = movie.type
                                                                session.selectedPlaybackTitle = movie.name
                                                                session.selectedPlaybackPoster = movie.poster ?: ""
                                                                previousView = "menu"
                                                                activeView = "details"
                                                            },
                                                            onViewMore = { title, items, configId ->
                                                                gridViewTitle = title
                                                                gridViewItems = items
                                                                gridViewConfigId = configId
                                                                activeView = "grid"
                                                            }
                                                        )
                                                    }
                                                }
                                                NavDestination.Search -> {
                                                    val searchHomeVm = hiltViewModel<HomeViewModel>()
                                                    SearchScreen(
                                                        currentProfile = currentProfile,
                                                        watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            session.selectedPlaybackId = movie.id
                                                            session.selectedPlaybackType = movie.type
                                                            session.selectedPlaybackTitle = movie.name
                                                            session.selectedPlaybackPoster = movie.poster ?: ""
                                                            searchFocusTarget = "poster"
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        onViewMore = { title, items ->
                                                            searchFocusTarget = if (title == "Movies") "movies" else "series"
                                                            gridViewTitle = title
                                                            gridViewItems = items
                                                            gridViewConfigId = ""
                                                            activeView = "grid"
                                                        },
                                                        moviesViewMoreRequester = searchMoviesViewMoreRequester,
                                                        seriesViewMoreRequester = searchSeriesViewMoreRequester,
                                                        resultsRequester = searchResultsRequester,
                                                        lastFocusedId = searchLastFocusedId,
                                                        onFocusedIdChange = { searchLastFocusedId = it },
                                                        entryRequester = searchEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Search]!!
                                                    )
                                                }
                                                NavDestination.Profile -> {
                                                    // logout() is a side effect, not a render — run it once via
                                                    // LaunchedEffect rather than inline in the composable body.
                                                    // logout() persists runtime state on IO before flipping
                                                    // currentProfile to null, and nothing here moves currentNav
                                                    // away from Profile in the meantime, so calling it inline
                                                    // re-fired it on every recomposition until that IO completed.
                                                    LaunchedEffect(Unit) {
                                                        sessionProfileId = null
                                                        sessionRestoreAttemptedProfileId = null
                                                        activeView = "menu"
                                                        themeManager.resetTheme()
                                                        mainViewModel.logout()
                                                    }
                                                }
                                                NavDestination.Watchlist -> {
                                                    val watchlistHomeVm = hiltViewModel<HomeViewModel>()
                                                    WatchlistScreen(
                                                        currentProfile = currentProfile,
                                                        entryRequester = watchlistEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Watchlist]!!,
                                                        watchedIds = watchlistHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            selectedMovieId = movie.id
                                                            selectedMovieType = movie.type
                                                            selectedMovieTitle = movie.name
                                                            selectedMoviePoster = movie.poster ?: ""
                                                            selectedMovieLogo = movie.logo ?: ""
                                                            selectedAddonBaseUrl = movie.addonBaseUrl
                                                            detailsResumePlaybackHint = null
                                                            session.selectedPlaybackId = movie.id
                                                            session.selectedPlaybackType = movie.type
                                                            session.selectedPlaybackTitle = movie.name
                                                            session.selectedPlaybackPoster = movie.poster ?: ""
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        onPlayResolvedStream = onPlayResolvedStream
                                                    )
                                                }
                                                NavDestination.Settings -> {
                                                    val homeVm = hiltViewModel<HomeViewModel>()
                                                    key(settingsResetKey) {
                                                        SettingsScreen(
                                                            currentProfile = currentProfile,
                                                            onBack = {
                                                                // Leaving Settings is a navigation, not a menu
                                                                // open: Home focuses its own content once loaded.
                                                                currentNav = NavDestination.Home
                                                            },
                                                            entryRequester = settingsEntryRequester,
                                                            drawerRequester = drawerRequesters[NavDestination.Settings]!!,
                                                            onDashboardChanged = { homeVm.invalidate() },
                                                            onContentFocusChanged = { settingsContentFocused = it }
                                                        )
                                                    }
                                                }
                                                NavDestination.Exit -> { /* App closes */ }
                                            }
                                        }
                                    )
                                }
                                } // Crossfade end
                                } // Double-back Box end
                        } else if (view == "grid") {
                            // gridViewItems isn't rememberSaveable (MetaItem isn't Parcelable), so a
                            // process-death recreation restores gridViewConfigId/gridViewTitle but
                            // loses the items themselves, leaving a header with nothing under it.
                            // Bounce back to Home rather than show that broken empty screen. Key off
                            // the title rather than configId — Search's "View More" callback always
                            // passes an empty configId (there's no catalog to page through), so
                            // configId alone can't tell a legitimately-empty Search grid from a
                            // restored one; title is set at every call site regardless of source.
                            LaunchedEffect(Unit) {
                                if (gridViewTitle.isNotEmpty() && gridViewItems.isEmpty()) {
                                    activeView = "menu"
                                }
                            }
                            val gridVm = hiltViewModel<HomeViewModel>()
                            val gridNavPosition = currentProfile?.navPosition ?: "left"
                            val gridEntryRequester = remember { FocusRequester() }
                            val handleGridNavigate: (NavDestination) -> Unit = { destination ->
                                if (destination == NavDestination.Exit) {
                                    finishAffinity()
                                } else {
                                    currentNav = destination
                                    activeView = "menu"
                                }
                            }
                            val gridContent: @Composable () -> Unit = {
                                GridViewScreen(
                                    title = gridViewTitle,
                                    items = gridViewItems,
                                    lastFocusedIndex = gridRestoreState.focusedIndex,
                                    onFocusChange = { gridRestoreState.focusedIndex = it },
                                    onMovieClick = { movie ->
                                        selectedMovieId = movie.id
                                        selectedMovieType = movie.type
                                        selectedMovieTitle = movie.name
                                        selectedMoviePoster = movie.poster ?: ""
                                        selectedMovieLogo = movie.logo ?: ""
                                        selectedAddonBaseUrl = movie.addonBaseUrl
                                        detailsResumePlaybackHint = null
                                        session.selectedPlaybackId = movie.id
                                        session.selectedPlaybackType = movie.type
                                        session.selectedPlaybackTitle = movie.name
                                        session.selectedPlaybackPoster = movie.poster ?: ""
                                        previousView = "grid"
                                        activeView = "details"
                                    },
                                    onBack = {
                                        gridRestoreState.focusedIndex = null
                                        gridRestoreState.scrollIndex = 0
                                        gridRestoreState.scrollOffset = 0
                                        activeView = "menu"
                                    },
                                    onLoadMore = {
                                        if (gridViewConfigId.isNotEmpty()) {
                                            gridVm.loadMoreItems(gridViewConfigId)
                                        }
                                    },
                                    initialScrollIndex = gridRestoreState.scrollIndex,
                                    initialScrollOffset = gridRestoreState.scrollOffset,
                                    onScrollPositionChange = { index, offset ->
                                        gridRestoreState.scrollIndex = index
                                        gridRestoreState.scrollOffset = offset
                                    },
                                    watchedIds = gridVm.state.collectAsState().value.watchedIds,
                                    externalEntryRequester = gridEntryRequester
                                )
                            }
                            if (gridNavPosition == "top") {
                                TopNavigationBar(
                                    currentDestination = currentNav,
                                    currentProfile = currentProfile,
                                    topNavRequesters = drawerRequesters,
                                    onNavigate = handleGridNavigate,
                                    onEnterContent = { runCatching { gridEntryRequester.requestFocus() } },
                                    onLogout = {
                                        sessionProfileId = null
                                        sessionRestoreAttemptedProfileId = null
                                        activeView = "menu"
                                        themeManager.resetTheme()
                                        mainViewModel.logout()
                                    },
                                    onExit = { finishAffinity() },
                                    content = gridContent
                                )
                            } else {
                                NavDrawer(
                                    currentDestination = currentNav,
                                    currentProfile = currentProfile,
                                    drawerRequesters = drawerRequesters,
                                    onNavigate = handleGridNavigate,
                                    onClose = { runCatching { gridEntryRequester.requestFocus() } },
                                    content = gridContent
                                )
                            }
                            // Sync gridViewItems when ViewModel state updates (after loadMoreItems)
                            val vmState by gridVm.state.collectAsState()
                            LaunchedEffect(vmState.rows) {
                                if (gridViewConfigId.isNotEmpty()) {
                                    val updatedRow = vmState.rows.find { it.configId == gridViewConfigId }
                                    if (updatedRow != null && updatedRow.items.size > gridViewItems.size) {
                                        gridViewItems = updatedRow.items
                                    }
                                }
                            }
                        } else if (view == "details" || (view == "player" && session.selectedPlaybackId.startsWith("trailer_"))) {
                            val detailsNavController = rememberNavController()
                            val startRoute = "detail/${java.net.URLEncoder.encode(selectedMovieType, "UTF-8")}/${java.net.URLEncoder.encode(selectedMovieId, "UTF-8")}?addon=${java.net.URLEncoder.encode(selectedAddonBaseUrl ?: "", "UTF-8")}&resume=${java.net.URLEncoder.encode(detailsResumePlaybackHint ?: "", "UTF-8")}"

                            // Navigate to initial details when first entering
                            LaunchedEffect(selectedMovieType, selectedMovieId) {
                                val currentRoute = detailsNavController.currentBackStackEntry?.destination?.route
                                if (currentRoute == null || currentRoute == "detail_start") {
                                    detailsNavController.navigate(startRoute) {
                                        popUpTo("detail_start") { inclusive = true }
                                    }
                                }
                            }

                            BackHandler {
                                if (!detailsNavController.popBackStack()) {
                                    activeView = previousView
                                }
                            }

                            // Shared onPlayClick lambda for all detail screens
                            val onPlayClick: (String, String, String, String, String, String, com.hereliesaz.illumera.data.model.stremio.Stream, List<com.hereliesaz.illumera.domain.AddonSubtitle>, List<com.hereliesaz.illumera.data.model.stremio.Stream>, List<com.hereliesaz.illumera.data.model.stremio.MetaVideo>) -> Unit = { url, playbackId, playbackType, playbackTitle, seriesTitle, logo, stream, addonSubtitles, availableStreams, episodes ->
                                val resolvedPlaybackTitle = playbackTitle.ifBlank { selectedMovieTitle }
                                val resolvedSeriesTitle = seriesTitle.ifBlank { selectedMovieTitle }
                                val isSeriesPlayback = playbackType.equals("series", ignoreCase = true) ||
                                    playbackType.equals("tv", ignoreCase = true)
                                if (isSeriesPlayback && resolvedSeriesTitle.isNotBlank()) {
                                    selectedMovieTitle = resolvedSeriesTitle
                                }
                                if (logo.isNotBlank()) selectedMovieLogo = logo
                                session.queuePlaybackActive = session.queueStartPending
                                if (!session.queuePlaybackActive) session.queueWholeShowActive = false
                                session.queueStartPending = false
                                session.currentEpisodeList = episodes
                                session.currentStream = stream
                                val subtitlePayload = buildSubtitlePayload(stream, addonSubtitles)
                                val sourcePayloadInput = if (availableStreams.isNotEmpty()) availableStreams else listOf(stream)
                                val sourcePayload = buildSourcePayload(streams = sourcePayloadInput, selectedStream = stream)
                                session.pendingSourceSelection = PendingSourceSelection(
                                    playbackId = playbackId,
                                    launchedStream = stream,
                                    candidateStreams = sourcePayloadInput
                                )
                                if (url.startsWith("magnet:")) {
                                    uiScope.launch {
                                        mainViewModel.persistActiveProfileState()
                                        session.selectedPlaybackId = playbackId
                                        session.selectedPlaybackType = playbackType
                                        session.selectedPlaybackTitle = resolvedPlaybackTitle
                                        session.selectedPlaybackPoster = selectedMoviePoster
                                        session.selectedTrailerAudioUrl = ""
                                        session.selectedPlayerSubtitles = subtitlePayload
                                        session.selectedPlayerSources = sourcePayload
                                        session.selectedVideoUrl = ""
                                        activeView = "player"
                                        session.startTorrent(url, stream.fileIdx ?: -1, stream.behaviorHints?.filename ?: "")
                                    }
                                } else {
                                    session.stopTorrent()
                                    uiScope.launch {
                                        mainViewModel.persistActiveProfileState()
                                        session.selectedPlaybackId = playbackId
                                        session.selectedPlaybackType = playbackType
                                        session.selectedPlaybackTitle = resolvedPlaybackTitle
                                        session.selectedPlaybackPoster = selectedMoviePoster
                                        session.selectedTrailerAudioUrl = ""
                                        session.selectedPlayerSubtitles = subtitlePayload
                                        session.selectedPlayerSources = sourcePayload
                                        session.selectedVideoUrl = url
                                        when (currentProfile?.playerPreference) {
                                            "external" -> launchExternalPlayer(this@MainActivity, url)
                                            "ask" -> session.showPlayerChoiceDialog = true
                                            else -> activeView = "player"
                                        }
                                    }
                                }
                            }

                            NavHost(
                                navController = detailsNavController,
                                startDestination = "detail_start",
                            ) {
                                composable("detail_start") { }
                                composable(
                                    "detail/{type}/{id}?addon={addon}&resume={resume}",
                                    arguments = listOf(
                                        navArgument("type") { type = NavType.StringType },
                                        navArgument("id") { type = NavType.StringType },
                                        navArgument("addon") { type = NavType.StringType; defaultValue = "" },
                                        navArgument("resume") { type = NavType.StringType; defaultValue = "" }
                                    )
                                ) { backStackEntry ->
                                    val detailType = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("type") ?: "movie", "UTF-8")
                                    val detailId = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("id") ?: "", "UTF-8")
                                    val detailAddon = backStackEntry.arguments?.getString("addon")?.takeIf { it.isNotEmpty() }
                                    val detailResume = backStackEntry.arguments?.getString("resume")?.takeIf { it.isNotEmpty() }

                                    DetailsScreen(
                                        type = detailType,
                                        id = detailId,
                                        addonBaseUrl = detailAddon,
                                        resumePlaybackHint = detailResume,
                                        autoSelectSource = currentProfile?.autoSelectSource ?: false,
                                        rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true,
                                        onPosterResolved = { selectedMoviePoster = it },
                                        onPlayClick = onPlayClick,
                                        onAddToQueue = { queueManager.add(it) },
                                        queueAutoPlayId = queueAutoPlayId,
                                        onQueueAutoPlayConsumed = { queueAutoPlayId = null },
                                        onNavigateToDetails = { navType, navId ->
                                            val route = "detail/${java.net.URLEncoder.encode(navType, "UTF-8")}/${java.net.URLEncoder.encode(navId, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        },
                                        onNavigateToCastDetail = { castPersonId, castPersonName ->
                                            val route = "cast_detail/$castPersonId/${java.net.URLEncoder.encode(castPersonName, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        },
                                        onNavigateToStudioDetail = { entityId, entityKind, entityName, sourceType ->
                                            val route = "studio_detail/$entityId/$entityKind/${java.net.URLEncoder.encode(entityName, "UTF-8")}/$sourceType"
                                            detailsNavController.navigate(route)
                                        },
                                        trailerReturnToken = trailerReturnToken,
                                        isTrailerLoading = isTrailerLoading,
                                        onTrailerClick = { youtubeKey, trailerName ->
                                            isTrailerLoading = true
                                            uiScope.launch {
                                                val extractor = com.hereliesaz.illumera.data.trailer.YouTubeExtractor()
                                                val source = extractor.extractPlaybackSource(youtubeKey)
                                                isTrailerLoading = false
                                                if (source != null) {
                                                    session.selectedVideoUrl = source.videoUrl
                                                    session.selectedTrailerAudioUrl = source.audioUrl ?: ""
                                                    session.selectedPlaybackId = "trailer_$youtubeKey"
                                                    session.selectedPlaybackType = selectedMovieType
                                                    session.selectedPlaybackTitle = trailerName
                                                    session.selectedPlaybackPoster = selectedMoviePoster
                                                    session.selectedPlayerSubtitles = emptyList()
                                                    session.selectedPlayerSources = emptyList()
                                                    activeView = "player"
                                                } else {
                                                    showTrailerError = true
                                                }
                                            }
                                        }
                                    )
                                }
                                composable(
                                    "cast_detail/{personId}/{personName}",
                                    arguments = listOf(
                                        navArgument("personId") { type = NavType.StringType },
                                        navArgument("personName") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val castPersonId = (backStackEntry.arguments?.getString("personId") ?: "0").toIntOrNull() ?: 0
                                    val castPersonName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("personName") ?: "", "UTF-8")

                                    com.hereliesaz.illumera.ui.cast.CastDetailScreen(
                                        personId = castPersonId,
                                        personName = castPersonName,
                                        onBackPress = { detailsNavController.popBackStack() },
                                        onNavigateToDetails = { navType, navId ->
                                            val route = "detail/${java.net.URLEncoder.encode(navType, "UTF-8")}/${java.net.URLEncoder.encode(navId, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        }
                                    )
                                }
                                composable(
                                    "studio_detail/{entityId}/{entityKind}/{entityName}/{sourceType}",
                                    arguments = listOf(
                                        navArgument("entityId") { type = NavType.StringType },
                                        navArgument("entityKind") { type = NavType.StringType },
                                        navArgument("entityName") { type = NavType.StringType },
                                        navArgument("sourceType") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val studioEntityId = (backStackEntry.arguments?.getString("entityId") ?: "0").toIntOrNull() ?: 0
                                    val studioEntityKind = backStackEntry.arguments?.getString("entityKind") ?: "company"
                                    val studioEntityName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("entityName") ?: "", "UTF-8")
                                    val studioSourceType = backStackEntry.arguments?.getString("sourceType") ?: "movie"

                                    com.hereliesaz.illumera.ui.studio.StudioDetailScreen(
                                        entityId = studioEntityId,
                                        entityKind = studioEntityKind,
                                        entityName = studioEntityName,
                                        sourceType = studioSourceType,
                                        onBackPress = { detailsNavController.popBackStack() },
                                        onNavigateToDetails = { navType, navId ->
                                            val route = "detail/${java.net.URLEncoder.encode(navType, "UTF-8")}/${java.net.URLEncoder.encode(navId, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        }
                                    )
                                }
                            }
                        }
                        if (view == "player") {
                            if (session.selectedVideoUrl.isNotBlank() && session.currentStream == null &&
                                !session.selectedPlaybackId.startsWith("trailer_")
                            ) {
                                // The session's sources, subtitles, episode list and current stream
                                // survive a configuration change but not process death, while
                                // selectedVideoUrl is saved and would otherwise resume a degraded,
                                // silently broken player session. Every legitimate NON-TRAILER playback
                                // start sets currentStream alongside selectedVideoUrl, so seeing one
                                // without the other only happens after process death for normal
                                // playback — send the user back to Details to re-resolve.
                                // Trailers are exempt: onTrailerClick never sets currentStream (it
                                // has no Stream object, just a resolved YouTube URL), so this guard
                                // would otherwise fire on every legitimate trailer play.
                                LaunchedEffect(Unit) {
                                    activeView = "details"
                                }
                            } else if (session.selectedVideoUrl.isBlank() && session.torrentProgress == null) {
                                // torrentProgress is lost with the session on process death even when
                                // a TorrentService download is still running
                                // (its foreground service survives independently). Stop it here so
                                // this recovery path doesn't silently abandon an orphaned download.
                                LaunchedEffect(Unit) {
                                    session.stopTorrent()
                                    activeView = "details"
                                }
                            } else {
                            val rememberedTrackSelection = remember(session.selectedPlaybackId) {
                                playbackTrackSelectionStore.getSelection(session.selectedPlaybackId)
                            }
                            val playerSources = remember(session.selectedPlayerSources) { session.selectedPlayerSources }
                            val playerSubtitles = remember(session.selectedPlayerSubtitles) {
                                session.selectedPlayerSubtitles.toPlayerSubtitleSources()
                            }

                            // Compute next episode
                            // Addons type shows as "series", "tv" or "anime"; any of them with an episode list has a next episode.
                            val isSeries = session.selectedPlaybackType.lowercase() in SERIES_PLAYBACK_TYPES
                            val autoplayNext = if (session.queuePlaybackActive) session.queueWholeShowActive
                                else currentProfile?.autoplayNextEpisode == true
                            // A queued single episode plays once: no next episode, so its end
                            // leaves the player and the queue moves on.
                            val queueSingleEpisode = session.queuePlaybackActive && !session.queueWholeShowActive
                            val nextEpisode = remember(session.selectedPlaybackId, selectedMovieId, session.currentEpisodeList, isSeries, queueSingleEpisode) {
                                if (isSeries && !queueSingleEpisode && session.currentEpisodeList.isNotEmpty()) {
                                    findNextEpisode(selectedMovieId, session.selectedPlaybackId, session.currentEpisodeList)
                                } else null
                            }
                            val nextEpisodeInfo = remember(nextEpisode) {
                                nextEpisode?.let { ep ->
                                    NextEpisodeInfo(
                                        title = episodeDisplayTitle(ep),
                                        thumbnail = ep.thumbnail,
                                        seasonNumber = ep.season,
                                        episodeNumber = ep.episode
                                    )
                                }
                            }

                            // Fetch skip intro/outro segments from IntroDB
                            val skipIntroEnabled = currentProfile?.skipIntro == true
                            val needIntroDB = skipIntroEnabled || autoplayNext
                            var skipSegmentInfo by remember { mutableStateOf<SkipSegmentInfo?>(null) }
                            LaunchedEffect(session.selectedPlaybackId, needIntroDB, skipIntroEnabled) {
                                skipSegmentInfo = null
                                if (!needIntroDB) return@LaunchedEffect
                                if (!isSeries || session.selectedPlaybackId.isBlank()) return@LaunchedEffect
                                val parts = session.selectedPlaybackId.split(":")
                                if (parts.size < 3) return@LaunchedEffect
                                val imdbId = parts.dropLast(2).joinToString(":")
                                val season = parts[parts.lastIndex - 1].toIntOrNull() ?: return@LaunchedEffect
                                val episode = parts.last().toIntOrNull() ?: return@LaunchedEffect
                                val response = introRepository.getSegments(imdbId, season, episode)
                                if (response != null) {
                                    skipSegmentInfo = SkipSegmentInfo(
                                        introStartMs = if (skipIntroEnabled) response.intro?.start_ms else null,
                                        introEndMs = if (skipIntroEnabled) response.intro?.end_ms else null,
                                        outroStartMs = response.outro?.start_ms,
                                        outroEndMs = response.outro?.end_ms
                                    )
                                }
                            }

                            lateinit var tryNextRankedSource: suspend () -> Unit
                            tryNextRankedSource = nextSource@{
                                val pending = session.pendingSourceSelection
                                val candidates = pending?.candidateStreams.orEmpty()
                                val current = session.currentStream
                                val currentIndex = candidates.indexOfFirst { candidate ->
                                    candidate === current || resolvePlayableSourceUrl(candidate) == session.selectedVideoUrl ||
                                        (current != null && candidate.infoHash != null && candidate.infoHash == current.infoHash && candidate.addonTransportUrl == current.addonTransportUrl)
                                }
                                // If currentIndex is -1 (stream not found), drop(0) would wrap back
                                // to the first candidate and loop forever. Guard: no stream found = no fallback.
                                val nextStream = if (currentIndex < 0) null
                                else candidates.drop(currentIndex + 1)
                                    .firstOrNull { !it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank() }
                                if (nextStream == null) {
                                    session.playbackStatus = null
                                    session.pendingSourceSelection = null
                                    activeView = "details"
                                    return@nextSource
                                }
                                val nextPosition = candidates.indexOf(nextStream) + 1
                                session.playbackStatus = "That source didn't play · trying source $nextPosition of ${candidates.size}"

                                val nextUrl = resolvePlayableSourceUrl(nextStream)
                                if (nextUrl == null) {
                                    session.playbackStatus = null
                                    activeView = "details"
                                    return@nextSource
                                }
                                session.currentStream = nextStream
                                session.pendingSourceSelection = PendingSourceSelection(
                                    playbackId = session.selectedPlaybackId,
                                    launchedStream = nextStream,
                                    candidateStreams = candidates
                                )
                                session.selectedPlayerSources = buildSourcePayload(candidates, nextStream)

                                val requestType = nextStream.addonRequestType
                                val requestId = nextStream.addonRequestId
                                if (!requestType.isNullOrBlank() && !requestId.isNullOrBlank()) {
                                    session.playbackStatus = "Finding subtitles for source $nextPosition"
                                    val addonSubs = subtitleRepository.getSubtitlesForStream(
                                        type = requestType,
                                        playbackId = requestId,
                                        stream = nextStream
                                    )
                                    session.selectedPlayerSubtitles = buildSubtitlePayload(
                                        nextStream,
                                        addonSubs
                                    )
                                }
                                session.playbackStatus = "Opening source $nextPosition of ${candidates.size}"

                                if (nextUrl.startsWith("magnet:")) {
                                    session.selectedVideoUrl = ""
                                    // TorrentProgress(sourceError=true) is the one signal that advances
                                    // the ranked list, so an error here only logs (and keeps the progress).
                                    session.startTorrent(
                                        nextUrl, nextStream.fileIdx ?: -1, nextStream.behaviorHints?.filename ?: "",
                                        errorContext = "Ranked fallback source error",
                                        clearProgressOnError = false
                                    )
                                } else {
                                    session.stopTorrent()
                                    session.selectedVideoUrl = nextUrl
                                }
                            }

                            PlayerScreen(
                                videoUrl = session.selectedVideoUrl,
                                trailerAudioUrl = session.selectedTrailerAudioUrl.takeIf { it.isNotBlank() },
                                title = session.selectedPlaybackTitle.ifBlank { selectedMovieTitle },
                                seriesTitle = selectedMovieTitle.takeIf {
                                    session.selectedPlaybackType.equals("series", ignoreCase = true)
                                },
                                logoUrl = selectedMovieLogo.takeIf { it.isNotBlank() },
                                poster = session.selectedPlaybackPoster,
                                movieId = session.selectedPlaybackId,
                                mediaType = session.selectedPlaybackType,
                                seriesId = selectedMovieId.takeIf {
                                    session.selectedPlaybackType.equals("series", ignoreCase = true) ||
                                        session.selectedPlaybackType.equals("tv", ignoreCase = true) ||
                                        session.selectedPlaybackType.equals("episode", ignoreCase = true)
                                },
                                sources = playerSources,
                                subtitles = playerSubtitles,
                                preferredAudioTrackId = rememberedTrackSelection?.audioTrackId,
                                preferredSubtitleTrackId = rememberedTrackSelection?.subtitleTrackId,
                                initialSubtitleDelayMs = rememberedTrackSelection?.subtitleDelayMs ?: 0L,
                                playbackSettings = PlaybackSettings(
                                    tunnelingEnabled = currentProfile?.tunnelingEnabled ?: false,
                                    mapDV7ToHevc = currentProfile?.mapDV7ToHevc ?: false,
                                    decoderPriority = currentProfile?.decoderPriority ?: 1,
                                    frameRateMatching = currentProfile?.frameRateMatching ?: false,
                                    autoplayNextEpisode = autoplayNext,
                                    autoSelectSource = currentProfile?.autoSelectSource ?: false,
                                    autoplayThresholdMode = currentProfile?.autoplayThresholdMode ?: "percentage",
                                    autoplayThresholdPercent = currentProfile?.autoplayThresholdPercent ?: 95,
                                    autoplayThresholdSeconds = currentProfile?.autoplayThresholdSeconds ?: 30,
                                    preferredAudioLanguage = currentProfile?.preferredAudioLanguage ?: "",
                                    preferredAudioLanguageSecondary = currentProfile?.preferredAudioLanguageSecondary ?: "",
                                    preferredSubtitleLanguage = currentProfile?.preferredSubtitleLanguage ?: "",
                                    preferredSubtitleLanguageSecondary = currentProfile?.preferredSubtitleLanguageSecondary ?: "",
                                    subtitleSize = currentProfile?.subtitleSize ?: 100,
                                    subtitleOffset = currentProfile?.subtitleOffset ?: 0,
                                    subtitleTextColor = currentProfile?.subtitleTextColor?.toInt() ?: 0xFFFFFFFF.toInt(),
                                    subtitleBackgroundColor = currentProfile?.subtitleBackgroundColor?.toInt() ?: 0x00000000,
                                    assRendererEnabled = currentProfile?.assRendererEnabled ?: false
                                ),
                                skipSegmentInfo = skipSegmentInfo,
                                nextEpisodeInfo = if (nextEpisode != null) nextEpisodeInfo else null,
                                onAutoplayNextEpisode = if (nextEpisode != null) {
                                    { playerCurrentSourceUrl ->
                                        // Read before the session end below consumes it.
                                        val watchedCandidates = session.pendingSourceSelection?.candidateStreams
                                        // Mark current episode as completed
                                        handlePlayerSessionEnd(
                                            sessionResult = PlayerSessionResult(
                                                positionMs = 0L,
                                                durationMs = null,
                                                isCompleted = true,
                                                selectedSourceUrl = playerCurrentSourceUrl ?: session.selectedVideoUrl,
                                                selectedAudioTrackId = null,
                                                selectedSubtitleTrackId = null
                                            ),
                                            selectedPlaybackId = session.selectedPlaybackId,
                                            playbackTrackSelectionStore = playbackTrackSelectionStore,
                                            sourceSelectionStore = sourceSelectionStore,
                                            pendingSourceSelection = session.pendingSourceSelection,
                                            onConsumePendingSelection = { session.pendingSourceSelection = null },
                                            onResumeHintResolved = { detailsResumePlaybackHint = it },
                                            rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                        )

                                        val nextPlaybackId = episodePlaybackId(selectedMovieId, nextEpisode)
                                        val nextStreamId = episodeStreamId(selectedMovieId, nextEpisode)
                                        val nextPlaybackTitle = episodeDisplayTitle(nextEpisode)

                                        val autoplay = autoplayNext
                                        val autoSelect = currentProfile?.autoSelectSource == true
                                        val willAutoResolve = autoplay || autoSelect
                                        session.episodeSwitchJob?.cancel()
                                        session.episodeSwitchGeneration += 1L
                                        session.isEpisodeSwitchLoading = true
                                        session.pendingEpisodeSwitch = if (!willAutoResolve) {
                                            PendingEpisodeSwitch(
                                                playbackId = nextPlaybackId,
                                                playbackTitle = nextPlaybackTitle,
                                                streamRequestId = nextStreamId,
                                                streams = null,
                                                addonSubs = emptyList(),
                                                playerCurrentSourceUrl = playerCurrentSourceUrl
                                            )
                                        } else {
                                            null
                                        }

                                        // Each step names itself on screen (the loading feed) and in the
                                        // stall report, so a hand-off that stops says where it stopped.
                                        val switchGeneration = session.episodeSwitchGeneration
                                        var switchStep = "finding sources"
                                        var fetchedStreams: List<com.hereliesaz.illumera.data.model.stremio.Stream>? = null
                                        if (willAutoResolve) session.playbackStatus = "Next episode · finding sources for $nextPlaybackTitle"
                                        fun showSourceList() {
                                            session.playbackStatus = null
                                            session.isEpisodeSwitchLoading = false
                                            session.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                playbackId = nextPlaybackId,
                                                playbackTitle = nextPlaybackTitle,
                                                streamRequestId = nextStreamId,
                                                streams = fetchedStreams.orEmpty(),
                                                addonSubs = emptyList(),
                                                playerCurrentSourceUrl = playerCurrentSourceUrl
                                            )
                                        }
                                        if (willAutoResolve) uiScope.launch {
                                            delay(AUTOPLAY_STALL_MS)
                                            val stillSwitching = session.episodeSwitchGeneration == switchGeneration &&
                                                session.selectedPlaybackId != nextPlaybackId && session.pendingEpisodeSwitch == null
                                            if (stillSwitching) {
                                                com.hereliesaz.illumera.crash.AppErrors.e(
                                                    "Autoplay",
                                                    "Next episode didn't start within ${AUTOPLAY_STALL_MS / 1000}s; stopped at: $switchStep"
                                                )
                                                session.episodeSwitchJob?.cancel()
                                                showSourceList()
                                            }
                                        }

                                        session.episodeSwitchJob = uiScope.launch {
                                          try {
                                            val streamsDeferred = async { requestOrFallback(emptyList()) { addonRepository.getStreams("series", nextStreamId) } }
                                            val subtitlesDeferred = async { requestOrFallback(emptyList()) { subtitleRepository.getSubtitles("series", nextStreamId) } }

                                            val rawStreams = streamsDeferred.await()
                                            val addonSubs = subtitlesDeferred.await()
                                            switchStep = "ranking ${rawStreams.size} sources"

                                            // Off the main thread: ranking a long list froze the UI on TV boxes.
                                            val streams = if (currentProfile?.sourceSortingEnabled == true) withContext(Dispatchers.Default) {
                                                val enabledQ = StreamSortingService.parseEnabledQualities(currentProfile?.sourceEnabledQualities ?: "4k,1080p,720p,unknown")
                                                val excludeP = StreamSortingService.parseExcludePhrases(currentProfile?.sourceExcludePhrases ?: "")
                                                val addonOrders = addonRepository.getAddonSortOrders()
                                                val excludedF = StreamSortingService.parseExcludedFormats(currentProfile?.sourceExcludedFormats ?: "")
                                                streamSortingService.sortAndFilter(rawStreams, enabledQ, excludeP, addonOrders, currentProfile?.sourceSortPrimary ?: "quality", currentProfile?.sourceMaxSizeGb ?: 0, excludedF, currentProfile?.sourceEpisodeTargetSizeMb ?: 750, currentProfile?.sourceMinimumSeeds ?: 5, currentProfile)
                                            } else rawStreams
                                            fetchedStreams = streams
                                            switchStep = "choosing from ${streams.size} sources"

                                            if (streams.isEmpty()) {
                                                session.playbackStatus = null
                                                session.isEpisodeSwitchLoading = false
                                                session.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streamRequestId = nextStreamId,
                                                    streams = emptyList(),
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Resolve the actual stream the user was watching (may differ from initial if they switched sources)
                                            val actualStream = if (playerCurrentSourceUrl != null) {
                                                watchedCandidates?.firstOrNull { candidate ->
                                                    resolvePlayableSourceUrl(candidate) == playerCurrentSourceUrl
                                                } ?: session.currentStream
                                            } else session.currentStream

                                            // Priority 1: Same bingeGroup + same addon as current stream (when autoplay or autoselect is on)
                                            val currentBingeGroup = actualStream?.behaviorHints?.bingeGroup
                                            val currentAddonUrl = actualStream?.addonTransportUrl
                                            val bingeMatch = if ((autoplay || autoSelect) && !currentBingeGroup.isNullOrBlank()) {
                                                streams.firstOrNull {
                                                    it.behaviorHints?.bingeGroup == currentBingeGroup &&
                                                        it.addonTransportUrl == currentAddonUrl &&
                                                        (!it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank())
                                                }
                                            } else null
                                            // Priority 2: Remembered source
                                            val rememberSource = currentProfile?.rememberSourceSelection ?: true
                                            val preferred = if (rememberSource) sourceSelectionStore.findPreferredStream(nextPlaybackId, streams) else null
                                            // Priority 3: First playable (autoplay or autoSelectSource)
                                            val streamToPlay = bingeMatch
                                                ?: preferred
                                                ?: if (autoplay || autoSelect) streams.firstOrNull { !it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank() } else null

                                            if (streamToPlay == null) {
                                                session.playbackStatus = null
                                                session.isEpisodeSwitchLoading = false
                                                session.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streamRequestId = nextStreamId,
                                                    streams = streams,
                                                    addonSubs = addonSubs,
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            val nextUrl = resolvePlayableSourceUrl(streamToPlay)
                                            if (nextUrl == null) {
                                                session.playbackStatus = null
                                                session.isEpisodeSwitchLoading = false
                                                session.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streamRequestId = nextStreamId,
                                                    streams = streams,
                                                    addonSubs = addonSubs,
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Auto-resolved: keep the guard active through subtitle refinement.
                                            session.pendingEpisodeSwitch = null
                                            switchStep = "finding subtitles (${if (nextUrl.startsWith("magnet:")) "torrent" else "direct link"})"
                                            session.playbackStatus = "Next episode · finding subtitles"

                                            val sourceAwareSubs = subtitleRepository.getSubtitlesForStream(
                                                type = "series",
                                                playbackId = nextStreamId,
                                                stream = streamToPlay,
                                                fallback = addonSubs
                                            )
                                            switchStep = "opening the source"
                                            session.playbackStatus = "Next episode · opening $nextPlaybackTitle"
                                            // Opening can stall after the switch itself is done: the new
                                            // episode never draws. Wait for its first frame, not the switch.
                                            session.awaitingFirstFrameId = nextPlaybackId
                                            val openedKind = if (nextUrl.startsWith("magnet:")) "torrent" else "direct link"
                                            val openedFrom = streamToPlay.addonDisplayName ?: "unknown addon"
                                            uiScope.launch {
                                                delay(AUTOPLAY_FIRST_FRAME_MS)
                                                if (session.episodeSwitchGeneration == switchGeneration &&
                                                    session.awaitingFirstFrameId == nextPlaybackId
                                                ) {
                                                    com.hereliesaz.illumera.crash.AppErrors.e(
                                                        "Autoplay",
                                                        "Next episode opened ($openedKind from $openedFrom) but drew no frame in " +
                                                            "${AUTOPLAY_FIRST_FRAME_MS / 1000}s; url set: ${session.selectedVideoUrl.isNotBlank()}, " +
                                                            "torrent: ${session.torrentProgress?.status ?: "none"}"
                                                    )
                                                    session.awaitingFirstFrameId = null
                                                    showSourceList()
                                                }
                                            }
                                            val subtitlePayload = buildSubtitlePayload(streamToPlay, sourceAwareSubs)
                                            val sourcePayload = buildSourcePayload(streams, streamToPlay)

                                            session.pendingSourceSelection = PendingSourceSelection(
                                                playbackId = nextPlaybackId,
                                                launchedStream = streamToPlay,
                                                candidateStreams = streams
                                            )
                                            session.currentStream = streamToPlay
                                            session.isEpisodeSwitchLoading = false

                                            if (nextUrl.startsWith("magnet:")) {
                                                // Drop the previous episode's URL so it doesn't replay under the new title.
                                                session.selectedVideoUrl = ""
                                                session.selectedPlaybackId = nextPlaybackId
                                                session.selectedPlaybackType = "series"
                                                session.selectedPlaybackTitle = nextPlaybackTitle
                                                session.selectedPlayerSubtitles = subtitlePayload
                                                session.selectedPlayerSources = sourcePayload
                                                session.startTorrent(nextUrl, streamToPlay.fileIdx ?: -1, streamToPlay.behaviorHints?.filename ?: "")
                                            } else {
                                                session.stopTorrent()
                                                session.selectedPlaybackId = nextPlaybackId
                                                session.selectedPlaybackType = "series"
                                                session.selectedPlaybackTitle = nextPlaybackTitle
                                                session.selectedPlayerSubtitles = subtitlePayload
                                                session.selectedPlayerSources = sourcePayload
                                                session.selectedVideoUrl = nextUrl
                                                // PlayerScreen will recompose due to movieId/videoUrl key change
                                            }
                                          } catch (cancelled: CancellationException) {
                                            throw cancelled
                                          } catch (e: Exception) {
                                            com.hereliesaz.illumera.crash.AppErrors.e("Autoplay", "Next-episode hand-off failed while $switchStep", e)
                                            showSourceList()
                                          }
                                        }
                                    }
                                } else null,
                                episodes = session.currentEpisodeList,
                                currentPlaybackId = session.selectedPlaybackId,
                                onEpisodeSelected = if (session.currentEpisodeList.isNotEmpty()) {
                                    episodeSelect@{ episode, playerCurrentSourceUrl ->
                                        // Guard against a double-tap/rapid re-selection firing a second
                                        // independent switch while one is already resolving — whichever
                                        // network call happened to finish last would otherwise win,
                                        // regardless of which episode the user actually intended last.
                                        if (session.isEpisodeSwitchLoading) return@episodeSelect
                                        val epPlaybackId = episodePlaybackId(selectedMovieId, episode)
                                        val epStreamId = episodeStreamId(selectedMovieId, episode)
                                        val epTitle = episodeDisplayTitle(episode)

                                        // Picking an episode by hand is the viewer's choice, not the queue's.
                                        session.queuePlaybackActive = false
                                        session.queueWholeShowActive = false
                                        val autoplay = currentProfile?.autoplayNextEpisode == true
                                        val autoSelect = currentProfile?.autoSelectSource == true
                                        val willAutoResolve = autoplay || autoSelect
                                        session.episodeSwitchJob?.cancel()
                                        session.episodeSwitchGeneration += 1L
                                        session.isEpisodeSwitchLoading = true
                                        session.pendingEpisodeSwitch = if (!willAutoResolve) {
                                            PendingEpisodeSwitch(
                                                playbackId = epPlaybackId,
                                                playbackTitle = epTitle,
                                                streamRequestId = epStreamId,
                                                streams = null,
                                                addonSubs = emptyList(),
                                                playerCurrentSourceUrl = playerCurrentSourceUrl
                                            )
                                        } else {
                                            null
                                        }

                                        session.episodeSwitchJob = uiScope.launch {
                                            val streamsDeferred = async { requestOrFallback(emptyList()) { addonRepository.getStreams("series", epStreamId) } }
                                            val subtitlesDeferred = async { requestOrFallback(emptyList()) { subtitleRepository.getSubtitles("series", epStreamId) } }

                                            val rawStreams2 = streamsDeferred.await()
                                            val addonSubs = subtitlesDeferred.await()

                                            val streams = if (currentProfile?.sourceSortingEnabled == true) {
                                                val enabledQ = StreamSortingService.parseEnabledQualities(currentProfile?.sourceEnabledQualities ?: "4k,1080p,720p,unknown")
                                                val excludeP = StreamSortingService.parseExcludePhrases(currentProfile?.sourceExcludePhrases ?: "")
                                                val addonOrders = addonRepository.getAddonSortOrders()
                                                val excludedF = StreamSortingService.parseExcludedFormats(currentProfile?.sourceExcludedFormats ?: "")
                                                streamSortingService.sortAndFilter(rawStreams2, enabledQ, excludeP, addonOrders, currentProfile?.sourceSortPrimary ?: "quality", currentProfile?.sourceMaxSizeGb ?: 0, excludedF, currentProfile?.sourceEpisodeTargetSizeMb ?: 750, currentProfile?.sourceMinimumSeeds ?: 5, currentProfile)
                                            } else rawStreams2

                                            if (streams.isEmpty()) {
                                                session.isEpisodeSwitchLoading = false
                                                session.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streamRequestId = epStreamId,
                                                    streams = emptyList(),
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Resolve the actual stream the user was watching
                                            val actualStream = if (playerCurrentSourceUrl != null) {
                                                session.pendingSourceSelection?.candidateStreams?.firstOrNull { candidate ->
                                                    resolvePlayableSourceUrl(candidate) == playerCurrentSourceUrl
                                                } ?: session.currentStream
                                            } else session.currentStream

                                            // Priority 1: Same bingeGroup + same addon as current stream (when autoplay or autoselect is on)
                                            val currentBingeGroup = actualStream?.behaviorHints?.bingeGroup
                                            val currentAddonUrl = actualStream?.addonTransportUrl
                                            val bingeMatch = if ((autoplay || autoSelect) && !currentBingeGroup.isNullOrBlank()) {
                                                streams.firstOrNull {
                                                    it.behaviorHints?.bingeGroup == currentBingeGroup &&
                                                        it.addonTransportUrl == currentAddonUrl &&
                                                        (!it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank())
                                                }
                                            } else null

                                            // Priority 2: Auto-select first available (only when autoSelectSource is on)
                                            val streamToPlay = bingeMatch
                                                ?: if (autoSelect) streams.firstOrNull { !it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank() } else null

                                            if (streamToPlay == null) {
                                                session.isEpisodeSwitchLoading = false
                                                session.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streamRequestId = epStreamId,
                                                    streams = streams,
                                                    addonSubs = addonSubs,
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            val epUrl = resolvePlayableSourceUrl(streamToPlay)
                                            if (epUrl == null) {
                                                session.isEpisodeSwitchLoading = false
                                                session.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streamRequestId = epStreamId,
                                                    streams = streams,
                                                    addonSubs = addonSubs,
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Auto-resolved: keep the guard active through subtitle refinement.
                                            session.pendingEpisodeSwitch = null
                                            handlePlayerSessionEnd(
                                                sessionResult = PlayerSessionResult(
                                                    positionMs = 0L,
                                                    durationMs = null,
                                                    isCompleted = false,
                                                    selectedSourceUrl = playerCurrentSourceUrl ?: session.selectedVideoUrl,
                                                    selectedAudioTrackId = null,
                                                    selectedSubtitleTrackId = null
                                                ),
                                                selectedPlaybackId = session.selectedPlaybackId,
                                                playbackTrackSelectionStore = playbackTrackSelectionStore,
                                                sourceSelectionStore = sourceSelectionStore,
                                                pendingSourceSelection = session.pendingSourceSelection,
                                                onConsumePendingSelection = { session.pendingSourceSelection = null },
                                                onResumeHintResolved = { detailsResumePlaybackHint = it },
                                                rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                            )

                                            val sourceAwareSubs = subtitleRepository.getSubtitlesForStream(
                                                type = "series",
                                                playbackId = epStreamId,
                                                stream = streamToPlay,
                                                fallback = addonSubs
                                            )
                                            val subtitlePayload = buildSubtitlePayload(streamToPlay, sourceAwareSubs)
                                            val sourcePayload = buildSourcePayload(streams, streamToPlay)

                                            session.pendingSourceSelection = PendingSourceSelection(
                                                playbackId = epPlaybackId,
                                                launchedStream = streamToPlay,
                                                candidateStreams = streams
                                            )
                                            session.currentStream = streamToPlay
                                            session.isEpisodeSwitchLoading = false

                                            if (epUrl.startsWith("magnet:")) {
                                                // Drop the previous episode's URL so it doesn't replay under the new title.
                                                session.selectedVideoUrl = ""
                                                session.selectedPlaybackId = epPlaybackId
                                                session.selectedPlaybackType = "series"
                                                session.selectedPlaybackTitle = epTitle
                                                session.selectedPlayerSubtitles = subtitlePayload
                                                session.selectedPlayerSources = sourcePayload
                                                session.startTorrent(epUrl, streamToPlay.fileIdx ?: -1, streamToPlay.behaviorHints?.filename ?: "")
                                            } else {
                                                session.stopTorrent()
                                                session.selectedPlaybackId = epPlaybackId
                                                session.selectedPlaybackType = "series"
                                                session.selectedPlaybackTitle = epTitle
                                                session.selectedPlayerSubtitles = subtitlePayload
                                                session.selectedPlayerSources = sourcePayload
                                                session.selectedVideoUrl = epUrl
                                            }
                                        }
                                    }
                                } else null,
                                episodeSwitchSources = session.pendingEpisodeSwitch?.let { pending ->
                                    pending.streams
                                        ?.mapNotNull(::buildPlayerSourceOption)
                                        ?.distinctBy { it.id }
                                },
                                isEpisodeSwitchLoading = session.isEpisodeSwitchLoading,
                                episodeSwitchTitle = session.pendingEpisodeSwitch?.playbackTitle,
                                onEpisodeSwitchSourceSelected = session.pendingEpisodeSwitch?.let { pending ->
                                    { sourceUrl: String ->
                                        val streamToPlay = pending.streams?.firstOrNull { resolvePlayableSourceUrl(it) == sourceUrl }
                                        if (streamToPlay == null) {
                                            session.pendingEpisodeSwitch = null
                                            return@let
                                        }

                                        session.episodeSwitchJob?.cancel()
                                        session.episodeSwitchGeneration += 1L
                                        session.pendingEpisodeSwitch = null
                                        session.isEpisodeSwitchLoading = true
                                        session.episodeSwitchJob = uiScope.launch {
                                            val sourceAwareSubs = subtitleRepository.getSubtitlesForStream(
                                                type = "series",
                                                playbackId = pending.streamRequestId,
                                                stream = streamToPlay,
                                                fallback = pending.addonSubs
                                            )

                                            // Now save progress for current episode
                                            handlePlayerSessionEnd(
                                                sessionResult = PlayerSessionResult(
                                                    positionMs = 0L,
                                                    durationMs = null,
                                                    isCompleted = false,
                                                    selectedSourceUrl = pending.playerCurrentSourceUrl ?: session.selectedVideoUrl,
                                                    selectedAudioTrackId = null,
                                                    selectedSubtitleTrackId = null
                                                ),
                                                selectedPlaybackId = session.selectedPlaybackId,
                                                playbackTrackSelectionStore = playbackTrackSelectionStore,
                                                sourceSelectionStore = sourceSelectionStore,
                                                pendingSourceSelection = session.pendingSourceSelection,
                                                onConsumePendingSelection = { session.pendingSourceSelection = null },
                                                onResumeHintResolved = { detailsResumePlaybackHint = it },
                                                rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                            )

                                            val subtitlePayload = buildSubtitlePayload(streamToPlay, sourceAwareSubs)
                                            val sourcePayload = buildSourcePayload(pending.streams, streamToPlay)

                                            session.pendingSourceSelection = PendingSourceSelection(
                                                playbackId = pending.playbackId,
                                                launchedStream = streamToPlay,
                                                candidateStreams = pending.streams
                                            )
                                            session.currentStream = streamToPlay
                                            session.pendingEpisodeSwitch = null
                                            session.isEpisodeSwitchLoading = false

                                            if (sourceUrl.startsWith("magnet:")) {
                                                // Drop the previous episode's URL so it doesn't replay under the new title.
                                                session.selectedVideoUrl = ""
                                                session.selectedPlaybackId = pending.playbackId
                                                session.selectedPlaybackType = "series"
                                                session.selectedPlaybackTitle = pending.playbackTitle
                                                session.selectedPlayerSubtitles = subtitlePayload
                                                session.selectedPlayerSources = sourcePayload
                                                session.startTorrent(sourceUrl, streamToPlay.fileIdx ?: -1, streamToPlay.behaviorHints?.filename ?: "")
                                            } else {
                                                session.stopTorrent()
                                                session.selectedPlaybackId = pending.playbackId
                                                session.selectedPlaybackType = "series"
                                                session.selectedPlaybackTitle = pending.playbackTitle
                                                session.selectedPlayerSubtitles = subtitlePayload
                                                session.selectedPlayerSources = sourcePayload
                                                session.selectedVideoUrl = sourceUrl
                                            }
                                        }
                                    }
                                },
                                onEpisodeSwitchDismissed = {
                                    session.episodeSwitchGeneration += 1L
                                    session.episodeSwitchJob?.cancel()
                                    session.episodeSwitchJob = null
                                    session.pendingEpisodeSwitch = null
                                    session.isEpisodeSwitchLoading = false
                                },
                                onResolveSourceSubtitles = { source ->
                                    val stream = source.addonStream
                                    val requestType = stream?.addonRequestType
                                    val requestId = stream?.addonRequestId
                                    if (stream == null || requestType.isNullOrBlank() || requestId.isNullOrBlank()) {
                                        playerSubtitles
                                    } else {
                                        val addonSubs = subtitleRepository.getSubtitlesForStream(
                                            type = requestType,
                                            playbackId = requestId,
                                            stream = stream
                                        )
                                        buildSubtitlePayload(stream, addonSubs).toPlayerSubtitleSources()
                                    }
                                },
                                onMagnetSourceSelected = { magnetUrl, sourceFileIdx, sourceFileName, onReady, onError ->
                                    session.pendingSourceSelection?.candidateStreams
                                        ?.firstOrNull { resolvePlayableSourceUrl(it) == magnetUrl }
                                        ?.let { session.currentStream = it }
                                    session.startTorrent(
                                        magnetUrl, sourceFileIdx, sourceFileName,
                                        errorContext = "Source switch error",
                                        onError = onError,
                                        onReady = onReady
                                    )
                                },
                                torrentProgress = session.torrentProgress,
                                playbackStatus = session.playbackStatus,
                                onFirstFrameRendered = {
                                    session.playbackStatus = null
                                    session.awaitingFirstFrameId = null
                                },
                                autoFallbackEnabled = currentProfile?.autoSelectSource == true && currentProfile?.sourceAutoFallback != false,
                                onSuspectSource = { status ->
                                    uiScope.launch {
                                        val currentStream = session.currentStream
                                        if (status == PlaybackDurationStatus.DEBRID_DOWNLOADING && currentStream != null) {
                                            val maxWait = currentProfile?.sourceDebridMaxWaitSeconds ?: 120
                                            session.playbackStatus = "Your debrid service is still downloading this · waiting up to ${maxWait}s"
                                            val readyUrl = debridManager.awaitPlayableSource(
                                                infoHash = currentStream.infoHash,
                                                fileName = currentStream.behaviorHints?.filename,
                                                maxWaitSeconds = maxWait
                                            )
                                            if (!readyUrl.isNullOrBlank()) {
                                                session.playbackStatus = "Download finished · opening the video"
                                                session.selectedVideoUrl = readyUrl
                                                session.currentStream = currentStream.copy(url = readyUrl)
                                                return@launch
                                            }
                                        }
                                        tryNextRankedSource()
                                    }
                                },
                                onBack = { sessionResult ->
                                    session.torrentProgress = null
                                    session.playbackStatus = null
                                    handlePlayerSessionEnd(
                                        sessionResult = sessionResult,
                                        selectedPlaybackId = session.selectedPlaybackId,
                                        playbackTrackSelectionStore = playbackTrackSelectionStore,
                                        sourceSelectionStore = sourceSelectionStore,
                                        pendingSourceSelection = session.pendingSourceSelection,
                                        onConsumePendingSelection = { session.pendingSourceSelection = null },
                                        onResumeHintResolved = { detailsResumePlaybackHint = it },
                                        rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                    )
                                    session.stopTorrent()
                                    if (session.selectedPlaybackId.startsWith("trailer_")) {
                                        trailerReturnToken++
                                        activeView = "details"
                                    } else if (sessionResult.isCompleted && queueManager.state.value.preferences.enabled) {
                                        val next = queueManager.advanceAfterPlayback(session.selectedPlaybackId)
                                        if (next != null) {
                                            selectedMovieId = next.seriesId ?: next.id
                                            selectedMovieType = if (next.type == "movie") "movie" else "series"
                                            selectedMovieTitle = next.title
                                            selectedMoviePoster = next.poster ?: ""
                                            selectedMovieLogo = ""
                                            selectedAddonBaseUrl = null
                                            session.selectedPlaybackId = next.id
                                            session.selectedPlaybackType = selectedMovieType
                                            session.selectedPlaybackTitle = next.title
                                            session.selectedPlaybackPoster = next.poster ?: ""
                                            queueAutoPlayId = next.id
                                            session.queueWholeShowActive = next.wholeShow
                                            session.queueStartPending = true
                                            previousView = "menu"
                                            activeView = "details"
                                            uiScope.launch { queueManager.ensureSuggestions() }
                                        } else {
                                            session.queueWholeShowActive = false
                                            session.queuePlaybackActive = false
                                            activeView = "details"
                                        }
                                    } else {
                                        activeView = "details"
                                    }
                                }
                            )
                            }
                        }
                    // ViewSwitcher end
                    }

                    // Player choice dialog (shown when playerPreference == "ask")
                    if (session.showPlayerChoiceDialog && session.selectedVideoUrl.isNotBlank()) {
                        PlayerChoiceDialog(
                            onInternal = {
                                session.showPlayerChoiceDialog = false
                                activeView = "player"
                            },
                            onExternal = {
                                session.showPlayerChoiceDialog = false
                                launchExternalPlayer(this@MainActivity, session.selectedVideoUrl)
                            },
                            onDismiss = {
                                session.showPlayerChoiceDialog = false
                            }
                        )
                    }

                    if (showTrailerError) {
                        Dialog(onDismissRequest = { showTrailerError = false }) {
                            Box(
                                modifier = Modifier
                                    .width(rememberDialogWidth(380))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                                    .padding(24.dp)
                            ) {
                                androidx.compose.foundation.layout.Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Trailer Unavailable",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        VoidButton(
                                            text = "Dismiss",
                                            onClick = { showTrailerError = false },
                                            isPrimary = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Update dialogs (auto-shown after splash)
                    if (_splashFinished.value && !updateDismissed && appUpdateManager.isPopupEnabled) {
                        when (val state = updateState) {
                            is UpdateState.UpdateAvailable -> {
                                UpdateAvailableDialog(
                                    info = state.info,
                                    onUpdate = {
                                        updateScope.launch { appUpdateManager.downloadAndInstall(state.info.apkUrl) }
                                    },
                                    onDismiss = { updateDismissed = true },
                                    onDontShowAgain = {
                                        appUpdateManager.setPopupEnabled(false)
                                        updateDismissed = true
                                    }
                                )
                            }
                            is UpdateState.Downloading -> {
                                UpdateDownloadingDialog(
                                    progress = state.progress,
                                    downloadedMb = state.downloadedMb,
                                    totalMb = state.totalMb
                                )
                            }
                            is UpdateState.Error -> {
                                UpdateErrorDialog(
                                    message = state.message,
                                    onRetry = {
                                        appUpdateManager.resetState()
                                        updateScope.launch { appUpdateManager.checkForUpdate() }
                                    },
                                    onDismiss = {
                                        appUpdateManager.resetState()
                                        updateDismissed = true
                                    }
                                )
                            }
                            is UpdateState.ReadyToInstall -> {
                                UpdateReadyToInstallDialog(
                                    onInstall = { appUpdateManager.retryInstall() },
                                    onDismiss = {
                                        appUpdateManager.resetState()
                                        updateDismissed = true
                                    }
                                )
                            }
                            else -> {}
                        }
                    }

                }
                }
            }
        }

        // Attach native splash overlay on top of Compose content — renders immediately
        if (showSplash) {
            attachSplashOverlay()
            // Async: dismiss splash immediately if the active profile has it disabled
            lifecycleScope.launch {
                val splashEnabledInProfile = profileConfigurationManager.getLastActiveProfileId()?.let { id ->
                    withContext(Dispatchers.IO) { addonDao.getProfileById(id) }
                }?.splashEnabled ?: true
                if (!splashEnabledInProfile) {
                    dismissSplash()
                }
            }
        }
    }

    companion object {
        private const val SPLASH_PAUSE_MS = 1200
        private const val KEY_SPLASH_SHOWN = "splash_shown"
    }
}
