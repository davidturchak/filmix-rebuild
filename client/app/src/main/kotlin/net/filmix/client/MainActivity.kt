package net.filmix.client

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.window.core.layout.WindowWidthSizeClass
import net.filmix.core.designsystem.theme.FilmixTheme
import net.filmix.core.model.Post
import net.filmix.core.model.VideoSource
import net.filmix.core.model.resolveVoiceLanguage
import net.filmix.feature.catalog.CatalogScreen
import net.filmix.feature.catalog.CatalogViewModel
import net.filmix.feature.config.ConfigScreen
import net.filmix.feature.config.ConfigViewModel
import net.filmix.feature.config.UpdatePrompt
import net.filmix.feature.detail.DetailScreen
import net.filmix.feature.detail.DetailViewModel
import net.filmix.feature.home.HomeScreen
import net.filmix.feature.home.HomeViewModel
import net.filmix.feature.library.HistoryScreen
import net.filmix.feature.library.HistoryViewModel
import net.filmix.feature.library.LibraryScreen
import net.filmix.feature.library.LibraryViewModel
import net.filmix.feature.profile.ProfileScreen
import net.filmix.feature.profile.ProfileViewModel
import net.filmix.feature.search.SearchScreen
import net.filmix.feature.search.SearchViewModel
import java.util.Locale

/**
 * What is playing plus where it sits in a series. Season and episode exist only
 * for history reporting — the reference app sends "0" for films.
 */
private data class ActivePlayback(
    val post: Post,
    val source: VideoSource,
    val season: String = "0",
    val episode: String = "0",
)

class MainActivity : ComponentActivity() {

    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = AppGraph.get(this)
        // UiModeManager is the authoritative signal — a TV reports 960x540dp,
        // *less* height than the tablet, so a width breakpoint would classify
        // it as an ordinary medium screen and apply arm's-length metrics.
        val isTv = (getSystemService(UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        enableEdgeToEdge()
        setContent {
            FilmixTheme(darkTheme = true, isTv = isTv) {
                FilmixApp(graph)
            }
        }
    }
}

@Composable
private fun FilmixApp(graph: AppGraph) {
    // rememberSaveable so the tab survives activity recreation (rotation,
    // process death restore) rather than snapping back to Home.
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }

    // Detail is an overlay rather than a tab: it covers whichever tab the user
    // came from, so back returns them there with that tab's state intact.
    // Navigation-Compose takes this over when search and the player land.
    var openPostId by rememberSaveable { mutableStateOf<Int?>(null) }

    // Playback sits above detail. Held as objects rather than ids because the
    // source list only exists on the loaded post.
    var playing by remember { mutableStateOf<ActivePlayback?>(null) }

    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val compact = widthClass == WindowWidthSizeClass.COMPACT
    val factory = remember(graph) { GraphViewModelFactory(graph) }

    // Detail and the player replace the tab rather than sitting on top of it, so
    // the tab's content leaves composition and its rememberSaveable state — a
    // grid's scroll offset above all — would be thrown away. The holder keeps
    // each tab's state per key, so coming back lands where the user left, and
    // so does switching tabs.
    val tabState = rememberSaveableStateHolder()

    // Built here rather than inside the Настройки branch: its init runs the
    // silent launch check, and a ViewModel constructed only when that screen
    // first composes would never check on a device nobody opens it on — which
    // is the whole reason the launch check exists. The store owner is the
    // activity either way, so Настройки gets this same instance.
    //
    // Above the detail and player branches, too: those return early, and a
    // process killed on a detail screen restores straight back into one — the
    // session that most needs the check would otherwise never run it. Only the
    // prompt waits below, so nothing is raised over playback.
    val configVm: ConfigViewModel = viewModel(factory = factory)

    // Read here rather than in the Search branch: the setting lives on the
    // hoisted Настройки ViewModel, and both screens need the same answer — the
    // search screen to listen in it, Настройки to show which chip is on.
    val voiceLanguage by configVm.voiceLanguage.collectAsState()
    val systemLanguageTag = remember { Locale.getDefault().toLanguageTag() }
    val voiceLanguageTag = resolveVoiceLanguage(voiceLanguage, systemLanguageTag)

