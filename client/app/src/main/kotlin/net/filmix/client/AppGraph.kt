package net.filmix.client

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.filmix.core.data.AuthRepository
import net.filmix.core.data.CatalogRepository
import net.filmix.core.data.PlaybackRepository
import net.filmix.core.data.ResumeStore
import net.filmix.core.data.SettingsStore
import net.filmix.core.data.TokenStore
import net.filmix.core.network.DeviceParams
import net.filmix.core.network.FilmixApi
import net.filmix.core.network.NetworkFactory
import net.filmix.feature.detail.DetailViewModel
import net.filmix.feature.home.HomeViewModel
import net.filmix.feature.profile.ProfileViewModel
import java.util.Locale

/**
 * Hand-wired object graph.
 *
 * The plan named Hilt, and that is still the right call once there are many
 * injection sites. At this size the graph is five objects, and keeping it
 * manual leaves the feature modules free of any DI dependency — they take
 * plain constructor arguments. Swap in Hilt when the feature count grows.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val tokenStore = TokenStore(appContext)

    private val deviceParams = DeviceParams(
        deviceId = androidId(appContext),
        deviceName = deviceName(),
        vendor = Build.MANUFACTURER,
        osVersion = Build.VERSION.RELEASE,
        appVersion = BuildConfig.VERSION_NAME,
        language = Locale.getDefault().language,
    )

    private val okHttp = NetworkFactory.okHttp(
        deviceParams = deviceParams,
        tokenProvider = tokenStore.asTokenProvider(),
        debugLogging = BuildConfig.DEBUG,
    )

    val api: FilmixApi = NetworkFactory.filmixApi(okHttp)

    val authRepository = AuthRepository(api, tokenStore)

    val catalogRepository = CatalogRepository(api)

    val settingsStore = SettingsStore(appContext)

    private val resumeStore = ResumeStore(appContext)

    val playbackRepository = PlaybackRepository(api, resumeStore, settingsStore)

    @SuppressLint("HardwareIds")
    private fun androidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    /** Matches the reference app: MODEL alone when it already carries the vendor. */
    private fun deviceName(): String {
        val vendor = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(vendor)) model else "$vendor $model"
    }
}

/** Keeps DI out of the feature modules — they take plain constructor arguments. */
class GraphViewModelFactory(private val graph: AppGraph) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(DetailViewModel::class.java) ->
            DetailViewModel(graph.catalogRepository) as T

        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(graph.catalogRepository) as T

        modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
            ProfileViewModel(graph.authRepository) as T

        else -> error("Unknown ViewModel ${modelClass.name}")
    }
}
