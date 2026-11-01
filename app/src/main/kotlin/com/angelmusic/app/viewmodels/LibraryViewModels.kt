/**
 * AngelMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.angelmusic.app.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.angelmusic.app.constants.AlbumFilter
import com.angelmusic.app.constants.AlbumFilterKey
import com.angelmusic.app.constants.AlbumSortDescendingKey
import com.angelmusic.app.constants.AlbumSortType
import com.angelmusic.app.constants.AlbumSortTypeKey
import com.angelmusic.app.constants.ArtistFilter
import com.angelmusic.app.constants.ArtistFilterKey
import com.angelmusic.app.constants.ArtistSongSortDescendingKey
import com.angelmusic.app.constants.ArtistSongSortType
import com.angelmusic.app.constants.ArtistSongSortTypeKey
import com.angelmusic.app.constants.ArtistSortDescendingKey
import com.angelmusic.app.constants.ArtistSortType
import com.angelmusic.app.constants.ArtistSortTypeKey
import com.angelmusic.app.constants.HideExplicitKey
import com.angelmusic.app.constants.HideVideoSongsKey
import com.angelmusic.app.constants.DataSaverEnabledKey
import com.angelmusic.app.constants.HideYoutubeShortsKey
import com.angelmusic.app.constants.LibraryFilter
import com.angelmusic.app.constants.LocalOnlyModeKey
import com.angelmusic.app.constants.PlaylistSortDescendingKey
import com.angelmusic.app.constants.PlaylistSortType
import com.angelmusic.app.constants.PlaylistSortTypeKey
import com.angelmusic.app.constants.SongFilter
import com.angelmusic.app.constants.SongFilterKey
import com.angelmusic.app.constants.SongSortDescendingKey
import com.angelmusic.app.constants.SongSortType
import com.angelmusic.app.constants.SongSortTypeKey
import com.angelmusic.app.constants.TopSize
import com.angelmusic.app.db.MusicDatabase
import com.angelmusic.app.db.entities.Song
import com.angelmusic.app.extensions.filterExplicit
import com.angelmusic.app.extensions.filterExplicitAlbums
import com.angelmusic.app.extensions.filterVideoSongs
import com.angelmusic.app.extensions.filterYoutubeShorts
import com.angelmusic.app.extensions.toEnum
import com.angelmusic.app.playback.DownloadUtil
import com.angelmusic.app.utils.LocalAudioScanner
import com.angelmusic.app.utils.SyncUtils
import com.angelmusic.app.utils.dataStore
import com.angelmusic.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

private data class SongLibraryOptions(
    val filter: SongFilter,
    val sortType: SongSortType,
    val descending: Boolean,
    val hideExplicit: Boolean,
    val hideVideoSongs: Boolean,
)

data class LibrarySongsSnapshot(
    val filter: SongFilter,
    val sortType: SongSortType,
    val descending: Boolean,
    val hideExplicit: Boolean,
    val hideVideoSongs: Boolean,
    val songs: List<Song>,
)

private data class AlbumLibraryOptions(
    val filter: AlbumFilter,
    val sortType: AlbumSortType,
    val descending: Boolean,
    val hideExplicit: Boolean,
)

private data class PlaylistLibraryOptions(
    val sortType: PlaylistSortType,
    val descending: Boolean,
    val hideYoutubeShorts: Boolean,
    val localOnly: Boolean,
)

private data class ArtistSongOptions(
    val sortType: ArtistSongSortType,
    val descending: Boolean,
    val hideExplicit: Boolean,
    val hideVideoSongs: Boolean,
)

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanResult = MutableStateFlow<LocalAudioScanner.ScanResult?>(null)
    val scanResult = _scanResult.asStateFlow()

    val allSongs =
        context.dataStore.data
            .map { preferences ->
                SongLibraryOptions(
                    // Keep the saved filter so it returns when local-only mode is turned off.
                    filter = if (preferences[LocalOnlyModeKey] == true) SongFilter.LOCAL
                    else preferences[SongFilterKey].toEnum(SongFilter.LIKED),
                    sortType = preferences[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                    descending = preferences[SongSortDescendingKey] ?: true,
                    hideExplicit = preferences[HideExplicitKey] ?: false,
                    hideVideoSongs = (preferences[HideVideoSongsKey] ?: false) ||
                        (preferences[DataSaverEnabledKey] ?: false),
                )
            }.distinctUntilChanged()
            .flatMapLatest { options ->
                val songs = when (options.filter) {
                    SongFilter.LIBRARY -> database.songs(options.sortType, options.descending)
                    SongFilter.LIKED -> database.likedSongs(options.sortType, options.descending)
                    SongFilter.DOWNLOADED -> database.downloadedSongs(options.sortType, options.descending)
                    SongFilter.UPLOADED -> database.uploadedSongs(options.sortType, options.descending)
                    SongFilter.LOCAL -> database.localSongs(options.sortType, options.descending)
                }
                songs
                    .map {
                        LibrarySongsSnapshot(
                            filter = options.filter,
                            sortType = options.sortType,
                            descending = options.descending,
                            hideExplicit = options.hideExplicit,
                            hideVideoSongs = options.hideVideoSongs,
                            songs = it.filterExplicit(options.hideExplicit)
                                .filterVideoSongs(options.hideVideoSongs),
                        )
                    }
                    .onStart {
                        // Clear the previous query as soon as a filter or sort changes.
                        // The UI also checks this snapshot's options before exposing rows,
                        // so a stale list can never be played during the preference handoff.
                        emit(
                            LibrarySongsSnapshot(
                                filter = options.filter,
                                sortType = options.sortType,
                                descending = options.descending,
                                hideExplicit = options.hideExplicit,
                                hideVideoSongs = options.hideVideoSongs,
                                songs = emptyList(),
                            ),
                        )
                    }
            }.stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                LibrarySongsSnapshot(
                    filter = SongFilter.LIKED,
                    sortType = SongSortType.CREATE_DATE,
                    descending = true,
                    hideExplicit = false,
                    hideVideoSongs = false,
                    songs = emptyList(),
                ),
            )

    fun scanLocalFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanResult.value = null
            try {
                val result = LocalAudioScanner.scanAndInsert(context, database)
                _scanResult.value = result
            } catch (e: Exception) {
                _scanResult.value = LocalAudioScanner.ScanResult(0, 0, 0)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearScanResult() {
        _scanResult.value = null
    }

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncLibrarySongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    if (it[LocalOnlyModeKey] == true) ArtistFilter.LOCAL
                    else it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                    it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                    it[ArtistSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                    ArtistFilter.LOCAL -> database.artistsLocal(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allArtists.collect { artists ->
                artists
                    .map { it.artist }
                    // A local artist has no YouTube page to fetch — asking for one is
                    // a guaranteed round trip to nothing, and in local-only mode it is
                    // network traffic the mode exists to avoid.
                    .filter { !it.isLocal }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val albumOptions =
        context.dataStore.data
            .map { preferences ->
                AlbumLibraryOptions(
                    // Keep the saved filter so it returns when local-only mode is turned off.
                    filter = if (preferences[LocalOnlyModeKey] == true) AlbumFilter.LOCAL
                    else preferences[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                    sortType = preferences[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                    descending = preferences[AlbumSortDescendingKey] ?: true,
                    hideExplicit = preferences[HideExplicitKey] ?: false,
                )
            }.distinctUntilChanged()

    /** Device albums, listed under the online ones with a divider. */
    val localAlbums =
        albumOptions
            .flatMapLatest { options ->
                database.albumsLocal(options.sortType, options.descending)
                    .map { it.filterExplicitAlbums(options.hideExplicit) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allAlbums =
        albumOptions
            .flatMapLatest { options ->
                val albums = when (options.filter) {
                    AlbumFilter.LIKED -> database.albumsLiked(options.sortType, options.descending)
                    AlbumFilter.LIBRARY -> database.albums(options.sortType, options.descending)
                    AlbumFilter.UPLOADED -> database.albumsUploaded(options.sortType, options.descending)
                    AlbumFilter.LOCAL -> database.albumsLocal(options.sortType, options.descending)
                }
                albums.map { it.filterExplicitAlbums(options.hideExplicit) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0 && !it.album.isLocal
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map { preferences ->
                PlaylistLibraryOptions(
                    sortType = preferences[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE),
                    descending = preferences[PlaylistSortDescendingKey] ?: true,
                    hideYoutubeShorts = preferences[HideYoutubeShortsKey] ?: false,
                    localOnly = preferences[LocalOnlyModeKey] == true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { options ->
                database.playlists(options.sortType, options.descending).map { playlists ->
                    // A synced YouTube playlist carries a browseId; the ones the user
                    // made here don't, and those are the only ones local-only shows.
                    playlists
                        .filter { !options.localOnly || it.playlist.browseId == null }
                        .filterYoutubeShorts(options.hideYoutubeShorts)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() }
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = requireNotNull(savedStateHandle.get<String>("artistId")) { "Missing artistId" }
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map { preferences ->
                ArtistSongOptions(
                    sortType = preferences[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE),
                    descending = preferences[ArtistSongSortDescendingKey] ?: true,
                    hideExplicit = preferences[HideExplicitKey] ?: false,
                    hideVideoSongs = (preferences[HideVideoSongsKey] ?: false) ||
                        (preferences[DataSaverEnabledKey] ?: false),
                )
            }.distinctUntilChanged()
            .flatMapLatest { options ->
                database.artistSongs(artistId, options.sortType, options.descending).map {
                    it.filterExplicit(options.hideExplicit).filterVideoSongs(options.hideVideoSongs)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            syncUtils.performFullSyncSuspend()
            _isRefreshing.value = false
        }
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
    // The mixed Library view is built from liked/bookmarked YouTube rows; in
    // local-only mode the same three shelves read the on-device library instead.
    private val localOnly = context.dataStore.data
        .map { it[LocalOnlyModeKey] == true }
        .distinctUntilChanged()

    var artists = localOnly
        .flatMapLatest { local ->
            if (local) database.artistsLocal(ArtistSortType.NAME, false)
            else database.artistsBookmarked(ArtistSortType.CREATE_DATE, true)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[LocalOnlyModeKey] == true) }
        .distinctUntilChanged()
        .flatMapLatest { (hideExplicit, local) ->
            val source = if (local) database.albumsLocal(AlbumSortType.NAME, false)
            else database.albumsLiked(AlbumSortType.CREATE_DATE, true)
            source.map { it.filterExplicitAlbums(hideExplicit) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = context.dataStore.data
        .map { (it[HideYoutubeShortsKey] ?: false) to (it[LocalOnlyModeKey] == true) }
        .distinctUntilChanged()
        .flatMapLatest { (hideYoutubeShorts, local) ->
            database.playlists(PlaylistSortType.CREATE_DATE, true).map { playlists ->
                playlists
                    .filter { !local || it.playlist.browseId == null }
                    .filterYoutubeShorts(hideYoutubeShorts)
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            albums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0 && !it.album.isLocal
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            artists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter { !it.isLocal }
                    .filter {
                        it.thumbnailUrl == null ||
                                Duration.between(
                                    it.lastUpdateTime,
                                    LocalDateTime.now(),
                                ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}
