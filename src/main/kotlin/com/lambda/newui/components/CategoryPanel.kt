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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.theme.LambdaColors

@Composable
fun CategoryPanel(tag: ModuleTag) {
    val modules = ModuleRegistry.modules.filter { it.tag == tag && it.showInClickGui.value }
    if (modules.isEmpty()) return

    // 1. State to track if the dropdown is expanded
    var expanded by remember { mutableStateOf(true) } // Set to false if you want collapsed by default

    val shape = RoundedCornerShape(3.dp)

    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(shape)
            .background(LambdaColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, LambdaColors.Border.copy(alpha = 0.6f), shape)
    ) {
        // 2. Clickable Header (Row)
        Row(
            modifier = Modifier
                .background(LambdaColors.HeaderBg.copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.type == PointerEventType.Press) {
                                if (event.buttons.isSecondaryPressed) {
                                    expanded = !expanded
                                }
                            }
                        }
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tag.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = LambdaColors.OnSurface,
            )
        }

        // 5. Content (Modules) - only visible when expanded
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp)
            ) {
                modules.forEach { module ->
                    ModuleCard(module)
                }
            }
        }
    }
}