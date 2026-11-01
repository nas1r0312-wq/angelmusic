/**
 * AngelMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.angelmusic.app.ui.screens.playlist

import com.angelmusic.app.utils.makeTimeString

import com.angelmusic.app.ui.utils.sharedTitleTarget

import com.angelmusic.app.ui.component.sf
import com.angelmusic.app.ui.component.SymbolIcon
import com.angelmusic.app.ui.component.chromeShadow
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFArrowDownCircle
import com.composables.sfsymbols.monochrome.SFECircle
import com.composables.sfsymbols.monochrome.SFHeart
import com.composables.sfsymbols.monochrome.SFHeartFill
import com.composables.sfsymbols.monochrome.SFShuffle
import com.composables.sfsymbols.monochrome.SFWifiSlash
import com.composables.sfsymbols.monochrome.SFXmark
import com.angelmusic.app.LocalAmbientBackgroundColor
import com.angelmusic.app.LocalDatabase
import com.angelmusic.app.LocalDownloadUtil
import com.angelmusic.app.LocalPlayerAwareWindowInsets
import com.angelmusic.app.LocalPlayerConnection
import com.angelmusic.app.LocalTabView
import com.angelmusic.app.R
import com.angelmusic.app.constants.HideExplicitKey
import com.angelmusic.app.db.entities.Playlist
import com.angelmusic.app.db.entities.PlaylistEntity
import com.angelmusic.app.db.entities.PlaylistSongMap
import com.angelmusic.app.extensions.toMediaItem
import com.angelmusic.app.models.toMediaMetadata
import com.angelmusic.app.playback.DownloadTarget
import com.angelmusic.app.playback.combinedDownloadState
import com.angelmusic.app.playback.cancelDownloads
import com.angelmusic.app.playback.downloadSongs
import com.angelmusic.app.playback.queues.ListQueue
import com.angelmusic.app.playback.queues.YouTubePlaylistQueue
import com.angelmusic.app.playback.queues.YouTubeQueue
import com.angelmusic.app.playback.removeDownloads
import com.angelmusic.app.ui.component.AlbumStyleHeroImage
import com.angelmusic.app.ui.component.AnimatedPlayPauseIcon
import com.angelmusic.app.ui.component.ChromeScrim
import com.angelmusic.app.ui.component.ContrastCircleButton
import com.angelmusic.app.ui.component.GlassCircleButton
import com.angelmusic.app.ui.component.GlassComponent
import com.angelmusic.app.ui.component.HeroCardHeader
import com.angelmusic.app.ui.component.HeroTopBlur
import com.angelmusic.app.ui.component.LocalAppBackdrop
import com.angelmusic.app.ui.component.LocalGlassEffectConfig
import com.angelmusic.app.ui.component.LocalMenuState
import com.angelmusic.app.ui.ios.compat.TextButton
import com.angelmusic.app.ui.component.NavigationTitle
import com.angelmusic.app.ui.component.YouTubeGridItem
import com.angelmusic.app.ui.component.YouTubeListItem
import com.angelmusic.app.ui.component.asSoftIconTint
import com.angelmusic.app.ui.component.backdrop.backdrops.LayerBackdrop
import com.angelmusic.app.ui.component.backdrop.backdrops.layerBackdrop
import com.angelmusic.app.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.angelmusic.app.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.angelmusic.app.ui.component.isGlassAllowed
import com.angelmusic.app.ui.component.liquidGlass
import com.angelmusic.app.ui.component.rememberChromeScrimProgress
import com.angelmusic.app.ui.component.rememberHeroSource
import com.angelmusic.app.ui.component.ZeroScrollProgress
import com.angelmusic.app.ui.component.rememberHeroTopBlur
import com.angelmusic.app.ui.component.resolvedTextColor
import com.angelmusic.app.ui.component.shapes.ContinuousRoundedRectangle
import com.angelmusic.app.ui.component.withFixedStyle
import com.angelmusic.app.ui.ios.compat.AppTheme
import com.angelmusic.app.ui.ios.compat.Checkbox
import com.angelmusic.app.ui.ios.compat.CircularProgressIndicator
import com.angelmusic.app.ui.ios.compat.Icon
import com.angelmusic.app.ui.ios.compat.IconButton
import com.angelmusic.app.ui.ios.compat.LocalContentColor
import com.angelmusic.app.ui.ios.compat.SnackbarHost
import com.angelmusic.app.ui.ios.compat.Text
import com.angelmusic.app.ui.ios.compat.ripple
import com.angelmusic.app.ui.menu.YouTubeAlbumMenu
import com.angelmusic.app.ui.menu.YouTubeArtistMenu
import com.angelmusic.app.ui.menu.YouTubePlaylistMenu
import com.angelmusic.app.ui.menu.YouTubeSelectionSongMenu
import com.angelmusic.app.ui.menu.YouTubeSongMenu
import com.angelmusic.app.ui.player.MeshGradientBackground
import com.angelmusic.app.ui.player.dimmed
import com.angelmusic.app.ui.theme.HeroTintedContent
import com.angelmusic.app.ui.theme.rememberArtworkTint
import com.angelmusic.app.ui.utils.FloatingChromeSpacer
import com.angelmusic.app.ui.utils.additiveChrome
import com.angelmusic.app.ui.utils.appTopBarWindowInsets
import com.angelmusic.app.ui.utils.backToMain
import com.angelmusic.app.ui.utils.bounceClick
import com.angelmusic.app.ui.utils.combinedBounceClick
import com.angelmusic.app.ui.utils.heroPullZoom
import com.angelmusic.app.ui.utils.listOverscroll
import com.angelmusic.app.ui.utils.albumRoute
import com.angelmusic.app.ui.utils.artistRoute
import com.angelmusic.app.ui.utils.onlinePlaylistRoute
import com.angelmusic.app.ui.utils.rememberHeroZoom
import com.angelmusic.app.utils.listItemShape
import com.angelmusic.app.utils.rememberPreference
import com.angelmusic.app.viewmodels.OnlinePlaylistViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.angelmusic.app.ui.ios.compat.OutlinedTextField as TextField
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current.get()
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val relatedItems by viewModel.relatedItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val exitSearch = {
        focusManager.clearFocus()
        isSearching = false
        query = TextFieldValue()
    }
    val downloadUtil = LocalDownloadUtil.current.get()
    val context = LocalContext.current
    val downloads by downloadUtil.downloads.collectAsStateWithLifecycle()

    val downloadState = remember(songs, downloads) {
        combinedDownloadState(songs, downloads) { it.id }
    }

    val filteredSongs = remember(songs, query) {
        if (query.text.isEmpty()) songs.mapIndexed { i, s -> i to s }
        else songs.mapIndexed { i, s -> i to s }.filter {
            it.second.title.contains(query.text, true) ||
                    it.second.artists.fastAny { a -> a.name.contains(query.text, true) }
        }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.second.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 150
        }
    }

    if (isSearching) {
        BackHandler(onBack = exitSearch)
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    // Keep top-bar glass fixed; the global preference controls the nav bar and mini player.
    val glassConfig = LocalGlassEffectConfig.current.withFixedStyle()
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val chromeShape = ContinuousRoundedRectangle(percent = 50)
    val chromeContentColor = if (useGlass) glassConfig.resolvedTextColor() else AppTheme.colorScheme.onSurface

    // Unattached backdrop (never .layerBackdrop'd): glass chrome sampling it
    // early-returns → translucent frosted surface, no RenderNode self-reference.
    // See ArtistScreen.kt for the full explanation of the cycle this avoids.
    val heroBackdrop = rememberLayerBackdrop()

    // Attach the list backdrop so sibling chrome can blur list content without sampling itself.
    val heroZoom = rememberHeroZoom()

    // Instant seed: route thumbnail arg (every caller passes it) -> live tile
    // thumb -> seeded playlist. Paints hero + mesh bg on frame one.
    val instantPlaylistThumb = com.angelmusic.app.ui.component.detailSeed(
        viewModel.routeThumb,
        com.angelmusic.app.ui.utils.pendingSharedArtworkThumbFor(playlist?.id),
        playlist?.thumbnail,
    )
    val onlineChrome = com.angelmusic.app.ui.component.rememberDetailChromeEntrance(iconCount = 3)

    val appearance = com.angelmusic.app.ui.component.rememberPlaylistAppearance(
        instantPlaylistThumb ?: playlist?.thumbnail,
        0xFFFF375F.toInt(),
    )
    val tint = appearance.background
    com.angelmusic.app.PublishAmbientBackgroundColor(tint)
    val onTint = appearance.foreground

    // Include the page tint in the captured backdrop so blur matches the visible page.
    val listBackdrop = rememberLayerBackdrop()
    val backdropFreeze = rememberBackdropFreeze()

    // Per-screen Haze source, the same one Album and Artist already keep: the hero
    // square and the tint plane under it become the backdrop HeroMergeBand and
    // HeroTopBlur sample through. Without it both fall back to a flat crossfade, so
    // this screen had a fade where the others had a merge.
    val hazeState = remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tint),
    ) {
      HeroTintedContent(tint = tint, backdrop = heroBackdrop) {
        // Built INSIDE the provider so liquidGlass captures heroBackdrop, not the
        // root appBackdrop — sampling appBackdrop here is the RenderNode cycle.
        val chromeBackgroundModifier = if (useGlass) {
            Modifier.chromeShadow(chromeShape).liquidGlass(config = glassConfig, shape = chromeShape)
        } else {
            Modifier.background(LocalContentColor.current.copy(alpha = 0.15f), chromeShape)
        }
        // Capture through a plain Box; LazyColumn's recycled item layers do not flatten reliably.
        Box(modifier = Modifier
            .nestedScroll(backdropFreeze.connection)

            // Cache list content so sibling redraws do not re-record the backdrop.
            .graphicsLayer()
            .layerBackdrop(listBackdrop, frozen = backdropFreeze.frozen)
            .graphicsLayer()
            .hazeSource(hazeState)) {
                com.angelmusic.app.ui.component.ArtworkMeshBackground(instantPlaylistThumb ?: playlist?.thumbnail, paused = { lazyListState.isScrollInProgress }, appearance = appearance)
        LazyColumn(
            state = lazyListState,
            overscrollEffect = heroZoom.listOverscroll(),
            modifier = Modifier.heroPullZoom(heroZoom),
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .union(WindowInsets.ime)
                .asPaddingValues(),
        ) {
            if (!isLoading && (error != null || songs.isEmpty())) {
                playlist?.let { loadedPlaylist ->
                    item(key = "playlist_header", contentType = "header") {
                        OnlinePlaylistHeader(
                            playlist = loadedPlaylist,
                            songs = songs,
                            dbPlaylist = dbPlaylist,
                            downloadState = downloadState,
                            navController = navController,
                            coroutineScope = coroutineScope,
                            continuation = viewModel.continuation,
                            heroBackdrop = heroBackdrop,
                            heroScale = heroZoom.scale,
                            hazeState = hazeState,
                            pageColor = tint,
                            scrollBlurProgress = rememberHeroTopBlur(lazyListState),
                            onStartSearch = { isSearching = true },
                            searchValue = query.takeIf { isSearching },
                            onSearchValueChange = { query = it },
                            onSearchImeAction = { focusManager.clearFocus() },
                            searchFocusRequester = focusRequester,
                        )
                    }
                    item(key = "playlist_unavailable", contentType = "placeholder") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                stringResource(if (error != null) R.string.playlist_load_failed else R.string.playlist_is_empty),
                                color = AppTheme.colorScheme.onBackground,
                            )
                            if (error != null) TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                        }
                    }
                } ?: item(key = "playlist_unavailable", contentType = "placeholder") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = com.angelmusic.app.ui.component.topBarContentPadding(), start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.filter_playlists), style = AppTheme.typography.headlineSmall, color = AppTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.playlist_load_failed), color = AppTheme.colorScheme.onBackground)
                        TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                    }
                }
            } else if (playlist == null || songs.isEmpty()) {
                // Loading draws the loaded page's own header from the tile's seed, so the shared artwork
                // lands on the same bounds and nothing shimmers or swaps when the data arrives.
                item(key = "playlist_header", contentType = "header") {
                    com.angelmusic.app.ui.component.CollectionDetailHeader(
                        title = com.angelmusic.app.ui.utils.selectedArtworkTitle() ?: viewModel.routeTitle.orEmpty(),
                        artwork = instantPlaylistThumb,
                        metadata = "",
                        foreground = onTint,
                        playing = false,
                        enabled = false,
                        onPlay = {},
                        onShuffle = {},
                        modifier = Modifier.padding(top = com.angelmusic.app.ui.component.topBarContentPadding()),
                    )
                }
                // The loaded page has a search field and song rows under this header; hold their space so
                // the content fades into place instead of pushing the list down.
                item(key = "playlist_loading_rows", contentType = "placeholder") {
                    com.angelmusic.app.ui.component.shimmer.ShimmerHost {
                        Column {
                            com.angelmusic.app.ui.component.ShimmerBox(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(36.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            repeat(8) { com.angelmusic.app.ui.component.shimmer.ListItemPlaceHolder() }
                        }
                    }
                }
            } else {
                playlist?.let { playlist ->
                    item(key = "playlist_header", contentType = "header") {
                        OnlinePlaylistHeader(
                                onStartSearch = { isSearching = true },
                                searchValue = query.takeIf { isSearching },
                                onSearchValueChange = { query = it },
                                onSearchImeAction = { focusManager.clearFocus() },
                                searchFocusRequester = focusRequester,
                                playlist = playlist,
                                songs = songs,
                                dbPlaylist = dbPlaylist,
                                downloadState = downloadState,
                                navController = navController,
                                coroutineScope = coroutineScope,
                                continuation = viewModel.continuation,
                                heroBackdrop = heroBackdrop,
                                heroScale = heroZoom.scale,
                                hazeState = hazeState,
                                pageColor = tint,
                                scrollBlurProgress = rememberHeroTopBlur(lazyListState),
                        )
                    }

                    itemsIndexed(
                        filteredSongs,
                        key = { _, (_, item) -> item.id },
                        contentType = { _, _ -> "song_row" }
                    ) { index, (_, songItem) ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(songItem.id)
                            } else {
                                selection.remove(songItem.id)
                            }
                        }

                        YouTubeListItem(
                            item = songItem,
                            isActive = mediaMetadata?.id == songItem.id,
                            isPlaying = isPlaying,
                            isSelected = inSelectMode && songItem.id in selection,
                            shape = listItemShape(index, filteredSongs.size),
                            flat = true,
                            secondaryAdditiveChrome = onTint == androidx.compose.ui.graphics.Color.White,
                            subtitleAdditiveChrome = false,
                            subtitleColor = onTint.copy(alpha = 0.78f),
                            modifier = Modifier
                                .combinedBounceClick(
                                    enabled = !hideExplicit || !songItem.explicit,
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(songItem.id !in selection)
                                        } else if (songItem.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubePlaylistQueue(
                                                    playlistId = playlist.id,
                                                    playlistTitle = playlist.title,
                                                    initialSongs = filteredSongs.map { it.second },
                                                    initialContinuation = viewModel.continuation,
                                                    startIndex = index
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!inSelectMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            inSelectMode = true
                                            onCheckedChange(true)
                                        }
                                    }
                                )
                                .animateItem(),
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = songItem.id in selection,
                                        onCheckedChange = onCheckedChange
                                    )
                                } else {
                                    IconButton(onClick = {
                                        menuState.show {
                                            YouTubeSongMenu(songItem, navController, menuState::dismiss)
                                        }
                                    }) {
                                        SymbolIcon(symbol = sf("ellipsis"), contentDescription = null)
                                    }
                                }
                            }
                        )
                    }

                    if (isLoadingMore) {
                        item(key = "loading_more") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                repeat(2) { index -> com.angelmusic.app.ui.component.SongRowSkeleton(index = index) }
                            }
                        }
                    }

                    if (relatedItems.isNotEmpty() && !isSearching) {
                        item(key = "related_title") {
                            NavigationTitle(
                                title = stringResource(R.string.you_might_also_like),
                                modifier = Modifier.animateItem()
                            )
                        }

                        item(key = "related_items") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                            ) {
                                items(relatedItems, key = { it.id }) { item ->
                                    YouTubeGridItem(
                                        item = item,
                                        modifier = Modifier
                                            .width(160.dp)
                                            .combinedBounceClick(
                                                onClick = {
                                                    when (item) {
                                                        is PlaylistItem -> navController.navigate(onlinePlaylistRoute(item.id, item.title, item.thumbnail))
                                                        is AlbumItem -> navController.navigate(albumRoute(item.browseId, item.thumbnail))
                                                        is ArtistItem -> navController.navigate(artistRoute(item.id, item.thumbnail))
                                                        is SongItem -> playerConnection.playQueue(
                                                            YouTubeQueue(WatchEndpoint(videoId = item.id))
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        when (item) {
                                                            is PlaylistItem -> YouTubePlaylistMenu(
                                                                playlist = item,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss
                                                            )
                                                            is SongItem -> YouTubeSongMenu(
                                                                song = item,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss
                                                            )
                                                            is AlbumItem -> YouTubeAlbumMenu(
                                                                albumItem = item,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss
                                                            )
                                                            is ArtistItem -> YouTubeArtistMenu(
                                                                artist = item,
                                                                onDismiss = menuState::dismiss
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(50.dp))
                    }
                }
            }
        }
        }

        // Keep search and selection behavior; fade the title after scrolling past the hero.
        val chromeScrimProgress = rememberChromeScrimProgress(lazyListState)
        // Sample the attached list capture so the glass blurs list and hero content.
        CompositionLocalProvider(LocalAppBackdrop provides listBackdrop) {
        // Draw the blur before the chrome row so it stays behind the buttons and scrim.
        com.angelmusic.app.ui.component.TopFadeBlur(
            hazeState = hazeState,
            pageColor = tint,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
        ChromeScrim(progress = chromeScrimProgress)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(appTopBarWindowInsets())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inSelectMode) {
                GlassCircleButton(onClick = onExitSelectionMode) {
                    SymbolIcon(symbol = sf("SFXmark"), contentDescription = null)
                }

                Text(
                    text = pluralStringResource(R.plurals.n_song, selection.size, selection.size),
                    style = AppTheme.typography.titleMedium,
                    color = chromeContentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                com.angelmusic.app.ui.component.GlassPillRow(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(chromeShape)
                        .then(com.angelmusic.app.ui.component.pillGlassBackground(chromeShape))
                        .padding(horizontal = 8.dp),
                ) {
                    Checkbox(
                        checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == filteredSongs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(filteredSongs.map { it.second.id })
                            }
                        }
                    )
                    GlassCircleButton(
                        onClick = {
                            if (selection.isNotEmpty()) {
                                menuState.show {
                                    YouTubeSelectionSongMenu(
                                        songSelection = filteredSongs.filter { it.second.id in selection }
                                            .map { it.second },
                                        onDismiss = menuState::dismiss,
                                        clearAction = onExitSelectionMode
                                    )
                                }
                            }
                        }
                    ) {
                        SymbolIcon(symbol = sf("ellipsis"), contentDescription = null, modifier = Modifier.alpha(if (selection.isNotEmpty()) 1f else 0.4f))
                    }
                }
            } else {
                GlassCircleButton(
                    onClick = { if (isSearching) exitSearch() else navController.navigateUp() },
                    onLongClick = { if (isSearching) exitSearch() else navController.backToMain() },
                    modifier = with(onlineChrome) {
                        Modifier.backEnter().then(com.angelmusic.app.ui.component.backExitModifier())
                    },
                ) {
                    SymbolIcon(symbol = sf("chevron.left"), contentDescription = null)
                }

                AnimatedContent(
                    targetState = !transparentAppBar,
                    transitionSpec = {
                        fadeIn().togetherWith(fadeOut())
                    },
                    label = "TopAppBarTitle",
                    modifier = Modifier.weight(1f)
                ) { show ->
                    if (show) {
                        Text(
                            text = playlist?.title ?: "",
                            style = AppTheme.typography.titleMedium,
                            color = chromeContentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val ambientColor = LocalAmbientBackgroundColor.current.value
                val pillIconTint = if (useGlass && ambientColor.isSpecified) ambientColor.asSoftIconTint() else chromeContentColor
                com.angelmusic.app.ui.component.GlassPillRow(
                    modifier = with(onlineChrome) { Modifier.containerEnter() }
                        .height(48.dp)
                        .clip(chromeShape)
                        .then(com.angelmusic.app.ui.component.pillGlassBackground(chromeShape))
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = {
                            playlist?.shareLink?.let { link ->
                                val intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, link)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        SymbolIcon(symbol = sf("square.and.arrow.up"), contentDescription = stringResource(R.string.share), size = 20.dp, modifier = with(onlineChrome) {
                                Modifier.iconEnter(1)

                                    .additiveChrome(useGlass && ambientColor.isSpecified)
                            }, tint = pillIconTint)
                    }

                    IconButton(
                        onClick = {
                            playlist?.let { pl ->
                                menuState.show {
                                    YouTubePlaylistMenu(
                                        playlist = pl,
                                        songs = songs,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        SymbolIcon(symbol = sf("ellipsis"), contentDescription = stringResource(R.string.more_options), size = 20.dp, modifier = with(onlineChrome) {
                                Modifier.iconEnter(2)

                                    .additiveChrome(useGlass && ambientColor.isSpecified)
                            }, tint = pillIconTint)
                    }
                }
            }
        }
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
      }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlinePlaylistHeader(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    downloadState: Int,
    navController: NavController,
    coroutineScope: CoroutineScope,
    continuation: String?,
    heroBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    heroScale: Float = 1f,
    hazeState: HazeState? = null,
    pageColor: Color = Color.Black,
    scrollBlurProgress: State<Float> = ZeroScrollProgress,
    onStartSearch: () -> Unit = {},
    searchValue: TextFieldValue? = null,
    onSearchValueChange: (TextFieldValue) -> Unit = {},
    onSearchImeAction: () -> Unit = {},
    searchFocusRequester: FocusRequester? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val database = LocalDatabase.current.get()
    val menuState = LocalMenuState.current
    val downloads by LocalDownloadUtil.current.get().downloads.collectAsStateWithLifecycle()
    val artwork = com.angelmusic.app.ui.utils.pendingSharedArtworkThumbFor(playlist.id) ?: playlist.thumbnail
    val appearance = com.angelmusic.app.ui.component.rememberPlaylistAppearance(artwork)
    val duration = remember(songs) { songs.sumOf { it.duration ?: 0 } }
    com.angelmusic.app.ui.component.CollectionDetailHeader(
        title = playlist.title,
        subtitle = playlist.author?.name,
        artwork = artwork,
        metadata = pluralStringResource(R.plurals.n_song, songs.size, songs.size) +
            if (duration > 0) " \u00b7 " + makeTimeString(duration * 1000L) else "",
        foreground = appearance.foreground,
        playing = isPlaying && songs.any { it.id == mediaMetadata?.id },
        enabled = songs.isNotEmpty(),
        onPlay = {
            if (songs.any { it.id == mediaMetadata?.id }) playerConnection.togglePlayPause()
            else playerConnection.playQueue(YouTubePlaylistQueue(
                playlistId = playlist.id, playlistTitle = playlist.title,
                initialSongs = songs, initialContinuation = continuation,
            ))
        },
        onShuffle = { playerConnection.playQueue(ListQueue(title = playlist.title, items = songs.shuffled().map { it.toMediaItem() })) },
        onSearch = onStartSearch,
        searchValue = searchValue,
        onSearchValueChange = onSearchValueChange,
        onSearchImeAction = onSearchImeAction,
        searchFocusRequester = searchFocusRequester,
        onRightAction = {
                        menuState.show {
                            YouTubePlaylistMenu(
                                playlist = playlist,
                                songs = songs,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
        modifier = modifier.padding(top = com.angelmusic.app.ui.component.topBarContentPadding()),
    )
}
