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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.lambda.gui.components.HudGuiLayout
import com.lambda.newui.state.LambdaState.observe

/** Text size at [HudGuiLayout.scale] 1. Scene dp, so it grows with the framebuffer. */
private val BASE_TEXT_SIZE = 10.sp

/** The HUD-wide text colour. Read through a composable so the caller recomposes when it changes. */
@Composable
fun hudTextColor(): Color = HudGuiLayout.textColor.observe().value

/**
 * A line (or block) of HUD text in the HUD's colour, size and shadow. HUD modules build their
 * [com.lambda.module.HudModule.Content] from this so every element reads the same over the world.
 */
@Composable
fun HudText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = hudTextColor()
) {
    val scale by HudGuiLayout.scale.observe()
    val size = BASE_TEXT_SIZE * scale
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        lineHeight = size * 1.25f,
        style = TextStyle(shadow = Shadow(color = MaterialTheme.colorScheme.scrim, offset = Offset(2f, 2f)))
    )
}
