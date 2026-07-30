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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.state.LambdaState.observe
import com.lambda.newui.theme.LambdaColors
import kotlin.math.roundToInt

private const val GRID_SIZE_DP = 4f

private fun snapToGrid(value: Float, gridSize: Float): Float {
    return (value / gridSize).roundToInt() * gridSize
}

@Composable
fun CategoryPanel(tag: ModuleTag, zIndex: Float = 0f, onFocus: () -> Unit = {}) {
    // ModuleRegistry.modules is fixed after load, so the tag filter can be cached.
    // "Show In ClickGui" is a live setting, so it has to be observed on every module —
    // reading it here re-runs this filter whenever any of them changes.
    val tagged = remember(tag) { ModuleRegistry.modules.filter { it.tag == tag } }
    val modules = tagged.filter { it.showInClickGui.observe().value }
    if (modules.isEmpty()) return

    var expanded by remember { mutableStateOf(true) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val shape = RoundedCornerShape(1.dp)

    Column(
        modifier = Modifier
            .zIndex(zIndex)
            .offset {
                IntOffset(
                    snapToGrid(dragOffset.x, GRID_SIZE_DP * density).roundToInt(),
                    snapToGrid(dragOffset.y, GRID_SIZE_DP * density).roundToInt()
                )
            }
            .width(100.dp)
            .background(LambdaColors.Surface, shape)
            .border(1.dp, LambdaColors.Border, shape)
    ) {
        Row(
            modifier = Modifier
                .background(LambdaColors.HeaderBg)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onFocus() },
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
                                onFocus()
                                if (event.buttons.isSecondaryPressed) {
                                    expanded = !expanded
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
                color = LambdaColors.OnSurface,
                style = TextStyle(shadow = Shadow(offset = Offset(2f, 2f)))
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                modules.forEach { module ->
                    // Keyed because the list is now dynamic — `remember` is positional,
                    // so without a key a module appearing or disappearing would shift
                    // every card below it onto the wrong retained state.
                    key(module.name) {
                        ModuleCard(module)
                    }
                }
            }
        }
    }
}