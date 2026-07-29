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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.module.Module
import com.lambda.newui.theme.LambdaColors

/**
 * A clickable card representing a single [Module].
 * Displays the module name, toggles on click, and animates the background color
 * between enabled (blue highlight) and disabled (dark muted) states.
 */
@Composable
fun ModuleCard(module: Module) {
    // Track module enabled state reactively.
    // We re-read isEnabled each recomposition; Compose will recompose when the value changes
    // because we wrap it in a mutableStateOf + remember pattern with a key.
    var enabled by remember { mutableStateOf(module.isEnabled) }

    // Sync with actual module state each recomposition
    enabled = module.isEnabled

    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) LambdaColors.ModuleEnabled else LambdaColors.ModuleDisabled,
        animationSpec = tween(durationMillis = 150),
        label = "moduleCardBg"
    )

    val textColor = if (enabled) LambdaColors.OnSurface else LambdaColors.OnSurfaceVariant
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable {
                module.toggle()
                enabled = module.isEnabled
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = module.name,
            fontSize = 12.sp,
            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}
