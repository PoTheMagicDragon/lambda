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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.components.CategoryPanel
import com.lambda.newui.theme.LambdaTheme

/**
 * Top-level composable for the Lambda click GUI.
 *
 * Renders a horizontally scrollable row of [CategoryPanel]s — one per [ModuleTag] in the
 * default tag set. Each panel lists its modules as clickable toggle cards.
 */
@Composable
fun ClickGuiContent() {
    LambdaTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            ModuleTag.shownTags.forEach { tag ->
                CategoryPanel(tag)
            }
        }
    }
}
