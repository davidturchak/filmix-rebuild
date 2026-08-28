package net.filmix.core.model

/** A video app the system can hand playback to, as shown in the Config list. */
data class ExternalPlayer(
    val label: String,
    val packageName: String,
)

/**
 * Android TV's dead-end resolver: it matches every media intent solely to
 * show an "unsupported" toast, and lists itself under the label "None".
 * Offering it as a player would be offering a broken remote button.
 */
private val stubPackages = setOf(
    "com.android.tv.frameworkpackagestubs",
    "com.google.android.tv.frameworkpackagestubs",
)

/**
 * The list PackageManager returns, made presentable: without ourselves (we
 * answer the same probe intent we query with) or the TV stub, one entry per
 * package even when an app exports several player activities, ordered for a
 * settings list.
 */
fun normalisePlayerList(entries: List<ExternalPlayer>, selfPackage: String): List<ExternalPlayer> =
    entries
        .filter { it.packageName != selfPackage && it.packageName !in stubPackages }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }

data class ExternalProgress(
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * Decodes the position an external player reported in its activity result.
 *
 * Two conventions cover the players that report at all: MX-compatible apps use
 * "position"/"duration" as Int milliseconds, VLC uses "extra_position"/
 * "extra_duration" as Long. VLC's keys win when both are present — VLC also
 * ships the MX keys but only fills its own. Either type is accepted for any
 * key because third-party players are sloppy about which they write.
 *
 * Null means "nothing usable reported": the caller must leave the stored
 * resume point alone rather than overwrite it with zero.
 */
fun externalProgressFromExtras(extras: Map<String, Any?>): ExternalProgress? {
    val position = extras.msValue("extra_position") ?: extras.msValue("position")
    if (position == null || position <= 0L) return null
    val duration = extras.msValue("extra_duration") ?: extras.msValue("duration") ?: 0L
    return ExternalProgress(position, duration)
}

private fun Map<String, Any?>.msValue(key: String): Long? = when (val value = this[key]) {
    is Long -> value
    is Int -> value.toLong()
    else -> null
}
