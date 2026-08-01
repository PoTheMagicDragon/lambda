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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.ConfigEditor
import com.lambda.config.ConfigEditorD5l
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.imgui.flag.ImGuiInputTextFlags
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

/**
 * @see [com.lambda.config.Config]
 */
class StringSetting(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<StringSetting, String>,
    defaultValue: String,
    visibility: () -> Boolean,
    var multiline: Boolean = false,
    var flags: Int = ImGuiInputTextFlags.None,
) : Setting<String>(name, description, defaultValue, layer, config, visibility) {

    @ExperimentalMaterial3Api
    @Composable
    override fun gui() {}

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(greedyString(name)) { parameter ->
            execute {
                trySetValue(parameter().value())
            }
        }
    }

    @Suppress("unused", "unchecked_cast")
    companion object {
        @ConfigEditorD5l
        fun ConfigEditor.SettingEditBuilder<String>.multiline(multiline: Boolean) {
            (entries as Collection<StringSetting>).forEach { it.multiline = multiline }
        }

        @ConfigEditorD5l
        fun ConfigEditor.SettingEditBuilder<String>.flags(flags: Int) {
            (entries as Collection<StringSetting>).forEach { it.flags = flags }
        }
    }
}