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

import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.GuiEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.gui.OverlayBackgroundScreen
import com.lambda.module.modules.client.Client
import com.lambda.newui.theme.SystemThemeTracker
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundHandler.play

object ComposeClickGui : Loadable {
    var open = false
        private set

    override fun load(): String {
        ComposePreloader.start()
        return "Loaded ComposeClickGui"
    }

    init {
        listenUnsafe<GuiEvent.EndImguiFrame> {
            if (!open) return@listenUnsafe
            ComposeRenderer.render()
        }

        listenUnsafe<ClientEvent.Startup> { warmUp() }
    }

    /**
     * Builds the scene, runs its first composition and sets up Skia's GL context, all of which
     * the first open would otherwise pay for as a visible stutter.
     *
     * These need the client thread, so they cannot be moved to [ComposePreloader]'s worker - but
     * this fires before the first frame is drawn, so the cost lands while the game is still
     * loading and nothing is animating yet, rather than freezing a live menu later.
     */
    private fun warmUp() {
        runCatching {
            ComposeRenderer.initialize()
            SystemThemeTracker.refresh()
            // Renders the scene into the offscreen buffer but skips the composite, so this
            // warmed frame never reaches the screen.
            ComposeRenderer.render(composite = false)
        }.onFailure { LOG.warn("Compose GUI warmup failed; the first open may stutter", it) }
    }

    fun toggle() {
        if (open) ComposeScreen.close()
        else {
            val current = mc.currentScreen
            if (current is ComposeScreen) return
            if (Client.clientSounds) LambdaSound.ModuleOn.play()

            ComposeRenderer.initialize()
            SystemThemeTracker.refresh()

            ComposeScreen.parentScreen = current
            (current as? OverlayBackgroundScreen)?.onOverlaidByGui()
            mc.setScreen(ComposeScreen)
            open = true
        }
    }

    fun close() {
        if (Client.clientSounds) LambdaSound.ModuleOff.play()
        open = false
        // Drop our references to the game snapshot. The GPU texture itself lives until the
        // frosted windows re-record on reopen (their cached draw commands still reference it);
        // freeing it eagerly would mean closing the scene and losing window positions.
        ComposeRenderer.releaseBackdrop()
    }
}
