/**
 * AngleMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anglemusic.app.ui.screens

import com.anglemusic.app.ui.component.sf
import dev.chrisbanes.haze.hazeSource
import com.anglemusic.app.ui.component.SymbolIcon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.anglemusic.app.ui.component.rememberTitleCollapseProgressState
import com.anglemusic.app.ui.ios.foundation.IosTheme
import androidx.compose.foundation.layout.size
import com.anglemusic.app.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import com.anglemusic.app.ui.ios.compat.Icon
import com.anglemusic.app.ui.ios.compat.AppTheme
import com.anglemusic.app.ui.ios.compat.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.anglemusic.app.LocalPlayerAwareWindowInsets
import com.anglemusic.app.LocalPlayerConnection
import com.anglemusic.app.R
import com.anglemusic.app.ui.utils.rememberGridColumns
import com.anglemusic.app.constants.GridItemSize
import com.anglemusic.app.constants.GridItemsSizeKey
import com.anglemusic.app.constants.GridThumbnailHeight
import com.anglemusic.app.constants.MiniPlayerBottomSpacing
import com.anglemusic.app.constants.MiniPlayerHeight
import com.anglemusic.app.constants.NavigationBarHeight
import com.anglemusic.app.ui.component.LargeScreenTitle
import com.anglemusic.app.ui.component.GlassCircleButton
import com.anglemusic.app.ui.component.HeroBackground
import com.anglemusic.app.ui.utils.rememberHeroZoom
import com.anglemusic.app.ui.utils.heroPullZoom
import com.anglemusic.app.ui.utils.listOverscroll
import com.anglemusic.app.ui.component.GlassComponent
import com.anglemusic.app.ui.component.LocalGlassEffectConfig
import com.anglemusic.app.ui.component.LocalMenuState
import com.anglemusic.app.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.anglemusic.app.ui.component.isGlassAllowed
import com.anglemusic.app.ui.component.liquidGlass
import com.anglemusic.app.ui.component.rememberHeroSource
import com.anglemusic.app.ui.component.rememberHeroTopBlur
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.anglemusic.app.ui.component.rememberHeroTint
import com.anglemusic.app.ui.component.shapes.ContinuousRoundedRectangle
import com.anglemusic.app.ui.component.shimmer.GridItemPlaceHolder
import com.anglemusic.app.ui.component.shimmer.ShimmerHost
import com.anglemusic.app.ui.component.YouTubeGridItem
import com.anglemusic.app.ui.menu.YouTubeAlbumMenu
import com.anglemusic.app.ui.theme.AppleTokens
import com.anglemusic.app.ui.theme.HeroTintedContent
import com.anglemusic.app.ui.utils.backToMain
import com.anglemusic.app.ui.utils.albumRoute
import com.anglemusic.app.ui.utils.combinedBounceClick
import com.anglemusic.app.utils.rememberEnumPreference
import com.anglemusic.app.viewmodels.NewReleaseViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewReleaseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: NewReleaseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val newReleaseAlbums by viewModel.newReleaseAlbums.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val heroUrl = newReleaseAlbums.firstOrNull()?.thumbnail
    val heroSource = rememberHeroSource(
        staticArt = heroUrl,
        songs = emptyList()
    )
    val tint = rememberHeroTint(heroUrl)
    val onTint = AppleTokens.onColor(tint)

    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val heroBackdrop = rememberLayerBackdrop()

    val heroZoom = rememberHeroZoom()
    val lazyGridState = rememberLazyGridState()

    HeroBackground(
        tint = tint,
        heroSource = heroSource,
        heroScale = heroZoom.scale,
        // Progressive top blur: artwork dissolves under the status bar as the grid scrolls up.
        topBlurProgress = rememberHeroTopBlur(lazyGridState),
        modifier = Modifier.fillMaxSize(),
    ) {
      HeroTintedContent(tint = tint, backdrop = heroBackdrop) {
        val chromeShape = ContinuousRoundedRectangle(percent = 50)
        val hazeState = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = lazyGridState,
                overscrollEffect = heroZoom.listOverscroll(),
                modifier = Modifier.heroPullZoom(heroZoom).fillMaxSize().hazeSource(hazeState),
                columns = rememberGridColumns(),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        LargeScreenTitle(
                            title = stringResource(R.string.new_release_albums),
                            color = onTint,
                        )
                    }
                }

                items(
                    items = newReleaseAlbums.distinctBy { it.id },
                    key = { it.id },
                ) { album ->
                    YouTubeGridItem(
                        item = album,
                        isActive = mediaMetadata?.album?.id == album.id,
                        isPlaying = isPlaying,
                        fillMaxWidth = true,
                        coroutineScope = coroutineScope,
                        modifier =
                        Modifier
                            .combinedBounceClick(
                                onClick = {
                                    navController.navigate(albumRoute(album.id, album.thumbnail))
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeAlbumMenu(
                                            albumItem = album,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                    )
                }

                if (newReleaseAlbums.isEmpty()) {
                    items(8) {
                        ShimmerHost {
                            GridItemPlaceHolder(fillMaxWidth = true)
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(MiniPlayerHeight + MiniPlayerBottomSpacing + NavigationBarHeight + 50.dp))
                }
            }

            val newReleaseProgress = com.anglemusic.app.ui.component.rememberTitleCollapseProgressState(lazyGridState)
            com.anglemusic.app.ui.component.TopFadeBlur(
                hazeState = hazeState,
                pageColor = tint,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(appTopBarWindowInsets())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(
                    onClick = { navController.navigateUp() },
                    onLongClick = { navController.backToMain() },
                ) {
                    SymbolIcon(symbol = sf("chevron.left"), contentDescription = null)
                }

                Text(
                    text = stringResource(R.string.new_release_albums),
                    style = IosTheme.typography.headline,
                    color = onTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .graphicsLayer { alpha = newReleaseProgress.value },
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.size(44.dp))
            }
        }
      }
    }
}
