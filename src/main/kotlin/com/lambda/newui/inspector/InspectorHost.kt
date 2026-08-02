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

package com.lambda.newui.inspector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.lambda.module.modules.client.LambdaTheme
import com.lambda.module.modules.client.LayoutInspector

/**
 * Name of the marker composable that bounds the inspected subtree. The slot table belongs to
 * the whole composition, so without a marker the inspector would list its own panel as part
 * of the UI it is inspecting.
 */
const val INSPECTED_ROOT = "InspectedRoot"

/**
 * Wraps the click GUI so it can be inspected, and draws the inspector on top of it.
 *
 * Deliberately self-contained: it polls its own settings and carries its own theme, so
 * enabling the inspector costs the host exactly one call site. That keeps this branch to a
 * couple of lines of footprint on existing files, which is what makes it cheap to merge into
 * a feature branch and drop again afterwards.
 *
 * When the module is off this is a straight pass-through. Turning it on restructures the
 * composition, so the GUI's transient state (panel focus order, the open settings panel)
 * resets — acceptable for a debug tool, and it is what makes the composition start recording
 * the parameter information the details panel reads.
 */
@Composable
fun InspectorHost(content: @Composable () -> Unit) {
    // The config layer isn't snapshot-aware, so mirror it into Compose state once a frame
    // rather than making the renderer pump it for us.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { LayoutInspector.sync() }
        }
    }

    val settings by LayoutInspector.state

    if (!settings.enabled) {
        content()
        return
    }

    val compositionData = currentCompositionData()

    Box(Modifier.fillMaxSize()) {
        InspectedRoot(content)
        // The host may wrap the GUI from outside its theme, so the panel brings its own.
        LambdaTheme {
            InspectorOverlay(compositionData, settings)
        }
    }
}

@Composable
private fun InspectedRoot(content: @Composable () -> Unit) {
    content()
}

@OptIn(InternalComposeApi::class)
@Composable
private fun currentCompositionData(): CompositionData {
    // Composition-wide and one-way: groups already composed keep whatever they recorded, so
    // parameters only start appearing once each call recomposes.
    currentComposer.collectParameterInformation()
    return currentComposer.compositionData
}
