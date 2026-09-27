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

package com.lambda.newui.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry

/**
 * Runtime state of the Compose HUD: the clocks HUD content refreshes on, the open context menu,
 * and whether there is anything to draw this frame. The layout itself lives in [HudLayer]; the
 * user-facing settings and the lock/hide toggles stay in [com.lambda.gui.components.HudGuiLayout].
 */
object ComposeHud : Loadable {
    /** Every [HudModule] in the registry, enabled or not. */
    val modules: List<HudModule> by lazy { ModuleRegistry.modules.filterIsInstance<HudModule>() }

    /** The HUD context menu currently open, if any. Only shown while the click GUI is open. */
    var contextMenu by mutableStateOf<HudContextMenuState?>(null)

    private var tick by mutableLongStateOf(0L)
    private var frame by mutableLongStateOf(0L)

    /**
     * True when the HUD layer has something to draw: the vanilla HUD is not hidden (F1) and at
     * least one HUD element is enabled. Checked every frame, so it stays a plain read.
     */
    val hasVisibleElements: Boolean
        get() = !mc.options.hudHidden && modules.any { it.isEnabled }

    init {
        listenUnsafe<TickEvent.Post> { tick++ }
    }

    /** Called once per frame the overlay is rendered, before the scene draws. */
    fun onFrame() {
        frame++
    }

    /**
     * Subscribes the calling composable to the client tick, so content built from live game data
     * (position, speed, tps, tasks) is rebuilt every tick. Content that only shows Compose state
     * does not need this: it recomposes on its own when that state changes.
     */
    @Composable
    fun observeTick(): Long = tick

    /**
     * Subscribes the calling composable to every rendered frame. Costs a recomposition per frame,
     * so reserve it for values that visibly move between ticks, like the camera rotation.
     */
    @Composable
    fun observeFrame(): Long = frame
}
