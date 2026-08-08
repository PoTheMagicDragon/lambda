/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.newui.theme

import com.lambda.util.Timer
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import kotlin.time.Duration.Companion.milliseconds

object SystemThemeTracker {
    private const val POLL_INTERVAL = 500

    private var dark = queryIsDark()
    private val queryTimer = Timer()

    val isSystemDark get() = dark

    fun refresh() {
        queryTimer.reset()
        dark = queryIsDark()
    }

    fun poll() {
        if (!queryTimer.timePassed(POLL_INTERVAL.milliseconds)) return
        queryTimer.reset()
        dark = queryIsDark()
    }

    private fun queryIsDark(): Boolean =
        when (currentSystemTheme) {
            SystemTheme.LIGHT -> false
            else -> true
        }
}
