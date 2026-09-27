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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.gui.components.ClickGuiLayout
import com.lambda.gui.components.HudGuiLayout
import com.lambda.gui.snap.RectF
import com.lambda.module.HudModule
import com.lambda.newui.ComposeRenderer
import com.lambda.newui.state.LambdaState.observe
import com.lambda.newui.state.LambdaState.observeEnabled
import kotlin.math.max
import kotlin.math.roundToInt

/** Inner space between an element's background edge and its content. */
private val ELEMENT_PADDING = 4.dp

/** Shared between the elements while the HUD is being edited, so a drag can snap to the others. */
private class HudEditState {
    /** Bounds of every shown element in root pixels, keyed by module name. */
    val bounds = mutableStateMapOf<String, RectF>()
    var dragging by mutableStateOf<HudModule?>(null)
    var snapLines by mutableStateOf(HudSnap.Lines.NONE)
}

/**
 * The HUD: every enabled [HudModule] at its saved position. Drawn under the click GUI on every
 * frame the overlay renders, and, while the click GUI is open ([editing]), dragged, snapped and
 * managed through a context menu, unless [HudGuiLayout.isLocked].
 */
@Composable
fun HudLayer(editing: Boolean) {
    val locked = HudGuiLayout.isLocked
    val shownWhileEditing = HudGuiLayout.isShownInGUI
    val edit = remember { HudEditState() }
    val snapLineColor = ClickGuiLayout.snapLineColor.toCompose()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(editing) {
                if (!editing) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type != PointerEventType.Press) continue
                        // Main pass runs after the children, so an unconsumed press hit no
                        // element (and, above this layer, no click GUI window).
                        if (event.changes.any { it.isConsumed }) continue
                        if (!event.buttons.isSecondaryPressed) continue
                        ComposeHud.contextMenu = HudContextMenuState.Background(event.changes.first().position)
                        // Consumed so the overlay root does not read it as a click-away and
                        // close the menu it just opened.
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .drawWithContent {
                if (edit.dragging != null) drawDragGrid()
                drawContent()
                drawSnapLines(edit.snapLines, snapLineColor)
            }
    ) {
        // "Hide HUD" only applies while editing: in-game the HUD always shows.
        if (!editing || shownWhileEditing) {
            ComposeHud.modules.forEach { hud ->
                key(hud.name) {
                    val enabled by hud.observeEnabled()
                    if (enabled) HudElement(hud, editing, locked, edit)
                }
            }
        }
    }
}

@Composable
private fun HudElement(hud: HudModule, editing: Boolean, locked: Boolean, edit: HudEditState) {
    val density = LocalDensity.current
    val x by hud.hudX.observe()
    val y by hud.hudY.observe()
    val background by hud.backgroundColor.observe()
    val cornerRadius by HudGuiLayout.cornerRadius.observe()
    val scene = ComposeRenderer.sceneSize.value

    var size by remember { mutableStateOf(IntSize.Zero) }
    // Where the element actually landed, in root pixels: the drag and the context menu start
    // from this rather than the setting, which may sit off-screen after a resolution change.
    var originPx by remember { mutableStateOf(Offset.Zero) }

    // Elements keep their saved position but never leave the screen: a layout saved on a larger
    // window is pulled back inside this one.
    val origin = with(density) { clampToScene(Offset(x.dp.toPx(), y.dp.toPx()), size, scene) }
    val shape = RoundedCornerShape(cornerRadius.dp)
    val backgroundColor = background.toCompose()
    val draggable = editing && !locked

    DisposableEffect(hud) {
        onDispose { edit.bounds.remove(hud.name) }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
            .onSizeChanged { size = it }
            .onGloballyPositioned {
                originPx = it.positionInRoot()
                if (editing) edit.bounds[hud.name] = RectF(originPx.x, originPx.y, it.size.width.toFloat(), it.size.height.toFloat())
            }
            .then(if (editing) Modifier.hudEditing(hud, locked, edit, { originPx }, { size }) else Modifier)
            .then(if (draggable) Modifier.cornerMarks() else Modifier)
            .then(if (backgroundColor.alpha > 0f) Modifier.background(backgroundColor, shape) else Modifier)
            .padding(ELEMENT_PADDING)
    ) {
        hud.Content()
    }
}

/**
 * Right-click opens the element's menu; a left-button drag moves it, snapping as it goes and
 * writing the new position straight into the module's settings. Locked elements still get the
 * menu (it is how they are removed) but not the drag.
 */
