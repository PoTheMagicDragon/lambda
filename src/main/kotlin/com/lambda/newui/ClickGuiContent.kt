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
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.components.CategoryPanel
import com.lambda.newui.state.LambdaState.observeShownTags
import org.jetbrains.compose.resources.painterResource

/**
 * The click GUI: the menu bar and one draggable panel per shown category. Composed permanently
 * by [LambdaOverlay] and placed only while [visible], so panel positions and focus order survive
 * closing and reopening. Clicking away from an open settings panel is handled by the overlay
 * root, which owns [selectedSettingsModule].
 */
@Composable
fun ClickGuiContent(
	visible: Boolean,
	selectedSettingsModule: Module?,
	onSettingsModuleChange: (Module?) -> Unit
) {
	val shownTags by observeShownTags()
	val focusOrder = remember { mutableStateListOf<ModuleTag>() }

	val zIndexOf = shownTags
		.sortedBy { focusOrder.indexOf(it) }
		.withIndex()
		.associate { (index, tag) -> tag to index.toFloat() }

	Column(
		modifier = Modifier
			.placeIf(visible)
			.fillMaxSize(),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		// Menu bar
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(20.dp)
				.frostedBackground(colorScheme.background)
				.border(BorderStroke(1.dp, colorScheme.outline))
		) {
			Image(
				painter = painterResource(Res.drawable.lambda),
				contentDescription = "Lambda logo"
			)
		}

		// Categories
		Row(
			horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
			modifier = Modifier
				.fillMaxSize()
				.padding(6.dp)
				.offset(y = 100.dp),
		) {
			shownTags.forEach { tag ->
				key(tag) {
					CategoryPanel(
						tag = tag,
						zIndex = zIndexOf[tag] ?: 0f,
						onFocus = {
							focusOrder.remove(tag)
							focusOrder.add(tag)
						},
						selectedSettingsModule = selectedSettingsModule,
						onSettingsModuleChange = onSettingsModuleChange
					)
				}
			}
		}
	}
}
