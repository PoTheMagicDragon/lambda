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
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.config.categories.UserAutomationCategory
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.newui.frosted
import com.lambda.newui.theme.Radius

/**
 * Renders the "Module Settings" and "Automation Config" sections that used to sit
 * as buttons at the top of the old ImGui settings popup. They are appended below
 * the module's own settings instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleConfigSettings(module: Module) {
    if (module == AutoUpdater) return

    if (module.automationConfig !== AutomationConfig.DEFAULT) {
        var linked by remember(module) { mutableStateOf(module.backingAutomationConfig) }

        SettingsGroup(
            name = "Automation Config",
            suffix = if (linked !== module.defaultAutomationConfig) "(${linked.name})" else null
        ) {
            AutomationConfigSelector(module, linked) { selected ->
                (module.backingAutomationConfig as? UserAutomationConfig)?.linkedModules?.value?.remove(module.name)
                (selected as? UserAutomationConfig)?.linkedModules?.value?.add(module.name)
                module.automationConfig = selected
                linked = module.backingAutomationConfig
            }

            // The linked config's cores are aliased into the module's own automation config,
            // so the tree is rebuilt on switch to pick up the swapped values.
            key(linked) {
                SettingsTree(module.automationConfig.settingLayers)
            }
        }
    }

    SettingsGroup("Module Settings") {
        module.keybindSetting.gui()
        module.prioritySetting.gui()
        module.disableOnReleaseSetting.gui()
        module.drawSetting.gui()
        if (module is HudModule) module.backgroundColor.gui()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { module.resetSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                shape = RoundedCornerShape(Radius.Small),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(text = "Reset", fontSize = 9.sp)
            }
        }
    }
}

/**
 * Dropdown listing the module's own automation config plus every user defined one.
 */
@Composable
private fun AutomationConfigSelector(
    module: Module,
    linked: AutomationConfig,
    onSelect: (AutomationConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(module.defaultAutomationConfig) +
        UserAutomationCategory.configs.filterIsInstance<AutomationConfig>()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            // Clipped as a whole so the field and its open list read as one control.
            .clip(RoundedCornerShape(Radius.Small))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 16.dp)
                .background(colorScheme.surfaceContainerHigh.frosted())
                .clickable { expanded = !expanded },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "Linked Config: ${linked.name}",
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    style = TextStyle(
                        fontSize = 9.sp, lineHeight = 9.sp, lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = colorScheme.onSurface,
                    style = TextStyle(
                        fontSize = 7.sp, lineHeight = 7.sp, lineHeightStyle = LineHeightStyle(
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
                    .background(colorScheme.surfaceContainer.frosted())
            ) {
                options.forEach { option ->
                    val selected = option === linked
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 16.dp)
                            .clickable {
                                expanded = false
                                if (!selected) onSelect(option)
                            }
                            .background(if (selected) colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = option.name,
                            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
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
        }
    }
}
