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
 * A title's Details page. [title], [poster] and [logo] are what the opener already knew, shown
 * until the page resolves its own and handed to the player when playback starts here.
 * [queueAutoPlayId] is the episode or movie a queue advance asks the page to start; the page
 * starts it once (DetailsViewModel remembers that it did). [instance] keeps two pages for the
 * same title (a queue advance within one show, or a recommendation loop) apart on the stack.
 */
@Serializable
data class DetailsKey(
    val type: String,
    val id: String,
    val addon: String? = null,
    val title: String = "",
    val poster: String = "",
    val logo: String = "",
    val queueAutoPlayId: String? = null,
    val instance: Int = 0
) : NavKey {
    /** Names this page to the playback session, which hands its resume hint back to it. */
    val playbackOwnerTag: String get() = "$type:$id:$instance"
}

/**
 * A cast member's filmography, opened from a Details page. [instance], like DetailsKey's, keeps
 * the same person opened twice on one stack (via another title) two distinct entries.
 */
@Serializable
data class CastKey(val personId: Int, val name: String, val instance: Int = 0) : NavKey

/** A studio, network or company's titles, opened from a Details page. */
@Serializable
data class StudioKey(
    val entityId: Int,
    val kind: String,
    val name: String,
    val sourceType: String,
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
            subclass(CastKey::class, CastKey.serializer())
            subclass(StudioKey::class, StudioKey.serializer())
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
 * - Details, Cast and Studio pages are flat entries: each opened page is pushed, Back pops it.
 */
object BackStackOps {

    fun openDetails(
        stack: MutableList<NavKey>,
        type: String,
        id: String,
        addon: String? = null,
        title: String = "",
        poster: String = "",
        logo: String = "",
        queueAutoPlayId: String? = null
    ) {
        stack.add(
            DetailsKey(
                type = type,
                id = id,
                addon = addon,
                title = title,
                poster = poster,
                logo = logo,
                queueAutoPlayId = queueAutoPlayId,
                instance = stack.count { it is DetailsKey }
            )
        )
    }

    fun openCast(stack: MutableList<NavKey>, personId: Int, name: String) {
        stack.add(CastKey(personId, name, instance = stack.count { it is CastKey }))
    }

    fun openStudio(stack: MutableList<NavKey>, entityId: Int, kind: String, name: String, sourceType: String) {
        stack.add(StudioKey(entityId, kind, name, sourceType, instance = stack.count { it is StudioKey }))
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
        poster: String,
        queueAutoPlayId: String? = null
    ) {
        returnFromPlayer(stack)
        openDetails(stack, type = type, id = id, title = title, poster = poster, queueAutoPlayId = queueAutoPlayId)
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
