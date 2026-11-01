/**
 * AngleMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anglemusic.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anglemusic.app.constants.LocalAlbumsByYearKey
import com.anglemusic.app.constants.LocalSongSortDescendingKey
import com.anglemusic.app.constants.LocalSongSortTypeKey
import com.anglemusic.app.constants.PlaylistSortType
import com.anglemusic.app.constants.SongSortType
import com.anglemusic.app.db.MusicDatabase
import com.anglemusic.app.db.entities.Album
import com.anglemusic.app.db.entities.Artist
import com.anglemusic.app.db.entities.Playlist
import com.anglemusic.app.db.entities.Song
import com.anglemusic.app.extensions.toEnum
import com.anglemusic.app.utils.LocalAudioScanner
import com.anglemusic.app.utils.LocalFolderIndex
import com.anglemusic.app.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Home in local-only mode: Room rows and the folder index, never the network. */
@HiltViewModel
class LocalHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    val songs: StateFlow<List<Song>> = context.dataStore.data
        .map { it[LocalSongSortTypeKey].toEnum(SongSortType.NAME) to (it[LocalSongSortDescendingKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) -> database.localSongs(sortType, descending) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Newest release first by default; SQLite sorts files without a year last on DESC.
    val albums: StateFlow<List<Album>> = context.dataStore.data
        .map { it[LocalAlbumsByYearKey] ?: true }
        .distinctUntilChanged()
        .flatMapLatest { byYear -> if (byYear) database.albumsLocalByYearDesc() else database.albumsLocalByNameAsc() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = database.artistsLocalByNameAsc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Playlists made here; a synced YouTube playlist carries a browseId.
    val playlists: StateFlow<List<Playlist>> = database.playlists(PlaylistSortType.CREATE_DATE, true)
        .map { list -> list.filter { it.playlist.browseId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders = MutableStateFlow<List<LocalFolderIndex.Folder>>(emptyList())
    val isRefreshing = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.IO) { loadFolders() }
    }

    /** Rescans the device, then rebuilds the folder index. */
    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            try {
                runCatching { LocalAudioScanner.scanAndInsert(context, database) }
                loadFolders()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private suspend fun loadFolders() {
        folders.value = runCatching { LocalFolderIndex.load(context) }.getOrDefault(emptyList())
    }
}
