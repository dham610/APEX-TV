package com.streamvault.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.streamvault.app.navigation.graph.registerLiveGraph
import com.streamvault.app.ui.components.shell.AppNavigationChrome
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.feature.settings.presentation.BackupImportPreviewDialog
import com.streamvault.core.navigation.AppDestination
import com.streamvault.core.navigation.NavigationActions
import com.streamvault.core.navigation.NavigationOptions
import com.streamvault.core.ui.components.shell.UiDestination
import com.streamvault.feature.settings.navigation.registerSettingsGraph
import com.streamvault.feature.settings.parental.ParentalControlGroupScreen
import com.streamvault.feature.settings.api.SettingsPlatformHost
import com.streamvault.feature.settings.presentation.SettingsScreen
import com.streamvault.feature.playback.api.PlaybackPlatformHost
import com.streamvault.feature.playback.navigation.registerPlaybackGraph
import com.streamvault.feature.provider.api.ProviderBackupPreviewRequest
import com.streamvault.feature.provider.navigation.registerProviderGraph
import com.streamvault.app.navigation.toLivePlayerRequest
import com.streamvault.app.navigation.toPlayerNavigationRequest
import com.streamvault.feature.catalog.api.CatalogChannelPlaybackContext
import com.streamvault.feature.catalog.api.CatalogDashboardShelfCustomizationContent
import com.streamvault.feature.catalog.api.CatalogNavigationChrome
import com.streamvault.feature.catalog.api.CatalogPlatformHost
import com.streamvault.feature.catalog.api.CatalogScaffoldContent
import com.streamvault.feature.catalog.navigation.registerCatalogGraph
import com.streamvault.feature.settings.presentation.DashboardShelfCustomizationDialog
import com.streamvault.domain.model.ContentType
import com.streamvault.feature.system.api.SystemScaffoldContent
import com.streamvault.feature.system.navigation.SystemRoutePatterns
import com.streamvault.feature.system.navigation.registerSystemGraph
import com.streamvault.feature.stremio.api.StremioScaffoldContent
import com.streamvault.feature.stremio.navigation.registerStremioGraph

