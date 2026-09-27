package com.hereliesaz.illumera.ui.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.hereliesaz.illumera.data.model.stremio.MetaVideo
import com.hereliesaz.illumera.data.model.stremio.Stream
import com.hereliesaz.illumera.data.torrent.TorrentProgress
import com.hereliesaz.illumera.ui.player.base.PlayerSourceOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Playback state for the activity: what is playing, its sources and subtitles, the torrent
 * behind it and the autoplay/queue flags. Scoped to the activity, so it survives
 * configuration changes; the plain ids, titles and flags also survive process death through
 * [SavedStateHandle]. MainActivity still drives it (PR 1a moves only the storage).
 */
@HiltViewModel
class PlaybackSessionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val torrent: TorrentStreamController
) : ViewModel() {

    // Saved across process death (primitives and strings only).
    var selectedVideoUrl by saved("selectedVideoUrl", "")
    var selectedTrailerAudioUrl by saved("selectedTrailerAudioUrl", "")
    var selectedPlaybackId by saved("selectedPlaybackId", "")
    var selectedPlaybackType by saved("selectedPlaybackType", "movie")
    var selectedPlaybackTitle by saved("selectedPlaybackTitle", "")
    var selectedPlaybackPoster by saved("selectedPlaybackPoster", "")

    // Autoplay has two owners. Playback the queue started on its own follows the
    // queue's mode: a whole-show item plays straight through, an episode item plays
    // once and hands back to the queue. Anything the viewer starts themselves
    // follows Settings → Autoplay Next Episode, whatever the queue is set to.
    // queueStartPending marks the next play as the queue's; queuePlaybackActive
    // says the current one is.
    var queueWholeShowActive by saved("queueWholeShowActive", false)
    var queueStartPending by saved("queueStartPending", false)
    var queuePlaybackActive by saved("queuePlaybackActive", false)

    // Kept across configuration changes only.
    var torrentProgress: TorrentProgress?
        get() = torrent.progress
        set(value) { torrent.progress = value }
    // What the app is doing around playback (fallback, debrid wait); cleared on the next first frame.
    var playbackStatus by mutableStateOf<String?>(null)
    // The episode an autoplay hand-off opened, until its first frame draws.
    var awaitingFirstFrameId by mutableStateOf<String?>(null)

    internal var selectedPlayerSubtitles by mutableStateOf<List<PlayerSubtitlePayload>>(emptyList())
    var selectedPlayerSources by mutableStateOf<List<PlayerSourceOption>>(emptyList())
    internal var pendingSourceSelection by mutableStateOf<PendingSourceSelection?>(null)
    var showPlayerChoiceDialog by mutableStateOf(false)
    var currentEpisodeList by mutableStateOf<List<MetaVideo>>(emptyList())
    var currentStream by mutableStateOf<Stream?>(null)
    internal var pendingEpisodeSwitch by mutableStateOf<PendingEpisodeSwitch?>(null)
    var isEpisodeSwitchLoading by mutableStateOf(false)
    var episodeSwitchJob: Job? = null
    var episodeSwitchGeneration: Long = 0L

    /**
     * Streams [magnet] through TorrentService. The service's callbacks write into this
     * session; by default a ready stream becomes [selectedVideoUrl].
     */
    fun startTorrent(
        magnet: String,
        fileIdx: Int,
        fileName: String,
        errorContext: String = "Stream error",
        clearProgressOnError: Boolean = true,
        onError: (String) -> Unit = {},
        onReady: (String) -> Unit = { localUrl -> selectedVideoUrl = localUrl }
    ) {
        torrent.start(
            magnet = magnet,
            fileIdx = fileIdx,
            fileName = fileName,
            errorContext = errorContext,
            clearProgressOnError = clearProgressOnError,
            onError = onError,
            onReady = onReady
        )
    }

    fun stopTorrent() {
        torrent.stop()
    }

    /**
     * The coroutine scope that runs the episode-switch, watchdog and fallback jobs belongs to
     * MainActivity's composition, so those jobs die with it. Drops the state they would have
     * settled, so a recreated screen doesn't wait on a job that no longer exists.
     */
    fun onUiJobsCancelled() {
        episodeSwitchGeneration += 1L
        episodeSwitchJob?.cancel()
        episodeSwitchJob = null
        if (pendingEpisodeSwitch?.streams == null) pendingEpisodeSwitch = null
        isEpisodeSwitchLoading = false
        playbackStatus = null
        awaitingFirstFrameId = null
    }

    override fun onCleared() {
        torrent.release()
    }

    private fun <T : Any> saved(key: String, default: T): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            private val state = mutableStateOf(savedStateHandle.get<T>(key) ?: default)

            override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                state.value = value
                savedStateHandle[key] = value
            }
        }
}
