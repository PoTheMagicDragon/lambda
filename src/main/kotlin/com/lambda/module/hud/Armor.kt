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

package com.lambda.module.hud

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.context.SafeContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.hud.ComposeHud
import com.lambda.newui.hud.HudText
import com.lambda.newui.hud.ItemIcons
import com.lambda.newui.state.LambdaState.observe
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import net.minecraft.component.type.DyedColorComponent
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.ItemTags
import net.minecraft.util.Identifier
import kotlin.math.roundToInt

@Suppress("unused")
object Armor : HudModule(
    name = "Armor",
    description = "Shows the armor you are wearing and how much durability it has left",
    tag = ModuleTag.HUD,
) {
    private enum class Layout(override val displayName: String) : NamedEnum {
        Horizontal("Horizontal"),
        Vertical("Vertical"),
    }

    private enum class DurabilityFormat(override val displayName: String) : NamedEnum {
        Percentage("Percentage"),
        Remaining("Remaining"),
        Both("Both"),
        None("None"),
    }

    // Kept as Setting references so the Compose element can observe() them.
    private val layoutSetting = setting("Layout", Layout.Horizontal)
    private val iconSizeSetting = setting("Icon Size", 24f, 12f..64f, 1f)
    private val formatSetting = setting("Durability Format", DurabilityFormat.Percentage)
    private val showBarSetting = setting("Durability Bar", true, "Draw a bar under each piece")
    private val showNameSetting = setting("Show Name", false, "Write the item's name next to its icon")
    private val showEmptySetting = setting("Show Empty Slots", false, "Keep a placeholder for slots with nothing equipped")
    private val showGlintSetting = setting("Enchantment Glint", true, "Animate the enchanted shimmer over enchanted pieces")

    private val format by formatSetting
    private val showEmpty by showEmptySetting

    private val slots = listOf(
        EquipmentSlot.HEAD to "Helmet",
        EquipmentSlot.CHEST to "Chestplate",
        EquipmentSlot.LEGS to "Leggings",
        EquipmentSlot.FEET to "Boots",
    )

    private class Piece(val slot: String, val stack: ItemStack) {
        val isEmpty get() = stack.isEmpty
        val name: String get() = stack.name.string

        /** Durability left as 0..1, or null when the item cannot wear out. */
        val remaining: Float?
            get() = if (stack.isDamageable && stack.maxDamage > 0) {
                1f - stack.damage.toFloat() / stack.maxDamage
            } else null

        fun durabilityText(format: DurabilityFormat): String? {
            val fraction = remaining ?: return null
            val percent = "${(fraction * 100).toInt()}%"
            val absolute = "${stack.maxDamage - stack.damage}/${stack.maxDamage}"
            return when (format) {
                DurabilityFormat.Percentage -> percent
                DurabilityFormat.Remaining -> absolute
                DurabilityFormat.Both -> "$percent ($absolute)"
                DurabilityFormat.None -> null
            }
        }
    }

    private fun SafeContext.pieces() = slots.map { (slot, label) -> Piece(label, player.getEquippedStack(slot)) }

    private fun shownPieces(pieces: List<Piece>, showEmpty: Boolean) =
        if (showEmpty) pieces else pieces.filter { !it.isEmpty }

    override fun ImGuiBuilder.buildLayout() {
        val pieces = runSafe { pieces() } ?: return
        val shown = shownPieces(pieces, showEmpty)
        if (shown.isEmpty()) {
            text("No armor")
            return
        }
        shown.forEach { piece ->
            if (piece.isEmpty) text("${piece.slot}: -")
            else text(listOfNotNull(piece.name, piece.durabilityText(format)).joinToString(" "))
        }
    }

    @Composable
    override fun Content() {
        ComposeHud.observeTick()
        val layout by layoutSetting.observe()
        val iconSize by iconSizeSetting.observe()
        val format by formatSetting.observe()
        val showBar by showBarSetting.observe()
        val showName by showNameSetting.observe()
        val showEmpty by showEmptySetting.observe()
        val showGlint by showGlintSetting.observe()

        val pieces = runSafe { pieces() } ?: return
        val shown = shownPieces(pieces, showEmpty)
        if (shown.isEmpty()) {
            HudText("No armor")
            return
        }

        when (layout) {
            Layout.Horizontal -> Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top
            ) {
                shown.forEach { piece ->
                    // Icon on top, everything about it stacked underneath, like an inventory slot.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        ArmorIcon(piece, iconSize, showGlint)
                        if (showBar) DurabilityBar(piece, iconSize)
                        if (showName && !piece.isEmpty) HudText(piece.name)
                        DurabilityLabel(piece, format)
                    }
                }
            }

            Layout.Vertical -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEach { piece ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            ArmorIcon(piece, iconSize, showGlint)
                            if (showBar) DurabilityBar(piece, iconSize)
                        }
                        Column {
                            if (showName && !piece.isEmpty) HudText(piece.name)
                            DurabilityLabel(piece, format)
                        }
                    }
                }
            }
        }
    }

    /**
     * The item's sprite at [size] dp. Dyeable items are drawn as the game draws them: the base
     * layer tinted with the dye (or leather's default brown) and the untinted overlay on top. An
     * empty slot is an outlined square; an item without a flat sprite shows its initial instead.
     * Enchanted pieces get the vanilla shimmer drawn over their pixels, see [drawGlint].
     */
    @Composable
    private fun ArmorIcon(piece: Piece, size: Float, showGlint: Boolean) {
        val colors = MaterialTheme.colorScheme
        val itemId = if (piece.isEmpty) null else Registries.ITEM.getId(piece.stack.item)
        val sprite = itemId?.let { ItemIcons.sprite(it) }
        val glint = if (showGlint && sprite != null && piece.stack.hasGlint()) ItemIcons.texture(GLINT_TEXTURE) else null
        val glintBrush = remember(glint) { glint?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) } }

        Box(
            modifier = Modifier
                .size(size.dp)
                .then(if (sprite == null) Modifier.border(1.dp, colors.outline.copy(alpha = 0.6f)) else Modifier)
                .then(
                    if (glint != null && glintBrush != null && sprite != null) {
                        Modifier.drawWithContent {
                            drawContent()
                            // Reading the frame count here redraws the shimmer every frame.
                            ComposeHud.frameCount
                            drawGlint(sprite, glint, glintBrush)
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                itemId == null -> {}
                sprite == null -> HudText(piece.name.take(1))
                else -> {
                    val tint = if (piece.stack.isIn(ItemTags.DYEABLE)) {
                        // The component stores plain RGB; force it opaque.
                        Color(DyedColorComponent.getColor(piece.stack, DyedColorComponent.DEFAULT_COLOR) or OPAQUE)
                    } else null
                    Image(
                        bitmap = sprite,
                        contentDescription = piece.name,
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) },
                        filterQuality = FilterQuality.None
                    )
                    ItemIcons.overlay(itemId)?.let { overlay ->
                        Image(
                            bitmap = overlay,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            filterQuality = FilterQuality.None
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DurabilityLabel(piece: Piece, format: DurabilityFormat) {
        if (piece.isEmpty) return
        val remaining = piece.remaining ?: return
        val text = piece.durabilityText(format) ?: return
        HudText(text, color = durabilityColor(remaining))
    }

    @Composable
    private fun DurabilityBar(piece: Piece, width: Float) {
        val remaining = piece.remaining ?: return
        val color = durabilityColor(remaining)
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(3.dp)
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.5f))
                    drawRect(color, size = Size(size.width * remaining, size.height))
                }
        )
    }

    /**
     * The enchantment shimmer as vanilla draws it over GUI items: the glint texture tiled so one
     * repeat spans eight icons, scrolled on two different periods, rotated ten degrees and blended
     * additively as its own square (vanilla's `SRC_COLOR, ONE` blend). Built in a layer that is
     * then masked by the item's own alpha, so the shimmer stops at the item's outline.
     */
    private fun DrawScope.drawGlint(mask: ImageBitmap, glint: ImageBitmap, brush: ShaderBrush) {
        val icon = size.width
        val time = System.currentTimeMillis() * GLINT_SPEED
        val scrollX = (time % 110_000L) / 110_000f
        val scrollY = (time % 30_000L) / 30_000f
        val texelScale = icon * GLINT_ICONS_PER_REPEAT / glint.width
        // Big enough to still cover the icon after the scroll and rotation move it about.
        val reach = icon * GLINT_ICONS_PER_REPEAT * 2f / texelScale

        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { blendMode = BlendMode.Plus })
            withTransform({
                translate(-scrollX * icon * GLINT_ICONS_PER_REPEAT, scrollY * icon * GLINT_ICONS_PER_REPEAT)
                rotate(GLINT_ANGLE, pivot = Offset.Zero)
                scale(texelScale, texelScale, pivot = Offset.Zero)
            }) {
                drawRect(brush, topLeft = Offset(-reach, -reach), size = Size(reach * 2f, reach * 2f))
                drawRect(brush, topLeft = Offset(-reach, -reach), size = Size(reach * 2f, reach * 2f), blendMode = BlendMode.Modulate)
            }
            val extent = IntSize(size.width.roundToInt(), size.height.roundToInt())
            drawImage(mask, dstSize = extent, blendMode = BlendMode.DstIn, filterQuality = FilterQuality.None)
            canvas.restore()
        }
    }

    /** Green at full, through yellow, to red when nearly broken. */
    private fun durabilityColor(fraction: Float): Color =
        Color.hsv(hue = 120f * fraction.coerceIn(0f, 1f), saturation = 0.85f, value = 1f)

    private const val OPAQUE = 0xFF000000.toInt()

    private val GLINT_TEXTURE: Identifier = Identifier.ofVanilla("textures/misc/enchanted_glint_item.png")

    /** Vanilla's glint texture matrix: 1/8 scale, so one texture repeat covers eight items. */
    private const val GLINT_ICONS_PER_REPEAT = 8f
    private const val GLINT_ANGLE = 10f
    private const val GLINT_SPEED = 8L
}
