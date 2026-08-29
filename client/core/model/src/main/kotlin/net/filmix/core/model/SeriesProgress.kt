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
     * one voice-over survives switching to another. Only finished rows carry
     * across: an in-progress row belongs to the file it was saved for, and
     * surfacing it here would promise a "continue" the player cannot honour
     * (this translation's file has no stored position, so it starts at 0:00).
     * An exact-key row still wins over any cross-translation match: a position
     * in *this* file is the truth.
     */
    fun seasonWatch(
        season: Season,
        translation: SeriesTranslation,
        progressByKey: Map<String, WatchProgress>,
    ): SeasonWatch {
        val episodes = translation.episodes
        if (episodes.isEmpty()) return SeasonWatch(emptyMap(), null, false)

        // One pass over the season's other voice-overs, freshest finished row
        // per episode number — instead of rescanning them all per episode.
        val foreignFinished = HashMap<String, WatchProgress>()
        for (other in season.translations) {
            if (other.name == translation.name) continue
            for (foreign in other.episodes) {
                val progress = progressByKey[foreign.resumeKey] ?: continue
                if (!progress.isFinished) continue
                val key = episodeNumberKey(foreign.number)
                val best = foreignFinished[key]
                if (best == null || progress.updatedAt > best.updatedAt) {
                    foreignFinished[key] = progress
                }
            }
        }

        val states = HashMap<String, EpisodeWatchState>()
        var freshest: Episode? = null
        var freshestAt = Long.MIN_VALUE

        for (episode in episodes) {
            val matched = progressByKey[episode.resumeKey]
                ?: foreignFinished[episodeNumberKey(episode.number)]
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
        val (season, translation, episode, progress) = playlist.seasons
            .asSequence()
            .flatMap { season ->
                season.translations.asSequence().flatMap { translation ->
                    translation.episodes.asSequence().mapNotNull { episode ->
                        progressByKey[episode.resumeKey]?.let {
                            PlayedEpisode(season, translation, episode, it)
                        }
                    }
                }
            }
            .maxByOrNull { it.progress.updatedAt } ?: return null

        // Advance only when the finished episode really ends the season: the
        // freshest translation may be a partial dub, and its last episode says
        // nothing about the episodes other voice-overs still have.
        if (progress.isFinished &&
            episode == translation.episodes.lastOrNull() &&
            isSeasonEnd(season, episode)
        ) {
            val next = playlist.seasons.getOrNull(playlist.seasons.indexOf(season) + 1)
            if (next != null) {
                val carried = next.translations.firstOrNull { it.name == translation.name }
                    ?: next.translations.firstOrNull()
                return PlaylistPosition(next.number, carried?.name ?: translation.name)
            }
        }
        return PlaylistPosition(season.number, translation.name)
    }

    private data class PlayedEpisode(
        val season: Season,
        val translation: SeriesTranslation,
        val episode: Episode,
        val progress: WatchProgress,
    )

    /** True when no translation of the season has an episode past [episode]. */
    private fun isSeasonEnd(season: Season, episode: Episode): Boolean {
        val order = episodeOrder(episode.number)
        return season.translations.none { translation ->
            translation.episodes.any { episodeOrder(it.number) > order }
        }
    }

    /**
     * Episode numbers are raw JSON keys from each translation's own subtree, so
     * "01" in one voice-over and "1" in another are the same episode. Compare
     * numerically when possible, the same accommodation the season/episode
     * sorting already makes.
     */
    private fun episodeNumberKey(number: String): String {
        val trimmed = number.trim()
        return trimmed.toIntOrNull()?.toString() ?: trimmed
    }

    private fun episodeOrder(number: String): Int =
        number.trim().toIntOrNull() ?: Int.MAX_VALUE
}
