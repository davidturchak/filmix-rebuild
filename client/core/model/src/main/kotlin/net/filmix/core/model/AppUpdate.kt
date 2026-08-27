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
}
