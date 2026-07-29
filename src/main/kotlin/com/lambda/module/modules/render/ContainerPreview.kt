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

package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.config.Tab
import com.lambda.config.entries.Setting.Companion.onValueChange
import com.lambda.config.settings.complex.Bind
import com.lambda.interaction.material.container.containers.EnderChestContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.InputUtils.isSatisfied
import com.lambda.util.KeyCode
import com.lambda.util.item.ItemStackUtils.bundleContents
import com.lambda.util.item.ItemStackUtils.shulkerBoxContents
import com.lambda.util.item.ItemUtils.bundles
import com.lambda.util.item.ItemUtils.shulkerBoxes
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.block.ShulkerBoxBlock
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.ScreenRect
import net.minecraft.client.gui.render.state.ColoredQuadGuiElementRenderState
import net.minecraft.client.gui.render.state.GuiRenderState
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState
import net.minecraft.client.gui.render.state.TextGuiElementRenderState
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.tooltip.TooltipComponent
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.item.KeyedItemRenderState
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.TextureSetup
import net.minecraft.entity.LivingEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.tooltip.TooltipData
import net.minecraft.screen.slot.Slot
import net.minecraft.util.Colors
import net.minecraft.util.DyeColor
import net.minecraft.util.Identifier
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.random.Random
import net.minecraft.world.World
import org.joml.Matrix3x2f
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ContainerPreview : Module(
    name = "ContainerPreview",
    description = "Renders shulker box contents visually in tooltips",
    tag = ModuleTag.RENDER,
) {
    private const val CONTAINER_TOOLTIP_TAB = "Container Tooltip"
    private const val CONTENT_PREVIEW_TAB = "Content Preview"

    @Tab(CONTAINER_TOOLTIP_TAB) private val lockKey by setting("Lock Key", Bind(KeyCode.LeftShift.code, 0, -1), "Key to lock the tooltip in place for item interaction")
    @Tab(CONTAINER_TOOLTIP_TAB) private val colorTint by setting("Color Tint", true, "Tint the background with the shulker box color")

    @Tab(CONTENT_PREVIEW_TAB) private val contentPreview by setting("Content Preview", true, "Show a preview of the most common item in a container on the container item in inventories")
    @Tab(CONTENT_PREVIEW_TAB) private val previewItemScale by setting("Item Scale", 11f, 1f..32f, 0.1f, "Scale of the item icons on a container item") { contentPreview }
    @Tab(CONTENT_PREVIEW_TAB) private val previewItemXOffset by setting("Item X Offset", -2f, -32f..32f, 0.1f, "X offset of the item icons on a container item") { contentPreview }
    @Tab(CONTENT_PREVIEW_TAB) private val previewItemYOffset by setting("Item Y Offset", 2f, -32f..32f, 0.1f, "Y offset of the item icons on a container item") { contentPreview }
    @Tab(CONTENT_PREVIEW_TAB) private val previewItemWeightedCount by setting("Weighted Count", true, description = "Count items for preview in containers relative to max stack size") { contentPreview }
        .onValueChange { _, _ -> containerCache.clear() }
    @Tab(CONTENT_PREVIEW_TAB) private val showFillBar by setting("Fill Bar", true, "Show a fill bar of item proportions along the bottom of container items")
    @Tab(CONTENT_PREVIEW_TAB) private val showHighestItemCount by setting("Highest Item Count", true, "Show the total count of the most common item on container items")

    private val background = Identifier.ofVanilla("textures/gui/container/shulker_box.png")

    private var lockedSlot: Slot? = null
    private var lockedStack: ItemStack? = null
        set(value) {
            if (value !== field) lockedSlot = null
            field = value
        }
    private var lockedX: Int = 0
    private var lockedY: Int = 0

    @JvmStatic
    var isRenderingSubTooltip: Boolean = false
        private set

    // Cache for container contents summary
    val containerCache = object : LinkedHashMap<Int, ContainerPreviewInfo>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ContainerPreviewInfo>): Boolean {
            return size > 200
        }
    }

    private const val ROWS = 3
    private const val COLS = 9
    private const val SLOT_SIZE = 18
    private const val PADDING = 7
    private const val TITLE_HEIGHT = 14

    @JvmStatic
    val isLocked: Boolean
        get() = lockedSlot != null

    private fun getTooltipWidth() = PADDING + COLS * SLOT_SIZE + PADDING
    private fun getTooltipHeight() = TITLE_HEIGHT + ROWS * SLOT_SIZE + PADDING

    /**
     * Check if the mouse is over the locked tooltip area (for click blocking)
     */
    @JvmStatic
    fun isMouseOverLockedTooltip(mouseX: Int, mouseY: Int): Boolean {
        if (!isLocked) return false
        val width = getTooltipWidth()
        val height = getTooltipHeight()
        return mouseX >= lockedX && mouseX < lockedX + width &&
                mouseY >= lockedY && mouseY < lockedY + height
    }

    /**
     * Calculate text color based on background luminance.
     * Returns dark text for light backgrounds and white text for dark backgrounds.
     */
    private fun getTextColor(tintColor: Int): Int {
        val r = ((tintColor shr 16) and 0xFF) / 255f
        val g = ((tintColor shr 8) and 0xFF) / 255f
        val b = (tintColor and 0xFF) / 255f
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        return if (luminance > 0.7f) Colors.DARK_GRAY else Colors.WHITE
    }

    @JvmStatic
    fun renderShulkerTooltip(
	    context: DrawContext,
	    textRenderer: TextRenderer,
	    mouseX: Int,
	    mouseY: Int
	) = runSafe {
        val handledScreen = mc.currentScreen as? HandledScreen<*> ?: return@runSafe
        val slot = handledScreen.focusedSlot ?: return@runSafe

        val width = getTooltipWidth()
        val height = getTooltipHeight()

        val lockKeyPressed = lockKey.isSatisfied()

        if (lockKeyPressed && lockedSlot == null) {
            lockedSlot = slot
            lockedX = calculateTooltipX(mouseX, width)
            lockedY = calculateTooltipY(mouseY, height)
        } else if (!lockKeyPressed && lockedSlot != null) {
            lockedSlot = null
        }

        if (isLocked) {
            renderLockedTooltipInternal(context, textRenderer)
            return@runSafe
        }

        renderTooltipForStack(context, textRenderer, slot.stack, calculateTooltipX(mouseX, width), calculateTooltipY(mouseY, height), false)
    }

    /**
     * Render the locked tooltip - called from mixin when we're locked
     */
    @JvmStatic
    fun renderLockedTooltip(context: DrawContext, textRenderer: TextRenderer) {
        lockedStack = lockedSlot?.stack
        if (!lockKey.isSatisfied() || lockedSlot?.stack?.isEmpty == true) {
            lockedSlot = null
            return
        }
        renderLockedTooltipInternal(context, textRenderer)
    }

    private fun renderLockedTooltipInternal(context: DrawContext, textRenderer: TextRenderer) {
        val stack = lockedStack ?: return
        renderTooltipForStack(context, textRenderer, stack, lockedX, lockedY, true)
    }

    private fun renderTooltipForStack(context: DrawContext, textRenderer: TextRenderer, stack: ItemStack, x: Int, y: Int, allowHover: Boolean) {
        val contents = getContainerContents(stack)
        val width = getTooltipWidth()

        val matrices = context.matrices
        matrices.pushMatrix()

        val tintColor = getContainerTintColor(stack)

        drawBackground(context, x, y, width, tintColor)
        val name = stack.name
        val textColor = getTextColor(tintColor)
        context.drawText(textRenderer, name, x + PADDING, y + 4, textColor, false)

        val slotsStartX = x + PADDING
        val slotsStartY = y + TITLE_HEIGHT

        val actualMouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val actualMouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()

        var hoveredStack: ItemStack? = null
        var hoveredSlotX = 0
        var hoveredSlotY = 0

        for ((index, item) in contents.withIndex()) {
            if (index >= COLS * ROWS) break

            val slotCol = index % COLS
            val slotRow = index / COLS

            val slotX = slotsStartX + slotCol * SLOT_SIZE
            val slotY = slotsStartY + slotRow * SLOT_SIZE
            val itemX = slotX + 1
            val itemY = slotY + 1

            if (allowHover) {
                val isHovered = actualMouseX >= slotX && actualMouseX < slotX + SLOT_SIZE &&
                        actualMouseY >= slotY && actualMouseY < slotY + SLOT_SIZE

                if (isHovered && !item.isEmpty) {
                    context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80FFFFFF.toInt())
                    hoveredStack = item
                    hoveredSlotX = actualMouseX
                    hoveredSlotY = actualMouseY
                }
            }

            if (!item.isEmpty) {
                context.drawItem(item, itemX, itemY)
                context.drawStackOverlay(textRenderer, item, itemX, itemY)
            }
        }

        matrices.popMatrix()

	    hoveredStack?.let { stack ->
            matrices.pushMatrix()

            if (isPreviewableContainer(stack)) {
                val nestedWidth = getTooltipWidth()
                val nestedHeight = getTooltipHeight()
                val nestedX = calculateTooltipX(hoveredSlotX, nestedWidth)
                val nestedY = calculateTooltipY(hoveredSlotY, nestedHeight)
                renderTooltipForStack(context, textRenderer, stack, nestedX, nestedY, false)
            } else {
                isRenderingSubTooltip = true
                try {
                    context.drawItemTooltip(textRenderer, stack, hoveredSlotX, hoveredSlotY)
                } finally {
                    isRenderingSubTooltip = false
                }
            }
            matrices.popMatrix()
        }
    }

    private fun getContainerContents(stack: ItemStack): List<ItemStack> {
        return when {
            isShulkerBox(stack) -> stack.shulkerBoxContents
            isEnderChest(stack) -> EnderChestContainer.stacks
            else -> emptyList()
        }
    }

    private fun getContainerTintColor(stack: ItemStack): Int {
        if (!colorTint) return 0xFFFFFFFF.toInt()

        return when {
            isShulkerBox(stack) -> {
                val color = getShulkerColor(stack)
                color?.entityColor ?: 0xFFFFFFFF.toInt()
            }
            isEnderChest(stack) -> 0xFF1E1E2E.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }

    private fun calculateTooltipX(mouseX: Int, width: Int): Int {
        val screenWidth = mc.window.scaledWidth
        var x = mouseX + 12
        if (x + width > screenWidth) {
            x = mouseX - width - 12
        }
        if (x < 0) x = 0
        return x
    }

    private fun calculateTooltipY(mouseY: Int, height: Int): Int {
        val screenHeight = mc.window.scaledHeight
        var y = mouseY - 12
        if (y + height > screenHeight) {
            y = screenHeight - height
        }
        if (y < 0) y = 0
        return y
    }

    private fun drawBackground(context: DrawContext, x: Int, y: Int, width: Int, tintColor: Int) {
        // Draw the shulker box texture background with tint
        // The shulker_box.png texture is 176x166
        // Top part (title area): y=0 to y=17
        // Slot area: y=17 to y=89 (3 rows of 18px each + borders)
        // Bottom: y=160 onwards

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            background,
            x, y,
            0f, 0f,
            width, TITLE_HEIGHT,
            width, TITLE_HEIGHT,
            256, 256,
            tintColor
        )

        // Middle rows
	    (0 until ROWS).forEach { row ->
		    context.drawTexture(
			    RenderPipelines.GUI_TEXTURED,
			    background,
			    x, y + TITLE_HEIGHT + row * SLOT_SIZE,
			    0f, 17f,
			    width, SLOT_SIZE,
			    width, SLOT_SIZE,
			    256, 256,
			    tintColor
		    )
	    }

        // Bottom
        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            background,
            x, y + TITLE_HEIGHT + ROWS * SLOT_SIZE,
            0f, 160f,
            width, PADDING,
            width, PADDING,
            256, 256,
            tintColor
        )
    }

    private fun getShulkerColor(stack: ItemStack): DyeColor? {
        val item = stack.item
        if (item is BlockItem) {
            val block = item.block
            if (block is ShulkerBoxBlock) {
                return block.color
            }
        }
        return null
    }

    private fun getPreviewItemForContainer(container: ItemStack): ContainerPreviewInfo {
        val hash = container.hashCode()

        return containerCache.computeIfAbsent(hash) {
            val contents = container.shulkerBoxContents + container.bundleContents
            if (contents.isEmpty()) return@computeIfAbsent ContainerPreviewInfo(null, false)

            val groups = contents.filter { stack -> stack.item != Items.AIR }
                .groupBy { stack -> stack.item }
                .map { (item, stacks) ->
                    val rawCount = stacks.sumOf { it.count }
                    val weight = if (previewItemWeightedCount) 64f / item.maxCount else 1f
                    ItemGroup(stacks.first(), rawCount, rawCount * weight)
                }
            if (groups.isEmpty()) return@computeIfAbsent ContainerPreviewInfo(null, false)

            val unique = groups.size
            val mostCommon = groups.maxByOrNull { it.weightedCount }
            val previewStack = mostCommon?.let {
                it.stack.copyWithCount(max(1, it.rawCount.coerceAtMost(it.stack.maxCount)))
            }

            // Per-item-type fill bar segments, largest fraction first (matches ItemFinder)
            val segments = groups
                .map { FillSegment(getItemAverageColor(it.stack), it.rawCount / it.stack.maxCount.toFloat()) }
                .sortedByDescending { it.stacksFilled }
            // A shulker box holds 27 stacks; a bundle holds one stack worth
            val capacity = if (isBundle(container)) 1f else 27f

            ContainerPreviewInfo(previewStack, unique > 1, mostCommon?.rawCount ?: 0, segments, capacity)
        }
    }

    /** Computes the average color of an item's particle sprite, used for fill bar segments. */
    private fun getItemAverageColor(stack: ItemStack): Int {
        return try {
            val renderState = ItemRenderState()
            mc.itemModelManager.clearAndUpdate(renderState, stack, ItemDisplayContext.GUI, mc.world, mc.player, 0)
            val sprite = renderState.getParticleSprite(Random.create()) ?: return 0xFFFFFFFF.toInt()
            nativeImageAverageColor(sprite.contents.image)
        } catch (_: Exception) {
            0xFFFFFFFF.toInt()
        }
    }

    private fun nativeImageAverageColor(image: NativeImage): Int {
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        var aSum = 0.0
        var pixelCount = 0
        for (px in 0 until image.width) {
            for (py in 0 until image.height) {
                val argb = image.getColorArgb(px, py)
                val a = ColorHelper.getAlpha(argb)
                if (a == 0) continue
                val r = ColorHelper.getRed(argb)
                val g = ColorHelper.getGreen(argb)
                val b = ColorHelper.getBlue(argb)
                rSum += r.toDouble() * r * a
                gSum += g.toDouble() * g * a
                bSum += b.toDouble() * b * a
                aSum += a.toDouble()
                pixelCount++
            }
        }
        if (aSum <= 0.0 || pixelCount == 0) return 0xFFFFFFFF.toInt()
        val r = sqrt(rSum / aSum).roundToInt().coerceIn(0, 255)
        val g = sqrt(gSum / aSum).roundToInt().coerceIn(0, 255)
        val b = sqrt(bSum / aSum).roundToInt().coerceIn(0, 255)
        val a = (aSum / pixelCount).roundToInt().coerceIn(0, 255)
        return ColorHelper.getArgb(a, r, g, b)
    }

    @JvmStatic
    fun isShulkerBox(stack: ItemStack) = stack.item in shulkerBoxes

    @JvmStatic
    fun isEnderChest(stack: ItemStack) = stack.item == Items.ENDER_CHEST && EnderChestContainer.stacks.isNotEmpty()

    @JvmStatic
    fun isPreviewableContainer(stack: ItemStack) = isShulkerBox(stack) || isEnderChest(stack)

    @JvmStatic
    fun isBundle(stack: ItemStack) = stack.item in bundles

	@JvmStatic
	fun drawOnItem(drawContext: DrawContext, state: GuiRenderState, entity: LivingEntity?, world: World?, stack: ItemStack, x: Int, y: Int, seed: Int) {
		if (!isShulkerBox(stack) && !isBundle(stack)) return
        if (!contentPreview && !showFillBar && !showHighestItemCount) return
        val preview = getPreviewItemForContainer(stack)
        if (preview.stack == null) return

        if (contentPreview) {
            // Apply scaling
            val scale = previewItemScale / 16.0f
            val itemMatrix = Matrix3x2f(drawContext.matrices)

            // Required to center the icon correctly, due to how the item gets centered by the renderer
            val shift = 8 * (1 - scale) // 0 at scale 1.0, 8 at scale 0.0

            val newScreenX = ((x + previewItemXOffset + shift) / scale).toInt()
            val newScreenY = (((y - previewItemYOffset) + shift) / scale).toInt()

            itemMatrix.scale(scale, scale)

            val keyedItemRenderState = KeyedItemRenderState()
            mc.itemModelManager.clearAndUpdate(keyedItemRenderState, preview.stack, ItemDisplayContext.GUI, world, entity, seed)

            state.addItem(
                ItemGuiElementRenderState(
                    preview.stack.item.name.toString(), itemMatrix, keyedItemRenderState, newScreenX, newScreenY, drawContext.scissorStack.peekLast()
                )
            )
            if (preview.hasMore) {
                state.addText(
                    TextGuiElementRenderState(
                        mc.textRenderer, buildText {
                            literal("+")
                        }.asOrderedText(), itemMatrix, newScreenX + 14, newScreenY - 2, -1, Integer.MIN_VALUE, true, false, drawContext.scissorStack.peekLast()
                    )
                )
            }
        }

        // Fill bar and count use the unscaled matrix so they align to the actual 16x16 slot
        val overlayMatrix = Matrix3x2f(drawContext.matrices)
        val scissor = drawContext.scissorStack.peekLast()

        val barPresent = showFillBar && preview.segments.isNotEmpty()
        if (barPresent) {
            renderFillBar(state, overlayMatrix, scissor, preview, x, y)
        }
        if (showHighestItemCount && preview.highestCount > 1) {
            renderCountText(state, overlayMatrix, scissor, preview.highestCount, x, y, barPresent)
        }
	}

    private fun renderFillBar(state: GuiRenderState, pose: Matrix3x2f, scissor: ScreenRect?, info: ContainerPreviewInfo, x: Int, y: Int) {
        // Sub-pixel geometry ported from the original ItemFinder.renderBar: a thin 0.4px black
        // frame on all sides around a 1px-tall colored bar
        val borderSize = 0.4f
        val lineLength = 16f - borderSize * 2f
        val xStart = x + borderSize
        val yStart = y + 15f - borderSize
        val xEnd = xStart + lineLength
        val yEnd = yStart + 1f

        // Black outline framing the bar on all sides
        fillQuad(state, pose, scissor, xStart - borderSize, yStart - borderSize, xEnd + borderSize, yEnd + borderSize, 0xFF000000.toInt())
        // White "empty" track behind the colored segments
        fillQuad(state, pose, scissor, xStart, yStart, xEnd, yEnd, 0xFFFFFFFF.toInt())

        // Colored segments proportional to how full the container is
        var filled = 0f
        for (segment in info.segments) {
            val width = (segment.stacksFilled / info.capacity) * lineLength
            if (width <= 0f) continue
            fillQuad(state, pose, scissor, xStart + filled, yStart, xStart + filled + width, yEnd, segment.color)
            filled += width
        }
    }

    private fun renderCountText(state: GuiRenderState, pose: Matrix3x2f, scissor: ScreenRect?, count: Int, x: Int, y: Int, aboveBar: Boolean) {
        val text = count.toString()
        val textRenderer = mc.textRenderer
        // Scale the count down so it fits the 16x16 slot (a full-size digit is far too large)
        val scale = 0.5f
        val textMatrix = Matrix3x2f(pose).scale(scale, scale)
        // Sit above the fill bar when it's present, otherwise use the normal bottom-right corner
        val baseline = if (aboveBar) y + 13 else y + 15
        // Right-align to the slot's right edge, in the scaled coordinate space
        val textX = ((x + 16) / scale).toInt() - textRenderer.getWidth(text)
        val textY = (baseline / scale).toInt() - textRenderer.fontHeight
        state.addText(
            TextGuiElementRenderState(
                textRenderer, buildText { literal(text) }.asOrderedText(), textMatrix, textX, textY, -1, Integer.MIN_VALUE, true, false, scissor
            )
        )
    }

    /**
     * Draws a colored quad with sub-pixel-accurate float coordinates. [ColoredQuadGuiElementRenderState]
     * only accepts integer coordinates, so scale the pose matrix down and the coordinates up to
     * recover fractional-pixel precision (needed for the thin fill bar border).
     */
    private fun fillQuad(state: GuiRenderState, pose: Matrix3x2f, scissor: ScreenRect?, x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        val subpixel = 10f
        val subPose = Matrix3x2f(pose).scale(1f / subpixel)
        state.addSimpleElement(
            ColoredQuadGuiElementRenderState(
                RenderPipelines.GUI, TextureSetup.empty(), subPose,
                (x0 * subpixel).roundToInt(), (y0 * subpixel).roundToInt(),
                (x1 * subpixel).roundToInt(), (y1 * subpixel).roundToInt(),
                color, color, scissor
            )
        )
    }

    open class ContainerComponent(val stack: ItemStack) : TooltipData, TooltipComponent {
        override fun drawItems(textRenderer: TextRenderer, x: Int, y: Int, width: Int, height: Int, context: DrawContext) {}
        override fun getHeight(textRenderer: TextRenderer): Int = 0
        override fun getWidth(textRenderer: TextRenderer): Int = 0
    }

    data class ContainerPreviewInfo(
        val stack: ItemStack?,
        val hasMore: Boolean,
        val highestCount: Int = 0,
        val segments: List<FillSegment> = emptyList(),
        val capacity: Float = 27f,
    )

    data class FillSegment(val color: Int, val stacksFilled: Float)

    private data class ItemGroup(val stack: ItemStack, val rawCount: Int, val weightedCount: Float)
}
