package net.filmix.client

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import net.filmix.core.model.ExternalPlayer
import net.filmix.core.model.normalisePlayerList

/**
 * Resolves and launches third-party video players. Lives in :app for the same
 * reason UpdateInstaller does: feature modules stay free of PackageManager and
 * Intent plumbing — Config gets the list through a provider lambda.
 */
object ExternalPlayers {

    /**
     * Representative of what playback actually sends: apps whose filters are
     * scheme- or extension-picky match or miss this the same way they would a
     * real stream URL.
     */
    private fun probeIntent(): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse("https://example.com/video.mp4"), "video/mp4")

    fun installed(context: Context): List<ExternalPlayer> {
        val pm = context.packageManager
        val entries = pm.queryIntentActivities(probeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .map { ExternalPlayer(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        return normalisePlayerList(entries, context.packageName)
    }

    /** The chosen player can be uninstalled at any time; verify before launch. */
    fun isInstalled(context: Context, packageName: String): Boolean =
        probeIntent().setPackage(packageName)
            .resolveActivity(context.packageManager) != null

    fun playbackIntent(
        packageName: String,
        url: String,
        title: String,
        startPositionMs: Long,
    ): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(url), "video/mp4")
        .setPackage(packageName)
        // MX Player conventions; VLC reads title and position too.
        .putExtra("title", title)
        .putExtra("position", startPositionMs.toInt())
        .putExtra("from_start", false)
        // Opt into the position/duration result extras. No NEW_TASK flag: the
        // player must stay in our task or the result never comes back.
        .putExtra("return_result", true)
}
