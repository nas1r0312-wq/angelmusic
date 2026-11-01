/**
 * AngleMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anglemusic.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.anglemusic.app.constants.AppLanguageKey
import com.anglemusic.app.constants.DisableScreenshotKey
import com.anglemusic.app.constants.SYSTEM_DEFAULT
import com.anglemusic.app.constants.StopMusicOnTaskClearKey
import com.anglemusic.app.db.MusicDatabase
import com.anglemusic.app.history.HistoryLocalDataSource
import com.anglemusic.app.playback.DownloadUtil
import com.anglemusic.app.playback.MusicService
import com.anglemusic.app.playback.PlaybackServiceConnector
import com.anglemusic.app.playback.PlayerConnection
import com.anglemusic.app.utils.SyncUtils
import com.anglemusic.app.utils.dataStore
import com.anglemusic.app.utils.get
import com.anglemusic.app.utils.setAppLocale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PLAY_INTRO = "com.anglemusic.app.extra.PLAY_INTRO"
        internal const val ACTION_SEARCH = "com.anglemusic.app.action.SEARCH"
        internal const val ACTION_LIBRARY = "com.anglemusic.app.action.LIBRARY"
    }

    @Inject
    lateinit var database: dagger.Lazy<MusicDatabase>

    @Inject
    lateinit var downloadUtil: dagger.Lazy<DownloadUtil>

    @Inject
    lateinit var syncUtils: dagger.Lazy<SyncUtils>

    @Inject
    lateinit var historyDataSource: dagger.Lazy<HistoryLocalDataSource>

    @Inject
    lateinit var listenTogetherManager: dagger.Lazy<com.anglemusic.app.listentogether.ListenTogetherManager>

    @Inject
    lateinit var partyManager: dagger.Lazy<com.anglemusic.app.jam.JamManager>

    @Inject
    lateinit var playbackServiceConnector: PlaybackServiceConnector

    private var pendingIntent: Intent? = null

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var activityStarted = false
    private var pendingPlaybackBind: Runnable? = null

    override fun onStart() {
        super.onStart()
        activityStarted = true
        if (MusicService.isRunning) {
            bindPlaybackService()
        } else {
            val bind = Runnable {
                pendingPlaybackBind = null
                if (activityStarted) bindPlaybackService()
            }
            pendingPlaybackBind = bind
            window.decorView.postDelayed(bind, 32L)
        }
    }

    override fun onStop() {
        activityStarted = false
        pendingPlaybackBind?.let(window.decorView::removeCallbacks)
        pendingPlaybackBind = null
        playbackServiceConnector.unbind(this)
        super.onStop()
    }

    private fun bindPlaybackService() {
        // The service promotes itself when bound, so bind only while this activity is visible.
        playbackServiceConnector.bind(this, lifecycleScope) { connection ->
            playerConnection = connection
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Handle back navigation here; swiping the task away is handled by the service.
        if (dataStore.get(StopMusicOnTaskClearKey, false) &&
            playerConnection?.isPlaying?.value == true &&
            isFinishing
        ) {
            // onStop already unbound the connection; only stop the service here.
            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }

    internal fun takePendingIntent(): Intent? {
        val current = pendingIntent
        pendingIntent = null
        return current
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Retain the intent until Compose's listener is ready to handle it.
        pendingIntent = intent
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            com.anglemusic.app.ui.component.MenuAnchor.lastDown =
                androidx.compose.ui.geometry.Offset(ev.x, ev.y)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locale = dataStore[AppLanguageKey]
                ?.takeUnless { it == SYSTEM_DEFAULT }
                ?.let { Locale.forLanguageTag(it) }
                ?: Locale.getDefault()
            setAppLocale(this, locale)
        }

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        // Cold launch from the launcher only (WelcomeActivity hands off with this extra);
        // notification/deep-link opens go straight to the app.
        val playIntro = savedInstanceState == null && intent.getBooleanExtra(EXTRA_PLAY_INTRO, false)
        setContent {
            var showIntro by rememberSaveable { mutableStateOf(playIntro) }
            Box(Modifier.fillMaxSize()) {
                AppRootContent(
                    activity = this@MainActivity,
                    intentRouter = deepLinkRouter,
                    playerConnection = playerConnection,
                    database = database,
                    downloadUtil = downloadUtil,
                    syncUtils = syncUtils,
                    historyDataSource = historyDataSource,
                )
                if (showIntro) com.anglemusic.app.ui.component.AngleMusicIntro(onFinished = { showIntro = false })
            }
        }
    }

    internal val deepLinkRouter by lazy {
        AppIntentRouter(this, { playerConnection }, listenTogetherManager, partyManager)
    }
}
