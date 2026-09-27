package com.hereliesaz.illumera.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/*
 * The app's root back stack: a list of these keys, saved across process death. Keys hold
 * only strings and ints so a restore after R8 never depends on Parcelable class names.
 */

/** The whole menu area: side menu / top bar and the main screen chosen in it. Always the bottom entry. */
@Serializable
data object MainKey : NavKey

/**
 * "View more" grid. Its items live in MainActivity's in-memory holder, not in the key, so
 * after process death the grid has nothing to show and returns to the screen below it.
 */
@Serializable
data class GridKey(val title: String, val configId: String) : NavKey

/**
 * A Details page with its own nested cast/studio/recommendation stack. [instance] keeps
 * two pages for the same title (a queue advance within one show) apart on the stack.
 */
@Serializable
data class DetailsKey(
    val type: String,
    val id: String,
    val addon: String? = null,
    val title: String = "",
    val poster: String = "",
    val logo: String = "",
    val instance: Int = 0
) : NavKey

/** The internal player; the playback session holds what it plays. Trailers use it too. */
@Serializable
data object PlayerKey : NavKey

/** Saves the back stack by registered subclass, without reflection on class names. */
val AppBackStackConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(MainKey::class, MainKey.serializer())
            subclass(GridKey::class, GridKey.serializer())
            subclass(DetailsKey::class, DetailsKey.serializer())
            subclass(PlayerKey::class, PlayerKey.serializer())
        }
    }
}

/**
 * Every change MainActivity makes to the back stack. Plain list operations, so the
 * navigation contract is testable without a composition.
 *
 * - The stack is never empty and [MainKey] is always at the bottom.
 * - Back pops one entry; at [MainKey] alone it pops nothing and the menu area handles Back.
 * - Leaving the player pops only the player, so Back lands on whatever opened it.
 * - A queue advance replaces the player with the next item's Details on top of the stack.
 */
object BackStackOps {

    fun openDetails(
        stack: MutableList<NavKey>,
        type: String,
        id: String,
        addon: String? = null,
        title: String = "",
        poster: String = "",
        logo: String = ""
    ) {
        stack.add(
            DetailsKey(
                type = type,
                id = id,
                addon = addon,
                title = title,
                poster = poster,
                logo = logo,
                instance = stack.count { it is DetailsKey }
            )
        )
    }

    fun openGrid(stack: MutableList<NavKey>, title: String, configId: String) {
        if (stack.lastOrNull() is GridKey) stack.removeAt(stack.lastIndex)
        stack.add(GridKey(title, configId))
    }

    fun openPlayer(stack: MutableList<NavKey>) {
        if (stack.lastOrNull() != PlayerKey) stack.add(PlayerKey)
    }

    /** Pops the player if it is on top; returns whether it was. */
    fun returnFromPlayer(stack: MutableList<NavKey>): Boolean {
        if (stack.lastOrNull() != PlayerKey) return false
        stack.removeAt(stack.lastIndex)
        ensureRoot(stack)
        return true
    }

    /** The queue moved on: the player gives way to the next item's Details, pushed on top. */
    fun queueAdvance(
        stack: MutableList<NavKey>,
        type: String,
        id: String,
        title: String,
        poster: String
    ) {
        returnFromPlayer(stack)
        openDetails(stack, type = type, id = id, title = title, poster = poster)
    }

    /** Back: pops one entry unless only [MainKey] is left. */
    fun pop(stack: MutableList<NavKey>): Boolean {
        if (stack.size <= 1) {
            ensureRoot(stack)
            return false
        }
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** A grid restored with no items has nothing to show; returns to the screen before it. */
    fun dropEmptyGrid(stack: MutableList<NavKey>, hasItems: Boolean): Boolean {
        if (hasItems || stack.lastOrNull() !is GridKey) return false
        return pop(stack)
    }

    /** Back to the menu area alone (menu choice from the grid, logout). */
    fun resetToMain(stack: MutableList<NavKey>) {
        if (stack.firstOrNull() != MainKey) {
            stack.clear()
            stack.add(MainKey)
            return
        }
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    private fun ensureRoot(stack: MutableList<NavKey>) {
        if (stack.isEmpty()) stack.add(MainKey)
    }
}
