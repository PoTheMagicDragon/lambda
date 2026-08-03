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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ColorSettingGui(
    name: String,
    red: Int,
    green: Int,
    blue: Int,
    alpha: Int,
    onColorChange: (r: Int, g: Int, b: Int, a: Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clickable { expanded = !expanded }
        ) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                style = TextStyle(
                    fontSize = 9.sp, lineHeight = 9.sp, lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )

            Box(
                modifier = Modifier
                    .size(14.dp, 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(red, green, blue, alpha))
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(top = 2.dp, start = 6.dp)) {
                ColorSlider("R", red) { onColorChange(it, green, blue, alpha) }
                ColorSlider("G", green) { onColorChange(red, it, blue, alpha) }
                ColorSlider("B", blue) { onColorChange(red, green, it, alpha) }
                ColorSlider("A", alpha) { onColorChange(red, green, blue, it) }
            }
        }
    }
}

@Composable
private fun ColorSlider(label: String, colorValue: Int, onValueChange: (Int) -> Unit) {
    val fraction = colorValue / 255f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .height(12.dp)
            .background(Color(0xFF333333))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val percent = (change.position.x / size.width).coerceIn(0f, 1f)
                    onValueChange((percent * 255).toInt())
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val percent = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange((percent * 255).toInt())
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(MaterialTheme.colorScheme.primary)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                style = TextStyle(
                    fontSize = 8.sp, lineHeight = 8.sp, lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )
            Text(
                text = colorValue.toString(),
                color = Color.White,
                style = TextStyle(
                    fontSize = 8.sp, lineHeight = 8.sp, lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )
        }
    }
}
