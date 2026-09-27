package com.hereliesaz.illumera.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The root back stack's contract: what each navigation does to the list ([BackStackOps]),
 * and how NavDisplay, set up as MainActivity sets it up, hands Back to the entries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppBackStackTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun stack(vararg keys: NavKey): MutableList<NavKey> = keys.toMutableList()

    // ---- BackStackOps ----

    @Test
    fun detailsThenPlayerThenBackReturnsToDetails() {
        val s = stack(MainKey)
        BackStackOps.openDetails(s, type = "movie", id = "tt1")
        BackStackOps.openPlayer(s)
        assertEquals(PlayerKey, s.last())

        // The player's Back ends the session, which asks to return from the player.
        assertTrue(BackStackOps.returnFromPlayer(s))
        assertEquals(listOf(MainKey, DetailsKey("movie", "tt1")), s)
    }

    @Test
    fun trailerEndReturnsToDetails() {
        val s = stack(MainKey)
        BackStackOps.openDetails(s, type = "series", id = "tt2")
        BackStackOps.openPlayer(s) // trailers use the same player entry
        BackStackOps.returnFromPlayer(s)
        assertEquals(DetailsKey("series", "tt2"), s.last())
    }

    @Test
    fun debridLibraryPlaybackFromMainReturnsToMain() {
        val s = stack(MainKey)
        BackStackOps.openPlayer(s)
        BackStackOps.returnFromPlayer(s)
        assertEquals(listOf<NavKey>(MainKey), s)
    }

    @Test
    fun queueAdvancePushesDetailsOnTopAndTwoBacksReachMain() {
        val s = stack(MainKey)
        BackStackOps.openDetails(s, type = "series", id = "tt3")
        BackStackOps.openPlayer(s)

        BackStackOps.queueAdvance(s, type = "series", id = "tt3", title = "Next", poster = "")
        assertEquals(3, s.size)
        val advanced = s.last() as DetailsKey
        assertEquals("tt3", advanced.id)
        // Same title twice on the stack stays two distinct entries.
        assertTrue(advanced != s[1])

        assertTrue(BackStackOps.pop(s))
        assertEquals(DetailsKey("series", "tt3"), s.last())
        assertTrue(BackStackOps.pop(s))
        assertEquals(listOf<NavKey>(MainKey), s)
        // Main alone: Back belongs to the menu area.
        assertFalse(BackStackOps.pop(s))
        assertEquals(listOf<NavKey>(MainKey), s)
    }

    @Test
    fun gridRestoredWithoutItemsPops() {
        val s = stack(MainKey, GridKey("Popular", "cfg"))
        assertFalse(BackStackOps.dropEmptyGrid(s, hasItems = true))
        assertEquals(GridKey("Popular", "cfg"), s.last())

        assertTrue(BackStackOps.dropEmptyGrid(s, hasItems = false))
        assertEquals(listOf<NavKey>(MainKey), s)
    }

    @Test
    fun gridThenDetailsBackReturnsToGrid() {
        val s = stack(MainKey)
        BackStackOps.openGrid(s, "Popular", "cfg")
        BackStackOps.openDetails(s, type = "movie", id = "tt4")
        BackStackOps.pop(s)
        assertEquals(GridKey("Popular", "cfg"), s.last())
    }

    @Test
    fun returnFromPlayerLeavesOtherPagesAlone() {
        val s = stack(MainKey, DetailsKey("movie", "tt5"))
        assertFalse(BackStackOps.returnFromPlayer(s))
        assertEquals(2, s.size)
    }

    @Test
    fun openPlayerTwiceKeepsOnePlayer() {
        val s = stack(MainKey)
        BackStackOps.openPlayer(s)
        BackStackOps.openPlayer(s)
        assertEquals(listOf(MainKey, PlayerKey), s)
    }

    @Test
    fun resetToMainDropsEverythingAbove() {
        val s = stack(MainKey, GridKey("a", ""), DetailsKey("movie", "x"), PlayerKey)
        BackStackOps.resetToMain(s)
        assertEquals(listOf<NavKey>(MainKey), s)
    }

    @Test
    fun backStackSurvivesSaveAndRestore() {
        val tester = StateRestorationTester(compose)
        var restored: NavBackStack<NavKey>? = null
        tester.setContent { restored = rememberNavBackStack(AppBackStackConfiguration, MainKey) }
        compose.runOnUiThread {
            val s = restored!!
            BackStackOps.openGrid(s, "Popular", "cfg")
            BackStackOps.openDetails(s, type = "series", id = "tt9", addon = "https://addon", title = "T")
            BackStackOps.openPlayer(s)
        }
        val before = restored!!.toList()
        restored = null
        tester.emulateSavedInstanceStateRestore()
        assertEquals(before, restored!!.toList())
    }

    // ---- NavDisplay, configured as in MainActivity ----

    private lateinit var backStack: NavBackStack<NavKey>
    private lateinit var nested: NavHostController
    private var nextToken = 0
    private var detailsToken = -1
    private var playerBacks = 0

    private fun setUpDisplay(playerOwnsBack: Boolean) {
        compose.setContent {
            backStack = rememberNavBackStack(AppBackStackConfiguration, MainKey)
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
                    entry<MainKey> { Text("main") }
                    entry<DetailsKey> {
                        detailsToken = rememberSaveable { ++nextToken }
                        nested = rememberNavController()
                        NavHost(nested, startDestination = "detail") {
                            composable("detail") { Text("detail") }
                            composable("cast") { Text("cast") }
                        }
                    }
                    entry<PlayerKey> {
                        if (playerOwnsBack) BackHandler { playerBacks++ }
                        Text("player")
                    }
                }
            )
        }
        compose.waitForIdle()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun atMainAloneNavDisplayLeavesBackToTheScreens() {
        setUpDisplay(playerOwnsBack = true)
        assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun backFromDetailsPopsToMain() {
        setUpDisplay(playerOwnsBack = true)
        compose.runOnUiThread { BackStackOps.openDetails(backStack, "movie", "tt1") }
        compose.waitForIdle()
        pressBack()
        assertEquals(listOf<NavKey>(MainKey), backStack.toList())
    }

    @Test
    fun playerBackHandlerOwnsBackOverNavDisplay() {
        setUpDisplay(playerOwnsBack = true)
        compose.runOnUiThread {
            BackStackOps.openDetails(backStack, "movie", "tt1")
        }
        compose.waitForIdle()
        compose.runOnUiThread { BackStackOps.openPlayer(backStack) }
        compose.waitForIdle()

        pressBack()
        assertEquals(1, playerBacks)
        assertEquals(PlayerKey, backStack.last())
    }

    @Test
    fun detailsNestedStackSurvivesThePlayer() {
        setUpDisplay(playerOwnsBack = true)
        compose.runOnUiThread { BackStackOps.openDetails(backStack, "movie", "tt1") }
        compose.waitForIdle()
        compose.runOnUiThread { nested.navigate("cast") }
        compose.waitForIdle()
        val tokenBeforePlayer = detailsToken

        compose.runOnUiThread { BackStackOps.openPlayer(backStack) }
        compose.waitForIdle()
        compose.runOnUiThread { BackStackOps.returnFromPlayer(backStack) }
        compose.waitForIdle()

        // The page comes back where the viewer left it, not rebuilt from its start.
        assertEquals("cast", nested.currentBackStackEntry?.destination?.route)
        assertEquals(tokenBeforePlayer, detailsToken)

        // Back inside Details pops the nested page first, then the Details entry.
        pressBack()
        assertEquals("detail", nested.currentBackStackEntry?.destination?.route)
        assertTrue(backStack.last() is DetailsKey)
        pressBack()
        assertEquals(listOf<NavKey>(MainKey), backStack.toList())
    }
}
