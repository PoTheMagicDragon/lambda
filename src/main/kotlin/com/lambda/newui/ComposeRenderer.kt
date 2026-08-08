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

import androidx.compose.runtime.mutableStateOf
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
import com.lambda.module.modules.client.Style
import com.lambda.newui.theme.SystemThemeTracker
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.gl.GlBackend
import net.minecraft.client.texture.GlTexture
import java.util.OptionalInt
import java.util.OptionalDouble
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
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

    /**
     * The scene's size in framebuffer pixels, which is also the coordinate space
     * [androidx.compose.ui.layout.LayoutCoordinates.positionInRoot] reports in. Observable so
     * layouts that clamp themselves to the screen react to a resize.
     *
     * `LocalWindowInfo.containerSize` cannot be used for this: no platform window backs a
     * [CanvasLayersComposeScene], so it stays [IntSize.Zero].
     */
    val sceneSize = mutableStateOf(IntSize.Zero)

    // Pending input events
    private var mouseX = 0f
    private var mouseY = 0f
    private var buttonsDown = 0

    private var guiFboTexture: GpuTexture? = null
    private var guiFboView: GpuTextureView? = null
    private var guiFboWidth = 0
    private var guiFboHeight = 0

    // Wrap of the Minecraft framebuffer used to snapshot the game for frosted windows.
    private var backdropTarget: BackendRenderTarget? = null
    private var backdropSurface: Surface? = null
    private var backdropFboId = -1
    private var backdropWidth = 0
    private var backdropHeight = 0
    private var liveBackdropFrame: Image? = null
    private var retiredBackdropFrame: Image? = null

    fun initialize() {
        if (initialized) return

        scene = CanvasLayersComposeScene(
            coroutineContext = Dispatchers.Unconfined,
            density = Density(3f),
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

    /**
     * @param composite whether to draw the finished GUI onto Minecraft's framebuffer. Pass
     *   false to warm the pipeline up - the scene still renders into the offscreen buffer,
     *   compiling Skia's shaders and building its glyph atlas, but nothing reaches the screen.
     */
    fun render(composite: Boolean = true) {
        if (!initialized || scene == null) return

        // Throttled internally, and only reached while the GUI is open, so the OS theme is
        // never queried when nothing is on screen.
        SystemThemeTracker.poll()
        Style.updateLambdaTheme()

        val width = mc.window.framebufferWidth
        val height = mc.window.framebufferHeight
        if (width <= 0 || height <= 0) return

        val glState = saveGLState()
        resetPixelStore()

        try {
            val directContext = directContext
                ?: DirectContext.makeGL().also { context ->
                    directContext = context
                }

            if (guiFboTexture == null || guiFboWidth != width || guiFboHeight != height) {
                guiFboView?.close()
                guiFboTexture?.close()

                val gpuDevice = RenderSystem.getDevice()
                guiFboTexture = gpuDevice.createTexture(
                    { "Lambda GUI Glow FBO" },
                    15,
                    TextureFormat.RGBA8,
                    width,
                    height,
                    1,
                    1
                )
                guiFboView = gpuDevice.createTextureView(guiFboTexture)
                guiFboWidth = width
                guiFboHeight = height
            }

            val gpuDevice = RenderSystem.getDevice()
            gpuDevice.createCommandEncoder().createRenderPass(
                { "Clear GUI FBO" },
                guiFboView,
                OptionalInt.of(0x00000000), // Clear with transparent
                null,
                OptionalDouble.empty()
            )?.close()

            val guiFboId = (guiFboTexture as GlTexture).getOrCreateFramebuffer((gpuDevice as GlBackend).bufferManager, null)

            // Resolved before resetGLAll so any framebuffer creation stays out of Skia's blind spot.
            val gameFbo = resolveGameFramebuffer(gpuDevice)

            val originalFbId = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, guiFboId)

            // Invalidate Skia's GL state cache only after all of Minecraft's raw GL work above,
            // so every Skia operation this frame rebinds whatever state it relies on.
            directContext.resetGLAll()

            updateGameBackdrop(directContext, gameFbo)

            if (currentWidth != width || currentHeight != height) {
                previousRenderTarget?.close()
                surface?.close()

                val currentTarget =
                    BackendRenderTarget.makeGL(
                        width, height,
                        sampleCnt = 0,
                        stencilBits = 8,
                        guiFboId,
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

                scene?.density = Density(height / 720f)
                scene?.size = IntSize(width, height)
                // Set before the scene renders below, so this frame's composition sees it.
                sceneSize.value = IntSize(width, height)
            }

            val canvas = surface?.canvas ?: return

            scene?.render(canvas.asComposeCanvas(), System.nanoTime())

            surface?.flushAndSubmit()
            directContext.flush()

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, originalFbId)

            if (!composite) return

            guiFboView?.let { view ->
                GuiGlowRenderer.renderGlow(
                    view,
                    if (Style.enableGlow) Style.glowRadius else 0f,
                    if (Style.enableGlow) Style.glowIntensity else 0f,
                    Style.glowColor1,
                    Style.glowColor2
                )
            }
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

    private data class GameFbo(val id: Int, val width: Int, val height: Int)

    private fun resolveGameFramebuffer(backend: GlBackend): GameFbo? {
        if (!Style.blur.value) return null
        val framebuffer = mc.framebuffer ?: return null
        val colorTexture = framebuffer.colorAttachment as? GlTexture ?: return null
        return GameFbo(
            colorTexture.getOrCreateFramebuffer(backend.bufferManager, null),
            framebuffer.textureWidth,
            framebuffer.textureHeight
        )
    }

    /**
     * Snapshots the game framebuffer into a Skia image for [GameBackdrop]. The wrap of an
     * external render target makes [Surface.makeImageSnapshot] copy, so this is a GPU-side
     * copy of the frame as rendered so far — exactly what sits behind the GUI windows.
     */
    private fun updateGameBackdrop(context: DirectContext, gameFbo: GameFbo?) {
        if (gameFbo == null) {
            releaseBackdropTargets()
            publishBackdropFrame(null)
            return
        }

        if (backdropSurface == null ||
            backdropFboId != gameFbo.id ||
            backdropWidth != gameFbo.width ||
            backdropHeight != gameFbo.height
        ) {
            releaseBackdropTargets()
            val target = BackendRenderTarget.makeGL(
                gameFbo.width, gameFbo.height,
                sampleCnt = 0,
                stencilBits = 0,
                gameFbo.id,
                FramebufferFormat.GR_GL_RGBA8
            )
            backdropTarget = target
            backdropSurface = Surface.makeFromBackendRenderTarget(
                context,
                target,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.sRGB
            )
            backdropFboId = gameFbo.id
            backdropWidth = gameFbo.width
            backdropHeight = gameFbo.height
        }

        publishBackdropFrame(backdropSurface?.let { wrap ->
            // Skia caches makeImageSnapshot and only re-copies after it observes a write to the
            // surface. Minecraft mutates the wrapped framebuffer behind Skia's back, so without
            // this the snapshot would stay frozen at the first frame forever.
            wrap.notifyContentWillChange(ContentChangeMode.DISCARD)
            wrap.makeImageSnapshot()
        })
    }

    /**
     * Cached draw commands may reference the previous snapshot until the scene re-records
     * against the new one during the upcoming render, so it is retired for one frame and
     * closed on the frame after.
     */
    private fun publishBackdropFrame(image: Image?) {
        if (image == null && liveBackdropFrame == null && retiredBackdropFrame == null) return
        retiredBackdropFrame?.close()
        retiredBackdropFrame = liveBackdropFrame
        liveBackdropFrame = image
        GameBackdrop.frame.value = image
    }

    private fun releaseBackdropTargets() {
        backdropSurface?.close()
        backdropSurface = null
        backdropTarget?.close()
        backdropTarget = null
        backdropFboId = -1
        backdropWidth = 0
        backdropHeight = 0
    }

    /**
     * Frees the game snapshot and its framebuffer wrap. Safe while the GUI is closed: nothing
     * replays the cached draw commands until the next render, which republishes first.
     */
    fun releaseBackdrop() {
        GameBackdrop.frame.value = null
        liveBackdropFrame?.close()
        liveBackdropFrame = null
        retiredBackdropFrame?.close()
        retiredBackdropFrame = null
        releaseBackdropTargets()
    }

    fun destroy() {
        scene?.close()
        scene = null
        releaseBackdrop()
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
        val textureBindings: IntArray,
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
        val unpackSwapBytes: Int,
        val unpackLsbFirst: Int,
        val unpackRowLength: Int,
        val unpackImageHeight: Int,
        val unpackSkipRows: Int,
        val unpackSkipPixels: Int,
        val unpackSkipImages: Int,
        val unpackAlignment: Int,
    )

    private fun saveGLState(): GLState {
        val originalActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        val textureBindings = IntArray(8) { unit ->
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit)
            GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        }
        GL13.glActiveTexture(originalActiveTexture)

        return GLState(
            activeTexture = originalActiveTexture,
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            textureBindings = textureBindings,
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
            unpackSwapBytes = GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES),
            unpackLsbFirst = GL11.glGetInteger(GL11.GL_UNPACK_LSB_FIRST),
            unpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),
            unpackImageHeight = GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT),
            unpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),
            unpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS),
            unpackSkipImages = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES),
            unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),
        )
    }

    /**
     * Minecraft leaves the pixel-store unpack state non-zero after its own texture uploads.
     * Skia would inherit it when uploading its glyph atlas, reading each glyph from the wrong
     * offset and stride, which renders all text as garbage. Reset to the GL defaults first.
     */
    private fun resetPixelStore() {
        GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
    }

    private fun restoreGLState(state: GLState) {
        GL20.glUseProgram(state.program)
        state.textureBindings.forEachIndexed { unit, binding ->
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding)
        }
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
        GL11.glColorMask(true, true, true, true)
        GL11.glDepthMask(true)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, state.framebuffer)
        GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, state.unpackSwapBytes)
        GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, state.unpackLsbFirst)
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, state.unpackRowLength)
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, state.unpackImageHeight)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, state.unpackSkipRows)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, state.unpackSkipPixels)
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, state.unpackSkipImages)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, state.unpackAlignment)
    }
}
