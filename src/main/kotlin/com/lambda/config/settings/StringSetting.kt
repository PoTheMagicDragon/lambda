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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    override fun gui() {
        val stateValue by observeState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 1.dp),
                style = TextStyle(
                    fontSize = 9.sp, lineHeight = 9.sp, lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Color(0xFF222222))
                    .padding(2.dp)
            ) {
                BasicTextField(
                    value = stateValue,
                    onValueChange = { value = it },
                    singleLine = !multiline,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 9.sp,
                        color = Color.White
                    ),
                    cursorBrush = SolidColor(Color.White)
                )
            }
        }
    }

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
