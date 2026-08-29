package net.filmix.core.model

/**
 * One stored playback position, quality-independent (see [StreamLink.resumeKey]).
 * A stored row always means at least the minimum-resume threshold was watched —
 * shorter watches are deleted at save time — so any row that is not finished
 * counts as "in progress".
 */
data class WatchProgress(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    /** Near the end counts as finished; credits should not demand a rewatch. */
    val isFinished: Boolean
        get() = durationMs > 0 && positionMs > durationMs - FINISHED_TAIL_MS

    companion object {
        const val FINISHED_TAIL_MS = 60_000L
    }
}

enum class EpisodeWatchState { None, InProgress, Finished }

/**
 * Watch state of one resolved (season, translation) pair: per-episode marks
 * plus the episode the user should be offered next.
 */
data class SeasonWatch(
    /** Keyed by [Episode.number]. Episodes without progress are absent. */
    val states: Map<String, EpisodeWatchState>,
    /** Never null while the translation has episodes. */
    val current: Episode?,
    /** True when [current] is partway through — "continue" rather than "start". */
    val currentInProgress: Boolean,
)

/** Where in the playlist the user last was: season number and translation name. */
data class PlaylistPosition(val season: String, val translation: String)

object SeriesProgress {

    /**
     * Marks the resolved translation's episodes and picks the current one:
     * the freshest in-progress episode, else the one after the last finished,
     * else the last when everything is finished, else the first.
     *
     * A finished mark is a statement about the content, so it is matched across
     * the season's other translations by episode number — a checkmark earned in
     * one voice-over survives switching to another. An exact-key row still wins
     * over any cross-translation match: a position in *this* file is the truth.
     */
    fun seasonWatch(
        season: Season,
        translation: SeriesTranslation,
        progressByKey: Map<String, WatchProgress>,
    ): SeasonWatch {
        val episodes = translation.episodes
        if (episodes.isEmpty()) return SeasonWatch(emptyMap(), null, false)

        val states = HashMap<String, EpisodeWatchState>()
        var freshest: Episode? = null
        var freshestAt = Long.MIN_VALUE

        for (episode in episodes) {
            val exact = progressByKey[episode.resumeKey]
            val matched = exact
                ?: season.translations.asSequence()
                    .filter { it.name != translation.name }
                    .flatMap { it.episodes }
                    .filter { it.number == episode.number }
                    .mapNotNull { progressByKey[it.resumeKey] }
                    // Prefer a finished row, then the freshest, so a stale
                    // half-watch in a third voice-over cannot hide a checkmark.
                    .maxWithOrNull(compareBy({ it.isFinished }, { it.updatedAt }))
                ?: continue

            states[episode.number] =
                if (matched.isFinished) EpisodeWatchState.Finished else EpisodeWatchState.InProgress
            if (!matched.isFinished && matched.updatedAt > freshestAt) {
                freshest = episode
                freshestAt = matched.updatedAt
            }
        }

        val current = freshest ?: run {
            val lastFinished = episodes.indexOfLast { states[it.number] == EpisodeWatchState.Finished }
            when {
                lastFinished < 0 -> episodes.first()
                lastFinished + 1 < episodes.size -> episodes[lastFinished + 1]
                else -> episodes.last()
            }
        }
        return SeasonWatch(
            states = states,
            current = current,
            currentInProgress = states[current.number] == EpisodeWatchState.InProgress,
        )
    }

    /**
     * Where to land when the detail screen opens: the season and translation of
     * the freshest stored row — advanced to the next season once its own last
     * episode is finished. Null when nothing in the playlist has been played.
     */
    fun resumePoint(
        playlist: SeriesPlaylist,
        progressByKey: Map<String, WatchProgress>,
    ): PlaylistPosition? {
        var atSeason: Season? = null
        var atTranslation: SeriesTranslation? = null
        var atEpisode: Episode? = null
        var atProgress: WatchProgress? = null

        for (season in playlist.seasons) {
            for (translation in season.translations) {
                for (episode in translation.episodes) {
                    val progress = progressByKey[episode.resumeKey] ?: continue
                    if (atProgress == null || progress.updatedAt > atProgress.updatedAt) {
                        atSeason = season
                        atTranslation = translation
                        atEpisode = episode
                        atProgress = progress
                    }
                }
            }
        }
        val season = atSeason ?: return null
        val translation = atTranslation ?: return null

        if (atProgress?.isFinished == true && atEpisode == translation.episodes.lastOrNull()) {
            val next = playlist.seasons.getOrNull(playlist.seasons.indexOf(season) + 1)
            if (next != null) {
                val carried = next.translations.firstOrNull { it.name == translation.name }
                    ?: next.translations.firstOrNull()
                return PlaylistPosition(next.number, carried?.name ?: translation.name)
            }
        }
        return PlaylistPosition(season.number, translation.name)
    }
}
