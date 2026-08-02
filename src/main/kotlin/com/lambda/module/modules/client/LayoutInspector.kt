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

package com.lambda.module.modules.client

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

/**
 * Developer overlay that inspects the click GUI's own composable tree — hover to highlight,
 * click to pin, and read back the bounds, call site and parameters of any composable.
 *
 * Settings are mirrored into [state] rather than read from Compose directly, because the
 * config layer isn't snapshot-aware. [sync] is pumped once per frame from the renderer, the
 * same way [Style.updateLambdaTheme] is.
 */
object LayoutInspector : Module(
    name = "Layout Inspector",
    description = "Inspect the click GUI's composable tree, bounds and call sites",
    tag = ModuleTag.CLIENT,
) {
    private val showAllGroups by setting(
        "Show All Groups",
        false,
        "Include remember and inline groups that don't correspond to a composable call"
    )

    private val showLabels by setting(
        "Show Size Labels",
        true,
        "Draw a size badge next to the highlighted bounds"
    )

    private val refreshRate by setting(
        "Refresh Rate",
        250,
        50..2000,
        50,
        "How often the composable tree is re-read, in milliseconds"
    )

    data class Settings(
        val enabled: Boolean,
        val showAllGroups: Boolean,
        val showLabels: Boolean,
        val refreshMillis: Long,
    )

    val state = mutableStateOf(Settings(false, false, true, 250L))

    fun sync() {
        // Modifier metadata (the readable names in the details panel) is only recorded while
        // this global is set, and only for modifiers created afterwards — so flip it as soon
        // as the module is on and leave it, rather than toggling with the module.
        if (isEnabled && !isDebugInspectorInfoEnabled) isDebugInspectorInfoEnabled = true

        val next = Settings(
            enabled = isEnabled,
            showAllGroups = showAllGroups,
            showLabels = showLabels,
            refreshMillis = refreshRate.toLong(),
        )
        if (state.value != next) state.value = next
    }
}
