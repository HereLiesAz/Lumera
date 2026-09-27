package com.hereliesaz.illumera.ui.playback

import android.net.Uri
import com.hereliesaz.illumera.data.model.stremio.Stream
import com.hereliesaz.illumera.data.model.stremio.StreamSubtitle
import com.hereliesaz.illumera.data.player.PlaybackTrackSelectionStore
import com.hereliesaz.illumera.data.player.SourceSelectionStore
import com.hereliesaz.illumera.domain.AddonSubtitle
import com.hereliesaz.illumera.ui.player.PlayerSessionResult
import com.hereliesaz.illumera.ui.player.base.PlayerSourceOption
import com.hereliesaz.illumera.ui.player.base.PlayerSubtitleSource
import kotlinx.coroutines.CancellationException
import java.util.Locale

// Pure helpers that turn addon streams and subtitles into what the player takes, and that
// settle a finished session's remembered source. Moved unchanged from MainActivity.
internal const val SOURCE_SELECTION_COMMIT_MIN_POSITION_MS = 5_000L
internal const val SOURCE_SELECTION_FAILURE_RESET_MAX_POSITION_MS = 1_000L

internal data class PlayerSubtitlePayload(
    val id: String,
    val url: String,
    val name: String,
    val language: String?
)

internal data class PendingSourceSelection(
    val playbackId: String,
    val launchedStream: Stream,
    val candidateStreams: List<Stream>
)

internal data class PendingEpisodeSwitch(
    val playbackId: String,
    val playbackTitle: String,
    val streamRequestId: String,
    val streams: List<Stream>?,
    val addonSubs: List<AddonSubtitle>,
    val playerCurrentSourceUrl: String?
)

internal suspend fun <T> requestOrFallback(
    fallback: T,
    block: suspend () -> T
): T {
    return try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }
}

internal fun resolveSubtitleUrl(rawUrl: String, addonTransportUrl: String?): String? {
    val value = rawUrl.trim()
    if (value.isEmpty()) return null

    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    if (uri.isAbsolute) {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        return value
    }
    if (addonTransportUrl.isNullOrBlank()) return null

    val base = addonTransportUrl.trimEnd('/')
    val path = value.trimStart('/')
    if (path.isEmpty()) return null
    return "$base/$path"
}

internal fun sanitizeSubtitleSourceName(rawName: String?, fallback: String): String {
    val cleaned = rawName
        ?.replace("[", "")
        ?.replace("]", "")
        ?.trim()
        .orEmpty()
    return cleaned.ifEmpty { fallback }
}

internal fun subtitleNameFromUrl(rawUrl: String): String? {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
    val path = uri.path?.substringBefore('?').orEmpty()
    val rawName = path.substringAfterLast('/').ifEmpty { return null }
    val decoded = runCatching { Uri.decode(rawName) }.getOrDefault(rawName)
    val withoutExtension = decoded.substringBeforeLast('.', decoded).trim()
    return withoutExtension.ifEmpty { null }
}

internal fun normalizeSubtitleLanguageTag(rawLang: String?): String? {
    val value = rawLang?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return value.replace('_', '-').lowercase(Locale.ROOT)
}

internal val TORRENT_TRACKERS = listOf(
    // HTTP trackers (TCP — work even when UDP is blocked)
    "http://tracker.opentrackr.org:1337/announce",
    "http://tracker.openbittorrent.com:80/announce",
    "http://tracker1.bt.moack.co.kr:80/announce",
    "http://tracker.gbitt.info:80/announce",
    // UDP trackers (fallback)
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.openbittorrent.com:6969/announce",
    "udp://exodus.desync.com:6969/announce"
)

internal fun resolvePlayableSourceUrl(stream: Stream): String? {
    val directUrl = stream.url?.trim()?.takeIf { it.isNotEmpty() }
    if (directUrl != null) return directUrl

    val infoHash = stream.infoHash?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // Combine hardcoded trackers with addon-provided tracker URLs
    val addonTrackers = stream.sources
        ?.filter { it.startsWith("tracker:") }
        ?.map { it.removePrefix("tracker:") }
        ?: emptyList()
    val allTrackers = (addonTrackers + TORRENT_TRACKERS).distinct()
    val trackerParams = allTrackers.joinToString("") {
        "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}"
    }
    return "magnet:?xt=urn:btih:${infoHash}&dn=Video${trackerParams}"
}

