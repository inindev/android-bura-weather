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

package com.davidtakac.bura.forecast.cache

import com.davidtakac.bura.places.Coordinates
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ForecastCacherTest {
    @get:Rule
    val root = TemporaryFolder()

    @Test
    fun `corrupt cache file is deleted and read as no data instead of throwing`() = runTest {
        val coords = Coordinates(latitude = 45.0, longitude = 18.0)
        val dir = File(root.root, "forecasts").apply { mkdir() }
        val file = File(dir, coords.id).apply { writeText("{ torn json") }
        val cacher = ForecastCacher(root = root.root, appVersionName = "1.0")

        assertNull(cacher.get(coords))
        assertFalse(file.exists())
        // Subsequent reads see an empty cache rather than tripping over the bad file again.
        assertNull(cacher.get(coords))
    }
}