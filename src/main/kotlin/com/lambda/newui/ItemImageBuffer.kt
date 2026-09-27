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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import com.lambda.Lambda.mc
import com.mojang.blaze3d.systems.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.gl.GlBackend
import net.minecraft.client.render.DiffuseLighting
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.ProjectionMatrix2
import net.minecraft.client.render.item.KeyedItemRenderState
import net.minecraft.client.texture.GlTexture
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin

/**
 * Renders item stacks with Minecraft's own item renderer into an offscreen texture and hands it
 * to Compose as a Skia [Image], so HUD elements can show items exactly as the inventory does:
 * 3D block models, trims, dyes and the animated enchantment glint included.
 *
 * The stacks are laid out as a strip, one square cell per stack, in request order. A Compose
 * element [request]s what it wants each composition and draws its cell from [image];
 * [ComposeRenderer] drives the two render phases every frame the buffer [needsRender]:
 * [renderItems] is plain Minecraft GPU work and runs before Skia's GL state reset, [snapshot]
 * wraps the texture's framebuffer for Skia afterwards and copies it out, the same way the
 * frosted-glass backdrop imports the game frame.
 *
 * Owned by the composable that registers it; [close] must run on the render thread.
 */
class ItemImageBuffer : AutoCloseable {
    /**
     * The latest rendered strip, replaced whenever the items were redrawn. Read it in a draw
     * scope so the element redraws on each new strip. Superseded snapshots stay alive for one
     * frame because cached draw commands may still reference them.
     */
    val image: MutableState<Image?> = mutableStateOf(null, neverEqualPolicy())

    private var stacks: List<ItemStack> = emptyList()
    private var cell = 0

    // What the texture currently holds, for change detection. Copies, since the live stacks mutate.
    private var rendered: List<ItemStack> = emptyList()
    private var renderedCell = 0
    private var animated = false
    private var pending = false

    private var colorTexture: GpuTexture? = null
    private var colorView: GpuTextureView? = null
    private var depthTexture: GpuTexture? = null
    private var depthView: GpuTextureView? = null
    private var width = 0
    private var height = 0
    private var fboId = -1
    private var projection: ProjectionMatrix2? = null

    private var target: BackendRenderTarget? = null
    private var surface: Surface? = null
    private var wrappedFbo = -1
    private var wrappedWidth = 0
    private var wrappedHeight = 0
    private var live: Image? = null
    private var retired: Image? = null

    /** Cell width and height, in pixels, of the strip [image] holds. */
    val cellSize: Int get() = renderedCell

    /** What to render: [stacks] side by side, each in a [cellSize] pixel square. */
    fun request(stacks: List<ItemStack>, cellSize: Int) {
        this.stacks = stacks
        this.cell = cellSize
    }

    /**
     * True when the texture no longer matches the request: a stack changed, the cell size
     * changed, or an item animates (the glint shimmers), in which case every frame is a redraw.
     */
    val needsRender: Boolean
        get() {
            if (stacks.isEmpty() || cell <= 0) return live != null
            if (animated || cell != renderedCell || stacks.size != rendered.size) return true
            return stacks.indices.any { !ItemStack.areEqual(stacks[it], rendered[it]) }
        }

    /** Minecraft phase: draw the stacks into the texture. Before Skia touches GL this frame. */
    fun renderItems(backend: GlBackend) {
        if (!needsRender) return
        pending = true
        if (stacks.isEmpty() || cell <= 0) {
            rendered = emptyList()
            return
        }

        ensureTextures(backend, cell * stacks.size, cell)
        val colorTexture = colorTexture ?: return
        val depthTexture = depthTexture ?: return
        // Resolved here so the framebuffer object exists before Skia's state reset.
        fboId = (colorTexture as GlTexture).getOrCreateFramebuffer(backend.bufferManager, null)

        backend.createCommandEncoder().clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0)

        val previousProjection = RenderSystem.getProjectionMatrixBuffer()
        val previousProjectionType = RenderSystem.getProjectionType()
        val projection = projection ?: ProjectionMatrix2("Lambda HUD items", -1000f, 1000f, true).also { projection = it }

