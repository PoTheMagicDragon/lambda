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

package com.lambda.newui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import com.lambda.module.Module
import com.lambda.module.modules.client.LambdaTheme
import com.lambda.newui.hud.ComposeHud
import com.lambda.newui.hud.HudContextMenu
import com.lambda.newui.hud.HudLayer
import kotlinx.coroutines.launch

/**
 * Everything the Compose scene draws over the game, bottom to top: the HUD, the click GUI while
 * it is open, and the HUD's context menu. The click GUI stays composed while closed so its
 * windows keep their positions; it is merely left unplaced, which also keeps it out of hit
 * testing so presses on empty space reach the HUD.
 */
@Composable
fun LambdaOverlay() {
    LambdaTheme {
        val clickGuiOpen = ComposeClickGui.open
        var selectedSettingsModule by remember { mutableStateOf<Module?>(null) }
        val scope = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.type != PointerEventType.Press) continue
                            // A press nothing claimed: close whatever is open on top.
                            if (event.changes.any { it.isConsumed }) continue
                            ComposeHud.contextMenu = null
                            scope.launch { selectedSettingsModule = null }
                        }
                    }
                }
        ) {
            HudLayer(editing = clickGuiOpen)
            ClickGuiContent(
                visible = clickGuiOpen,
                selectedSettingsModule = selectedSettingsModule,
                onSettingsModuleChange = { selectedSettingsModule = it }
            )
            if (clickGuiOpen) ComposeHud.contextMenu?.let { HudContextMenu(it) }
        }
    }
}

/**
 * Measures the node as usual but, while [placed] is false, never places it. An unplaced node is
 * neither drawn nor hit-tested, yet stays composed, so its remembered state survives.
 */
fun Modifier.placeIf(placed: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (placed) placeable.place(0, 0)
    }
}
