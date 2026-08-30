package net.filmix.core.model

/** A published release, as described by BUILD/latest.json. */
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val commit: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val notes: String,
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
}
