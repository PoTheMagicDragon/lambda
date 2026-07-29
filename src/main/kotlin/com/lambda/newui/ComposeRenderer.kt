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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

@OptIn(InternalComposeUiApi::class)
object ComposeRenderer {
    private var directContext: DirectContext? = null
    private var previousRenderTarget: BackendRenderTarget? = null
    private var surface: Surface? = null
    private var scene: ComposeScene? = null

    private var currentWidth = 0
    private var currentHeight = 0
    private var initialized = false

    // Pending input events
    private var mouseX = 0f
    private var mouseY = 0f
    private var buttonsDown = 0

    fun initialize() {
        if (initialized) return

        scene = CanvasLayersComposeScene(
            coroutineContext = Dispatchers.Default,
            density = Density(1f),
            invalidate = {}
        ).apply {
            setContent {
                ClickGuiContent()
            }
        }

        listen<ClientEvent.Shutdown> {
            destroy()
        }

        initialized = true
        LOG.info("Compose renderer initialized")
    }

    fun render() {
        if (!initialized || scene == null) return

        val width = mc.window.framebufferWidth
        val height = mc.window.framebufferHeight
        if (width <= 0 || height <= 0) return

        val glState = saveGLState()

        try {
            val directContext = directContext
                ?: DirectContext.makeGL().also { context ->
                    directContext = context
                }

            directContext.resetGLAll()

            if (currentWidth != width || currentHeight != height) {
                previousRenderTarget?.close()
                surface?.close()

                val fbId = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
                val currentTarget =
                    BackendRenderTarget.makeGL(
                        width, height,
                        sampleCnt = 0,
                        stencilBits = 8,
                        fbId,
                        FramebufferFormat.GR_GL_RGBA8
                    ).also { target ->
                        previousRenderTarget = target
                    }

                surface = Surface.makeFromBackendRenderTarget(
                    directContext,
                    currentTarget,
                    SurfaceOrigin.BOTTOM_LEFT,
                    SurfaceColorFormat.RGBA_8888,
                    ColorSpace.sRGB
                )

                currentWidth = width
                currentHeight = height

                scene?.size = IntSize(width, height)
            }

            val canvas = surface?.canvas ?: return

            scene?.render(canvas.asComposeCanvas(), System.nanoTime())

            surface?.flushAndSubmit()
            directContext.flush()
        } catch (e: Exception) {
            LOG.error("Error rendering Compose UI", e)
        } finally {
            restoreGLState(glState)
        }
    }

    fun sendMouseMove(x: Double, y: Double) {
        val scale = mc.window.scaleFactor.toFloat()
        mouseX = (x * scale).toFloat()
        mouseY = (y * scale).toFloat()
        scene?.sendPointerEvent(
            eventType = PointerEventType.Move,
            position = Offset(mouseX, mouseY),
            buttons = PointerButtons(buttonsDown),
            button = PointerButton.Primary,
            type = PointerType.Mouse
        )
    }

    fun sendMousePress(x: Double, y: Double, button: Int) {
        val scale = mc.window.scaleFactor.toFloat()
        mouseX = (x * scale).toFloat()
        mouseY = (y * scale).toFloat()
        buttonsDown = buttonsDown or (1 shl button)
        val pointerButton = when (button) {
            0 -> PointerButton.Primary
            1 -> PointerButton.Secondary
            2 -> PointerButton.Tertiary
            else -> PointerButton.Primary
        }
        scene?.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(mouseX, mouseY),
            buttons = PointerButtons(buttonsDown),
            button = pointerButton,
            type = PointerType.Mouse
        )
    }

    fun sendMouseRelease(x: Double, y: Double, button: Int) {
        val scale = mc.window.scaleFactor.toFloat()
        mouseX = (x * scale).toFloat()
        mouseY = (y * scale).toFloat()
        buttonsDown = buttonsDown and (1 shl button).inv()
        val pointerButton = when (button) {
            0 -> PointerButton.Primary
            1 -> PointerButton.Secondary
            2 -> PointerButton.Tertiary
            else -> PointerButton.Primary
        }
        scene?.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(mouseX, mouseY),
            buttons = PointerButtons(buttonsDown),
            button = pointerButton,
            type = PointerType.Mouse
        )
    }

    fun sendMouseScroll(x: Double, y: Double, deltaX: Double, deltaY: Double) {
        val scale = mc.window.scaleFactor.toFloat()
        scene?.sendPointerEvent(
            eventType = PointerEventType.Scroll,
            position = Offset((x * scale).toFloat(), (y * scale).toFloat()),
            scrollDelta = Offset(deltaX.toFloat(), deltaY.toFloat()),
            buttons = PointerButtons(buttonsDown),
            button = PointerButton.Primary,
            type = PointerType.Mouse
        )
    }

    fun destroy() {
        scene?.close()
        scene = null
        surface?.close()
        surface = null
        previousRenderTarget?.close()
        previousRenderTarget = null
        directContext?.close()
        directContext = null
        currentWidth = 0
        currentHeight = 0
        initialized = false
    }

    private data class GLState(
        val activeTexture: Int,
        val program: Int,
        val texture2D: Int,
        val arrayBuffer: Int,
        val elementArrayBuffer: Int,
        val vertexArray: Int,
        val framebuffer: Int,
        val blendSrcRgb: Int,
        val blendDstRgb: Int,
        val blendSrcAlpha: Int,
        val blendDstAlpha: Int,
        val blendEquationRgb: Int,
        val blendEquationAlpha: Int,
        val blendEnabled: Boolean,
        val cullFaceEnabled: Boolean,
        val depthTestEnabled: Boolean,
        val stencilTestEnabled: Boolean,
        val scissorTestEnabled: Boolean,
    )

    private fun saveGLState(): GLState {
        return GLState(
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            texture2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
            arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
            elementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING),
            vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
            framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
            blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
            blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
            blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
            blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
            blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
            blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
            blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND),
            cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE),
            depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
            stencilTestEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
            scissorTestEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
        )
    }

    private fun restoreGLState(state: GLState) {
        GL20.glUseProgram(state.program)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.texture2D)
        GL13.glActiveTexture(state.activeTexture)
        GL30.glBindVertexArray(state.vertexArray)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, state.arrayBuffer)
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, state.elementArrayBuffer)
        GL20.glBlendEquationSeparate(state.blendEquationRgb, state.blendEquationAlpha)
        GL14.glBlendFuncSeparate(state.blendSrcRgb, state.blendDstRgb, state.blendSrcAlpha, state.blendDstAlpha)
        if (state.blendEnabled) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
        if (state.cullFaceEnabled) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
        if (state.depthTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
        if (state.stencilTestEnabled) GL11.glEnable(GL11.GL_STENCIL_TEST) else GL11.glDisable(GL11.GL_STENCIL_TEST)
        if (state.scissorTestEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST) else GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, state.framebuffer)
    }
}