@Composable
internal fun AppNavHost(
    navController: NavHostController,
    actions: NavigationActions,
    catalogDetailActions: CatalogDetailNavigationActions,
    payloads: AppNavigationPayloads,
    catalogPlatformHost: CatalogPlatformHost?,
    playbackPlatformHost: PlaybackPlatformHost?,
    settingsPlatformHost: SettingsPlatformHost,
    navigationDestinations: List<UiDestination>,
    startupReady: Boolean,
    onStartupNavigationRequested: (popUpTo: AppDestination) -> Unit,
    onTopLevelDestinationRequested: (AppDestination) -> Unit,
    onCloseApp: () -> Unit
) {
    // APEX TV: Play/Pause or Stop on the remote always stops a channel playing in the background PiP,
    // even on screens where the PiP window itself can't be focused.
    val backgroundPlayback: com.streamvault.app.live.BackgroundPlaybackViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val backgroundSession by backgroundPlayback.session.collectAsStateWithLifecycle()
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.onPreviewKeyEvent { event ->
            val stopKey = event.key == androidx.compose.ui.input.key.Key.MediaPlayPause ||
                event.key == androidx.compose.ui.input.key.Key.MediaStop
            if (backgroundSession != null && stopKey) {
                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) backgroundPlayback.stop()
                true
            } else {
                false
            }
        }
    ) {
    NavHost(
        navController = navController,
        startDestination = SystemRoutePatterns.WELCOME
    ) {
        registerSystemGraph(
            actions = actions,
            startupReady = startupReady,
            onStartupNavigationRequested = onStartupNavigationRequested,
            onTopLevelDestinationRequested = onTopLevelDestinationRequested,
            scaffold = appSystemScaffold(onTopLevelDestinationRequested),
        )
        registerProviderGraph(
            actions = actions,
            startupReady = startupReady,
            onStartupNavigationRequested = onStartupNavigationRequested,
            backupPreviewContent = { request: ProviderBackupPreviewRequest ->
                BackupImportPreviewDialog(
                    preview = request.preview,
                    plan = request.plan,
                    onDismiss = request.onDismiss,
                    onStrategySelected = request.onStrategySelected,
                    onImportPreferencesChanged = request.onImportPreferencesChanged,
                    onImportProvidersChanged = request.onImportProvidersChanged,
                    onImportSavedLibraryChanged = request.onImportSavedLibraryChanged,
                    onImportPlaybackHistoryChanged = request.onImportPlaybackHistoryChanged,
                    onImportMultiViewChanged = request.onImportMultiViewChanged,
                    onImportRecordingSchedulesChanged = request.onImportRecordingSchedulesChanged,
                    onImportStremioAddonsChanged = request.onImportStremioAddonsChanged,
                    isImporting = request.isImporting,
                    onConfirm = request.onConfirm
                )
            }
        )
        registerLiveGraph(actions, onTopLevelDestinationRequested)
        registerStremioGraph(
            navController = navController,
            actions = actions,
            onTopLevelDestinationRequested = onTopLevelDestinationRequested,
            scaffold = appStremioScaffold(onTopLevelDestinationRequested)
        )
        registerCatalogGraph(
            actions = actions,
            platformHost = catalogPlatformHost,
            scaffold = appCatalogScaffold(onTopLevelDestinationRequested),
            dashboardShelfCustomizationContent = appDashboardShelfCustomizationContent(),
            onTopLevelDestinationRequested = onTopLevelDestinationRequested,
            onOpenMovieDetail = catalogDetailActions::openMovieDetail,
            onOpenSeriesDetail = catalogDetailActions::openSeriesDetail,
            onPlayChannel = { channel, playbackContext ->
                actions.openPlayer(
                    channel.toLivePlayerRequest(
                        categoryId = playbackContext.categoryId,
                        providerId = playbackContext.providerId,
                        isVirtual = playbackContext.isVirtual,
                        combinedProfileId = playbackContext.combinedProfileId,
                        returnDestination = playbackContext.returnDestination
                    )
                )
            },
            onPlayMovie = { movie, returnDestination ->
                actions.openPlayer(movie.toPlayerNavigationRequest(returnDestination))
            },
            onPlayEpisode = { episode, series, returnDestination ->
                actions.openPlayer(
                    episode.toPlayerNavigationRequest(
                        returnDestination = returnDestination,
                        remoteSeriesId = series.providerSeriesId?.trim()?.takeIf(String::isNotBlank)
                            ?: series.seriesId.takeIf { it > 0L }?.toString()
                    )
                )
            },
            onPlayHistory = { history, returnDestination ->
                when (history.contentType) {
                    ContentType.SERIES -> actions.navigate(
                        AppDestination.SeriesDetail(history.contentId, returnDestination),
                        NavigationOptions(launchSingleTop = true)
                    )
                    ContentType.LIVE,
                    ContentType.MOVIE,
                    ContentType.VOD,
                    ContentType.SERIES_EPISODE -> actions.openPlayer(
                        history.toPlayerNavigationRequest(returnDestination)
                    )
                }
            },
            consumeMoviePresentationHint = payloads::consumeMoviePresentationHint,
            consumeSeriesPresentationHint = payloads::consumeSeriesPresentationHint,
            decodeDestination = AppRouteCodec::decode,
        )
        registerPlaybackGraph(actions, playbackPlatformHost, payloads::consumePlayerRequest)
        registerSettingsGraph(
            actions = actions,
            platformHost = settingsPlatformHost,
            navigationDestinations = navigationDestinations,
            settingsContent = { backupUri, openOpenSubtitlesSignIn, platformHost, destinations, onBack ->
                SettingsScreen(
                    onNavigate = { route ->
                        AppRouteCodec.decode(route)?.let(onTopLevelDestinationRequested)
                    },
                    currentRoute = AppRoutePatterns.SETTINGS,
                    platformHost = platformHost,
                    navigationDestinations = destinations,
                    onBack = onBack,
                    onAddProvider = dropUnlessResumed {
                        actions.navigate(AppDestination.ProviderSetup())
                    },
                    onEditProvider = { provider ->
                        actions.navigate(
                            AppDestination.ProviderSetup(providerId = provider.id),
                            NavigationOptions(launchSingleTop = true)
                        )
                    },
                    onNavigateToParentalControl = { providerId ->
                        actions.navigate(
                            AppDestination.ParentalControlGroups(providerId),
                            NavigationOptions(launchSingleTop = true)
                        )
                    },
                    initialBackupImportUri = backupUri,
                    openOpenSubtitlesSignIn = openOpenSubtitlesSignIn,
                    onCloseApp = onCloseApp
                )
            },
            parentalControlContent = { onBack, destinations ->
                ParentalControlGroupScreen(
                    currentRoute = AppRoutePatterns.SETTINGS,
                    onNavigate = { route ->
                        AppRouteCodec.decode(route)?.let(onTopLevelDestinationRequested)
                    },
                    onBack = onBack,
                    navigationDestinations = destinations,
                )
            }
        )
    }
    // APEX TV: PiP for a channel that keeps playing while other tabs are open.
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = currentEntry?.destination?.route
    if (currentRouteName != null &&
        currentRouteName != AppRoutePatterns.EPG &&
        currentRouteName != AppRoutePatterns.PLAYER &&
        currentRouteName != SystemRoutePatterns.WELCOME
    ) {
        com.streamvault.app.ui.components.shell.BackgroundPlaybackPip(
            modifier = androidx.compose.ui.Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(24.dp)
        )
    }
    }
}

