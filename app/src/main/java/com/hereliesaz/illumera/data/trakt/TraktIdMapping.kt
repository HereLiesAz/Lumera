package com.hereliesaz.illumera.data.trakt

import com.hereliesaz.illumera.data.model.WatchHistoryEntity
import com.hereliesaz.illumera.data.model.trakt.TraktIds

private val IMDB_ID = Regex("tt\\d+")

/**
 * Maps a local title id to the ids Trakt accepts in a sync body.
 * "tt123" → imdb, "tmdb:456" → tmdb. Episode ids ("tt123:1:2", "tmdb:456:1:2[:idx]")
 * map to their show. Anything else (addon-private ids) has no Trakt identity → null.
 */
fun traktIdsFor(id: String): TraktIds? {
    if (id.startsWith("tmdb:")) {
        val tmdb = id.removePrefix("tmdb:").substringBefore(':').toIntOrNull() ?: return null
        return TraktIds(tmdb = tmdb)
    }
    val base = id.substringBefore(':')
    return if (IMDB_ID.matches(base)) TraktIds(imdb = base) else null
}

/** The local id Lumera stores for a Trakt item: IMDb when known, else "tmdb:N". */
fun localIdFor(ids: TraktIds?): String? =
    ids?.imdb?.takeIf { IMDB_ID.matches(it) } ?: ids?.tmdb?.let { "tmdb:$it" }

/** Every local id a Trakt item could be saved under (IMDb and/or "tmdb:N"). */
fun localIdsFor(ids: TraktIds?): List<String> = listOfNotNull(
    ids?.imdb?.takeIf { IMDB_ID.matches(it) },
    ids?.tmdb?.let { "tmdb:$it" }
)

/**
 * Strip stream index suffix from playback ID for Trakt lookup.
 * "tt123:1:3:0" → "tt123:1:3"  (has stream index)
 * "tt123:1:3"   → "tt123:1:3"  (already normalized)
 * "tt123"       → "tt123"      (movie, no change)
 */
internal fun normalizePlaybackId(id: String): String {
    val parts = id.split(":")
    // Episode with stream index: 4+ parts where last is numeric (stream idx)
    // and second-to-last and third-to-last are also numeric (episode, season)
    if (parts.size >= 4 &&
        parts.last().toIntOrNull() != null &&
        parts[parts.size - 2].toIntOrNull() != null &&
        parts[parts.size - 3].toIntOrNull() != null
    ) {
        return parts.dropLast(1).joinToString(":")
    }
    return id
}

/** A watched title/episode pulled from Trakt, with every local id it may already live under. */
internal data class TraktWatchedEntry(
    val localIds: List<String>,
    val entity: WatchHistoryEntity
)

/**
 * Merges Trakt's watched list into local history and returns only the rows to write.
 * - Not present locally (under any of its ids, stream index ignored) → insert as watched.
 * - Present and already watched → untouched.
 * - Present, in progress, and touched locally after Trakt's watch → untouched (newer local state,
 *   e.g. a rewatch in progress).
 * - Present, in progress, older than Trakt's watch → marked watched, keeping its position/duration.
 */
internal fun mergeTraktWatchedHistory(
    local: List<WatchHistoryEntity>,
    incoming: List<TraktWatchedEntry>
): List<WatchHistoryEntity> {
    val byNormalizedId = HashMap<String, WatchHistoryEntity>(local.size)
    for (row in local) {
        val key = normalizePlaybackId(row.id)
        // Prefer the exact (un-suffixed) row when several stream variants exist.
        if (row.id == key || key !in byNormalizedId) byNormalizedId[key] = row
    }
    val writes = LinkedHashMap<String, WatchHistoryEntity>()
    for (entry in incoming) {
        val existing = entry.localIds.firstNotNullOfOrNull { byNormalizedId[it] }
        when {
            existing == null -> writes.putIfAbsent(entry.entity.id, entry.entity)
            existing.watched -> Unit
            existing.lastWatched > entry.entity.lastWatched -> Unit
            else -> writes[existing.id] = existing.copy(watched = true, scrobbled = true)
        }
    }
    return writes.values.toList()
}
