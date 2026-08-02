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

package com.lambda.config.settings.comparable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.brigadier.argument.boolean
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

class BooleanSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<BooleanSetting, Boolean>,
	visibility: () -> Boolean,
	defaultValue: Boolean
) : Setting<Boolean>(name, description, defaultValue, layer, config, visibility) {

	@ExperimentalMaterial3Api
	@Composable
	override fun gui() {
		val stateValue by observeState()
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			modifier = Modifier
				.fillMaxWidth()
				.height(16.dp)
				.clickable { value = !stateValue }
				.padding(horizontal = 4.dp)
		) {
			Box(
				modifier = Modifier
					.size(10.dp)
					.clip(RoundedCornerShape(2.dp))
					.background(if (stateValue) colorScheme.primary else Color(0xFF442233)),
				contentAlignment = Alignment.Center
			) {
				if (stateValue) {
					Text(
						text = "✔",
						color = Color.White,
						fontSize = 8.sp,
						modifier = Modifier.padding(bottom = 1.dp)
					)
				}
			}
			Text(
				text = name,
				color = colorScheme.onSurface,
				style = androidx.compose.ui.text.TextStyle(
					fontSize = 9.sp, lineHeight = 9.sp, lineHeightStyle = LineHeightStyle(
						alignment = LineHeightStyle.Alignment.Center,
						trim = LineHeightStyle.Trim.Both
					)
				)
			)
		}
	}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(boolean(name)) { parameter ->
			execute {
				trySetValue(parameter().value())
			}
		}
	}
}