private fun appSystemScaffold(
    onTopLevelDestinationRequested: (AppDestination) -> Unit,
): SystemScaffoldContent = { destination, title, subtitle, compactHeader, showScreenHeader, content ->
    AppScreenScaffold(
        currentRoute = AppRouteCodec.encode(destination),
        onNavigate = { route -> AppRouteCodec.decode(route)?.let(onTopLevelDestinationRequested) },
        title = title,
        subtitle = subtitle,
        navigationChrome = AppNavigationChrome.TopBar,
        compactHeader = compactHeader,
        showScreenHeader = showScreenHeader,
        content = content,
    )
}

private fun appStremioScaffold(
    onTopLevelDestinationRequested: (AppDestination) -> Unit,
): StremioScaffoldContent = { destination, title, subtitle, compactHeader, showScreenHeader, topBarVisible, content ->
    AppScreenScaffold(
        currentRoute = AppRouteCodec.encode(destination),
        onNavigate = { route -> AppRouteCodec.decode(route)?.let(onTopLevelDestinationRequested) },
        title = title,
        subtitle = subtitle,
        navigationChrome = AppNavigationChrome.TopBar,
        topBarVisible = topBarVisible,
        compactHeader = compactHeader,
        showScreenHeader = showScreenHeader,
        content = content,
    )
}

private fun appCatalogScaffold(
    onTopLevelDestinationRequested: (AppDestination) -> Unit
): CatalogScaffoldContent = { currentDestination, title, subtitle, chrome, topBarVisible, compactHeader, showScreenHeader, content ->
    AppScreenScaffold(
        currentRoute = AppRouteCodec.encode(currentDestination),
        onNavigate = { route -> AppRouteCodec.decode(route)?.let(onTopLevelDestinationRequested) },
        title = title,
        subtitle = subtitle,
        navigationChrome = when (chrome) {
            CatalogNavigationChrome.Rail -> AppNavigationChrome.Rail
            CatalogNavigationChrome.TopBar -> AppNavigationChrome.TopBar
        },
        topBarVisible = topBarVisible,
        compactHeader = compactHeader,
        showScreenHeader = showScreenHeader,
        content = content
    )
}

private fun appDashboardShelfCustomizationContent(): CatalogDashboardShelfCustomizationContent =
    { currentShelves, onDismiss, onSave ->
        DashboardShelfCustomizationDialog(
            currentShelves = currentShelves,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