    val active = playing
    if (active != null) {
        PlaybackHost(
            post = active.post,
            source = active.source,
            repository = graph.playbackRepository,
            settings = graph.settingsStore,
            saveScope = graph.appScope,
            season = active.season,
            episode = active.episode,
            onWatched = graph.libraryRepository::noteHistoryChanged,
            onExit = { playing = null },
        )
        return
    }

    val postId = openPostId
    if (postId != null) {
        BackHandler { openPostId = null }
        val detailVm: DetailViewModel = viewModel(factory = factory)
        val detailState by detailVm.state.collectAsState()
        val selection by detailVm.selection.collectAsState()
        val detailComments by detailVm.comments.collectAsState()
        val detailProgress by detailVm.progress.collectAsState()
        LaunchedEffect(postId) { detailVm.load(postId) }
        DetailScreen(
            state = detailState,
            compact = compact,
            comments = detailComments,
            // Detail renders outside AppScaffold, so it needs its own insets —
            // without them the season chips sit under the status bar and the
            // top row of the picker swallows taps.
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            onBack = { openPostId = null },
            onRelatedClick = { openPostId = it.id },
            onPlay = { source ->
                detailState.post?.let { playing = ActivePlayback(it, source) }
            },
            onToggleFavourite = detailVm::toggleFavourite,
            onToggleWatchLater = detailVm::toggleWatchLater,
            onVote = detailVm::vote,
            selection = selection,
            progress = detailProgress,
            onSelectSeason = detailVm::selectSeason,
            onSelectTranslation = detailVm::selectTranslation,
            onPlayEpisode = { episode ->
                detailState.post?.let { post ->
                    // The episode alone does not know its season; the picker's
                    // current selection does — resolve() applies the same
                    // fallbacks the picker itself displays with.
                    val season = selection.resolve(post.playlist)?.first?.number ?: "0"
                    playing = ActivePlayback(post, episode.source, season, episode.number)
                }
            },
        )
        return
    }

    // On a remote, BACK is the only way out of a tab, and it was leaving the
    // app: one press from the catalog dropped the user on the Google TV
    // launcher mid-browse. It now walks back to Home, and only leaves from
    // there — where BACK meaning "exit" is what a user expects.
    var railFocusTick by remember { mutableStateOf(0) }
    BackHandler(enabled = destination != Destination.Home) {
        destination = Destination.Home
        // The rail selects on focus, so the ring has to follow the pill.
        railFocusTick++
    }

    val launchUpdate by configVm.launchUpdate.collectAsState()

    // Whether Настройки was opened to finish an update rather than visited.
    // It decides who holds the cursor there, so it lasts exactly as long as
    // that errand: leaving the tab is the user saying they came for something
    // else, and the card must not grab the cursor off them next time.
    var updateAccepted by remember { mutableStateOf(false) }
    LaunchedEffect(destination) {
        if (destination != Destination.Config) updateAccepted = false
    }

    launchUpdate?.let { update ->
        UpdatePrompt(
            update = update,
            installed = configVm.version,
            onAccept = {
                configVm.acceptLaunchUpdate()
                // The download, the install-permission prompt and the installer
                // handoff all already live on the Настройки screen; sending the
                // user there reuses that path instead of duplicating it.
                destination = Destination.Config
                // The cursor goes to the rail only because the dialog's button
                // left composition and something must hold it — the update card
                // takes it back as soon as the card has a control to give,
                // which a running download does not.
                railFocusTick++
                updateAccepted = true
            },
            onDismiss = configVm::dismissLaunchUpdate,
        )
    }

