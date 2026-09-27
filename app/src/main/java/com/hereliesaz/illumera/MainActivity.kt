package com.hereliesaz.illumera

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hereliesaz.illumera.data.update.AppUpdateManager
import com.hereliesaz.illumera.data.update.UpdateInfo
import com.hereliesaz.illumera.data.update.UpdateState
import com.hereliesaz.illumera.data.player.PlaybackTrackSelectionStore
import com.hereliesaz.illumera.ui.util.rememberDialogWidth
import com.hereliesaz.illumera.ui.MainViewModel
import com.hereliesaz.illumera.ui.components.LumeraBackground
import com.hereliesaz.illumera.ui.details.DetailsScreen
import com.hereliesaz.illumera.ui.home.GridViewScreen
import com.hereliesaz.illumera.ui.home.HomeScreen
import com.hereliesaz.illumera.ui.watchlist.WatchlistScreen
import com.hereliesaz.illumera.data.queue.QueueManager
import com.hereliesaz.illumera.ui.home.HomeViewModel
import com.hereliesaz.illumera.data.model.stremio.MetaItem
import com.hereliesaz.illumera.data.model.stremio.Stream
import com.hereliesaz.illumera.data.model.stremio.MetaVideo
import com.hereliesaz.illumera.data.repository.IntroRepository
import com.hereliesaz.illumera.domain.AddonSubtitle
import com.hereliesaz.illumera.domain.DashboardTab
import com.hereliesaz.illumera.domain.episodeDisplayTitle
import com.hereliesaz.illumera.ui.navigation.AppBackStackConfiguration
import com.hereliesaz.illumera.ui.navigation.BackStackOps
import com.hereliesaz.illumera.ui.navigation.DetailsKey
import com.hereliesaz.illumera.ui.navigation.GridKey
import com.hereliesaz.illumera.ui.navigation.MainKey
import com.hereliesaz.illumera.ui.navigation.NavDestination
import com.hereliesaz.illumera.ui.navigation.PlayerKey
import com.hereliesaz.illumera.ui.navigation.NavDrawer
import com.hereliesaz.illumera.ui.navigation.TopNavigationBar
import com.hereliesaz.illumera.ui.player.PlayerScreen
import com.hereliesaz.illumera.ui.playback.PlaybackNav
import com.hereliesaz.illumera.ui.playback.PlaybackSessionViewModel
import com.hereliesaz.illumera.ui.playback.buildPlayerSourceOption
import com.hereliesaz.illumera.ui.playback.toPlayerSubtitleSources
import com.hereliesaz.illumera.ui.player.base.NextEpisodeInfo
import com.hereliesaz.illumera.ui.player.base.PlaybackSettings
import com.hereliesaz.illumera.ui.player.base.SkipSegmentInfo
import com.hereliesaz.illumera.ui.profiles.ProfileScreen
import com.hereliesaz.illumera.ui.profiles.ProfileViewModel
import com.hereliesaz.illumera.ui.search.SearchScreen
import com.hereliesaz.illumera.ui.settings.SettingsScreen
import com.hereliesaz.illumera.ui.addons.VoidButton
import com.hereliesaz.illumera.ui.theme.DefaultThemes
import com.hereliesaz.illumera.ui.theme.LocalRoundCorners
import com.hereliesaz.illumera.ui.theme.LocalHubRoundCorners
import com.hereliesaz.illumera.ui.theme.LumeraTheme
import com.hereliesaz.illumera.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.hereliesaz.illumera.data.local.AddonDao
import com.hereliesaz.illumera.data.profile.ProfileConfigurationManager
import kotlinx.coroutines.withContext

import javax.inject.Inject

