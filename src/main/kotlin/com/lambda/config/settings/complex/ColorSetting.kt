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

package com.lambda.config.settings.complex

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.optional
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess
import java.awt.Color as JColor

class ColorSetting(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<ColorSetting, JColor>,
    visibility: () -> Boolean,
    defaultValue: JColor
) : Setting<JColor>(name, description, defaultValue, layer, config, visibility) {
    @ExperimentalMaterial3Api
    @Composable
    override fun gui() {
        val stateValue by observeState()
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
                        .background(Color(stateValue.red, stateValue.green, stateValue.blue, stateValue.alpha))
                )
            }

            if (expanded) {
                Column(modifier = Modifier.padding(top = 2.dp, start = 6.dp)) {
                    ColorSlider("R", stateValue.red) { value = JColor(it, stateValue.green, stateValue.blue, stateValue.alpha) }
                    ColorSlider("G", stateValue.green) { value = JColor(stateValue.red, it, stateValue.blue, stateValue.alpha) }
                    ColorSlider("B", stateValue.blue) { value = JColor(stateValue.red, stateValue.green, it, stateValue.alpha) }
                    ColorSlider("A", stateValue.alpha) { value = JColor(stateValue.red, stateValue.green, stateValue.blue, it) }
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
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
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

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(integer("Red", 0, 255)) { red ->
            required(integer("Green", 0, 255)) { green ->
                required(integer("Blue", 0, 255)) { blue ->
                    optional(integer("Alpha", 0, 255)) { alpha ->
                        execute {
                            val alphaValue = alpha?.let { it().value() } ?: 255
                            trySetValue(JColor(red().value(), green().value(), blue().value(), alphaValue))
                        }
                    }
                }
            }
        }
    }
}
