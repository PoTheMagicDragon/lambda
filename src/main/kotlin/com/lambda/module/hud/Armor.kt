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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lambda.context.SafeContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.ComposeRenderer
import com.lambda.newui.ItemImageBuffer
import com.lambda.newui.hud.ComposeHud
import com.lambda.newui.hud.HudText
import com.lambda.newui.state.LambdaState.observe
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

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

        val pieces = runSafe { pieces() } ?: return
        val shown = shownPieces(pieces, showEmpty)
        if (shown.isEmpty()) {
            HudText("No armor")
            return
        }

        // Minecraft renders the shown pieces into one strip; each icon draws its cell of it.
        val icons = remember { ItemImageBuffer() }
        DisposableEffect(icons) {
            ComposeRenderer.registerItemBuffer(icons)
            onDispose { ComposeRenderer.unregisterItemBuffer(icons) }
        }
        val cellPx = with(LocalDensity.current) { iconSize.dp.roundToPx() }
        SideEffect { icons.request(shown.map { it.stack }, cellPx) }

        when (layout) {
            Layout.Horizontal -> Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top
            ) {
                shown.forEachIndexed { index, piece ->
                    // Icon on top, everything about it stacked underneath, like an inventory slot.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        ArmorIcon(piece, icons, index, iconSize)
                        if (showBar) DurabilityBar(piece, iconSize)
                        if (showName && !piece.isEmpty) HudText(piece.name)
                        DurabilityLabel(piece, format)
                    }
                }
            }

            Layout.Vertical -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEachIndexed { index, piece ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            ArmorIcon(piece, icons, index, iconSize)
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
     * The item as Minecraft renders it, [size] dp square: cell [index] of the strip [icons]
     * holds. The strip is rendered at the icon's pixel size, so its cell is copied 1:1 without
     * filtering. An empty slot is an outlined square.
     */
    @Composable
    private fun ArmorIcon(piece: Piece, icons: ItemImageBuffer, index: Int, size: Float) {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .size(size.dp)
                .then(if (piece.isEmpty) Modifier.border(1.dp, colors.outline.copy(alpha = 0.6f)) else Modifier)
                .drawBehind {
                    if (piece.isEmpty) return@drawBehind
                    val strip = icons.image.value ?: return@drawBehind
                    val cell = icons.cellSize.toFloat()
                    if (cell <= 0f || (index + 1) * cell > strip.width) return@drawBehind
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawImageRect(
                            strip,
                            Rect.makeXYWH(index * cell, 0f, cell, cell),
                            Rect.makeWH(this.size.width, this.size.height),
                            SamplingMode.DEFAULT,
                            null,
                            true
                        )
                    }
                }
        )
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

    /** Green at full, through yellow, to red when nearly broken. */
    private fun durabilityColor(fraction: Float): Color =
        Color.hsv(hue = 120f * fraction.coerceIn(0f, 1f), saturation = 0.85f, value = 1f)
}
