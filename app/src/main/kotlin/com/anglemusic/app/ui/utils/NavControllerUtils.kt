/**
 * AngleMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.anglemusic.app.ui.utils

import androidx.navigation.NavController
import com.anglemusic.app.ui.screens.Screens

fun NavController.backToMain() {
    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in Screens.MainRoutes
    ) {
        popBackStack()
    }
}
