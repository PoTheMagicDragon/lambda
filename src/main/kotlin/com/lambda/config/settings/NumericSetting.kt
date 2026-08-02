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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
		val stateValue by observeState()
		val valueString = stateValue.toString()

		val min = (range.start as Number).toFloat()
		val max = (range.endInclusive as Number).toFloat()
		val current = (stateValue as Number).toFloat()
		val fraction = ((current - min) / (max - min)).coerceIn(0f, 1f)

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 4.dp, vertical = 0.dp)
				.height(16.dp)
				.background(Color(0xFF333333))
				.pointerInput(Unit) {
					detectDragGestures { change, _ ->
						val percent = (change.position.x / size.width).coerceIn(0f, 1f)
						val newValue = min + (max - min) * percent
						value = when (stateValue) {
							is Int -> newValue.toInt() as T
							is Long -> newValue.toLong() as T
							is Float -> newValue as T
							is Double -> newValue.toDouble() as T
							else -> newValue as T
						}
					}
				}
				.pointerInput(Unit) {
					detectTapGestures { offset ->
						val percent = (offset.x / size.width).coerceIn(0f, 1f)
						val newValue = min + (max - min) * percent
						value = when (stateValue) {
							is Int -> newValue.toInt() as T
							is Long -> newValue.toLong() as T
							is Float -> newValue as T
							is Double -> newValue.toDouble() as T
							else -> newValue as T
						}
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
					text = name,
					color = Color.White,
					style = TextStyle(
						fontSize = 9.sp, lineHeight = 9.sp, lineHeightStyle = LineHeightStyle(
							alignment = LineHeightStyle.Alignment.Center,
							trim = LineHeightStyle.Trim.Both
						)
					)
				)
				Text(
					text = valueString,
					color = Color.White,
					style = TextStyle(
						fontSize = 9.sp, lineHeight = 9.sp, lineHeightStyle = LineHeightStyle(
							alignment = LineHeightStyle.Alignment.Center,
							trim = LineHeightStyle.Trim.Both
						)
					)
				)
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

