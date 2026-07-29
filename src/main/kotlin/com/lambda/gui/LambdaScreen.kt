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

package com.lambda.gui

import com.lambda.gui.components.ClickGuiLayout
import com.lambda.gui.compose.ComposeHost
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

object LambdaScreen : Screen(Text.of("Lambda")) {
    /**
     * The screen the GUI was opened over (e.g. the title screen).
     * Null when opened in-game; in that case closing returns to gameplay.
     */
    var parentScreen: Screen? = null

    override fun shouldPause() = false
    override fun removed() = ClickGuiLayout.close()
    override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, deltaTicks: Float) {}

    // M0 spike: forward input to Compose when it owns the GUI. ImGui uses its own GLFW hooks,
    // so these overrides only matter while ComposeHost.enabled is true.
    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        if (ComposeHost.enabled) ComposeHost.onMouseMove(mouseX, mouseY)
        super.mouseMoved(mouseX, mouseY)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (ComposeHost.enabled) {
            ComposeHost.onMousePress(click.x(), click.y())
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseReleased(click: Click): Boolean {
        if (ComposeHost.enabled) {
            ComposeHost.onMouseRelease(click.x(), click.y())
            return true
        }
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (ComposeHost.enabled) {
            ComposeHost.onMouseScroll(mouseX, mouseY, verticalAmount)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (parentScreen == null) {
            // In-game: keep vanilla behavior (blur + darken the rendered world).
            super.renderBackground(context, mouseX, mouseY, delta)
            return
        }
        // Off-screen mouse coords keep the parent's widgets from showing a hover state.
        parentScreen?.renderBackground(context, -1, -1, delta)
        parentScreen?.render(context, -1, -1, delta)
        // Flush deferred elements (e.g. widget text) so the darkening overlay covers them.
        context.drawDeferredElements()
        // NOTE: no GUI-layer applyBlur() here. MC's layer blur only blurs background
        // layers, never foreground widgets. To blur the whole parent (widgets included)
        // we run a framebuffer-level blur in DearImGui.render() after the full GUI pass.
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        parentScreen?.resize(width, height)
    }

    override fun close() {
        val previous = parentScreen
        parentScreen = null
        client?.setScreen(previous)
    }

    override fun applyBlur(context: DrawContext?) {
        if (!ClickGuiLayout.backgroundBlur) return
        super.applyBlur(context)
    }

    override fun renderDarkening(context: DrawContext?) {
        if (!ClickGuiLayout.backgroundDarkening) return
        super.renderDarkening(context)
    }
}