internal fun sourceDisplayLabel(stream: Stream): String {
    val primary = stream.description
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: stream.title
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: stream.name
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: "Source"
    return primary.replace('\n', ' ')
}

internal fun buildPlayerSourceOption(stream: Stream): PlayerSourceOption? {
    val url = resolvePlayableSourceUrl(stream) ?: return null
    val requestHeaders = stream.behaviorHints?.proxyHeaders?.request.orEmpty()
    val headerIdentity = requestHeaders.entries
        .sortedBy { it.key.lowercase(Locale.ROOT) }
        .joinToString("&") { (key, value) -> "$key=$value" }
    val sourceId = listOf(
        stream.addonTransportUrl.orEmpty(),
        url,
        (stream.fileIdx ?: -1).toString(),
        headerIdentity
    ).joinToString("\u001f")

    return PlayerSourceOption(
        id = sourceId,
        url = url,
        label = sourceDisplayLabel(stream),
        name = stream.name,
        title = stream.title,
        description = stream.description,
        fileIdx = stream.fileIdx ?: -1,
        fileName = stream.behaviorHints?.filename ?: "",
        addonTransportUrl = stream.addonTransportUrl,
        addonDisplayName = stream.addonDisplayName,
        requestHeaders = requestHeaders,
        addonStream = stream
    )
}

internal fun buildSourcePayload(
    streams: List<Stream>,
    selectedStream: Stream
): List<PlayerSourceOption> {
    val selectedUrl = resolvePlayableSourceUrl(selectedStream)
    return streams
        .mapNotNull(::buildPlayerSourceOption)
        .distinctBy { it.id }
        .sortedByDescending { option -> option.url == selectedUrl }
}

internal fun canonicalSubtitleUrlForId(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return rawUrl

    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
    val noQuery = trimmed.substringBefore('?').substringBefore('#')
    if (!uri.isAbsolute) return noQuery

    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val host = uri.host?.lowercase(Locale.ROOT)
    val path = uri.encodedPath ?: uri.path
    if (scheme.isNullOrBlank() || host.isNullOrBlank() || path.isNullOrBlank()) {
        return noQuery
    }
    val port = if (uri.port != -1) ":${uri.port}" else ""
    return "$scheme://$host$port$path"
}

internal fun buildSubtitleFallbackId(
    resolvedUrl: String,
    language: String?,
    name: String
): String {
    val canonicalUrl = canonicalSubtitleUrlForId(resolvedUrl)
    val canonicalLanguage = language.orEmpty().trim().lowercase(Locale.ROOT)
    val canonicalName = name.trim().lowercase(Locale.ROOT)
    return "lumera-sub:$canonicalLanguage|$canonicalName|$canonicalUrl"
}

internal fun buildEmbeddedSubtitlePayload(stream: Stream): List<PlayerSubtitlePayload> {
    return stream.subtitles
        .orEmpty()
        .mapNotNull { subtitle ->
            buildEmbeddedSubtitlePayloadItem(stream, subtitle)
        }
}

internal fun buildEmbeddedSubtitlePayloadItem(
    stream: Stream,
    subtitle: StreamSubtitle
): PlayerSubtitlePayload? {
    val rawUrl = subtitle.url?.trim().orEmpty()
    if (rawUrl.isEmpty()) return null

    val resolvedUrl = resolveSubtitleUrl(
        rawUrl = rawUrl,
        addonTransportUrl = subtitle.transportUrl ?: stream.addonTransportUrl
    ) ?: return null

    val fallbackName = subtitleNameFromUrl(resolvedUrl) ?: "Embedded subtitle"
    val name = sanitizeSubtitleSourceName(subtitle.name, fallbackName)
    val language = normalizeSubtitleLanguageTag(subtitle.lang)
    val subtitleId = subtitle.id
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: buildSubtitleFallbackId(
            resolvedUrl = resolvedUrl,
            language = language,
            name = name
        )
    return PlayerSubtitlePayload(
        id = subtitleId,
        url = resolvedUrl,
        name = name,
        language = language
    )
}

