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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.config.EntryLayer
import com.lambda.config.MultipleLayerType
import com.lambda.config.entries.Setting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTree(layer: EntryLayer<Setting<*>>) {
    when (layer) {
        is EntryLayer.Single -> {
            val setting = layer.entry
            AnimatedVisibility(visible = setting.visibility()) {
                setting.gui()
            }
        }
        is EntryLayer.Multiple -> {
            val hasTabs = layer.layers.any { it is EntryLayer.Multiple && it.multipleType == MultipleLayerType.Tab }

            if (hasTabs) {
                val tabs = layer.layers.filterIsInstance<EntryLayer.Multiple<Setting<*>>>()
                    .filter { it.multipleType == MultipleLayerType.Tab }
                var selectedTabIndex by remember { mutableStateOf(0) }

                Column {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().height(20.dp)
                    ) {
                        tabs.forEachIndexed { index, tabLayer ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(tabLayer.name, fontSize = 8.sp) }
                            )
                        }
                    }
                    
                    val selectedTab = tabs.getOrNull(selectedTabIndex)
                    if (selectedTab != null) {
                        Column(modifier = Modifier.padding(top = 2.dp)) {
                            selectedTab.layers.forEach { child ->
                                SettingsTree(child)
                            }
                        }
                    }
                }
            } else if (layer.multipleType == MultipleLayerType.Group) {
                var expanded by remember { mutableStateOf(false) }

                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 90f else 0f,
                    animationSpec = tween(150)
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "▶",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 4.dp).rotate(rotation),
                            style = TextStyle(
                                fontSize = 7.sp, lineHeight = 7.sp, lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both
                                )
                            )
                        )
                        Text(
                            text = layer.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = TextStyle(
                                fontSize = 9.sp, lineHeight = 9.sp, lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both
                                )
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(animationSpec = tween(150)) + fadeIn(animationSpec = tween(150)),
                        exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp)
                        ) {
                            layer.layers.forEach { child ->
                                SettingsTree(child)
                            }
                        }
                    }
                }
            } else {
                // Root or standard multiple
                Column(modifier = Modifier.fillMaxWidth()) {
                    layer.layers.forEach { child ->
                        SettingsTree(child)
                    }
                }
            }
        }
    }
}

