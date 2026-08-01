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

package com.lambda.newui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.state.LambdaState.observe
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val GRID_SIZE_DP = 4f

private fun snapToGrid(value: Float, gridSize: Float) =
    (value / gridSize).roundToInt() * gridSize

@Composable
fun CategoryPanel(
    tag: ModuleTag,
    zIndex: Float = 0f,
    onFocus: () -> Unit = {},
    selectedSettingsModule: Module? = null,
    onSettingsModuleChange: (Module?) -> Unit = {}
) {
    val tagged = remember(tag) { ModuleRegistry.modules.filter { it.tag == tag } }
    val modules = tagged.filter { it.showInClickGui.observe().value }
    if (modules.isEmpty()) return

    var expanded by remember { mutableStateOf(true) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val yOffsets = remember { androidx.compose.runtime.mutableStateMapOf<String, Float>() }
    var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(2.dp)
    val settingsShape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(
        modifier = Modifier
            .zIndex(zIndex)
            .width(100.dp)
            .offset {
                IntOffset(
                    snapToGrid(dragOffset.x, GRID_SIZE_DP * density).roundToInt(),
                    snapToGrid(dragOffset.y, GRID_SIZE_DP * density).roundToInt()
                )
            }
    ) {
        // Settings Panels - Defined first so they render behind the main panel
        Box(modifier = Modifier.matchParentSize()) {
            modules.forEach { module ->
                key(module.name) {
                    val yOffset = yOffsets[module.name] ?: 0f
                    AnimatedVisibility(
                        visible = selectedSettingsModule == module,
                        enter = slideInHorizontally(animationSpec = spring()) { -it / 2 } + fadeIn(),
                        exit = slideOutHorizontally(animationSpec = spring()) { -it / 2 } + fadeOut(),
                        modifier = Modifier
                            .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                            .offset { IntOffset(99.dp.roundToPx(), yOffset.roundToInt()) }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .clip(settingsShape)
                                .background(colors.surfaceVariant)
                                .border(1.dp, colors.outline, settingsShape)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            if (event.type == PointerEventType.Press) {
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "${module.name} Settings",
                                color = colors.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Main Panel
        key("MainPanel") {
            Column(
                modifier = Modifier
                    .onGloballyPositioned { panelCoordinates = it }
                    .width(100.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    scope.launch { onFocus() }
                                }
                            }
                        }
                    }
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, colors.outline, shape)
            ) {
                Row(
                    modifier = Modifier
                        .background(colors.primaryContainer)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { scope.launch { onFocus() } },
                                onDrag = { _, dragAmount ->
                                    dragOffset += dragAmount
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    if (event.type == PointerEventType.Press) {
                                        if (event.buttons.isSecondaryPressed) {
                                            scope.launch {
                                                expanded = !expanded
                                                if (!expanded && modules.contains(selectedSettingsModule)) {
                                                    onSettingsModuleChange(null)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tag.name,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer,
                        style = TextStyle(shadow = Shadow(color = colors.scrim, offset = Offset(2f, 2f)))
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                    ) {
                        modules.forEach { module ->
                            key(module.name) {
                                ModuleCard(
                                    module = module,
                                    isSettingsOpen = selectedSettingsModule == module,
                                    onRightClick = {
                                        if (selectedSettingsModule == module) {
                                            onSettingsModuleChange(null)
                                        } else {
                                            onSettingsModuleChange(module)
                                        }
                                    },
                                    onPositionChange = { cardCoordinates ->
                                        if (panelCoordinates != null && cardCoordinates.isAttached) {
                                            val pos = panelCoordinates!!.localPositionOf(cardCoordinates, Offset.Zero)
                                            yOffsets[module.name] = pos.y
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}