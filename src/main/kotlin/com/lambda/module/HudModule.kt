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

package com.lambda.module

import androidx.compose.runtime.Composable
import com.lambda.config.settings.complex.Bind
import com.lambda.gui.Layout
import com.lambda.module.tag.ModuleTag
import java.awt.Color

abstract class HudModule(
    name: String,
    description: String = "",
    tag: ModuleTag,
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: Bind = Bind.EMPTY,
) : Module(name, description, tag, alwaysListening, enabledByDefault, defaultKeybind = defaultKeybind), Layout {
    val backgroundColor = setting("Background Color", Color(0, 0, 0, 0))

    /**
     * Top-left corner of the element in the Compose scene's dp (a 720-tall virtual screen), so a
     * layout survives resolution changes. Hidden from the settings GUI; the HUD editor writes them
     * when the element is dragged or added.
     */
    val hudX = setting("HUD X", DEFAULT_POSITION, POSITION_RANGE, 1f) { false }
    val hudY = setting("HUD Y", DEFAULT_POSITION, POSITION_RANGE, 1f) { false }

    /**
     * The element as the Compose HUD draws it. Rebuilt whenever Compose state it reads changes;
     * content built from live game data reads [com.lambda.newui.hud.ComposeHud.observeTick].
     */
    @Composable
    abstract fun Content()

    private companion object {
        const val DEFAULT_POSITION = 8f
        val POSITION_RANGE = -100_000f..100_000f
    }
}
