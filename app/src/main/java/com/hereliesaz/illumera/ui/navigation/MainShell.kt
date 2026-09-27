package com.hereliesaz.illumera.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.navigation3.runtime.NavEntryDecorator
import com.hereliesaz.illumera.data.model.ProfileEntity

/**
 * What [MainShell] shares with the back-stack entries under it: whether the menu is open
 * (holds focus), and how to close it the way the chrome's own Back does.
 */
@Stable
class MainShellState {
    internal val contentState = NavDrawerContentState()

    /** True while the menu (side rail or top bar) holds focus. */
    var isMenuOpen by mutableStateOf(false)
        internal set

    internal var closeMenuAction: () -> Unit = {}

    /** Closes the menu: focus goes back to the content, restored where the layout allows. */
    fun closeMenu() = closeMenuAction()
}

@Composable
fun rememberMainShellState(): MainShellState = remember { MainShellState() }

/**
 * Registers "Back closes the open menu" inside each back-stack entry, after the entry's own
 * content. Back handlers run last-registered first, and an entry's screen registers its own
 * (a main screen's "Back opens the menu", for one) whenever it is composed again, e.g. on
 * the way back from a Details page; registering this after them keeps the open menu first.
 */
@Composable
fun <T : Any> rememberMenuBackNavEntryDecorator(state: MainShellState): NavEntryDecorator<T> =
    remember(state) {
        NavEntryDecorator { entry ->
            entry.Content()
            BackHandler(enabled = state.isMenuOpen) { state.closeMenu() }
        }
    }

/**
 * The app's screens with the menu chrome over them.
 *
 * [content] (the back stack's NavDisplay) sits at one fixed place in the tree, inside the side
 * menu's content layer, whatever the chrome does. The chrome, the side rail or the top bar
 * as [navPosition] says, is drawn over it only while [showChrome] (a main screen or the grid
 * is on top). Switching the menu layout crossfades the chrome alone, so the back stack, each
 * screen's state and the focused element all stay as they were.
 *
 * The NavDisplay should carry [rememberMenuBackNavEntryDecorator] with the same [state].
 */
@Composable
fun MainShell(
    navPosition: String,
    showChrome: Boolean,
    currentDestination: NavDestination,
    currentProfile: ProfileEntity?,
    menuRequesters: Map<NavDestination, FocusRequester>,
    onNavigate: (NavDestination) -> Unit,
    onEnterContent: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    state: MainShellState = rememberMainShellState(),
    content: @Composable () -> Unit
) {
    val contentState = state.contentState
    val isTop = navPosition == "top"
    SideEffect {
        // The same close as the chrome's own: the side menu restores the content's focused
        // element first; the top bar hands focus to the screen's entry point.
        state.closeMenuAction = if (isTop) {
            onEnterContent
        } else {
            { if (!contentState.restoreContentFocus()) onEnterContent() }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().navDrawerContentLayer(contentState)) {
            content()
        }
        if (showChrome) {
            DisposableEffect(Unit) { onDispose { state.isMenuOpen = false } }
            Crossfade(
                targetState = navPosition,
                animationSpec = tween(400),
                label = "NavSwitcher",
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { state.isMenuOpen = it.hasFocus }
            ) { position ->
                if (position == "top") {
                    TopNavigationBarOverlay(
                        currentDestination = currentDestination,
                        currentProfile = currentProfile,
                        topNavRequesters = menuRequesters,
                        onNavigate = onNavigate,
                        onEnterContent = onEnterContent,
                        onLogout = onLogout,
                        onExit = onExit
                    )
                } else {
                    NavDrawerRail(
                        contentState = contentState,
                        currentDestination = currentDestination,
                        currentProfile = currentProfile,
                        drawerRequesters = menuRequesters,
                        onNavigate = onNavigate,
                        onClose = onEnterContent
                    )
                }
            }
        }
    }
}
