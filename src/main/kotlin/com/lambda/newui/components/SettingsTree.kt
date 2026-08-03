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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.config.EntryLayer
import com.lambda.config.MultipleLayerType
import com.lambda.config.entries.Setting
import com.lambda.newui.frosted
import com.lambda.newui.theme.Radius

/**
 * Collapsible, labeled container used for setting groups and for the
 * module/automation config sections appended below a module's settings.
 */
@Composable
fun SettingsGroup(
    name: String,
    suffix: String? = null,
    content: @Composable () -> Unit
) {
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
                .heightIn(min = 16.dp)
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
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                style = TextStyle(
                    fontSize = 9.sp, lineHeight = 9.sp, lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )
            if (suffix != null) {
                Text(
                    text = suffix,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                    style = TextStyle(
                        fontSize = 8.sp, lineHeight = 8.sp, lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
            }
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
                content()
            }
        }
    }
}

/**
 * Whether this layer holds at least one currently visible setting, at any depth.
 * Layers that hold none are skipped entirely so the panel has no empty headers.
 */
fun EntryLayer.Multiple<Setting<*>>.hasVisibleSettings(): Boolean =
    layers.any { child ->
        when (child) {
            is EntryLayer.Single -> child.entry.visibility()
            is EntryLayer.Multiple -> child.hasVisibleSettings()
            else -> false
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                    .filter { it.multipleType == MultipleLayerType.Tab && it.hasVisibleSettings() }
                if (tabs.isEmpty()) return
                var selectedTabIndex by remember { mutableStateOf(0) }

                val colors = MaterialTheme.colorScheme
                val barShape = RoundedCornerShape(Radius.Small)

                Column {
                    // A TabRow splits its width evenly, which squeezes longer names down to a
                    // single wrapped character in the narrow panel. The tabs are laid out as one
                    // continuous segmented bar instead: labels keep their own width and wrap onto
                    // more rows when needed, and the single filled cell reads as "pick one".
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .clip(barShape)
                            .background(colors.surfaceContainer.frosted())
                            .border(1.dp, colors.outlineVariant, barShape)
                    ) {
                        tabs.forEachIndexed { index, tabLayer ->
                            val selected = selectedTabIndex == index
                            Box(
                                modifier = Modifier
                                    .background(if (selected) colors.primary else Color.Transparent)
                                    .clickable { selectedTabIndex = index }
                                    .padding(horizontal = 5.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tabLayer.name,
                                    color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
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

                    // The selection can fall out of range when a tab stops having visible settings.
                    val selectedTab = tabs.getOrNull(selectedTabIndex) ?: tabs.first()
                    Column {
                        selectedTab.layers.forEach { child ->
                            SettingsTree(child)
                        }
                    }
                }
            } else if (layer.multipleType == MultipleLayerType.Group) {
                if (!layer.hasVisibleSettings()) return
                SettingsGroup(layer.name) {
                    layer.layers.forEach { child ->
                        SettingsTree(child)
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

