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

package com.lambda.config.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.config.Config
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer

class FunctionSetting<T : () -> R, R>(
	name: String,
	description: String,
	defaultValue: T,
	config: Config,
	layer: SettingEntryLayer<FunctionSetting<T, R>, T>,
	visibility: () -> Boolean
) : Setting<T>(name, description, defaultValue, layer, config, visibility) {
	@ExperimentalMaterial3Api
	@Composable
	override fun gui() {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 4.dp, vertical = 1.dp),
			contentAlignment = Alignment.Center
		) {
			Button(
				onClick = { value() },
				modifier = Modifier
					.fillMaxWidth()
					.height(24.dp),
				contentPadding = PaddingValues(0.dp)
			) {
				Text(
					text = name,
					fontSize = 9.sp
				)
			}
		}
	}
}
