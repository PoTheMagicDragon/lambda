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

package com.lambda.newui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ── HSV helpers ──────────────────────────────────────────────────────────────

private data class HSV(val h: Float, val s: Float, val v: Float)

private fun rgbToHsv(r: Int, g: Int, b: Int): HSV {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val max = maxOf(rf, gf, bf); val min = minOf(rf, gf, bf)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == rf -> 60f * (((gf - bf) / delta) % 6f)
        max == gf -> 60f * (((bf - rf) / delta) + 2f)
        else -> 60f * (((rf - gf) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    return HSV(h, s, max)
}

private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Int, Int, Int> {
    val c = v * s; val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f)); val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Triple(
        ((r1 + m) * 255f).roundToInt().coerceIn(0, 255),
        ((g1 + m) * 255f).roundToInt().coerceIn(0, 255),
        ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
    )
}

private fun hueColor(h: Float): Color {
    val (r, g, b) = hsvToRgb(h, 1f, 1f)
    return Color(r, g, b)
}

// ── Compact text style used throughout ───────────────────────────────────────

private val compactStyle = TextStyle(
    fontSize = 8.sp, lineHeight = 8.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

private val labelStyle = TextStyle(
    fontSize = 9.sp, lineHeight = 9.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

// ── Public entry point (same signature as before) ────────────────────────────

@Composable
fun ColorSettingGui(
    name: String,
    red: Int,
    green: Int,
    blue: Int,
    alpha: Int,
    onColorChange: (r: Int, g: Int, b: Int, a: Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // ── Header: name + swatch ────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clickable { expanded = !expanded }
        ) {
            Text(text = name, color = colors.onSurface, style = labelStyle)
            Box(
                modifier = Modifier
                    .size(14.dp, 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .drawBehind { drawCheckerboard() }
                    .background(Color(red, green, blue, alpha))
                    .border(0.5.dp, colors.outline, RoundedCornerShape(2.dp))
            )
        }

        // ── Expanded picker ──────────────────────────────────────────────────
        if (expanded) {
            val hsv = remember(red, green, blue) { rgbToHsv(red, green, blue) }
            var hue by remember(red, green, blue) { mutableStateOf(hsv.h) }
            var sat by remember(red, green, blue) { mutableStateOf(hsv.s) }
            var value by remember(red, green, blue) { mutableStateOf(hsv.v) }

            fun emitFromHsv(h: Float = hue, s: Float = sat, v: Float = value, a: Int = alpha) {
                val (nr, ng, nb) = hsvToRgb(h, s, v)
                onColorChange(nr, ng, nb, a)
            }

            Column(modifier = Modifier.padding(top = 3.dp, start = 2.dp)) {
                // ── SV square ────────────────────────────────────────────────
                SaturationValuePicker(
                    hue = hue, sat = sat, value = value,
                    onSatValChange = { s, v ->
                        sat = s; value = v; emitFromHsv(s = s, v = v)
                    }
                )

                Spacer(Modifier.height(3.dp))

                // ── Hue bar ──────────────────────────────────────────────────
                HueBar(hue = hue, onHueChange = { h ->
                    hue = h; emitFromHsv(h = h)
                })

                Spacer(Modifier.height(2.dp))

                // ── Alpha bar ────────────────────────────────────────────────
                AlphaBar(red = red, green = green, blue = blue, alpha = alpha,
                    onAlphaChange = { a -> emitFromHsv(a = a) }
                )

                Spacer(Modifier.height(4.dp))

                // ── RGBA inputs ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ChannelInput("R", red, colors.onSurface, colors.surfaceContainerHigh, Modifier.weight(1f)) {
                        onColorChange(it, green, blue, alpha)
                    }
                    ChannelInput("G", green, colors.onSurface, colors.surfaceContainerHigh, Modifier.weight(1f)) {
                        onColorChange(red, it, blue, alpha)
                    }
                    ChannelInput("B", blue, colors.onSurface, colors.surfaceContainerHigh, Modifier.weight(1f)) {
                        onColorChange(red, green, it, alpha)
                    }
                    ChannelInput("A", alpha, colors.onSurface, colors.surfaceContainerHigh, Modifier.weight(1f)) {
                        onColorChange(red, green, blue, it)
                    }
                }

                Spacer(Modifier.height(2.dp))

                // ── Hex input ────────────────────────────────────────────────
                HexInput(
                    red = red, green = green, blue = blue, alpha = alpha,
                    textColor = colors.onSurface, bgColor = colors.surfaceContainerHigh,
                    onColorChange = onColorChange
                )

                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

// ── SV picker (saturation on X, value/brightness on Y) ───────────────────────

@Composable
private fun SaturationValuePicker(
    hue: Float, sat: Float, value: Float,
    onSatValChange: (s: Float, v: Float) -> Unit
) {
    val baseColor = hueColor(hue)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(3.dp))
            .drawBehind {
                // Base hue fill
                drawRect(baseColor)
                // White → transparent (left to right = saturation)
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                // Transparent → black (top to bottom = brightness)
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    onSatValChange(s, v)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onSatValChange(s, v)
                }
            }
    ) {
        // Crosshair indicator
        val indicatorSize = 6.dp
        val parentW = maxWidth
        val parentH = maxHeight
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (sat * (parentW.toPx() - indicatorSize.toPx())).roundToInt(),
                        ((1f - value) * (parentH.toPx() - indicatorSize.toPx())).roundToInt()
                    )
                }
                .size(indicatorSize)
                .border(1.5.dp, Color.White, CircleShape)
                .border(0.5.dp, Color.Black, CircleShape)
        )
    }
}

