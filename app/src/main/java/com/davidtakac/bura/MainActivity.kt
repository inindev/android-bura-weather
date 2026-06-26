/*
 * Copyright 2024 David Takač
 *
 * This file is part of Bura.
 *
 * Bura is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Bura is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Bura. If not, see <https://www.gnu.org/licenses/>.
 */

package com.davidtakac.bura

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidtakac.bura.places.Coordinates
import com.davidtakac.bura.theme.AppTheme
import com.davidtakac.bura.theme.Theme
import com.davidtakac.bura.theme.ThemeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A widget tap can ask to open a specific place and screen. Select the place
        // synchronously before composing so the start screen loads the right one.
        intent?.getStringExtra(EXTRA_PLACE_ID)?.let { placeId ->
            (application as App).container.selectedPlaceRepo
                .selectPlaceImmediate(Coordinates.fromId(placeId))
        }
        val widgetRoute = intent?.getStringExtra(EXTRA_NAV_ROUTE)
        setTransparentSystemBars()
        setContent {
            val themeViewModel = viewModel<ThemeViewModel>(factory = ThemeViewModel.Factory)
            val theme = themeViewModel.state.collectAsState().value
            val useDarkTheme = when (theme) {
                Theme.Dark -> true
                Theme.Light -> false
                Theme.FollowSystem -> isSystemInDarkTheme()
            }

            LaunchedEffect(useDarkTheme) {
                setSystemBarIconColors(useDarkTheme)
            }
            AppTheme(useDarkTheme) {
                AppNavHost(
                    theme = theme,
                    onThemeClick = themeViewModel::setTheme,
                    widgetRoute = widgetRoute
                )
            }
        }
    }

    private fun setTransparentSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun setSystemBarIconColors(darkTheme: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !darkTheme
        insetsController.isAppearanceLightNavigationBars = !darkTheme
    }

    companion object {
        const val EXTRA_PLACE_ID = "widget_place_id"
        const val EXTRA_NAV_ROUTE = "widget_nav_route"
    }
}