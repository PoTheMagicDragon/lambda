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

package com.lambda.newui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.components.CategoryPanel
import com.lambda.newui.state.LambdaState.observeShownTags
import com.lambda.newui.theme.LambdaTheme
import org.jetbrains.compose.resources.painterResource

/**
 * Top-level composable for the Lambda click GUI.
 *
 * Renders a row of [CategoryPanel]s — one per shown [ModuleTag]. Each panel lists its
 * modules as clickable toggle cards. Panels are individually draggable by their header.
 * Most recently interacted panel renders on top of others.
 *
 * The shown-tag set is observed, so toggling a category from the ImGui menu bar adds or
 * removes its panel live.
 */
@Composable
fun ClickGuiContent() {
	LambdaTheme {
		val shownTags by observeShownTags()

		// Focus history, most recently focused last. Derived from rather than mirroring
		// the shown set, so it tolerates tags appearing and disappearing: stale entries
		// are simply never looked up, and a tag that has never been focused is absent.
		val focusOrder = remember { mutableStateListOf<ModuleTag>() }

		// Never-focused tags yield indexOf == -1 and so sort to the front, i.e. render
		// beneath focused ones. sortedBy is stable, so they keep shownTags order among
		// themselves instead of collapsing onto a shared z-index.
		val zIndexOf = shownTags
			.sortedBy { focusOrder.indexOf(it) }
			.withIndex()
			.associate { (index, tag) -> tag to index.toFloat() }

		Column(
			modifier = Modifier.fillMaxSize(),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.height(20.dp)
					.background(colorScheme.background)
					.border(BorderStroke(1.dp, colorScheme.outline))
			) {
				Image(
					painter = painterResource(Res.drawable.lambda),
					contentDescription = "Lambda logo"
				)
			}
			Row(
				horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
				modifier = Modifier
					.fillMaxSize()
					.padding(6.dp)
					.offset(y = 100.dp),
			) {
				// Layout order stays the shown-tag order so panels don't jump around
				// horizontally when one is focused; only zIndex reacts to focus.
				shownTags.forEach { tag ->
					// Keyed so a tag being shown or hidden doesn't shift the panels after it
					// onto the wrong remembered drag offset and expanded state.
					key(tag) {
						CategoryPanel(
							tag = tag,
							zIndex = zIndexOf[tag] ?: 0f,
							onFocus = {
								focusOrder.remove(tag)
								focusOrder.add(tag) // Move to end = highest z-index
							}
						)
					}
				}
			}
		}
	}
}