    AppScaffold(
        current = destination,
        compact = compact,
        onSelect = { destination = it },
        railFocusTick = railFocusTick,
    ) { modifier ->
        tabState.SaveableStateProvider(destination.name) {
            when (destination) {
                Destination.Home -> {
                    val vm: HomeViewModel = viewModel(factory = factory)
                    val homeState by vm.state.collectAsState()
                    // The rails were loaded when the process started, and on
                    // the TV that can be days ago: the launcher resumes the
                    // backgrounded app rather than relaunching it. The
                    // ViewModel decides whether they are old enough to refetch.
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshIfStale() }
                    HomeScreen(
                        state = homeState,
                        compact = compact,
                        modifier = modifier,
                        onRetry = vm::refresh,
                        onPostClick = { openPostId = it.id },
                        onPlayClick = { openPostId = it.id },
                    )
                }

                Destination.Catalog -> {
                    val vm: CatalogViewModel = viewModel(factory = factory)
                    val sort by vm.sort.collectAsState()
                    val filterOptions by vm.filterOptions.collectAsState()
                    val items = vm.items.collectAsLazyPagingItems()
                    CatalogScreen(
                        items = items,
                        sort = sort.order,
                        direction = sort.direction,
                        filter = sort.filter,
                        filterOptions = filterOptions,
                        compact = compact,
                        modifier = modifier,
                        onSortChange = vm::setSort,
                        onDirectionToggle = vm::toggleDirection,
                        onFilterChange = vm::setFilter,
                        onClearFilter = vm::clearFilter,
                        onPostClick = { openPostId = it.id },
                    )
                }

                Destination.Search -> {
                    val vm: SearchViewModel = viewModel(factory = factory)
                    val q by vm.query.collectAsState()
                    val suggestions by vm.suggestions.collectAsState()
                    val submitted by vm.submittedQuery.collectAsState()
                    val results = vm.results.collectAsLazyPagingItems()
                    SearchScreen(
                        query = q,
                        suggestions = suggestions,
                        results = results,
                        compact = compact,
                        searched = submitted.isNotEmpty(),
                        voiceLanguageTag = voiceLanguageTag,
                        modifier = modifier,
                        onQueryChange = vm::onQueryChange,
                        onSubmit = vm::submit,
                        onClear = vm::clear,
                        onPostClick = { openPostId = it.id },
                        onVoiceResult = vm::submitVoiceResult,
                    )
                }

                Destination.Favourites -> {
                    val vm: LibraryViewModel = viewModel(factory = factory)
                    val libState by vm.state.collectAsState()
                    LibraryScreen(
                        state = libState,
                        compact = compact,
                        modifier = modifier,
                        onTabChange = vm::selectTab,
                        onPostClick = { openPostId = it.id },
                        onRetry = vm::refresh,
                    )
                }

                Destination.History -> {
                    val vm: HistoryViewModel = viewModel(factory = factory)
                    val historyState by vm.state.collectAsState()
                    HistoryScreen(
                        state = historyState,
                        compact = compact,
                        modifier = modifier,
                        onPostClick = { openPostId = it.id },
                        onRemove = vm::remove,
                        onClearAll = vm::clearAll,
                        onRetry = vm::refresh,
                    )
                }

                Destination.Profile -> {
                    val vm: ProfileViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsState()
                    ProfileScreen(
                        state = state,
                        modifier = modifier,
                        onStartPairing = vm::startPairing,
                        onSignOut = vm::signOut,
                    )
                }

                Destination.Config -> {
                    // The hoisted instance, so the launch check's result is
                    // already sitting in updateState when the user arrives.
                    val vm = configVm
                    val quality by vm.preferredQuality.collectAsState()
                    val players by vm.players.collectAsState()
                    val selectedPlayer by vm.selectedPlayer.collectAsState()
                    val updateState by vm.updateState.collectAsState()
                    val context = LocalContext.current
                    // Both change in other apps while ours is paused: install
                    // permission is granted in Settings, players get installed
                    // or removed in the Play Store — so re-read on resume.
                    // Sampled once at composition, the "open settings" prompt
                    // stayed up after the user had already said yes.
                    var canInstall by remember {
                        mutableStateOf(UpdateInstaller.canRequestInstalls(context))
                    }
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        canInstall = UpdateInstaller.canRequestInstalls(context)
                        vm.refreshPlayers()
                    }
                    ConfigScreen(
                        preferredQuality = quality,
                        players = players,
                        selectedPlayerPackage = selectedPlayer,
                        updateState = updateState,
                        modifier = modifier,
                        claimUpdateFocus = updateAccepted,
                        voiceLanguage = voiceLanguage,
                        systemLanguageTag = systemLanguageTag,
                        onQualityChange = vm::setPreferredQuality,
                        onPlayerChange = vm::setPlayer,
                        onVoiceLanguageChange = vm::setVoiceLanguage,
                        canInstallUpdates = canInstall,
                        onCheckUpdate = vm::checkForUpdate,
                        onDownloadUpdate = vm::downloadUpdate,
                        onInstallUpdate = {
                            vm.downloadedApk?.let { UpdateInstaller.install(context, it) }
                        },
                        onGrantInstallPermission = {
                            UpdateInstaller.openInstallPermissionSettings(context)
                        },
                        version = vm.version,
                    )
                }
            }
        }
    }
}
