package com.hereliesaz.illumera.data.trakt

import android.util.Log
import com.hereliesaz.illumera.data.local.AddonDao
import com.hereliesaz.illumera.data.model.SeriesNextUpEntity
import com.hereliesaz.illumera.data.model.WatchHistoryEntity
import com.hereliesaz.illumera.data.model.WatchlistEntity
import com.hereliesaz.illumera.data.profile.ProfileConfigurationManager
import com.hereliesaz.illumera.data.model.trakt.TraktIds
import com.hereliesaz.illumera.data.model.trakt.TraktPlaybackItem
import com.hereliesaz.illumera.data.model.trakt.TraktSyncEpisode
import com.hereliesaz.illumera.data.model.trakt.TraktSyncItem
import com.hereliesaz.illumera.data.model.trakt.TraktSyncRequest
import com.hereliesaz.illumera.data.model.trakt.TraktSyncSeason
import com.hereliesaz.illumera.data.model.trakt.TraktWatchedMovie
import com.hereliesaz.illumera.data.model.trakt.TraktWatchedShow
import com.hereliesaz.illumera.data.model.trakt.TraktWatchlistItem
import com.hereliesaz.illumera.data.remote.TraktSyncApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktSyncManager @Inject constructor(
    private val traktSyncApi: TraktSyncApiService,
    private val traktAuthManager: TraktAuthManager,
    private val dao: AddonDao,
    private val profileConfigurationManager: ProfileConfigurationManager,
    private val pendingStore: TraktWatchlistPendingStore
) {
    companion object {
        private const val TAG = "TraktSyncManager"
    }

    private val profileId: Int
        get() = profileConfigurationManager.getLastActiveProfileId() ?: 1

    private val syncMutex = Mutex()

    // Guards checkAndSync so the foreground trigger, the 30 s poll and the background
    // worker never run overlapping activity checks.
    private val checkMutex = Mutex()

    // Last known activity timestamps from Trakt (Fix #4/#10: volatile for thread safety)
    @Volatile private var lastWatchlistActivity: String? = null
    @Volatile private var lastPlaybackActivity: String? = null
    @Volatile private var lastWatchedActivity: String? = null
    @Volatile private var lastCollectionActivity: String? = null

    // Trakt collection membership (local ids, IMDb and "tmdb:N"), cached per profile.
    @Volatile private var collectionIds: Set<String>? = null
    @Volatile private var collectionProfileId: Int? = null

    // IDs currently being deleted from Trakt — sync skips these to prevent race conditions (Fix 5)
    // Fix #2: Thread-safe set for concurrent access from sync poll and delete operations
    private val pendingDeletes = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Lightweight check: queries /sync/last_activities and only runs
     * syncs for categories whose timestamps have changed.
     */
    suspend fun checkAndSync(): Boolean {
        if (traktAuthManager.getAccessToken() == null) return false
        // Another check is already running — it covers this one.
        if (!checkMutex.tryLock()) return false
        try {
            return checkAndSyncLocked()
        } finally {
            checkMutex.unlock()
        }
    }

    private suspend fun checkAndSyncLocked(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = traktSyncApi.getLastActivities()
                if (!response.isSuccessful) {
                    Log.w(TAG, "last_activities failed: ${response.code()}")
                    return@withContext false
                }

                val activities = response.body() ?: return@withContext false
                var synced = false

                // Check watchlist changes
                val watchlistTimestamp = activities.watchlist?.updatedAt
                if (watchlistTimestamp != null && watchlistTimestamp != lastWatchlistActivity) {
                    Log.d(TAG, "Watchlist activity changed: $lastWatchlistActivity → $watchlistTimestamp")
                    lastWatchlistActivity = watchlistTimestamp
                    syncWatchlist()
                    synced = true
                }

                // Check playback progress changes (continue watching)
                val moviesPaused = activities.movies?.pausedAt
                val episodesPaused = activities.episodes?.pausedAt
                val playbackTimestamp = listOfNotNull(moviesPaused, episodesPaused).maxOrNull()
                if (playbackTimestamp != null && playbackTimestamp != lastPlaybackActivity) {
                    Log.d(TAG, "Playback activity changed: $lastPlaybackActivity → $playbackTimestamp")
                    lastPlaybackActivity = playbackTimestamp
                    syncPlaybackProgress()
                    synced = true
                }

                // Check watched history changes (movies or episodes)
                val moviesWatched = activities.movies?.watchedAt
                val episodesWatched = activities.episodes?.watchedAt
                val watchedTimestamp = listOfNotNull(moviesWatched, episodesWatched).maxOrNull()
                if (watchedTimestamp != null && watchedTimestamp != lastWatchedActivity) {
                    Log.d(TAG, "Watched activity changed: $lastWatchedActivity → $watchedTimestamp")
                    lastWatchedActivity = watchedTimestamp
                    syncSeriesNextUp()
                    synced = true
                }

                // Check collection changes (only refetch if membership was ever loaded)
                val collectionTimestamp = listOfNotNull(
                    activities.movies?.collectedAt,
                    activities.episodes?.collectedAt
                ).maxOrNull()
                if (collectionTimestamp != null && collectionTimestamp != lastCollectionActivity) {
                    val firstSeen = lastCollectionActivity == null
                    lastCollectionActivity = collectionTimestamp
                    if (!firstSeen || collectionIds == null) {
                        refreshCollection()
                        synced = true
                    }
                }

                synced
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Activity check failed", e)
                false
            }
        }
    }

    /**
     * Initial sync after first connecting Trakt.
     * Pushes all local items to Trakt, then does a normal sync.
     * Fix #11: 30-second timeout prevents blocking the mutex indefinitely.
     */
    suspend fun initialSync() {
        withTimeoutOrNull(30_000L) {
            syncMutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        val localItems = dao.getWatchlistOnce(profileId)
                        if (localItems.isNotEmpty()) {
                            pushToTrakt(localItems)
                            Log.d(TAG, "Initial push: ${localItems.size} items")
                        }
                    } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                        com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Initial push failed", e)
                    }
                }
            }
        } ?: Log.w(TAG, "Initial sync timed out")
        syncWatchlist()
        // Bring Trakt's watched history in so watched badges are right on a fresh install.
        withContext(Dispatchers.IO) {
            try {
                importWatchedHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Initial watched import failed", e)
            }
        }
    }

    /**
     * Periodic watchlist sync with diff.
     * Pulls new items and removes items deleted on Trakt.
     * Does NOT push — local adds are pushed instantly via pushAdd().
     */
    suspend fun syncWatchlist(): Result<Unit> = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (traktAuthManager.getAccessToken() == null) {
                    return@withContext Result.failure(Exception("Not connected to Trakt"))
                }

                // 0. Retry local changes Trakt hasn't confirmed, then keep the ones that
                //    still failed out of the diff below so it doesn't undo them.
                val activeProfile = profileId
                retryPendingPushes(activeProfile)
                val pending = pendingStore.pending(activeProfile)
                val pendingAdds = pending.filter { it.op == TraktWatchlistPendingStore.Op.ADD }.map { it.id }.toSet()
                val pendingRemoves = pending.filter { it.op == TraktWatchlistPendingStore.Op.REMOVE }.map { it.id }.toSet()

                // 1. Fetch Trakt watchlist
                val traktItems = fetchAllTraktWatchlist()
                    ?: return@withContext Result.failure(Exception("Failed to fetch Trakt watchlist"))

                // 2. Get local watchlist
                val localItems = dao.getWatchlistOnce(profileId)

                // 3. Build lookup sets. A Trakt item matches a local one saved
                //    under either its IMDb id or "tmdb:N".
                fun idsOf(item: TraktWatchlistItem) = when (item.type) {
                    "movie" -> localIdsFor(item.movie?.ids)
                    "show" -> localIdsFor(item.show?.ids)
                    else -> emptyList()
                }
                val traktLocalIds = traktItems.flatMap { idsOf(it) }.toSet()

                val localIds = localItems.map { it.id }.toSet()

                // 4. Pull Trakt → local (items on Trakt but not local)
                val toPull = traktItems.filter { item ->
                    val ids = idsOf(item)
                    ids.isNotEmpty() && ids.none { it in localIds || it in pendingRemoves }
                }
                if (toPull.isNotEmpty()) {
                    pullFromTrakt(toPull)
                    Log.d(TAG, "Pulled ${toPull.size} items from Trakt")
                }

                // 5. Remove local items no longer on Trakt (deleted externally).
                // Local adds are pushed via pushAdd(); anything local, missing from
                // Trakt and not still pending was removed on Trakt's side.
                // Only consider items with Trakt-mappable IDs (IMDb or TMDB) — others
                // can't be matched against Trakt's response. (Fix: audit #7)
                val toRemove = localItems.filter {
                    traktIdsFor(it.id) != null && it.id !in traktLocalIds && it.id !in pendingAdds
                }
                for (item in toRemove) {
                    dao.removeFromWatchlist(profileId, item.id)
                    Log.d(TAG, "Removed ${item.title} (deleted on Trakt)")
                }

                Log.i(TAG, "Sync complete: pulled=${toPull.size}, removed=${toRemove.size}")
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.e(TAG, "Watchlist sync failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Push a single item to Trakt watchlist (called when user adds locally).
     */
    suspend fun pushAdd(item: WatchlistEntity) {
        if (traktAuthManager.getAccessToken() == null) return
        val activeProfile = profileId
        withContext(Dispatchers.IO) {
            pendingStore.mark(activeProfile, item.id, item.type, TraktWatchlistPendingStore.Op.ADD)
            try {
                if (pushToTrakt(listOf(item))) pendingStore.clear(activeProfile, item.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to push watchlist add to Trakt", e)
            }
        }
    }

    /**
     * Push a removal to Trakt watchlist (called when user removes locally).
     */
    suspend fun pushRemove(itemId: String, type: String) {
        if (traktAuthManager.getAccessToken() == null) return
        val activeProfile = profileId
        withContext(Dispatchers.IO) {
            pendingStore.mark(activeProfile, itemId, type, TraktWatchlistPendingStore.Op.REMOVE)
            try {
                if (removeFromTrakt(itemId, type)) pendingStore.clear(activeProfile, itemId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to push watchlist remove to Trakt", e)
            }
        }
    }

    /** Add a movie/show to the user's Trakt collection (Library). */
    suspend fun pushAddToCollection(id: String, type: String) = pushCollectionChange(id, type, add = true)

    /** Remove a movie/show from the user's Trakt collection (Library). */
    suspend fun pushRemoveFromCollection(id: String, type: String) = pushCollectionChange(id, type, add = false)

    private suspend fun pushCollectionChange(id: String, type: String, add: Boolean) {
        if (traktAuthManager.getAccessToken() == null) return
        val ids = traktIdsFor(id) ?: return
        val activeProfile = profileId
        withContext(Dispatchers.IO) {
            try {
                val item = listOf(TraktSyncItem(ids = ids))
                val body = if (type == "movie") TraktSyncRequest(movies = item)
                    else TraktSyncRequest(shows = item)
                val response = if (add) traktSyncApi.addToCollection(body)
                    else traktSyncApi.removeFromCollection(body)
                if (!response.isSuccessful) {
                    Log.w(TAG, "Trakt collection ${if (add) "add" else "remove"} failed: ${response.code()}")
                } else if (collectionProfileId == activeProfile) {
                    collectionIds = collectionIds?.let { if (add) it + id else it - id }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to update Trakt collection", e)
            }
        }
    }

    /**
     * Whether [id] is in the active profile's Trakt collection. Loads membership once per
     * profile; checkAndSync refreshes it when Trakt reports collection activity.
     * Returns false when not connected or Trakt can't be reached.
     */
    suspend fun isInTraktCollection(id: String): Boolean {
        if (traktAuthManager.getAccessToken() == null) return false
        val cached = collectionIds.takeIf { collectionProfileId == profileId }
            ?: withContext(Dispatchers.IO) { refreshCollection() }
            ?: return false
        return id in cached
    }

    /** Re-fetches collection membership; null (cache untouched) on failure. */
    private suspend fun refreshCollection(): Set<String>? {
        val activeProfile = profileId
        return try {
            val movies = traktSyncApi.getCollectionMovies()
            val shows = traktSyncApi.getCollectionShows()
            if (!movies.isSuccessful || !shows.isSuccessful) {
                Log.w(TAG, "Trakt collection fetch failed: ${movies.code()}/${shows.code()}")
                return null
            }
            val ids = buildSet {
                movies.body()?.forEach { addAll(localIdsFor(it.movie?.ids)) }
                shows.body()?.forEach { addAll(localIdsFor(it.show?.ids)) }
            }
            if (profileId == activeProfile) {
                collectionIds = ids
                collectionProfileId = activeProfile
            }
            ids
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to fetch Trakt collection", e)
            null
        }
    }

    /** Push a whole set of show episodes in one Trakt history request. */
    suspend fun pushSeriesEpisodesWatched(
        showImdbId: String,
        episodes: List<Pair<Int, Int>>,
        watched: Boolean
    ) {
        if (traktAuthManager.getAccessToken() == null || episodes.isEmpty()) return
        val showIds = traktIdsFor(showImdbId) ?: return
        withContext(Dispatchers.IO) {
            try {
                val seasons = episodes
                    .groupBy { it.first }
                    .toSortedMap()
                    .map { (season, entries) ->
                        TraktSyncSeason(
                            number = season,
                            episodes = entries.map { TraktSyncEpisode(it.second) }.distinctBy { it.number }
                        )
                    }
                val body = TraktSyncRequest(
                    shows = listOf(
                        TraktSyncItem(
                            ids = showIds,
                            seasons = seasons
                        )
                    )
                )
                val response = if (watched) traktSyncApi.addToHistory(body) else traktSyncApi.removeFromHistory(body)
                if (!response.isSuccessful) {
                    Log.w(TAG, "Trakt bulk history update failed: ${response.code()}")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to update series history on Trakt", e)
            }
        }
    }

    // ── Watch History (mark as watched) ──

    /**
     * Mark a movie as watched on Trakt.
     */
    suspend fun pushMovieWatched(imdbId: String) {
        if (traktAuthManager.getAccessToken() == null) return
        val ids = traktIdsFor(imdbId) ?: return
        withContext(Dispatchers.IO) {
            try {
                val body = TraktSyncRequest(movies = listOf(TraktSyncItem(ids = ids)))
                val response = traktSyncApi.addToHistory(body)
                Log.d(TAG, "pushMovieWatched $imdbId: ${response.code()}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to push movie watched to Trakt", e)
            }
        }
    }

    /**
     * Remove a movie from watched history on Trakt.
     */
    suspend fun pushMovieUnwatched(imdbId: String) {
        if (traktAuthManager.getAccessToken() == null) return
        val ids = traktIdsFor(imdbId) ?: return
        withContext(Dispatchers.IO) {
            try {
                val body = TraktSyncRequest(movies = listOf(TraktSyncItem(ids = ids)))
                val response = traktSyncApi.removeFromHistory(body)
                Log.d(TAG, "pushMovieUnwatched $imdbId: ${response.code()}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to push movie unwatched to Trakt", e)
            }
        }
    }

    /**
     * Mark an episode as watched on Trakt.
     */
    suspend fun pushEpisodeWatched(showImdbId: String, season: Int, episode: Int) {
        if (traktAuthManager.getAccessToken() == null) return
        val showIds = traktIdsFor(showImdbId) ?: return
        withContext(Dispatchers.IO) {
            try {
                val body = TraktSyncRequest(
                    shows = listOf(
                        TraktSyncItem(
                            ids = showIds,
                            seasons = listOf(TraktSyncSeason(season, listOf(TraktSyncEpisode(episode))))
                        )
                    )
                )
                val response = traktSyncApi.addToHistory(body)
                Log.d(TAG, "pushEpisodeWatched S${season}E${episode}: ${response.code()}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to push episode watched to Trakt", e)
            }
        }
    }

    /**
     * Remove an episode from watched history on Trakt.
     */
    suspend fun pushEpisodeUnwatched(showImdbId: String, season: Int, episode: Int) {
        if (traktAuthManager.getAccessToken() == null) return
        val showIds = traktIdsFor(showImdbId) ?: return
        withContext(Dispatchers.IO) {
            try {
                val body = TraktSyncRequest(
                    shows = listOf(
                        TraktSyncItem(
                            ids = showIds,
                            seasons = listOf(TraktSyncSeason(season, listOf(TraktSyncEpisode(episode))))
                        )
                    )
                )
                val response = traktSyncApi.removeFromHistory(body)
                Log.d(TAG, "pushEpisodeUnwatched S${season}E${episode}: ${response.code()}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to push episode unwatched to Trakt", e)
            }
        }
    }

    /**
     * Full playback progress sync with diff logic.
     *
     * For each LOCAL in-progress item where scrobbled == true:
     *   - On Trakt playback?        → keep local (more accurate position)
     *   - Not on playback, on watched? → mark as watched locally (finished on other app)
     *   - Not on either?            → user cleared it on Trakt → delete locally
     *
     * For each LOCAL in-progress item where scrobbled == false:
     *   → Never touched Trakt. Leave it alone.
     *
     * For each item on Trakt playback NOT in local:
     *   → Pull to Lumera (watched on other app)
     */
    suspend fun syncPlaybackProgress() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch Trakt playback progress
                val playbackResponse = traktSyncApi.getPlaybackProgress()
                if (!playbackResponse.isSuccessful) {
                    Log.w(TAG, "Playback progress fetch failed: ${playbackResponse.code()}")
                    return@withContext
                }
                val traktPlayback = playbackResponse.body() ?: emptyList()

                // 2. Build lookup: IMDb ID → TraktPlaybackItem
                val traktPlaybackIds = mutableMapOf<String, TraktPlaybackItem>()
                for (item in traktPlayback) {
                    val id = when (item.type) {
                        "movie" -> localIdFor(item.movie?.ids)
                        "episode" -> {
                            val showImdb = localIdFor(item.show?.ids) ?: continue
                            val ep = item.episode ?: continue
                            "$showImdb:${ep.season}:${ep.number}"
                        }
                        else -> null
                    }
                    if (id != null) traktPlaybackIds[id] = item
                }

                // 3. Fetch Trakt watched history with episode-level detail (Fix 2)
                // Fix #7: If this fails, we get null — skip removals entirely to prevent data loss
                val watchedData = fetchWatchedData()

                // 4. Process local scrobbled in-progress items
                val scrobbledItems = dao.getScrobbledInProgressItems()
                var removed = 0
                var markedWatched = 0

                for (local in scrobbledItems) {
                    // Fix 5: skip items currently being deleted to prevent race
                    if (local.id in pendingDeletes) continue

                    // Fix 1: strip stream index suffix for lookup
                    val normalizedId = normalizePlaybackId(local.id)

                    val onTraktPlayback = normalizedId in traktPlaybackIds

                    if (onTraktPlayback) {
                        // Still in progress on Trakt — keep local version (more accurate)
                        continue
                    }

                    // Fix #7: If watched data fetch failed, we can't determine if
                    // the item was cleared vs finished. Skip to avoid data loss.
                    if (watchedData == null) continue

                    // Not on Trakt playback. Check if the specific item was watched (Fix 2)
                    val onTraktWatched = if (local.type == "series") {
                        val parts = normalizedId.split(":")
                        if (parts.size >= 3) {
                            val showImdb = parts.dropLast(2).joinToString(":")
                            val season = parts[parts.size - 2].toIntOrNull()
                            val episode = parts[parts.size - 1].toIntOrNull()
                            season != null && episode != null && watchedData.isEpisodeWatched(showImdb, season, episode)
                        } else false
                    } else {
                        watchedData.isMovieWatched(local.id)
                    }

                    if (onTraktWatched) {
                        // Finished on another app → mark as watched locally
                        dao.upsertHistory(local.copy(watched = true))
                        markedWatched++
                        Log.d(TAG, "Marked watched (finished elsewhere): ${local.title}")
                    } else {
                        // Not on playback, not on watched → user cleared it on Trakt
                        dao.deleteHistoryItem(local.id)
                        removed++
                        Log.d(TAG, "Removed (cleared on Trakt): ${local.title}")
                    }
                }

                // 5. Pull new items from Trakt playback that aren't local
                var added = 0
                for ((id, item) in traktPlaybackIds) {
                    // Fix 5: skip items being deleted
                    if (id in pendingDeletes) continue

                    val type = if (item.type == "movie") "movie" else "series"

                    // Don't overwrite existing local progress (check both exact and with stream index)
                    val existing = dao.getHistoryItem(id)
                    if (existing != null) continue
                    if (type == "series") {
                        val hasLocal = dao.getLatestSeriesEpisodeHistory("$id:%") != null
                        if (hasLocal) continue
                    }

                    val title = when (item.type) {
                        "movie" -> item.movie?.title ?: "Unknown"
                        else -> {
                            val ep = item.episode
                            val showTitle = item.show?.title ?: "Unknown"
                            when {
                                ep?.title != null -> ep.title
                                ep?.season != null && ep.number != null -> "S${ep.season}:E${ep.number} - $showTitle"
                                else -> showTitle
                            }
                        }
                    }

                    // Fix #12: clamp progress to [0,100] and store duration=0 (unknown) rather
                    // than a fabricated 90-minute estimate that produces impossible positions for
                    // short-form content. The player will seek to the raw millisecond position
                    // without percentage math when duration is 0.
                    val estimatedDurationMs = 0L
                    val clampedProgress = item.progress.coerceIn(0f, 100f)
                    val estimatedPositionMs = ((clampedProgress / 100f) * (90 * 60 * 1000L)).toLong()
                        .coerceAtLeast(0L)

                    dao.upsertHistory(
                        WatchHistoryEntity(
                            id = id,
                            title = title,
                            poster = null,
                            position = estimatedPositionMs,
                            duration = estimatedDurationMs,
                            lastWatched = parseIsoTimestamp(item.pausedAt),
                            type = type,
                            watched = false,
                            scrobbled = true
                        )
                    )
                    added++
                }

                Log.i(TAG, "Playback sync: added=$added, removed=$removed, markedWatched=$markedWatched")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.e(TAG, "Playback progress sync failed", e)
            }
        }
    }

    /**
     * Sync series next-up entries from Trakt.
     * For each watched show on Trakt, fetches the show's watched progress
     * to get the next episode, and updates the local series_next_up table.
     */
    suspend fun syncSeriesNextUp() {
        withContext(Dispatchers.IO) {
            try {
                // Pull Trakt's watched movies and shows into local history (batched)
                val moviesResponse = traktSyncApi.getWatchedMovies()
                val watchedMovies = if (moviesResponse.isSuccessful) moviesResponse.body().orEmpty() else emptyList()
                val traktWatchedMovieIds = watchedMovies.flatMap { localIdsFor(it.movie.ids) }.toSet()

                // Get all watched shows from Trakt
                val showsResponse = traktSyncApi.getWatchedShows()
                if (!showsResponse.isSuccessful) {
                    importWatchedHistory(watchedMovies, emptyList())
                    Log.w(TAG, "Failed to fetch watched shows for next-up: ${showsResponse.code()}")
                    return@withContext
                }
                val watchedShows = showsResponse.body() ?: return@withContext
                importWatchedHistory(watchedMovies, watchedShows)

                val traktWatchedEpisodeIds = mutableSetOf<String>()
                for (show in watchedShows) {
                    val showIds = localIdsFor(show.show.ids)
                    show.seasons?.forEach { season ->
                        season.episodes?.forEach { ep ->
                            showIds.forEach { traktWatchedEpisodeIds.add("$it:${season.number}:${ep.number}") }
                        }
                    }
                }

                var updated = 0
                for (show in watchedShows) {
                    val imdbId = localIdFor(show.show.ids) ?: continue
                    val traktSlug = show.show.ids.slug ?: show.show.ids.trakt?.toString() ?: continue

                    try {
                        val progressResponse = traktSyncApi.getShowProgress(traktSlug)
                        if (!progressResponse.isSuccessful) continue
                        val progress = progressResponse.body() ?: continue

                        val nextEp = progress.nextEpisode
                        val existing = dao.getSeriesNextUp(profileId, imdbId)
                        if (nextEp != null) {
                            val airDate = nextEp.firstAired?.take(10)
                            val unchanged = existing != null &&
                                !existing.isComplete &&
                                existing.nextSeason == nextEp.season &&
                                existing.nextEpisode == nextEp.number
                            val revived = existing?.isComplete == true
                            val badgeState = when {
                                revived -> true
                                unchanged -> existing?.isNewEpisode ?: false
                                else -> false
                            }

                            dao.upsertSeriesNextUp(
                                SeriesNextUpEntity(
                                    profileId = profileId,
                                    seriesId = imdbId,
                                    title = show.show.title ?: "Unknown",
                                    poster = existing?.poster,
                                    nextSeason = nextEp.season,
                                    nextEpisode = nextEp.number,
                                    nextEpisodeTitle = nextEp.title,
                                    nextReleased = airDate,
                                    isComplete = false,
                                    isNewEpisode = badgeState,
                                    updatedAt = if (unchanged) existing.updatedAt else parseIsoTimestamp(show.lastWatchedAt)
                                )
                            )
                            if (!unchanged) updated++
                        } else {
                            if (existing != null && !existing.isComplete) {
                                dao.upsertSeriesNextUp(existing.copy(isComplete = true, updatedAt = System.currentTimeMillis()))
                                updated++
                            }
                        }
                    } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                        com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to fetch progress for $traktSlug", e)
                    }
                }

                // Reverse sync: unmark local items no longer watched on Trakt
                if (moviesResponse.isSuccessful && showsResponse.isSuccessful) {
                    val localWatched = dao.getScrobbledWatchedItems()
                    var unmarked = 0
                    for (item in localWatched) {
                        val normalizedId = normalizePlaybackId(item.id)
                        val stillWatched = if (item.type == "movie") {
                            item.id in traktWatchedMovieIds
                        } else {
                            normalizedId in traktWatchedEpisodeIds
                        }
                        if (!stillWatched) {
                            dao.deleteHistoryItem(item.id)
                            unmarked++
                            Log.d(TAG, "Unmarked (removed from Trakt): ${item.title}")
                        }
                    }
                    if (unmarked > 0) Log.i(TAG, "Reverse watched sync: unmarked $unmarked items")
                }

                Log.i(TAG, "Series next-up sync: updated $updated shows")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.e(TAG, "Series next-up sync failed", e)
            }
        }
    }

    /**
     * Fetches Trakt's watched movies and shows and imports them into local history.
     * Returns the number of rows written, or null if Trakt couldn't be read.
     */
    private suspend fun importWatchedHistory(): Int? {
        val movies = traktSyncApi.getWatchedMovies()
        val shows = traktSyncApi.getWatchedShows()
        if (!movies.isSuccessful || !shows.isSuccessful) {
            Log.w(TAG, "Watched history fetch failed: ${movies.code()}/${shows.code()}")
            return null
        }
        return importWatchedHistory(movies.body().orEmpty(), shows.body().orEmpty())
    }

    /**
     * Upserts watched rows for Trakt titles/episodes not already watched locally, in one
     * batch. Local in-progress rows newer than Trakt's watch are left alone; see
     * [mergeTraktWatchedHistory]. History lives in the active profile's runtime tables.
     */
    private suspend fun importWatchedHistory(
        movies: List<TraktWatchedMovie>,
        shows: List<TraktWatchedShow>
    ): Int {
        val incoming = ArrayList<TraktWatchedEntry>()
        for (watched in movies) {
            val ids = localIdsFor(watched.movie.ids)
            val id = ids.firstOrNull() ?: continue
            incoming += TraktWatchedEntry(
                localIds = ids,
                entity = WatchHistoryEntity(
                    id = id,
                    title = watched.movie.title ?: "Unknown",
                    poster = null,
                    position = 0L,
                    duration = 0L,
                    lastWatched = parseIsoTimestamp(watched.lastWatchedAt),
                    type = "movie",
                    watched = true,
                    scrobbled = true
                )
            )
        }
        for (show in shows) {
            val showIds = localIdsFor(show.show.ids)
            val showId = showIds.firstOrNull() ?: continue
            val showTitle = show.show.title ?: "Unknown"
            show.seasons?.forEach { season ->
                season.episodes?.forEach { ep ->
                    incoming += TraktWatchedEntry(
                        localIds = showIds.map { "$it:${season.number}:${ep.number}" },
                        entity = WatchHistoryEntity(
                            id = "$showId:${season.number}:${ep.number}",
                            title = "S${season.number}:E${ep.number} - $showTitle",
                            poster = null,
                            position = 0L,
                            duration = 0L,
                            lastWatched = parseIsoTimestamp(ep.lastWatchedAt ?: show.lastWatchedAt),
                            type = "series",
                            watched = true,
                            scrobbled = true
                        )
                    )
                }
            }
        }
        if (incoming.isEmpty()) return 0
        val writes = mergeTraktWatchedHistory(dao.getAllWatchHistoryOnce(), incoming)
            .filter { it.id !in pendingDeletes }
        if (writes.isNotEmpty()) dao.upsertHistoryItems(writes)
        Log.i(TAG, "Watched import: ${writes.size} of ${incoming.size} rows written")
        return writes.size
    }

    /** Mark IDs as pending deletion so the sync poll doesn't re-add them. */
    fun markPendingDelete(ids: List<String>) {
        for (id in ids) {
            pendingDeletes.add(id)
            pendingDeletes.add(normalizePlaybackId(id))
        }
    }

    /** Remove IDs from pending deletion (e.g., user hit undo). */
    fun unmarkPendingDelete(ids: List<String>) {
        for (id in ids) {
            pendingDeletes.remove(id)
            pendingDeletes.remove(normalizePlaybackId(id))
        }
    }

    /** Reset stored activity timestamps (e.g., on profile switch). */
    fun resetActivityState() {
        lastWatchlistActivity = null
        lastPlaybackActivity = null
        lastWatchedActivity = null
        lastCollectionActivity = null
        collectionIds = null
        collectionProfileId = null
    }

    // ── Internal ──

    /**
     * Fetch the complete Trakt watchlist. Returns null if ANY page fails,
     * so callers never act on incomplete data. (Fix #3)
     */
    private suspend fun fetchAllTraktWatchlist(): List<TraktWatchlistItem>? {
        val allItems = mutableListOf<TraktWatchlistItem>()
        var page = 1

        while (true) {
            val response = traktSyncApi.getWatchlist(page = page, limit = 100)
            if (!response.isSuccessful) {
                Log.w(TAG, "Trakt watchlist fetch failed on page $page: ${response.code()}")
                return null // Fail entirely — never return partial data
            }
            val items = response.body() ?: break
            if (items.isEmpty()) break
            allItems.addAll(items)
            if (items.size < 100) break
            page++
        }

        return allItems
    }

    /** Re-sends watchlist changes Trakt hasn't confirmed; clears each one it accepts. */
    private suspend fun retryPendingPushes(activeProfile: Int) {
        for (change in pendingStore.pending(activeProfile)) {
            val accepted = try {
                when (change.op) {
                    TraktWatchlistPendingStore.Op.ADD ->
                        pushToTrakt(listOf(WatchlistEntity(activeProfile, change.id, change.type, title = "", poster = null, addedAt = 0L)))
                    TraktWatchlistPendingStore.Op.REMOVE -> removeFromTrakt(change.id, change.type)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Retrying watchlist change failed", e)
                false
            }
            if (accepted) pendingStore.clear(activeProfile, change.id)
        }
    }

    /** Returns whether Trakt accepted the removal. */
    private suspend fun removeFromTrakt(itemId: String, type: String): Boolean {
        // Nothing Trakt can identify — treat as accepted so it doesn't stay pending forever.
        val ids = traktIdsFor(itemId) ?: return true
        val item = listOf(TraktSyncItem(ids = ids))
        val body = if (type == "movie") TraktSyncRequest(movies = item)
                   else TraktSyncRequest(shows = item)
        val response = traktSyncApi.removeFromWatchlist(body)
        if (!response.isSuccessful) Log.w(TAG, "Trakt watchlist remove failed: ${response.code()}")
        return response.isSuccessful
    }

    /** Returns whether Trakt accepted the items (true when there was nothing to send). */
    private suspend fun pushToTrakt(items: List<WatchlistEntity>): Boolean {
        val movies = items.filter { it.type == "movie" }
            .mapNotNull { item -> traktIdsFor(item.id)?.let { TraktSyncItem(ids = it) } }
        val shows = items.filter { it.type == "series" }
            .mapNotNull { item -> traktIdsFor(item.id)?.let { TraktSyncItem(ids = it) } }

        val body = TraktSyncRequest(
            movies = movies.ifEmpty { null },
            shows = shows.ifEmpty { null }
        )

        Log.d(TAG, "pushToTrakt: movies=${movies.size}, shows=${shows.size}")
        if (movies.isNotEmpty() || shows.isNotEmpty()) {
            val response = traktSyncApi.addToWatchlist(body)
            Log.d(TAG, "pushToTrakt response: ${response.code()}")
            if (!response.isSuccessful) {
                Log.w(TAG, "Trakt watchlist push failed: ${response.code()} - ${response.errorBody()?.string()}")
            }
            return response.isSuccessful
        }
        return true
    }

    /**
     * Watched data from Trakt with episode-level granularity. (Fix 2)
     */
    private class WatchedData(
        val movieIds: Set<String>,
        val episodeMap: Map<String, Set<Pair<Int, Int>>> // showImdbId → set of (season, episode)
    ) {
        fun isMovieWatched(imdbId: String) = imdbId in movieIds
        fun isEpisodeWatched(showImdbId: String, season: Int, episode: Int): Boolean {
            return episodeMap[showImdbId]?.contains(Pair(season, episode)) ?: false
        }
    }

    /**
     * Fetch watched history with episode-level detail.
     * Returns null on failure so callers can skip removal logic. (Fix #7)
     */
    private suspend fun fetchWatchedData(): WatchedData? {
        val movieIds = mutableSetOf<String>()
        val episodeMap = mutableMapOf<String, MutableSet<Pair<Int, Int>>>()
        try {
            val moviesResponse = traktSyncApi.getWatchedMovies()
            if (!moviesResponse.isSuccessful) return null
            moviesResponse.body()?.forEach { movieIds.addAll(localIdsFor(it.movie.ids)) }

            val showsResponse = traktSyncApi.getWatchedShows()
            if (!showsResponse.isSuccessful) return null
            showsResponse.body()?.forEach { show ->
                val showIds = localIdsFor(show.show.ids)
                if (showIds.isEmpty()) return@forEach
                val episodes = mutableSetOf<Pair<Int, Int>>()
                show.seasons?.forEach { season ->
                    season.episodes?.forEach { ep ->
                        episodes.add(Pair(season.number, ep.number))
                    }
                }
                showIds.forEach { episodeMap.getOrPut(it) { mutableSetOf() }.addAll(episodes) }
            }
        } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
            com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to fetch watched history", e)
            return null
        }
        return WatchedData(movieIds, episodeMap)
    }

    /**
     * Delete a playback item from Trakt by finding its Trakt playback ID.
     * Called when user clears progress in Lumera.
     */
    /**
     * Delete playback items from Trakt. Uses pendingDeletes to prevent
     * the sync poll from re-adding them during the delete operation. (Fix 5)
     */
    suspend fun deletePlaybackFromTrakt(localId: String) {
        if (traktAuthManager.getAccessToken() == null) return
        val normalizedId = normalizePlaybackId(localId)
        pendingDeletes.add(normalizedId)
        pendingDeletes.add(localId)
        withContext(Dispatchers.IO) {
            try {
                val response = traktSyncApi.getPlaybackProgress()
                if (!response.isSuccessful) return@withContext
                val items = response.body() ?: return@withContext

                for (item in items) {
                    val traktId = when (item.type) {
                        "movie" -> localIdFor(item.movie?.ids)
                        "episode" -> {
                            val showImdb = localIdFor(item.show?.ids) ?: continue
                            val ep = item.episode ?: continue
                            "$showImdb:${ep.season}:${ep.number}"
                        }
                        else -> null
                    } ?: continue

                    if (traktId == normalizedId) {
                        val playbackId = item.id ?: continue
                        val deleteResponse = traktSyncApi.deletePlaybackItem(playbackId)
                        Log.d(TAG, "Deleted playback $playbackId from Trakt: ${deleteResponse.code()}")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w(TAG, "Failed to delete playback from Trakt", e)
            } finally {
                pendingDeletes.remove(normalizedId)
                pendingDeletes.remove(localId)
            }
        }
    }

    private suspend fun pullFromTrakt(items: List<TraktWatchlistItem>) {
        for (item in items) {
            val (id, title, type) = when (item.type) {
                "movie" -> Triple(
                    localIdFor(item.movie?.ids) ?: continue,
                    item.movie?.title ?: "Unknown",
                    "movie"
                )
                "show" -> Triple(
                    localIdFor(item.show?.ids) ?: continue,
                    item.show?.title ?: "Unknown",
                    "series"
                )
                else -> continue
            }

            dao.addToWatchlist(
                WatchlistEntity(
                    profileId = profileId,
                    id = id,
                    type = type,
                    title = title,
                    poster = null,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun parseIsoTimestamp(iso: String?): Long {
        if (iso == null) return System.currentTimeMillis()
        return try {
            java.time.Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