internal fun buildAddonSubtitlePayload(addonSubtitles: List<AddonSubtitle>): List<PlayerSubtitlePayload> {
    return addonSubtitles.mapNotNull { subtitle ->
        val resolvedUrl = resolveSubtitleUrl(subtitle.url, addonTransportUrl = null) ?: return@mapNotNull null
        val name = sanitizeSubtitleSourceName(subtitle.addonName, "Addon subtitle")
        val language = normalizeSubtitleLanguageTag(subtitle.lang)
        val subtitleId = subtitle.id
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: buildSubtitleFallbackId(
                resolvedUrl = resolvedUrl,
                language = language,
                name = name
            )
        PlayerSubtitlePayload(
            id = subtitleId,
            url = resolvedUrl,
            name = name,
            language = language
        )
    }
}

internal fun buildSubtitlePayload(stream: Stream, addonSubtitles: List<AddonSubtitle>): List<PlayerSubtitlePayload> {
    return (buildEmbeddedSubtitlePayload(stream) + buildAddonSubtitlePayload(addonSubtitles))
        .distinctBy { payload ->
            val url = payload.url.lowercase(Locale.ROOT)
            val lang = payload.language.orEmpty().lowercase(Locale.ROOT)
            "$url|$lang"
        }
}

internal fun List<PlayerSubtitlePayload>.toPlayerSubtitleSources(): List<PlayerSubtitleSource> =
    map { subtitle ->
        PlayerSubtitleSource(
            id = subtitle.id,
            url = subtitle.url,
            label = subtitle.name,
            language = subtitle.language
        )
    }

internal fun handlePlayerSessionEnd(
    sessionResult: PlayerSessionResult,
    selectedPlaybackId: String,
    playbackTrackSelectionStore: PlaybackTrackSelectionStore,
    sourceSelectionStore: SourceSelectionStore,
    pendingSourceSelection: PendingSourceSelection?,
    onConsumePendingSelection: () -> Unit,
    onResumeHintResolved: (String?) -> Unit,
    rememberSourceSelection: Boolean = true
) {
    val playbackId = selectedPlaybackId.trim()
    if (playbackId.isBlank()) {
        onConsumePendingSelection()
        onResumeHintResolved(null)
        return
    }

    onResumeHintResolved(
        if (!sessionResult.isCompleted && sessionResult.positionMs >= SOURCE_SELECTION_COMMIT_MIN_POSITION_MS) {
            playbackId
        } else {
            null
        }
    )

    val hasAudioTrackSelection = !sessionResult.selectedAudioTrackId.isNullOrBlank()
    val hasSubtitleTrackSelection = !sessionResult.selectedSubtitleTrackId.isNullOrBlank()
    val hasSubtitleDelayChange = sessionResult.subtitleDelayMs != 0L
    if (hasAudioTrackSelection || hasSubtitleTrackSelection || hasSubtitleDelayChange) {
        playbackTrackSelectionStore.updateSelection(
            playbackId = playbackId,
            audioTrackId = sessionResult.selectedAudioTrackId,
            subtitleTrackId = sessionResult.selectedSubtitleTrackId,
            subtitleDelayMs = sessionResult.subtitleDelayMs,
            updateAudio = hasAudioTrackSelection,
            updateSubtitle = hasSubtitleTrackSelection,
            updateSubtitleDelay = true
        )
    }

    pendingSourceSelection?.let { pendingSelection ->
        val pendingPlaybackId = pendingSelection.playbackId.trim()
        if (pendingPlaybackId.isNotEmpty()) {
            val selectedStream = sessionResult.selectedSourceUrl
                ?.let { selectedSourceUrl ->
                    pendingSelection.candidateStreams.firstOrNull { candidate ->
                        resolvePlayableSourceUrl(candidate) == selectedSourceUrl
                    }
                }
                ?: pendingSelection.launchedStream

            val shouldCommitSource = rememberSourceSelection && (sessionResult.isCompleted ||
                sessionResult.positionMs >= SOURCE_SELECTION_COMMIT_MIN_POSITION_MS)
            if (shouldCommitSource) {
                sourceSelectionStore.rememberSelection(pendingPlaybackId, selectedStream)
            } else if (sessionResult.positionMs <= SOURCE_SELECTION_FAILURE_RESET_MAX_POSITION_MS) {
                val wasPreferred = sourceSelectionStore.findPreferredStream(
                    playbackId = pendingPlaybackId,
                    streams = listOf(selectedStream)
                ) != null
                if (wasPreferred) {
                    sourceSelectionStore.clearSelection(pendingPlaybackId)
                }
            }
        }
    }

    onConsumePendingSelection()
}
