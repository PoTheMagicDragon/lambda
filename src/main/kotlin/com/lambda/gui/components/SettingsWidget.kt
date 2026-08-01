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

package com.lambda.gui.components

import com.lambda.config.EntryLayer
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImGuiTabBarFlags

object SettingsWidget {
    /**
     * Builds the settings context popup content for the given config.
     */

    private fun ImGuiBuilder.drawLayers(root: EntryLayer.Multiple<Setting<*>>, idPrefix: String) {
	    var tabsDrawn = false

	    root.layers.forEach { layer ->
		    when (layer) {
			    is EntryLayer.Single<Setting<*>> -> drawSetting(layer.entry)
			    is EntryLayer.Group -> {
				    if (hasVisibleSettings(layer)) {
					    treeNode("${layer.name}##$idPrefix-group-${layer.name}") {
						    drawLayers(layer, "$idPrefix-${layer.name}")
					    }
				    }
			    }
			    is EntryLayer.Tab -> {
				    if (!tabsDrawn) {
					    tabsDrawn = true
					    val allTabs = root.layers
						    .filterIsInstance<EntryLayer.Tab<Setting<*>>>()
						    .filter { hasVisibleSettings(it) }
					    if (allTabs.isNotEmpty()) {
						    tabBar("##$idPrefix-tabs", ImGuiTabBarFlags.FittingPolicyResizeDown) {
							    allTabs.forEach { tab ->
								    tabItem(tab.name) {
									    drawLayers(tab, "$idPrefix-${tab.name}")
								    }
							    }
						    }
					    }
				    }
			    }
			    else -> {}
		    }
	    }
    }

    private fun ImGuiBuilder.drawSetting(setting: Setting<*>) {
	    if (!setting.visibility()) return
	    if (setting.disabled()) ImGui.beginDisabled()
//	    with(setting) { buildLayout() }
	    if (setting.disabled()) ImGui.endDisabled()
    }

    private fun hasVisibleSettings(layer: EntryLayer.Multiple<Setting<*>>): Boolean =
	    layer.layers.any { layer ->
		    when (layer) {
			    is SettingEntryLayer<*, *> -> layer.entry.visibility()
			    is EntryLayer.Multiple<Setting<*>> -> hasVisibleSettings(layer)
			    else -> false
		    }
	    }
}