private const val DOUBLE_BACK_EXIT_WINDOW_MS = 400L

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
    lateinit var playbackTrackSelectionStore: PlaybackTrackSelectionStore
    @Inject
    lateinit var introRepository: IntroRepository
    @Inject
    lateinit var profileConfigurationManager: ProfileConfigurationManager
    @Inject
    lateinit var appUpdateManager: AppUpdateManager
    @Inject
    lateinit var addonDao: AddonDao
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
            // The root back stack (Main, Grid, Details, Player), saved across process death.
            val backStack = rememberNavBackStack(AppBackStackConfiguration, MainKey)
            val topKey = backStack.lastOrNull()
            var selectedMovieId by rememberSaveable { mutableStateOf("") }
            var selectedMovieType by rememberSaveable { mutableStateOf("movie") }
            // Playback state lives in the activity-scoped session, so it outlives a
            // configuration change and TorrentService's callbacks always reach it.
            val session = hiltViewModel<PlaybackSessionViewModel>(viewModelStoreOwner = this@MainActivity)
            var selectedMovieTitle by rememberSaveable { mutableStateOf("") }
            var selectedMoviePoster by rememberSaveable { mutableStateOf("") }
            var selectedMovieLogo by rememberSaveable { mutableStateOf("") }
            var selectedAddonBaseUrl by rememberSaveable { mutableStateOf<String?>(null) }
            var queueAutoPlayId by rememberSaveable { mutableStateOf<String?>(null) }
            LaunchedEffect(topKey) {
                if (topKey !is DetailsKey && topKey != PlayerKey) session.queueStartPending = false
            }

            // Debrid library items (Watchlist's cloud storage section) are pre-resolved
            // file URLs with no addon Stream/catalog metadata behind them — this plays
            // one through illumera's own player instead of the hardcoded external
            // ACTION_VIEW intent WatchlistScreen previously used, respecting the same
            // playerPreference (internal/ask/external) as every other playback path.
            val onPlayResolvedStream: (id: String, url: String, title: String) -> Unit = { id, url, title ->
                session.startResolved(id, url, title, currentProfile?.playerPreference)
            }

            // The session asks for navigation and the back stack moves. Collected on
            // Main.immediate so a stack change lands in the same frame as the session change
            // that asked for it.
            LaunchedEffect(session) {
                withContext(Dispatchers.Main.immediate) {
                    session.navEvents.collect { event ->
                        when (event) {
                            PlaybackNav.OpenPlayer -> BackStackOps.openPlayer(backStack)
                            // Pops only the player: Back lands on Details, or on the menu area
                            // for debrid library playback started from Watchlist.
                            is PlaybackNav.ReturnFromPlayer -> BackStackOps.returnFromPlayer(backStack)
                            is PlaybackNav.OpenDetails -> {
                                selectedMovieId = event.movieId
                                selectedMovieType = event.movieType
                                selectedMovieTitle = event.title
                                selectedMoviePoster = event.poster
                                selectedMovieLogo = ""
                                selectedAddonBaseUrl = null
                                queueAutoPlayId = event.queueAutoPlayId
                                // On top of the stack: Back returns to the previous show's page,
                                // then to the menu area as the viewer left it.
                                BackStackOps.queueAdvance(
                                    backStack,
                                    type = event.movieType,
                                    id = event.movieId,
                                    title = event.title,
                                    poster = event.poster
                                )
                            }
                            is PlaybackNav.LaunchExternal -> launchExternalPlayer(this@MainActivity, event.url)
                            PlaybackNav.ShowPlayerChoice -> session.showPlayerChoiceDialog = true
                        }
                    }
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
                        
                        // Grid view state. Title and config id travel in GridKey; the items
                        // stay in memory here (MetaItem isn't saveable).
                        var gridViewItems by remember { mutableStateOf<List<MetaItem>>(emptyList()) }
                        val gridRestoreState = remember { GridRestoreState() }

                        // Search focus restoration
                        val searchMoviesViewMoreRequester = remember { FocusRequester() }
                        val searchSeriesViewMoreRequester = remember { FocusRequester() }
                        val searchResultsRequester = remember { FocusRequester() }
                        var searchFocusTarget by remember { mutableStateOf<String?>(null) }
                        var searchLastFocusedId by remember { mutableStateOf<String?>(null) }


                        // Focus Traffic Control
                        val drawerRequesters = remember { NavDestination.values().associateWith { FocusRequester() } }
                        val homeEntryRequester = remember { FocusRequester() }
                        val searchEntryRequester = remember { FocusRequester() }
                        val settingsEntryRequester = remember { FocusRequester() }
                        val watchlistEntryRequester = remember { FocusRequester() }

                        // STATE CHANGE TRIGGER:
                        val isMainTop = topKey == MainKey
                        LaunchedEffect(currentNav, isMainTop, settingsResetKey) {
                            if (!isMainTop) return@LaunchedEffect
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
                            if (backStack.lastOrNull() == MainKey && currentNav == NavDestination.Settings) {
                                delay(450) // Wait for Crossfade (400ms) + buffer
                                // Re-check after the delay: the user may have navigated away from
                                // Settings while this was pending, unmounting the FocusRequester's
                                // only attachment point and making requestFocus() throw.
                                // Skip if Settings already put focus back on the option that was
                                // changed (see PersonalizationSettings' Menu Position).
                                if (backStack.lastOrNull() == MainKey && currentNav == NavDestination.Settings && !settingsContentFocused) {
                                    try {
                                        settingsEntryRequester.requestFocus()
                                    } catch (_: IllegalStateException) {
                                    }
                                }
                            }
                        }


                        // Opens a title's Details from any main screen or the grid.
                        val openDetailsFor: (MetaItem) -> Unit = { movie ->
                            selectedMovieId = movie.id
                            selectedMovieType = movie.type
                            selectedMovieTitle = movie.name
                            selectedMoviePoster = movie.poster ?: ""
                            selectedMovieLogo = movie.logo ?: ""
                            selectedAddonBaseUrl = movie.addonBaseUrl
                            session.detailsResumePlaybackHint = null
                            session.selectedPlaybackId = movie.id
                            session.selectedPlaybackType = movie.type
                            session.selectedPlaybackTitle = movie.name
                            session.selectedPlaybackPoster = movie.poster ?: ""
                            BackStackOps.openDetails(
                                backStack,
                                type = movie.type,
                                id = movie.id,
                                addon = movie.addonBaseUrl,
                                title = movie.name,
                                poster = movie.poster ?: "",
                                logo = movie.logo ?: ""
                            )
                        }

                        // Back pops one entry. With the menu area alone on the stack Back is
                        // not NavDisplay's: the screens, the menu and double-back-exit keep it.
                        // No transitions, so a leaving player disposes (saving progress and
                        // releasing the decoder) at once, as before.
                        NavDisplay(
                            backStack = backStack,
                            onBack = { BackStackOps.pop(backStack) },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator()
                            ),
                            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                            popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                            predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                            entryProvider = entryProvider {
                            entry<MainKey> {
                            // Main screens keep the activity's ViewModels, shared with the grid.
                            CompositionLocalProvider(LocalViewModelStoreOwner provides this@MainActivity) {
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

                                            BackStackOps.resetToMain(backStack)
                                            themeManager.resetTheme()
                                            mainViewModel.logout()
                                        },
                                        onExit = { finishAffinity() },
                                        content = {
                                            when (currentNav) {
                                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> {
                                                    val vm = hiltViewModel<HomeViewModel>(viewModelStoreOwner = this@MainActivity)
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
                                                                openDetailsFor(movie)
                                                            },
                                                            onViewMore = { title, items, configId ->
                                                                gridViewItems = items
                                                                BackStackOps.openGrid(backStack, title, configId)
                                                            }
                                                        )
                                                    }
                                                }
                                                NavDestination.Search -> {
                                                    val searchHomeVm = hiltViewModel<HomeViewModel>(viewModelStoreOwner = this@MainActivity)
                                                    SearchScreen(
                                                        currentProfile = currentProfile,
                                                        watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            searchFocusTarget = "poster"
                                                            openDetailsFor(movie)
                                                        },
                                                        onViewMore = { title, items ->
                                                            searchFocusTarget = if (title == "Movies") "movies" else "series"
                                                            gridViewItems = items
                                                            BackStackOps.openGrid(backStack, title, "")
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
                                                        BackStackOps.resetToMain(backStack)
                                                        themeManager.resetTheme()
                                                        mainViewModel.logout()
                                                    }
                                                }
                                                NavDestination.Watchlist -> {
                                                    val watchlistHomeVm = hiltViewModel<HomeViewModel>(viewModelStoreOwner = this@MainActivity)
                                                    WatchlistScreen(
                                                        currentProfile = currentProfile,
                                                        entryRequester = watchlistEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Watchlist]!!,
                                                        watchedIds = watchlistHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            openDetailsFor(movie)
                                                        },
                                                        onPlayResolvedStream = onPlayResolvedStream
                                                    )
                                                }
                                                NavDestination.Settings -> {
                                                    val homeVm = hiltViewModel<HomeViewModel>(viewModelStoreOwner = this@MainActivity)
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
                                                    val vm = hiltViewModel<HomeViewModel>(viewModelStoreOwner = this@MainActivity)
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
                                                                openDetailsFor(movie)
                                                            },
                                                            onViewMore = { title, items, configId ->
                                                                gridViewItems = items
                                                                BackStackOps.openGrid(backStack, title, configId)
                                                            }
                                                        )
                                                    }
                                                }
                                                NavDestination.Search -> {
                                                    val searchHomeVm = hiltViewModel<HomeViewModel>(viewModelStoreOwner = this@MainActivity)
                                                    SearchScreen(
                                                        currentProfile = currentProfile,
                                                        watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            searchFocusTarget = "poster"
                                                            openDetailsFor(movie)
                                                        },
                                                        onViewMore = { title, items ->
                                                            searchFocusTarget = if (title == "Movies") "movies" else "series"
                                                            gridViewItems = items
                                                            BackStackOps.openGrid(backStack, title, "")
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
                                                        BackStackOps.resetToMain(backStack)
                                                        themeManager.resetTheme()
                                                        mainViewModel.logout()
                                                    }
                                                }
                                                NavDestination.Watchlist -> {
                                                    val watchlistHomeVm = hiltViewModel<HomeViewModel>(viewModelStoreOwner = this@MainActivity)
                                                    WatchlistScreen(
                                                        currentProfile = currentProfile,
                                                        entryRequester = watchlistEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Watchlist]!!,
                                                        watchedIds = watchlistHomeVm.state.collectAsState().value.watchedIds,
                                                        onMovieClick = { movie ->
                                                            openDetailsFor(movie)
                                                        },
                                                        onPlayResolvedStream = onPlayResolvedStream
                                                    )
                                                }
                                                NavDestination.Settings -> {
                                                    val homeVm = hiltViewModel<HomeViewModel>(viewModelStoreOwner = this@MainActivity)
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
                            }
                            }
                            entry<GridKey> { gridKey ->
                            CompositionLocalProvider(LocalViewModelStoreOwner provides this@MainActivity) {
                            val gridViewTitle = gridKey.title
                            val gridViewConfigId = gridKey.configId
                            // gridViewItems isn't saveable (MetaItem isn't Parcelable), so a
                            // process-death recreation restores the GridKey but not the items,
                            // leaving a header with nothing under it. Return to the screen before
                            // the grid rather than show that broken empty screen.
                            LaunchedEffect(Unit) {
                                BackStackOps.dropEmptyGrid(
                                    backStack,
                                    hasItems = gridViewTitle.isEmpty() || gridViewItems.isNotEmpty()
                                )
                            }
                            val gridVm = hiltViewModel<HomeViewModel>(viewModelStoreOwner = this@MainActivity)
                            val gridNavPosition = currentProfile?.navPosition ?: "left"
                            val gridEntryRequester = remember { FocusRequester() }
                            val handleGridNavigate: (NavDestination) -> Unit = { destination ->
                                if (destination == NavDestination.Exit) {
                                    finishAffinity()
                                } else {
                                    currentNav = destination
                                    BackStackOps.resetToMain(backStack)
                                }
                            }
                            val gridContent: @Composable () -> Unit = {
                                GridViewScreen(
                                    title = gridViewTitle,
                                    items = gridViewItems,
                                    lastFocusedIndex = gridRestoreState.focusedIndex,
                                    onFocusChange = { gridRestoreState.focusedIndex = it },
                                    onMovieClick = { movie ->
                                        openDetailsFor(movie)
                                    },
                                    onBack = {
                                        gridRestoreState.focusedIndex = null
                                        gridRestoreState.scrollIndex = 0
                                        gridRestoreState.scrollOffset = 0
                                        BackStackOps.pop(backStack)
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
                                        BackStackOps.resetToMain(backStack)
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
                            }
                            }
                            entry<DetailsKey> { detailsKey ->
                            // Back to an older Details page (after a queue advance): the
                            // selection follows the page on screen again.
                            LaunchedEffect(detailsKey) {
                                if (selectedMovieId != detailsKey.id || selectedMovieType != detailsKey.type) {
                                    selectedMovieId = detailsKey.id
                                    selectedMovieType = detailsKey.type
                                    selectedAddonBaseUrl = detailsKey.addon
                                    selectedMovieTitle = detailsKey.title
                                    selectedMoviePoster = detailsKey.poster
                                    selectedMovieLogo = detailsKey.logo
                                }
                            }

                            // Remembered inside the entry, so its cast/studio/recommendation stack
                            // is saved while the player is on top and comes back as it was.
                            val detailsNavController = rememberNavController()
                            val startRoute = "detail/${java.net.URLEncoder.encode(detailsKey.type, "UTF-8")}/${java.net.URLEncoder.encode(detailsKey.id, "UTF-8")}?addon=${java.net.URLEncoder.encode(detailsKey.addon ?: "", "UTF-8")}"

                            // Opens the title's page the first time only; a restored stack is kept.
                            LaunchedEffect(detailsKey) {
                                val currentRoute = detailsNavController.currentBackStackEntry?.destination?.route
                                if (currentRoute == null || currentRoute == "detail_start") {
                                    detailsNavController.navigate(startRoute) {
                                        popUpTo("detail_start") { inclusive = true }
                                    }
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
                                session.startFromDetails(
                                    url = url,
                                    playbackId = playbackId,
                                    playbackType = playbackType,
                                    playbackTitle = resolvedPlaybackTitle,
                                    poster = selectedMoviePoster,
                                    stream = stream,
                                    addonSubtitles = addonSubtitles,
                                    availableStreams = availableStreams,
                                    episodes = episodes,
                                    playerPreference = currentProfile?.playerPreference,
                                    persistProfileState = mainViewModel::persistActiveProfileState
                                )
                            }

                            NavHost(
                                navController = detailsNavController,
                                startDestination = "detail_start",
                            ) {
                                composable("detail_start") { }
                                composable(
                                    "detail/{type}/{id}?addon={addon}",
                                    arguments = listOf(
                                        navArgument("type") { type = NavType.StringType },
                                        navArgument("id") { type = NavType.StringType },
                                        navArgument("addon") { type = NavType.StringType; defaultValue = "" }
                                    )
                                ) { backStackEntry ->
                                    val detailType = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("type") ?: "movie", "UTF-8")
                                    val detailId = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("id") ?: "", "UTF-8")
                                    val detailAddon = backStackEntry.arguments?.getString("addon")?.takeIf { it.isNotEmpty() }

                                    DetailsScreen(
                                        type = detailType,
                                        id = detailId,
                                        addonBaseUrl = detailAddon,
                                        // Live, not a route argument: the page stays on the stack
                                        // under the player and must see the hint the session
                                        // leaves when it ends. DetailsScreen ignores other titles' hints.
                                        resumePlaybackHint = session.detailsResumePlaybackHint,
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
                                        isTrailerLoading = session.isTrailerLoading,
                                        onTrailerClick = { youtubeKey, trailerName ->
                                            session.startTrailer(youtubeKey, trailerName, selectedMovieType, selectedMoviePoster)
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
                            entry<PlayerKey> {
                            // PlayerViewModel stays on the activity for now, as before.
                            CompositionLocalProvider(LocalViewModelStoreOwner provides this@MainActivity) {
                            if (session.selectedVideoUrl.isNotBlank() && session.currentStream == null &&
                                !session.selectedPlaybackId.startsWith("trailer_")
                            ) {
                                // The session's sources, subtitles, episode list and current stream
                                // survive a configuration change but not process death, while
                                // selectedVideoUrl is saved and would otherwise resume a degraded,
                                // silently broken player session. Every legitimate NON-TRAILER playback
                                // start sets currentStream alongside selectedVideoUrl, so seeing one
                                // without the other only happens after process death for normal
                                // playback — pop back to the page that opened the player to re-resolve.
                                // Trailers are exempt: onTrailerClick never sets currentStream (it
                                // has no Stream object, just a resolved YouTube URL), so this guard
                                // would otherwise fire on every legitimate trailer play.
                                LaunchedEffect(Unit) {
                                    session.stopTorrent()
                                    BackStackOps.returnFromPlayer(backStack)
                                }
                            } else if (session.selectedVideoUrl.isBlank() && session.torrentProgress == null) {
                                // torrentProgress is lost with the session on process death even when
                                // a TorrentService download is still running
                                // (its foreground service survives independently). Stop it here so
                                // this recovery path doesn't silently abandon an orphaned download.
                                LaunchedEffect(Unit) {
                                    session.stopTorrent()
                                    BackStackOps.returnFromPlayer(backStack)
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
                            val isSeries = session.isSeriesPlayback
                            val autoplayNext = session.autoplayNextEnabled(currentProfile?.autoplayNextEpisode == true)
                            val queueSingleEpisode = session.isQueueSingleEpisode
                            val nextEpisode = remember(session.selectedPlaybackId, selectedMovieId, session.currentEpisodeList, isSeries, queueSingleEpisode) {
                                session.nextEpisodeFor(selectedMovieId)
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
                                        session.autoplayNextEpisode(selectedMovieId, nextEpisode, playerCurrentSourceUrl, currentProfile)
                                    }
                                } else null,
                                episodes = session.currentEpisodeList,
                                currentPlaybackId = session.selectedPlaybackId,
                                onEpisodeSelected = if (session.currentEpisodeList.isNotEmpty()) {
                                    { episode, playerCurrentSourceUrl ->
                                        session.selectEpisode(selectedMovieId, episode, playerCurrentSourceUrl, currentProfile)
                                    }
                                } else null,
                                episodeSwitchSources = session.pendingEpisodeSwitch?.let { pending ->
                                    pending.streams
                                        ?.mapNotNull(::buildPlayerSourceOption)
                                        ?.distinctBy { it.id }
                                },
                                isEpisodeSwitchLoading = session.isEpisodeSwitchLoading,
                                episodeSwitchTitle = session.pendingEpisodeSwitch?.playbackTitle,
                                onEpisodeSwitchSourceSelected = session.pendingEpisodeSwitch?.let {
                                    { sourceUrl: String -> session.pickEpisodeSwitchSource(sourceUrl, currentProfile) }
                                },
                                onEpisodeSwitchDismissed = session::dismissEpisodeSwitch,
                                onResolveSourceSubtitles = session::resolveSourceSubtitles,
                                onMagnetSourceSelected = session::selectMagnetSource,
                                torrentProgress = session.torrentProgress,
                                playbackStatus = session.playbackStatus,
                                onFirstFrameRendered = session::onFirstFrame,
                                autoFallbackEnabled = currentProfile?.autoSelectSource == true && currentProfile?.sourceAutoFallback != false,
                                onSuspectSource = { status -> session.onSuspectSource(status, currentProfile) },
                                onBack = { sessionResult -> session.end(sessionResult, currentProfile) }
                            )
                            }
                            }
                            }
                            }
                        ) // NavDisplay end
                    }

                    // Player choice dialog (shown when playerPreference == "ask")
                    if (session.showPlayerChoiceDialog && session.selectedVideoUrl.isNotBlank()) {
                        PlayerChoiceDialog(
                            onInternal = {
                                session.showPlayerChoiceDialog = false
                                BackStackOps.openPlayer(backStack)
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

                    if (session.showTrailerError) {
                        Dialog(onDismissRequest = { session.showTrailerError = false }) {
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
                                            onClick = { session.showTrailerError = false },
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
