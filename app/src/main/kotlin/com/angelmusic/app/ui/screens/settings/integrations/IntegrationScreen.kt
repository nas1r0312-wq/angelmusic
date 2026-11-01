/**
 * AngelMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.angelmusic.app.ui.screens.settings.integrations

import com.angelmusic.app.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.angelmusic.app.ui.ios.compat.AppTheme
import com.angelmusic.app.ui.ios.compat.Icon
import com.angelmusic.app.ui.ios.compat.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.angelmusic.app.LocalPlayerAwareWindowInsets
import com.angelmusic.app.R
import com.angelmusic.app.ui.component.IconButton
import com.angelmusic.app.ui.component.IntegrationCard
import com.angelmusic.app.ui.component.IntegrationCardItem
import com.angelmusic.app.ui.utils.backToMain
import com.angelmusic.app.ui.screens.settings.SettingsPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    SettingsPage(
        navController = navController,
        title = stringResource(R.string.integrations),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 16.dp,
    ) {
        IntegrationCard(
            title = "Services",
            items = listOf(
                IntegrationCardItem(
                    icon = painterResource(R.drawable.discord),
                    title = { Text(stringResource(R.string.discord_integration)) },
                    onClick = {
                        navController.navigate("settings/integrations/discord")
                    }
                ),
                IntegrationCardItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.lastfm_integration)) },
                    onClick = {
                        navController.navigate("settings/integrations/lastfm")
                    }
                )
            )
        )
    
    }
}
