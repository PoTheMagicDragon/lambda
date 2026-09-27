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

package com.lambda.module.hud

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color as ComposeColor
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.flag.ImGuiCol
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.hud.HudText
import com.lambda.newui.state.LambdaState.observe
import com.lambda.newui.state.LambdaState.observeEnabled
import java.awt.Color

@Suppress("unused")
object ModuleList : HudModule(
    name = "ModuleList",
    tag = ModuleTag.HUD,
) {
	// Kept as Setting references so the Compose element can observe() them.
	private val onlyBoundSetting = setting("Only Bound", false, "Only displays modules with a keybind")
	private val showKeybindSetting = setting("Show Keybind", true, "Display keybind next to a module")
	val onlyBound by onlyBoundSetting
	val showKeybind by showKeybindSetting

    init {
        drawSetting.value = false
    }

    override fun ImGuiBuilder.buildLayout() {
        val enabled = ModuleRegistry.modules.filter { it.isEnabled && it.draw }

        enabled.forEach {
            val bound = it.keybind.key != 0 || it.keybind.mouse != -1
            if (onlyBound && !bound) return@forEach
            text(it.name)

	        if (showKeybind) {
		        val color = if (!bound) Color.RED else Color.GREEN

		        sameLine()
		        withStyleColor(ImGuiCol.Text, color) { text(" [${it.keybind.name}]") }
	        }
        }
    }

    @Composable
    override fun Content() {
        val boundOnly by onlyBoundSetting.observe()
        val withKeybind by showKeybindSetting.observe()
        Column {
            ModuleRegistry.modules.forEach { module ->
                key(module.name) {
                    val enabled by module.observeEnabled()
                    val draw by module.drawSetting.observe()
                    val keybind by module.keybindSetting.observe()
                    val bound = keybind.key != 0 || keybind.mouse != -1
                    if (enabled && draw && (!boundOnly || bound)) {
                        Row {
                            HudText(module.name)
                            if (withKeybind) {
                                HudText(" [${keybind.name}]", color = if (bound) ComposeColor.Green else ComposeColor.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}
