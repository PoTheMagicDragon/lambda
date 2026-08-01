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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lambda.config.Config
import com.lambda.config.ConfigEditor
import com.lambda.config.ConfigEditorD5l
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.dsl.ImGuiBuilder
import java.text.NumberFormat
import java.util.*

/**
 * @see [com.lambda.config.Config]
 */
abstract class NumericSetting<T>(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<NumericSetting<T>, T>,
	defaultValue: T,
	visibility: () -> Boolean,
	open var range: ClosedRange<T>,
	open var step: T,
	var unit: String
) : Setting<T>(name, description, defaultValue, layer, config, visibility) where T : Number, T : Comparable<T> {
	override var value: T
		get() = super.value
		set(newVal) {
			super.value = newVal.coerceIn(range)
		}

	private val formatter = NumberFormat.getNumberInstance(Locale.getDefault())

	override fun toString() = "${formatter.format(value)}$unit"

	/**
	 * Subclasses must implement this to provide their specific slider widget.
	 */
	protected abstract fun ImGuiBuilder.buildSlider()

	@ExperimentalMaterial3Api
	@Composable
	override fun gui() {
		val showReset = isModified
		val resetButtonText = "R"
		val valueString = this.toString()

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(IntrinsicSize.Min), // Prevents the Row from stretching if children are smaller
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = name,
				style = MaterialTheme.typography.bodyMedium
			)

//			Slider(
//				value = value,
//				onValueChange = { newValue ->
//				},
//				modifier = Modifier.weight(1f)
//			)

			BoxWithConstraints(
				modifier = Modifier.fillMaxHeight(),
				contentAlignment = Alignment.CenterStart
			) {
				val textMeasurer = rememberTextMeasurer()
				val textStyle = MaterialTheme.typography.bodyMedium
				val valueTextWidth = remember(valueString) {
					textMeasurer.measure(
						text = valueString,
						style = textStyle
					).size.width.dp
				}

				if (maxWidth > valueTextWidth) {
					Text(
						text = valueString,
						style = textStyle
					)
				}
			}

			if (showReset) {
				TooltipBox(
					positionProvider = rememberPlainTooltipPositionProvider(),
					tooltip = {
						Text("Reset to default")
					},
					state = rememberTooltipState(isPersistent = false)
				) {
					Button(
						onClick = { reset() },
						modifier = Modifier.height(32.dp).width(32.dp),
						contentPadding = PaddingValues(0.dp)
					) {
						Text(resetButtonText)
					}
				}
			} else {
				Spacer(modifier = Modifier.size(width = 32.dp, height = 32.dp))
			}
		}
	}

	@Suppress("unchecked_cast", "unused")
	companion object {
		@ConfigEditorD5l
		fun <T> ConfigEditor.SettingEditBuilder<T>.range(range: ClosedRange<T>) where T : Number, T : Comparable<T> {
			(entries as Collection<NumericSetting<T>>).forEach { it.range = range }
		}

		@ConfigEditorD5l
		fun <T> ConfigEditor.SettingEditBuilder<T>.step(step: T) where T : Number, T : Comparable<T> {
			(entries as Collection<NumericSetting<T>>).forEach { it.step = step }
		}

		@ConfigEditorD5l
		fun <T> ConfigEditor.SettingEditBuilder<T>.unit(unit: String) where T : Number, T : Comparable<T> {
			(entries as Collection<NumericSetting<T>>).forEach { it.unit = unit }
		}
	}
}
