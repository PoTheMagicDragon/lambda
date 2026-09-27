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

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.gui.components.ClickGuiLayout
import com.lambda.gui.snap.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Snapping for dragged HUD elements, in root pixels, driven by the click GUI's snap settings.
 *
 * Guides are tiered like the ImGui [com.lambda.gui.snap.SnapHandler]: the other elements' edges
 * and centres win over the screen centre, which wins over the grid. Within a tier the closest
 * guide to any of the element's left/centre/right (top/centre/bottom) lines is taken.
 */
internal object HudSnap {
    /** Guide lines the element snapped to this move, drawn by [HudLayer] while dragging. */
    data class Lines(val x: Float?, val y: Float?) {
        companion object {
            val NONE = Lines(null, null)
        }
    }

    data class Result(val x: Float, val y: Float, val lines: Lines)

    private enum class Tier { Element, ScreenCenter, Grid }

    private class Guide(val pos: Float, val tier: Tier)

    private class Best(var distance: Float = Float.POSITIVE_INFINITY, var delta: Float = 0f, var pos: Float = 0f)

    /**
     * Snaps [proposed] against [others], the screen centre and the grid, then clamps it inside
     * [scene]. The snap distances are settings in dp, which [density] converts.
     */
    fun snap(proposed: RectF, others: Collection<RectF>, scene: IntSize, density: Density): Result {
        var x = proposed.x
        var y = proposed.y
        var lines = Lines.NONE

        if (ClickGuiLayout.snapEnabled) {
            val vertical = ArrayList<Guide>()
            val horizontal = ArrayList<Guide>()
            if (ClickGuiLayout.snapToEdges) {
                vertical += Guide(0f, Tier.Element)
                vertical += Guide(scene.width.toFloat(), Tier.Element)
                horizontal += Guide(0f, Tier.Element)
                horizontal += Guide(scene.height.toFloat(), Tier.Element)
                others.forEach {
                    vertical += Guide(it.left, Tier.Element)
                    vertical += Guide(it.right, Tier.Element)
                    horizontal += Guide(it.top, Tier.Element)
                    horizontal += Guide(it.bottom, Tier.Element)
                }
            }
            if (ClickGuiLayout.snapToCenters) others.forEach {
                vertical += Guide(it.cx, Tier.Element)
                horizontal += Guide(it.cy, Tier.Element)
            }
            if (ClickGuiLayout.snapToScreenCenter) {
                vertical += Guide(scene.width * 0.5f, Tier.ScreenCenter)
                horizontal += Guide(scene.height * 0.5f, Tier.ScreenCenter)
            }

            val gridStep = with(density) {
                if (ClickGuiLayout.snapToGrid && ClickGuiLayout.gridSize > 0f) max(4f, ClickGuiLayout.gridSize.dp.toPx()) else 0f
            }

            val snapX = snapAxis(floatArrayOf(proposed.left, proposed.cx, proposed.right), vertical, gridStep, density)
            val snapY = snapAxis(floatArrayOf(proposed.top, proposed.cy, proposed.bottom), horizontal, gridStep, density)
            snapX?.let { (best, tier) ->
                x += best.delta
                if (tier != Tier.Grid) lines = lines.copy(x = best.pos)
            }
            snapY?.let { (best, tier) ->
                y += best.delta
                if (tier != Tier.Grid) lines = lines.copy(y = best.pos)
            }
        }

        val maxX = max(0f, scene.width - proposed.w)
        val maxY = max(0f, scene.height - proposed.h)
        return Result(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY), lines)
    }

    /** The best guide per tier for [points], then the first tier that found one. */
    private fun snapAxis(points: FloatArray, guides: List<Guide>, gridStep: Float, density: Density): Pair<Best, Tier>? {
        val best = Tier.entries.associateWith { Best() }

        fun consider(guidePos: Float, tier: Tier, point: Float) {
            val distance = abs(point - guidePos)
            val out = best.getValue(tier)
            if (distance <= threshold(tier, density) && distance < out.distance) {
                out.distance = distance
                out.delta = guidePos - point
                out.pos = guidePos
            }
        }

        guides.forEach { guide -> points.forEach { consider(guide.pos, guide.tier, it) } }
        if (gridStep > 0f) points.forEach { point ->
            consider((point / gridStep).roundToInt() * gridStep, Tier.Grid, point)
        }

        return Tier.entries
            .firstOrNull { best.getValue(it).distance.isFinite() }
            ?.let { best.getValue(it) to it }
    }

    private fun threshold(tier: Tier, density: Density): Float = with(density) {
        when (tier) {
            Tier.Element -> ClickGuiLayout.snapDistanceElement
            Tier.ScreenCenter -> ClickGuiLayout.snapDistanceScreen
            Tier.Grid -> ClickGuiLayout.snapDistanceGrid
        }.dp.toPx().coerceAtLeast(1f)
    }
}
