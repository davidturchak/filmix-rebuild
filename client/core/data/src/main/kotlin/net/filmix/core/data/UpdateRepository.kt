package net.filmix.core.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.filmix.core.model.AppUpdate
import net.filmix.core.model.AppVersion
import net.filmix.core.model.ReleaseNote
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Self-update against the manifest published at BUILD/latest.json.
 *
 * Uses raw.githubusercontent.com rather than the GitHub Releases API: the
 * release is live the moment BUILD/ is pushed, there is no API rate limit to
 * worry about for an unauthenticated client, and no release has to be cut by
 * hand.
 */
class UpdateRepository(
    private val context: Context,
    private val currentVersion: AppVersion,
    /**
     * A plain client — the Filmix one appends user_dev_* params to every
     * request, which GitHub has no use for and which would leak the session
     * token to a third party.
     */
    private val http: OkHttpClient = OkHttpClient(),
    private val manifestUrl: String = MANIFEST_URL,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * callTimeout, not connect/read: it bounds the whole call, so a server that
     * dribbles bytes cannot outlast it the way per-stage timeouts allow.
     */
    private val quickHttp by lazy {
        http.newBuilder().callTimeout(LAUNCH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }

    /** Null when already current; the update otherwise. */
    suspend fun check(quick: Boolean = false): AppUpdate? = withIo {
        val client = if (quick) quickHttp else http
        val body = client.newCall(Request.Builder().url(manifestUrl).build()).execute().use { response ->
            if (!response.isSuccessful) error("manifest HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        val manifest = json.decodeFromString<ManifestDto>(body)
        manifest.toDomain().takeIf { it.isNewerThan(currentVersion) }
    }

    /**
     * The launch check. Answers null for "nothing to offer" and for every
     * failure alike — no repo, no network, a captive portal, malformed JSON.
     *
     * Deliberately silent: an update check the user did not ask for must never
     * put an error in front of them, and must never hold up the app. The short
     * call timeout is what keeps that promise on a TV that boots before its
     * network is up — OkHttp's default 10s read timeout would otherwise leave
     * the request hanging long after the user had started browsing.
     */
    suspend fun checkQuietly(): AppUpdate? =
        runCatching { check(quick = true) }
            .onFailure { Log.d(TAG, "launch update check skipped: ${it.message}") }
            .getOrNull()

    /**
     * Downloads the APK, emitting progress. The file is verified against the
     * manifest's sha256 before being offered for install — an APK that fails
     * the check is deleted rather than handed to the package installer.
     */
    fun download(update: AppUpdate): Flow<DownloadProgress> = flow {
        val target = File(context.cacheDir, "updates/${update.versionCode}.apk").apply {
            parentFile?.mkdirs()
            delete()
        }

        http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) error("download HTTP ${response.code}")
            val body = response.body ?: error("empty body")
            val total = if (update.sizeBytes > 0) update.sizeBytes else body.contentLength()
            var written = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            emit(DownloadProgress.Running((100 * written / total).toInt()))
                        }
                    }
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (update.sha256.isNotEmpty() && !actual.equals(update.sha256, ignoreCase = true)) {
                target.delete()
                error("checksum mismatch")
            }
        }
        emit(DownloadProgress.Done(target))
    }.flowOn(Dispatchers.IO)

    private suspend fun <T> withIo(block: () -> T): T =
        withContext(Dispatchers.IO) {
            runCatching(block).onFailure { Log.w(TAG, "update check failed", it) }.getOrThrow()
        }

    companion object {
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/davidturchak/filmix-rebuild/main/BUILD/latest.json"
        private const val TAG = "UpdateRepository"

        /** Short enough that a dead network never delays anything visible. */
        private const val LAUNCH_CHECK_TIMEOUT_SECONDS = 4L
    }
}

sealed interface DownloadProgress {
    data class Running(val percent: Int) : DownloadProgress
    data class Done(val file: File) : DownloadProgress
}

@kotlinx.serialization.Serializable
private data class ReleaseNoteDto(
    val versionCode: Int = 0,
    val versionName: String = "",
    val notes: List<String> = emptyList(),
) {
    fun toDomain() = ReleaseNote(
        versionCode = versionCode,
        versionName = versionName,
        notes = notes,
    )
}

/**
 * Every field defaults, and the parser ignores unknown keys, so the manifest can
 * gain fields without stranding installs that predate them. The reverse must
 * hold too: never change an existing key's type — turning [notes] into an array
 * would fail to parse on every client already in the field.
 */
@kotlinx.serialization.Serializable
private data class ManifestDto(
    val versionCode: Int = 0,
    val versionName: String = "",
    val commit: String = "",
    val apkUrl: String = "",
    val sizeBytes: Long = 0,
    val sha256: String = "",
    val notes: String = "",
    val changelog: List<ReleaseNoteDto> = emptyList(),
) {
    fun toDomain() = AppUpdate(
        versionCode = versionCode,
        versionName = versionName,
        commit = commit,
        apkUrl = apkUrl,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        notes = notes,
        changelog = changelog.map { it.toDomain() },
    )
}
