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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.module.Module
import com.lambda.newui.state.LambdaState.observeEnabled
import kotlinx.coroutines.launch

@Composable
fun ModuleCard(
    module: Module,
    isSettingsOpen: Boolean,
    onRightClick: () -> Unit,
    onPositionChange: (LayoutCoordinates) -> Unit
) {
    val enabled by module.observeEnabled()
    val colors = MaterialTheme.colorScheme

    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) colors.secondaryContainer else Color.Transparent,
        animationSpec = spring(),
        label = "moduleCardBg"
    )
    val textColor = if (enabled) colors.onSecondaryContainer else colors.onSurfaceVariant

    // The open settings panel is marked with an outline so the card keeps showing
    // its own enabled/disabled colouring.
    val outlineColor by animateColorAsState(
        targetValue = if (isSettingsOpen) colors.primary else Color.Transparent,
        animationSpec = spring(),
        label = "moduleCardOutline"
    )

    val currentOnRightClick by rememberUpdatedState(onRightClick)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .onGloballyPositioned { onPositionChange(it) }
            .fillMaxWidth()
            .background(backgroundColor)
            // The card spans the panel's full width, so an un-inset outline would put its
            // side edges underneath the panel's own border. Inset the outline, not the fill.
            .padding(horizontal = 1.dp)
            .border(1.dp, outlineColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Press) {
                            if (event.buttons.isSecondaryPressed) {
                                scope.launch { currentOnRightClick() }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .clickable { module.toggle() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = module.name,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(color = colors.scrim, offset = Offset(2f, 2f)))
        )
    }
}