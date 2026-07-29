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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.theme.LambdaColors

/**
 * A vertical panel displaying all modules belonging to a specific [ModuleTag] category.
 * Each module is rendered as a [ModuleCard].
 */
@Composable
fun CategoryPanel(tag: ModuleTag) {
    val modules = ModuleRegistry.modules.filter { it.tag == tag && it.showInClickGui.value }
    if (modules.isEmpty()) return

    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(LambdaColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, LambdaColors.Border.copy(alpha = 0.6f), shape)
    ) {
        // Category header
        Text(
            text = tag.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = LambdaColors.OnSurface,
            modifier = Modifier
                .background(LambdaColors.HeaderBg.copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .width(180.dp)
        )

        // Scrollable module list
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
