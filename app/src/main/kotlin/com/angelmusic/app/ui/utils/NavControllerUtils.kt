/**
 * AngelMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.angelmusic.app.ui.utils

import androidx.navigation.NavController
import com.angelmusic.app.ui.screens.Screens

fun NavController.backToMain() {
    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in Screens.MainRoutes
    ) {
        popBackStack()
    }
}
