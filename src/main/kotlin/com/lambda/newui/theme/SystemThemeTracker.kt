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

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

/**
 * Tracks whether the OS is in dark mode, as snapshot state.
 *
 * ### Why not `isSystemInDarkTheme()`
 *
 * Compose's own `isSystemInDarkTheme()` reads `LocalSystemTheme`, which is
 * `@InternalComposeUiApi` and — on this setup — never provided: the GUI drives a bare
 * [androidx.compose.ui.scene.ComposeScene] with no window layer, so the composition local
 * falls through to its default. That default does query the OS (via the same skiko call
 * used here), but it is a `staticCompositionLocalOf` behind a lazy holder, so the value is
 * computed once and then frozen for the life of the process, and reading a static local
 * creates no recomposition subscription. It would report the theme at startup and never
 * change. This object queries the same source but keeps it in ordinary snapshot state, so
 * a change actually recomposes.
 *
 * ### Cost
 *
 * `currentSystemTheme` bottoms out in a native JNI call (`SystemThemeHelper`), reading
 * `AppleInterfaceStyle` on macOS and the personalization registry key on Windows. There is
 * no AWT toolkit involved and no subprocess, so it is cheap enough to poll — but not free,
 * hence the throttle in [poll].
 */
object SystemThemeTracker {
    /**
     * Minimum gap between OS queries. The GUI only polls while it is open, so this trades a
     * worst-case half-second lag on a theme flip against two JNI calls per second.
     */
    private const val POLL_INTERVAL_MS = 500L

    private val darkState = mutableStateOf(queryIsDark())
    private var lastQuery = 0L

    /** Reading this from a composable subscribes it to OS theme changes. */
    val isDark: State<Boolean> get() = darkState

    /** Queries the OS immediately. Call when the GUI opens, so it never appears stale. */
    fun refresh() {
        lastQuery = System.currentTimeMillis()
        darkState.value = queryIsDark()
    }

    /**
     * Queries the OS at most once per [POLL_INTERVAL_MS]. Safe to call every frame.
     *
     * Snapshot writes take the global snapshot lock and are safe from any thread, so this
     * can run on the render thread without marshalling.
     */
    fun poll() {
        val now = System.currentTimeMillis()
        if (now - lastQuery < POLL_INTERVAL_MS) return
        lastQuery = now
        darkState.value = queryIsDark()
    }

    private fun queryIsDark(): Boolean = when (currentSystemTheme) {
        SystemTheme.LIGHT -> false
        // DARK, plus UNKNOWN on platforms skiko cannot read a preference from. The GUI has
        // always been dark, so that is the less surprising fallback.
        else -> true
    }
}