// ── Hue bar (rainbow gradient) ───────────────────────────────────────────────

@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit) {
    val rainbowColors = remember {
        (0..6).map { hueColor(it * 60f) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .drawBehind {
                drawRect(Brush.horizontalGradient(rainbowColors))
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
    ) {
        // Position indicator
        val fraction = hue / 360f
        val parentW = maxWidth
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (fraction * (parentW.toPx() - 4.dp.toPx())).roundToInt(), 0
                    )
                }
                .width(4.dp)
                .fillMaxHeight()
                .background(Color.White, RoundedCornerShape(1.dp))
                .border(0.5.dp, Color.Black, RoundedCornerShape(1.dp))
        )
    }
}

// ── Alpha bar (checkerboard + gradient) ──────────────────────────────────────

@Composable
private fun AlphaBar(
    red: Int, green: Int, blue: Int, alpha: Int,
    onAlphaChange: (Int) -> Unit
) {
    val solidColor = Color(red, green, blue)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .drawBehind {
                drawCheckerboard()
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, solidColor)))
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onAlphaChange(((offset.x / size.width).coerceIn(0f, 1f) * 255f).roundToInt())
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onAlphaChange(((change.position.x / size.width).coerceIn(0f, 1f) * 255f).roundToInt())
                }
            }
    ) {
        val fraction = alpha / 255f
        val parentW = maxWidth
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (fraction * (parentW.toPx() - 4.dp.toPx())).roundToInt(), 0
                    )
                }
                .width(4.dp)
                .fillMaxHeight()
                .background(Color.White, RoundedCornerShape(1.dp))
                .border(0.5.dp, Color.Black, RoundedCornerShape(1.dp))
        )
    }
}

// ── RGBA channel text input ──────────────────────────────────────────────────

@Composable
private fun ChannelInput(
    label: String, channelValue: Int,
    textColor: Color, bgColor: Color,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = textColor.copy(alpha = 0.6f), style = compactStyle)
        BasicTextField(
            value = channelValue.toString(),
            onValueChange = { text ->
                val parsed = text.filter { it.isDigit() }.take(3).toIntOrNull()
                if (parsed != null) onValueChange(parsed.coerceIn(0, 255))
                else if (text.isEmpty()) onValueChange(0)
            },
            textStyle = compactStyle.copy(color = textColor, textAlign = TextAlign.Center),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = 2.dp, vertical = 2.dp)
        )
    }
}

// ── Hex text input ───────────────────────────────────────────────────────────

@Composable
private fun HexInput(
    red: Int, green: Int, blue: Int, alpha: Int,
    textColor: Color, bgColor: Color,
    onColorChange: (r: Int, g: Int, b: Int, a: Int) -> Unit
) {
    val hexString = buildString {
        append('#')
        append("%02X".format(red))
        append("%02X".format(green))
        append("%02X".format(blue))
        if (alpha < 255) append("%02X".format(alpha))
    }

    val shape = RoundedCornerShape(2.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "Hex", color = textColor.copy(alpha = 0.6f), style = compactStyle,
            modifier = Modifier.padding(end = 4.dp))
        BasicTextField(
            value = hexString,
            onValueChange = { text ->
                parseHexColor(text)?.let { (r, g, b, a) -> onColorChange(r, g, b, a) }
            },
            textStyle = compactStyle.copy(color = textColor),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

private fun parseHexColor(text: String): List<Int>? {
    val hex = text.removePrefix("#")
    return when (hex.length) {
        6 -> {
            val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
            val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
            val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
            listOf(r, g, b, 255)
        }
        8 -> {
            val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
            val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
            val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
            val a = hex.substring(6, 8).toIntOrNull(16) ?: return null
            listOf(r, g, b, a)
        }
        else -> null
    }
}

// ── Checkerboard (transparency indicator) ────────────────────────────────────

private fun DrawScope.drawCheckerboard() {
    val cellSize = 4.dp.toPx()
    val cols = (size.width / cellSize).toInt() + 1
    val rows = (size.height / cellSize).toInt() + 1
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val color = if ((row + col) % 2 == 0) Color(0xFFCCCCCC) else Color(0xFF999999)
            drawRect(
                color = color,
                topLeft = Offset(col * cellSize, row * cellSize),
                size = Size(cellSize, cellSize)
            )
        }
    }
}
