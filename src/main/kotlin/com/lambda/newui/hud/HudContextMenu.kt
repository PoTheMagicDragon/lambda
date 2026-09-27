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

package com.lambda.newui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.gui.components.HudGuiLayout
import com.lambda.module.HudModule
import com.lambda.newui.ComposeRenderer
import com.lambda.newui.frostedBackground
import com.lambda.newui.state.LambdaState.observeEnabled
import com.lambda.newui.theme.Radius
import kotlin.math.roundToInt

/** What the HUD context menu was opened on, and where (root pixels). */
sealed class HudContextMenuState(val position: Offset) {
    /** Right-click on empty space: manage the HUD and add hidden elements. */
    class Background(position: Offset) : HudContextMenuState(position)

    /** Right-click on an element. */
    class Element(val hud: HudModule, position: Offset) : HudContextMenuState(position)
}

/**
 * The HUD's right-click menu. Hosted at the overlay root so it sits above the click GUI windows,
 * and closed by [ComposeHud.contextMenu] going null: on a choice here, or on a click anywhere
 * else, which the overlay root handles.
 */
@Composable
fun HudContextMenu(state: HudContextMenuState) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scene = ComposeRenderer.sceneSize.value
    var size by remember { mutableStateOf(IntSize.Zero) }
    val origin = clampToScene(state.position, size, scene)
    val shape = RoundedCornerShape(Radius.Small)

    fun close() {
        ComposeHud.contextMenu = null
    }

    Column(
        modifier = Modifier
            .offset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
            .onSizeChanged { size = it }
            .widthIn(min = 120.dp)
            .clip(shape)
            .frostedBackground(colors.surfaceVariant, shape)
            .border(1.dp, colors.outline, shape)
            .pointerInput(Unit) {
                // Presses inside the menu are the menu's own; the layers underneath must not see
                // them as a click-away.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Press) event.changes.forEach { it.consume() }
                    }
                }
            }
            .padding(vertical = 2.dp)
    ) {
        when (state) {
            is HudContextMenuState.Element -> {
                MenuItem("Remove HUD Element") {
                    state.hud.disable()
                    close()
                }
                MenuDivider()
                HudToggles(::close)
            }

            is HudContextMenuState.Background -> {
                HudToggles(::close)
                MenuDivider()
                val hidden = ComposeHud.modules
                    .filter { !it.observeEnabled().value }
                    .sortedBy { it.name.lowercase() }
                if (hidden.isEmpty()) {
                    MenuLabel("No hidden HUD elements", colors.onSurfaceVariant)
                } else {
                    MenuLabel("Add HUD Element", colors.onSurfaceVariant)
                    hidden.forEach { hud ->
                        MenuItem("+ ${hud.name}") {
                            // New elements appear where the menu was opened.
                            with(density) {
                                hud.hudX.value = state.position.x.toDp().value
                                hud.hudY.value = state.position.y.toDp().value
                            }
                            hud.enable()
                            close()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudToggles(close: () -> Unit) {
    MenuItem(if (HudGuiLayout.isLocked) "Unlock HUD" else "Lock HUD") {
        HudGuiLayout.isLocked = !HudGuiLayout.isLocked
        close()
    }
    MenuItem(if (HudGuiLayout.isShownInGUI) "Hide HUD" else "Show HUD") {
        HudGuiLayout.isShownInGUI = !HudGuiLayout.isShownInGUI
        close()
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(if (hovered) colors.primaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = if (hovered) colors.onPrimaryContainer else colors.onSurface,
        fontSize = 9.sp,
        lineHeight = 9.sp
    )
}

@Composable
private fun MenuLabel(label: String, color: Color) {
    Text(
        text = label,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        fontSize = 9.sp,
        lineHeight = 9.sp
    )
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}
