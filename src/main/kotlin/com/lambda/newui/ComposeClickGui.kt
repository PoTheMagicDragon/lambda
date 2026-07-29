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

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.gui.OverlayBackgroundScreen
import com.lambda.gui.components.ClickGuiLayout
import com.lambda.module.modules.client.Client
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundHandler.play

/**
 * Orchestrator for the Compose-based click GUI.
 *
 * Manages the open/close lifecycle, hooks into the render pipeline via [GuiEvent.EndImguiFrame],
 * and delegates rendering to [ComposeRenderer].
 *
 * Implements [Loadable] so it is auto-discovered by [com.lambda.core.Loader].
 */
object ComposeClickGui : Loadable {
    var open = false
        private set

    override fun load(): String {
        return "Loaded ComposeClickGui"
    }

    init {
        // Render the Compose GUI after ImGui finishes (MC framebuffer is bound at this point).
        listenUnsafe<GuiEvent.EndImguiFrame> {
            if (!open) return@listenUnsafe
            ComposeRenderer.render()
        }
    }

    /**
     * Toggle the Compose click GUI open/closed.
     * Uses the same screen-check logic as [ClickGuiLayout.toggle].
     */
    fun toggle() {
        if (open) {
            // ComposeScreen.close() restores the background screen and triggers
            // removed() -> close(), which flips `open` off and plays the sound.
            ComposeScreen.close()
        } else {
            val current = mc.currentScreen
            if (current is ComposeScreen) return
            if (Client.clientSounds) LambdaSound.ModuleOn.play()

            ComposeRenderer.initialize()

            ComposeScreen.parentScreen = if (current is ComposeScreen) null else current
            (current as? OverlayBackgroundScreen)?.onOverlaidByGui()
            mc.setScreen(ComposeScreen)
            open = true
        }
    }

    fun close() {
        if (Client.clientSounds) LambdaSound.ModuleOff.play()
        open = false
    }
}
