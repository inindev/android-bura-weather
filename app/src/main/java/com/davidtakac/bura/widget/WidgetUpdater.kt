/*
 * Copyright 2026 John Clark
 *
 * This file is part of Bura.
 *
 * Bura is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Bura is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Bura. If not, see <https://www.gnu.org/licenses/>.
 */

package com.davidtakac.bura.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.davidtakac.bura.App
import com.davidtakac.bura.AppContainer
import com.davidtakac.bura.forecast.UpdatePolicy
import java.time.Instant

/** Single place that fetches state and pushes RemoteViews. Shared by the provider and worker. */
object WidgetUpdater {
    suspend fun updateAll(context: Context, updatePolicy: UpdatePolicy = UpdatePolicy.Eager) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(context, WeatherWidgetProvider::class.java)
        )
        for (id in ids) update(context, appWidgetManager, id, updatePolicy = updatePolicy)
    }

    suspend fun update(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        advancePlace: Boolean = false,
        updatePolicy: UpdatePolicy = UpdatePolicy.Eager
    ) {
        val container = (context.applicationContext as App).container
        if (advancePlace) advancePlace(context, container, appWidgetId)
        val state = getWidgetState(context, container)(context, Instant.now(), appWidgetId, updatePolicy)
        appWidgetManager.updateAppWidget(appWidgetId, WidgetRenderer.render(context, state, appWidgetId))
    }

    /**
     * Repaints a widget from cached data only (never hits the network) and pushes it. Used to clear a
     * refresh spinner deterministically when a refresh is cut short by its deadline or an error: the
     * spinner lives in the launcher's host process and animates until something re-renders, so without
     * this it would spin until the next periodic tick. [forceStale] marks the result as not-current
     * (the just-failed refresh) even though the cache read itself does not attempt a download.
     */
    suspend fun render(context: Context, appWidgetId: Int, forceStale: Boolean = false) {
        val container = (context.applicationContext as App).container
        var state = getWidgetState(context, container)(
            context, Instant.now(), appWidgetId, UpdatePolicy.Static
        )
        if (forceStale && state is WidgetState.Loaded) state = state.copy(stale = true)
        AppWidgetManager.getInstance(context)
            .updateAppWidget(appWidgetId, WidgetRenderer.render(context, state, appWidgetId))
    }

    private suspend fun advancePlace(context: Context, container: AppContainer, appWidgetId: Int) {
        val places = container.savedPlacesRepo.getSavedPlaces()
        if (places.isEmpty()) return
        val store = WidgetPlaceStore(context)
        val currentId = store.getCoordsId(appWidgetId)
        val currentIndex = places.indexOfFirst { it.location.coordinates.id == currentId }
        val next = places[(currentIndex + 1).mod(places.size)]
        store.setCoordsId(appWidgetId, next.location.coordinates.id)
    }

    private fun getWidgetState(context: Context, container: AppContainer) = GetWidgetState(
        selectedPlaceRepo = container.selectedPlaceRepo,
        savedPlacesRepo = container.savedPlacesRepo,
        selectedUnitsRepo = container.selectedUnitsRepo,
        forecastRepo = container.forecastRepo,
        widgetPlaceStore = WidgetPlaceStore(context)
    )
}