package com.angelmusic.app.lyrics

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.angelmusic.app.constants.EnableBetterLyricsKey
import com.angelmusic.app.constants.EnableKugouKey
import com.angelmusic.app.constants.EnableLrcLibKey
import com.angelmusic.app.constants.EnableMusixmatchKey
import com.angelmusic.app.constants.EnablePaxsenixKey
import com.angelmusic.app.constants.EnableSimpMusicKey
import com.angelmusic.app.constants.EnableUnisonKey
import com.angelmusic.app.constants.EnableYouLyPlusKey
import com.angelmusic.app.constants.PreferredLyricsProvider
import com.angelmusic.app.utils.dataStore
import com.angelmusic.app.utils.get

object LyricsProviderSettings {
    fun idForLegacyPreference(provider: PreferredLyricsProvider): String = when (provider) {
        PreferredLyricsProvider.LRCLIB -> "LrcLib"
        PreferredLyricsProvider.KUGOU -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
        PreferredLyricsProvider.YOULYPLUS -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX -> "Paxsenix"
    }

    fun enabledKey(id: String): Preferences.Key<Boolean> = when (id) {
        "BetterLyrics" -> EnableBetterLyricsKey
        "Kugou" -> EnableKugouKey
        "LrcLib" -> EnableLrcLibKey
        "Musixmatch" -> EnableMusixmatchKey
        "Paxsenix" -> EnablePaxsenixKey
        "SimpMusic" -> EnableSimpMusicKey
        "Unison" -> EnableUnisonKey
        "YouLyPlus" -> EnableYouLyPlusKey
        else -> booleanPreferencesKey("enableLyrics$id")
    }

    fun isEnabled(context: Context, provider: LyricsProvider): Boolean =
        context.dataStore[enabledKey(provider.id)] ?: true

    fun initialize(context: Context, provider: LyricsProvider) {
        provider.initialize(context.applicationContext)
    }
}
