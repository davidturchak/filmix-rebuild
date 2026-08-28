package net.filmix.client

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import net.filmix.core.data.AuthRepository
import net.filmix.core.data.CatalogRepository
import net.filmix.core.data.LibraryRepository
import net.filmix.core.data.PlaybackRepository
import net.filmix.core.data.ResumeStore
import net.filmix.core.data.SessionState
import net.filmix.core.data.SettingsStore
import net.filmix.core.data.UpdateRepository
import net.filmix.core.data.TokenStore
import net.filmix.core.network.DeviceParams
import net.filmix.core.network.FilmixApi
import net.filmix.core.model.AppVersion
import net.filmix.core.model.ExternalPlayer
import net.filmix.core.network.NetworkFactory
import net.filmix.feature.catalog.CatalogViewModel
import net.filmix.feature.config.ConfigViewModel
import net.filmix.feature.detail.DetailViewModel
import net.filmix.feature.home.HomeViewModel
import net.filmix.feature.library.HistoryViewModel
import net.filmix.feature.library.LibraryViewModel
import net.filmix.feature.profile.ProfileViewModel
import net.filmix.feature.search.SearchViewModel
import java.util.Locale

/**
 * Hand-wired object graph.
 *
 * The plan named Hilt, and that is still the right call once there are many
 * injection sites. At this size the graph is five objects, and keeping it
 * manual leaves the feature modules free of any DI dependency — they take
 * plain constructor arguments. Swap in Hilt when the feature count grows.
 */
class AppGraph private constructor(context: Context) {

    private val appContext = context.applicationContext

    val tokenStore = TokenStore(appContext)

    val version = AppVersion(
        name = BuildConfig.VERSION_NAME,
        code = BuildConfig.VERSION_CODE,
        gitSha = BuildConfig.GIT_SHA,
        gitDirty = BuildConfig.GIT_DIRTY,
        debug = BuildConfig.DEBUG,
    )

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

    /** Shared so pairing on the profile screen reaches the library screens. */
    val sessionState = SessionState()

    /**
     * For writes that must outlive the screen that started them — the resume
     * position saved while the player is being torn down. A composition scope is
     * already cancelled by then, so those writes were simply dropped.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val authRepository = AuthRepository(api, tokenStore, sessionState)

    val catalogRepository = CatalogRepository(api)

    val settingsStore = SettingsStore(appContext)

    private val resumeStore = ResumeStore(appContext)

    val playbackRepository = PlaybackRepository(api, resumeStore, settingsStore)

    val libraryRepository = LibraryRepository(api, tokenStore)

    val updateRepository = UpdateRepository(appContext, version)

    /** Handed to ConfigViewModel so :feature:config never sees PackageManager. */
    val installedPlayersProvider: suspend () -> List<ExternalPlayer> = {
        withContext(Dispatchers.IO) { ExternalPlayers.installed(appContext) }
    }

    @SuppressLint("HardwareIds")
    private fun androidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    /** Matches the reference app: MODEL alone when it already carries the vendor. */
    private fun deviceName(): String {
        val vendor = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(vendor)) model else "$vendor $model"
    }

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        /**
         * One graph per process, not one per Activity.
         *
         * ViewModels are retained across an Activity recreation and this
         * Activity does not declare locale or font scale in `configChanges`, so
         * a per-Activity graph handed the retained library screens a
         * [SessionState] that nobody would write to again — and pairing stopped
         * reaching them, which is the exact staleness the signal exists to
         * prevent. Holding the application context, so this is not a leak.
         */
        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}

/** Keeps DI out of the feature modules — they take plain constructor arguments. */
class GraphViewModelFactory(private val graph: AppGraph) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(CatalogViewModel::class.java) ->
            CatalogViewModel(graph.catalogRepository, graph.settingsStore) as T

        modelClass.isAssignableFrom(DetailViewModel::class.java) ->
            DetailViewModel(graph.catalogRepository, graph.libraryRepository) as T

        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(graph.catalogRepository) as T

        modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
            HistoryViewModel(graph.libraryRepository, graph.sessionState) as T

        modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
            LibraryViewModel(graph.libraryRepository, graph.sessionState) as T

        modelClass.isAssignableFrom(SearchViewModel::class.java) ->
            SearchViewModel(graph.catalogRepository) as T

        modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
            ProfileViewModel(graph.authRepository) as T

        modelClass.isAssignableFrom(ConfigViewModel::class.java) ->
            ConfigViewModel(
                graph.settingsStore,
                graph.version,
                graph.updateRepository,
                graph.installedPlayersProvider,
            ) as T

        else -> error("Unknown ViewModel ${modelClass.name}")
    }
}