        // Vanilla's own recipe for drawing a GUI item into its item atlas.
        RenderSystem.outputColorTextureOverride = colorView
        RenderSystem.outputDepthTextureOverride = depthView
        RenderSystem.setProjectionMatrix(projection.set(width.toFloat(), height.toFloat()), ProjectionType.ORTHOGRAPHIC)
        try {
            val dispatcher = mc.gameRenderer.entityRenderDispatcher
            val lighting = mc.gameRenderer.diffuseLighting
            val consumers = mc.bufferBuilders.entityVertexConsumers
            val matrices = MatrixStack()
            var anyAnimated = false

            stacks.forEachIndexed { index, stack ->
                if (stack.isEmpty) return@forEachIndexed
                val state = KeyedItemRenderState()
                mc.itemModelManager.clearAndUpdate(state, stack, ItemDisplayContext.GUI, mc.world, mc.player, 0)
                anyAnimated = anyAnimated || state.isAnimated || stack.hasGlint()

                val x = index * cell
                matrices.push()
                matrices.translate(x + cell / 2f, cell / 2f, 0f)
                matrices.scale(cell.toFloat(), -cell.toFloat(), cell.toFloat())
                lighting.setShaderLights(if (state.isSideLit) DiffuseLighting.Type.ITEMS_3D else DiffuseLighting.Type.ITEMS_FLAT)
                RenderSystem.enableScissorForRenderTypeDraws(x, 0, cell, cell)
                state.render(matrices, dispatcher.queue, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0)
                dispatcher.render()
                consumers.draw()
                RenderSystem.disableScissorForRenderTypeDraws()
                matrices.pop()
            }

            rendered = stacks.map { it.copy() }
            renderedCell = cell
            animated = anyAnimated
        } finally {
            RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
            if (previousProjection != null) RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType)
        }
    }

    /** Skia phase: copy the texture into a new [image]. After Skia's GL state reset this frame. */
    fun snapshot(context: DirectContext) {
        if (!pending) return
        pending = false

        if (rendered.isEmpty() || colorTexture == null) {
            releaseWrap()
            publish(null)
            return
        }

        if (surface == null || wrappedFbo != fboId || wrappedWidth != width || wrappedHeight != height) {
            releaseWrap()
            val target = BackendRenderTarget.makeGL(
                width, height,
                sampleCnt = 0,
                stencilBits = 0,
                fboId,
                FramebufferFormat.GR_GL_RGBA8
            )
            this.target = target
            surface = Surface.makeFromBackendRenderTarget(
                context,
                target,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.sRGB
            )
            wrappedFbo = fboId
            wrappedWidth = width
            wrappedHeight = height
        }

        publish(surface?.let { wrap ->
            // Minecraft wrote to the framebuffer behind Skia's back; without this the cached
            // snapshot would never refresh.
            wrap.notifyContentWillChange(ContentChangeMode.DISCARD)
            wrap.makeImageSnapshot()
        })
    }

    private fun ensureTextures(backend: GlBackend, width: Int, height: Int) {
        if (colorTexture != null && this.width == width && this.height == height) return
        releaseTextures()
        colorTexture = backend.createTexture({ "Lambda HUD items" }, COLOR_USAGE, TextureFormat.RGBA8, width, height, 1, 1)
        colorView = backend.createTextureView(colorTexture)
        depthTexture = backend.createTexture({ "Lambda HUD items depth" }, DEPTH_USAGE, TextureFormat.DEPTH32, width, height, 1, 1)
        depthView = backend.createTextureView(depthTexture)
        this.width = width
        this.height = height
    }

    private fun publish(image: Image?) {
        if (image == null && live == null && retired == null) return
        retired?.close()
        retired = live
        live = image
        this.image.value = image
    }

    private fun releaseWrap() {
        surface?.close()
        surface = null
        target?.close()
        target = null
        wrappedFbo = -1
    }

    private fun releaseTextures() {
        colorView?.close()
        colorView = null
        colorTexture?.close()
        colorTexture = null
        depthView?.close()
        depthView = null
        depthTexture?.close()
        depthTexture = null
        width = 0
        height = 0
        fboId = -1
    }

    override fun close() {
        image.value = null
        live?.close()
        live = null
        retired?.close()
        retired = null
        releaseWrap()
        releaseTextures()
        projection?.close()
        projection = null
        rendered = emptyList()
        renderedCell = 0
        animated = false
        pending = false
    }

    private companion object {
        /** Light packed as vanilla uses for GUI items. */
        const val FULL_BRIGHT = 15728880

        // GpuTexture usage flags, as GuiRenderer creates its item atlas.
        const val COLOR_USAGE = 12
        const val DEPTH_USAGE = 8
    }
}
