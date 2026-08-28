package net.filmix.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.filmix.core.model.ParsedTranslation
import net.filmix.core.model.StreamLink
import net.filmix.core.model.VideoSource
import net.filmix.core.network.FilmixApi

/** Everything needed to start playback of one source. */
data class PlaybackRequest(
    val postId: Int,
    val title: String,
    val streamUrl: String,
    val translation: String,
    val quality: Int,
    val startPositionMs: Long,
)

class PlaybackRepository(
    private val api: FilmixApi,
    private val resumeStore: ResumeStore,
    private val settings: SettingsStore,
) {

    /**
     * Resolves a source to a concrete URL at the user's preferred quality
     * (falling back to the best available) and looks up any saved position.
     */
    suspend fun prepare(postId: Int, title: String, source: VideoSource): PlaybackRequest? {
        val preferred = settings.preferredQuality()
        val quality = StreamLink.selectQuality(source, preferred) ?: return null
        val url = source.urlFor(quality)
        return PlaybackRequest(
            postId = postId,
            title = title,
            streamUrl = url,
            translation = source.rawTranslation,
            quality = quality,
            startPositionMs = resumeStore.resumeFor(url) ?: 0L,
        )
    }

    suspend fun saveProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long) {
        resumeStore.save(request.streamUrl, request.postId, positionMs, durationMs)
    }

    /**
     * Best-effort history reporting. A failure here must never surface to the
     * user — playback has already happened either way.
     *
     * The reference app sends the bare voice-over name ("vo"), not the full
     * bracketed label, and the position in seconds; both are mirrored here.
     * `Response` does not throw on HTTP errors, so they are logged explicitly —
     * this call failing looks like nothing at all from the UI.
     *
     * Returns whether the server accepted the report, so the caller can tell
     * screens that already hold a history list to reload.
     */
    suspend fun reportWatched(request: PlaybackRequest, season: String = "0", episode: String = "0"): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                api.addWatched(
                    id = request.postId,
                    translation = ParsedTranslation.from(request.translation).voice,
                    season = season,
                    episode = episode,
                    time = request.startPositionMs / 1000,
                    quality = request.quality,
                )
            }.fold(
                onSuccess = { it.isSuccessful.also { ok -> if (!ok) Log.w(TAG, "add_watched HTTP ${it.code()}") } },
                onFailure = { Log.w(TAG, "add_watched failed", it); false },
            )
        }

    private companion object {
        const val TAG = "PlaybackRepository"
    }
}