private fun Modifier.hudEditing(
    hud: HudModule,
    locked: Boolean,
    edit: HudEditState,
    originPx: () -> Offset,
    size: () -> IntSize
): Modifier = pointerInput(hud, locked) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val buttons = currentEvent.buttons
        if (buttons.isSecondaryPressed) {
            down.consume()
            ComposeHud.contextMenu = HudContextMenuState.Element(hud, originPx() + down.position)
            return@awaitEachGesture
        }
        if (locked || !buttons.isPrimaryPressed) return@awaitEachGesture

        down.consume()
        ComposeHud.contextMenu = null
        edit.dragging = hud
        // The unsnapped position follows the mouse; what is shown (and saved) is its snap.
        var target = originPx()
        try {
            drag(down.id) { change ->
                // Read before consuming: a consumed change reports no position change.
                target += change.positionChange()
                change.consume()
                val extent = size()
                val proposed = RectF(target.x, target.y, extent.width.toFloat(), extent.height.toFloat())
                val others = edit.bounds.filterKeys { it != hud.name }.values
                val snapped = HudSnap.snap(proposed, others, ComposeRenderer.sceneSize.value, this)
                edit.snapLines = snapped.lines
                hud.hudX.value = snapped.x.toDp().value
                hud.hudY.value = snapped.y.toDp().value
            }
        } finally {
            edit.dragging = null
            edit.snapLines = HudSnap.Lines.NONE
        }
    }
}

/**
 * The editing outline: a halo and a border arc on each corner, concentric with the element's
 * rounded background, drawn outside its bounds so they never cover the content.
 */
private fun Modifier.cornerMarks(): Modifier = drawBehind {
    val rounding = HudGuiLayout.cornerRadius.value.dp.toPx()
    val haloThickness = HudGuiLayout.haloThickness.value.dp.toPx()
    val borderThickness = HudGuiLayout.borderThickness.value.dp.toPx()
    val inflate = HudGuiLayout.cornerInflate.value.dp.toPx()

    drawCornerArcs(
        radius = rounding + inflate + 0.5f * haloThickness + 1.dp.toPx(),
        color = HudGuiLayout.haloColor.value.toCompose(),
        thickness = haloThickness
    )
    drawCornerArcs(
        radius = rounding + 0.5f * borderThickness + 0.75f.dp.toPx(),
        color = HudGuiLayout.borderColor.value.toCompose(),
        thickness = borderThickness
    )
}

private fun DrawScope.drawCornerArcs(radius: Float, color: Color, thickness: Float) {
    if (radius <= 0f || thickness <= 0f) return
    val diameter = radius * 2f
    val arcSize = Size(diameter, diameter)
    val style = Stroke(thickness)
    // Angles run clockwise from 3 o'clock; each arc is centred [radius] in from its corner.
    drawArc(color, 180f, 90f, false, Offset(0f, 0f), arcSize, style = style)
    drawArc(color, 270f, 90f, false, Offset(size.width - diameter, 0f), arcSize, style = style)
    drawArc(color, 0f, 90f, false, Offset(size.width - diameter, size.height - diameter), arcSize, style = style)
    drawArc(color, 90f, 90f, false, Offset(0f, size.height - diameter), arcSize, style = style)
}

private fun DrawScope.drawDragGrid() {
    if (!ClickGuiLayout.snapEnabled || !ClickGuiLayout.snapToGrid) return
    val step = max(4f, ClickGuiLayout.gridSize.dp.toPx())
    val color = Color.White.copy(alpha = 28f / 255f)
    var x = 0f
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height))
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y))
        y += step
    }
}

private fun DrawScope.drawSnapLines(lines: HudSnap.Lines, color: Color) {
    val thickness = 2.dp.toPx()
    lines.x?.let { drawLine(color, Offset(it, 0f), Offset(it, size.height), thickness) }
    lines.y?.let { drawLine(color, Offset(0f, it), Offset(size.width, it), thickness) }
}

/** Keeps a box of [size] at [origin] inside [scene]; before the scene size is known, as is. */
internal fun clampToScene(origin: Offset, size: IntSize, scene: IntSize): Offset {
    if (scene.width <= 0 || scene.height <= 0) return origin
    return Offset(
        origin.x.coerceIn(0f, max(0f, (scene.width - size.width).toFloat())),
        origin.y.coerceIn(0f, max(0f, (scene.height - size.height).toFloat()))
    )
}

internal fun java.awt.Color.toCompose() = Color(red, green, blue, alpha)
