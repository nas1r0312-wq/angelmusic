package com.anglemusic.app.lyrics

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.anglemusic.app.constants.EnableBetterLyricsKey
import com.anglemusic.app.constants.EnableKugouKey
import com.anglemusic.app.constants.EnableLrcLibKey
import com.anglemusic.app.constants.EnableMusixmatchKey
import com.anglemusic.app.constants.EnablePaxsenixKey
import com.anglemusic.app.constants.EnableSimpMusicKey
import com.anglemusic.app.constants.EnableUnisonKey
import com.anglemusic.app.constants.EnableYouLyPlusKey
import com.anglemusic.app.constants.PreferredLyricsProvider
import com.anglemusic.app.utils.dataStore
import com.anglemusic.app.utils.get

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
