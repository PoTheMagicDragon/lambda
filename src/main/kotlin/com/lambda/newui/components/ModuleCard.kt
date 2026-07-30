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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.module.Module
import com.lambda.newui.state.LambdaState.observeEnabled

/**
 * A clickable card representing a single [Module].
 * Displays the module name, toggles on click, and animates the background color
 * between its enabled and disabled states.
 *
 * The enabled flag is observed from the module itself, so the card stays correct when
 * the module is toggled by a keybind, a command, or a config load rather than a click.
 */
@Composable
fun ModuleCard(module: Module) {
    val enabled by module.observeEnabled()

    val colors = MaterialTheme.colorScheme

    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) colors.secondaryContainer else colors.surfaceContainer,
        animationSpec = tween(durationMillis = 150),
        label = "moduleCardBg"
    )

    val textColor = if (enabled) colors.onSecondaryContainer else colors.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable { module.toggle() }
            .padding(horizontal = 5.dp, vertical = 3.dp)
    ) {
        Text(
            text = module.name,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(offset = Offset(2f, 2f)))
        )
    }
}
