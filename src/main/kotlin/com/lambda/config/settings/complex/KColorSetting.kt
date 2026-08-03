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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.optional
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.newui.components.ColorSettingGui
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

class KColorSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<KColorSetting, Color>,
	visibility: () -> Boolean,
	defaultValue: Color
) : Setting<Color>(name, description, defaultValue, layer, config, visibility) {
	@ExperimentalMaterial3Api
	@Composable
	override fun gui() {
		val stateValue by observeState()
		
		ColorSettingGui(
			name = name,
			red = (stateValue.red * 255).toInt(),
			green = (stateValue.green * 255).toInt(),
			blue = (stateValue.blue * 255).toInt(),
			alpha = (stateValue.alpha * 255).toInt(),
			onColorChange = { r, g, b, a ->
				value = Color(r / 255f, g / 255f, b / 255f, a / 255f)
			}
		)
	}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(integer("Red", 0, 255)) { red ->
			required(integer("Green", 0, 255)) { green ->
				required(integer("Blue", 0, 255)) { blue ->
					optional(integer("Alpha", 0, 255)) { alpha ->
						execute {
							val alphaValue = alpha?.let { it().value() } ?: 255
							trySetValue(Color(red().value(), green().value(), blue().value(), alphaValue))
						}
					}
				}
			}
		}
	}
}