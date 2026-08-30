package net.filmix.core.model

/** One release's notes, as authored in CHANGELOG.md. */
data class ReleaseNote(
    val versionCode: Int,
    val versionName: String,
    val notes: List<String>,
)

/** A published release, as described by BUILD/latest.json. */
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val commit: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val notes: String,
    /**
     * Every release the manifest still carries, not only this one. Defaulted so
     * a manifest written before this existed still parses.
     */
    val changelog: List<ReleaseNote> = emptyList(),
) {
    /**
     * Compared by versionCode, never by name: names repeat during development
     * while the code is derived from the commit count and only increases.
     */
    fun isNewerThan(current: AppVersion): Boolean = versionCode > current.code

    /**
     * Whether the launch prompt should be raised, given the versionCode the
     * user last answered "Позже" to (0 on a fresh install).
     *
     * Strictly greater, so declining a release suppresses that release only —
     * the next one still gets through. Compared by code for the same reason
     * [isNewerThan] is.
     */
    fun shouldPrompt(dismissedVersionCode: Int): Boolean = versionCode > dismissedVersionCode

    /**
     * What the user has missed: every release newer than the one installed,
     * newest first.
     *
     * A device can sit several versions behind — the whole reason the launch
     * check exists — and showing only the newest release's notes hides what the
     * ones in between fixed.
     *
     * Falls back to a single entry built from [notes] when the manifest carries
     * no changelog, so a new client reading an old manifest still says
     * something rather than nothing. Filtered by code, never by name, for the
     * reason [isNewerThan] gives.
     */
    fun changesSince(current: AppVersion): List<ReleaseNote> = changesSince(current.code)

    /** As above, for the call sites that hold only the code. */
    fun changesSince(currentCode: Int): List<ReleaseNote> {
        if (changelog.isEmpty()) {
            return if (notes.isBlank()) {
                emptyList()
            } else {
                listOf(ReleaseNote(versionCode, versionName, listOf(notes)))
            }
        }
        return changelog
            .filter { it.versionCode > currentCode }
            .sortedByDescending { it.versionCode }
    }

    val sizeLabel: String
        get() = if (sizeBytes <= 0) "" else "%.1f МБ".format(sizeBytes / 1048576.0)
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val percent: Int) : UpdateState
    data class ReadyToInstall(val update: AppUpdate) : UpdateState
    data class Failed(val message: String) : UpdateState

    /**
     * Whether the plain "check for updates" button belongs on screen.
     *
     * UpToDate must be included. Nothing ever returns the state to Idle, so
     * hiding the button there stranded it for the life of the process: a check
     * that found nothing could not be repeated, while a failed one could. It
     * also costs the remote its cursor, since the button it was on leaves
     * composition.
     *
     * Failed and the download states carry their own buttons.
     */
    val offersCheck: Boolean
        get() = this is Idle || this is Checking || this is UpToDate

    /**
     * Which set of controls the section is showing.
     *
     * Coarser than the state on purpose: a download's progress ticks are a new
     * [Downloading] every few percent but the same stage throughout, and the
     * cursor must not be re-claimed twenty times while the bar fills.
     */
    val stage: UpdateStage
        get() = when {
            offersCheck -> UpdateStage.Check
            this is Available -> UpdateStage.Available
            this is Downloading -> UpdateStage.Downloading
            this is ReadyToInstall -> UpdateStage.Ready
            else -> UpdateStage.Failed
        }
}

enum class UpdateStage {
    Check,
    Available,
    Downloading,
    Ready,
    Failed;

    /**
     * Whether the stage has a control to hand the cursor to. A download shows
     * only a progress bar, so there is nothing to focus until it finishes.
     */
    val hasAction: Boolean get() = this != Downloading
}
