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

@file:OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)

package com.lambda.gui.compose

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.lambda.Lambda.mc
import com.lambda.gui.components.ClickGuiLayout
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.Dispatchers
import net.minecraft.client.gl.GlBackend
import net.minecraft.client.texture.GlTexture
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER

/**
 * M0 spike: renders a Jetpack Compose (Compose Multiplatform) UI over Minecraft via Skia, as a
 * proof-of-concept alternative to [com.lambda.gui.DearImGui].
 *
 * Rendering strategy (M0 refinement): Compose draws into a **Skia-owned offscreen surface**, and
 * only when the scene actually changed (input, animation, resize). Every frame we then blit that
 * cached image onto MC's framebuffer as a single quad. This:
 *   - keeps the expensive Compose/Skia work off MC's framebuffer and off the render loop when the
 *     GUI is static, so it no longer stalls Minecraft's own time-based animations, and
 *   - isolates Skia's GL state from MC (it fully owns the offscreen target).
 *
 * Not production wiring. Requires -Dskiko.renderApi=OPENGL and -Dskiko.macos.opengl.enabled=true
 * (set in build.gradle.kts loom runs). See docs/compose-migration.md.
 */
object ComposeHost {
    /** When true, [render] draws Compose instead of Dear ImGui (toggled by the spike keybind). */
    var enabled = false

    private var context: DirectContext? = null
    private var scene: ComposeScene? = null

    // Compose renders here; Skia fully controls this surface's GL state.
    private var offscreen: Surface? = null
    private var offscreenW = -1
    private var offscreenH = -1
    private var snapshot: Image? = null

    // A thin Skia surface wrapping MC's framebuffer, used only to blit [snapshot] in each frame.
    private var mcSurface: Surface? = null
    private var mcRenderTarget: BackendRenderTarget? = null
    private var mcW = -1
    private var mcH = -1
    private var mcFbId = -1

    private fun ensureScene(width: Int, height: Int) {
        if (scene == null) {
            // Must be created on the render thread with MC's GL context current.
            context = DirectContext.makeGL()
            scene = CanvasLayersComposeScene(
                Density(mc.window.scaleFactor.toFloat()),
                LayoutDirection.Ltr,
                IntSize(width, height),
                Dispatchers.Unconfined,
            ).apply { setContent { SpikeContent() } }
            println("[ComposeHost] M0: Skia GL context + ComposeScene initialized (${width}x${height})")
        }
        scene!!.density = Density(mc.window.scaleFactor.toFloat())
        scene!!.size = IntSize(width, height)
    }

    /** @return true if the offscreen surface was (re)created this call (forces a redraw). */
    private fun ensureOffscreen(width: Int, height: Int): Boolean {
        if (offscreen != null && width == offscreenW && height == offscreenH) return false
        offscreen?.close()
        snapshot?.close()
        snapshot = null
        offscreen = Surface.makeRenderTarget(context!!, false, ImageInfo.makeN32Premul(width, height))
        offscreenW = width
        offscreenH = height
        return true
    }

    private fun ensureMcSurface(fbId: Int, width: Int, height: Int) {
        if (mcSurface != null && width == mcW && height == mcH && fbId == mcFbId) return
        mcSurface?.close()
        mcRenderTarget?.close()
        mcRenderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, fbId, FramebufferFormat.GR_GL_RGBA8)
        mcSurface = Surface.makeFromBackendRenderTarget(
            context!!,
            mcRenderTarget!!,
            SurfaceOrigin.BOTTOM_LEFT, // match the GL framebuffer origin so content isn't flipped
            SurfaceColorFormat.RGBA_8888,
            ColorSpace.sRGB,
        )
        mcW = width
        mcH = height
        mcFbId = fbId
    }

    // MC leaves GL pixel-store unpack state set from its own uploads; Skia assumes SKIP_* are 0
    // when uploading its glyph atlas, so stale values scramble text. Reset before any Skia upload.
    private fun resetUnpackState() {
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
    }

    fun render() {
        if (!enabled || !ClickGuiLayout.open) return
        val width = mc.window.framebufferWidth
        val height = mc.window.framebufferHeight
        if (width <= 0 || height <= 0) return

        // Same FBO lookup DearImGui.render() uses: the GL id backing MC's main framebuffer.
        val fbId = (mc.framebuffer.getColorAttachment() as GlTexture).getOrCreateFramebuffer(
            (RenderSystem.getDevice() as GlBackend).bufferManager, null
        )

        ensureScene(width, height)
        val ctx = context!!
        val sizeChanged = ensureOffscreen(width, height)
        ensureMcSurface(fbId, width, height)

        // 1) Recompose + redraw Compose ONLY when something changed (input, animation, resize).
        //    A static GUI does no work here, so it can't stall MC's render loop.
        Snapshot.sendApplyNotifications()
        val sc = scene!!
        val dirty = sizeChanged || snapshot == null || sc.hasInvalidations()
        if (dirty) {
            resetUnpackState()
            ctx.resetAll()
            val off = offscreen!!
            off.canvas.clear(0) // transparent — only the button/shadow are opaque
            sc.render(off.canvas.asComposeCanvas(), System.nanoTime())
            off.flushAndSubmit()
            ctx.resetAll()
            snapshot?.close()
            snapshot = off.makeImageSnapshot()
        }

        // 2) Blit the cached Compose image onto MC's framebuffer. This is the only per-frame work;
        //    a single quad, decoupled from Compose's own (offscreen) render timing.
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbId)
        resetUnpackState()
        ctx.resetAll()
        mcSurface!!.canvas.drawImage(snapshot!!, 0f, 0f)
        mcSurface!!.flushAndSubmit()
        ctx.resetAll()
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    // --- Input forwarding (called from LambdaScreen) --------------------------------------------
    // MC screen handlers give scaled GUI coords; the scene works in framebuffer pixels.

    private fun toPixels(xScaled: Double, yScaled: Double): Offset {
        val sw = mc.window.scaledWidth.toDouble().coerceAtLeast(1.0)
        val sh = mc.window.scaledHeight.toDouble().coerceAtLeast(1.0)
        val fw = mc.window.framebufferWidth.toDouble()
        val fh = mc.window.framebufferHeight.toDouble()
        return Offset((xScaled / sw * fw).toFloat(), (yScaled / sh * fh).toFloat())
    }

    fun onMouseMove(x: Double, y: Double) {
        scene?.sendPointerEvent(PointerEventType.Move, toPixels(x, y))
    }

    fun onMousePress(x: Double, y: Double) {
        scene?.sendPointerEvent(PointerEventType.Press, toPixels(x, y), button = PointerButton.Primary)
    }

    fun onMouseRelease(x: Double, y: Double) {
        scene?.sendPointerEvent(PointerEventType.Release, toPixels(x, y), button = PointerButton.Primary)
    }

    fun onMouseScroll(x: Double, y: Double, vertical: Double) {
        scene?.sendPointerEvent(
            PointerEventType.Scroll,
            toPixels(x, y),
            scrollDelta = Offset(0f, -vertical.toFloat()),
        )
    }
}
