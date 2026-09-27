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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImVec2
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.hud.ComposeHud
import com.lambda.newui.hud.HudText
import com.lambda.newui.hud.hudTextColor
import com.lambda.util.FormattingUtils.format
import com.lambda.util.ServerTPSUtils
import com.lambda.util.ServerTPSUtils.recentData

@Suppress("unused")
object Tps : HudModule(
	name = "TPS",
	description = "Display the server's tick rate",
	tag = ModuleTag.HUD,
) {
	private val format by setting("Tick format", ServerTPSUtils.TickFormat.Tps)
	private val showGraph by setting("Show TPS Graph", false)
	private val graphHeight by setting("Graph Height", 40f, 10f..200f, 1f)
	private val graphWidth by setting("Graph Width", 200f, 10f..500f, 1f)
	private val graphStride by setting("Graph Stride", 1, 1..20, 1)

	override fun ImGuiBuilder.buildLayout() {
		val data = recentData(format)
		if (data.isEmpty()) {
			text("No ${format.displayName} data yet")
			return
		}
		val current = data.last()
		val avg = data.average().toFloat()
		if (!showGraph) {
			text("${format.displayName}: ${avg.format()}${format.unit}")
			return
		}
		val overlay = "cur ${current.format()}${format.unit} | avg ${avg.format()}${format.unit}"

		plotLines(
			label = "##TPSPlot",
			values = data,
			overlayText = overlay,
			graphSize = ImVec2(graphWidth, graphHeight),
			stride = graphStride
		)
	}

	@Composable
	override fun Content() {
		ComposeHud.observeTick()
		val data = recentData(format)
		if (data.isEmpty()) {
			HudText("No ${format.displayName} data yet")
			return
		}
		val current = data.last()
		val avg = data.average().toFloat()
		if (!showGraph) {
			HudText("${format.displayName}: ${avg.format()}${format.unit}")
			return
		}
		val overlay = "cur ${current.format()}${format.unit} | avg ${avg.format()}${format.unit}"
		val lineColor = hudTextColor()

		Box(
			modifier = Modifier
				.size(graphWidth.dp, graphHeight.dp)
				.drawBehind { drawGraph(data, graphStride, lineColor) },
			contentAlignment = Alignment.TopCenter
		) {
			HudText(overlay)
		}
	}

	/** A line through every [stride]th sample, scaled to the samples' range, over a dim backdrop. */
	private fun DrawScope.drawGraph(values: FloatArray, stride: Int, color: Color) {
		drawRect(Color.Black.copy(alpha = 0.35f))
		val points = values.filterIndexed { index, _ -> index % stride == 0 }
		if (points.size < 2) return
		val min = points.min()
		val range = (points.max() - min).takeIf { it > 0f } ?: 1f
		val path = Path()
		points.forEachIndexed { index, value ->
			val x = index * size.width / (points.size - 1)
			val y = size.height - (value - min) / range * size.height
			if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
		}
		drawPath(path, color, style = Stroke(1.5.dp.toPx()))
	}
}
