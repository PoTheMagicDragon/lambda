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

import com.lambda.gui.components.ClickGuiLayout
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen

object ComposeScreen : Screen(buildText { literal("Lambda Screen") }) {
    var parentScreen: Screen? = null

    override fun shouldPause() = false

    override fun removed() = ComposeClickGui.close()

    override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, deltaTicks: Float) {}

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (parentScreen == null) {
            // In-game: keep vanilla behavior (blur + darken the rendered world).
            super.renderBackground(context, mouseX, mouseY, delta)
            return
        }
        // Off-screen mouse coords keep the parent's widgets from showing a hover state.
        parentScreen?.renderBackground(context, -1, -1, delta)
        parentScreen?.render(context, -1, -1, delta)
        context.drawDeferredElements()
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

    // --- Input forwarding to Compose ---

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        ComposeRenderer.sendMousePress(click.x(), click.y(), click.button())
        return true
    }

    override fun mouseReleased(click: Click): Boolean {
        ComposeRenderer.sendMouseRelease(click.x(), click.y(), click.button())
        return true
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        ComposeRenderer.sendMouseMove(mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        ComposeRenderer.sendMouseScroll(mouseX, mouseY, horizontalAmount, verticalAmount)
        return true
    }
}
