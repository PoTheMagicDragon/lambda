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

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.lambda.module.modules.client.Style
import com.lambda.newui.state.LambdaState.observe
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter

object GameBackdrop {
    /**
     * GPU snapshot of the Minecraft framebuffer, replaced by [ComposeRenderer] right before
     * each scene render. Draw scopes read it so every new snapshot re-records their backdrop.
     * The renderer owns the lifecycle: an outdated snapshot stays alive for one extra frame
     * because cached draw commands may still reference it until the scene re-records.
     */
    val frame: MutableState<Image?> = mutableStateOf(null, neverEqualPolicy())
}

/**
 * Blur-aware fill color for content drawn on top of a [frostedBackground]: scales the color's
 * alpha by the blur opacity while blur is enabled so the glass reads through content fills,
 * and returns the color untouched when blur is off. Apply to fills only — text, icons, and
 * borders should stay fully opaque.
 */
@Composable
fun Color.frosted(): Color {
    val blurEnabled by Style.enableBlur.observe()
    if (!blurEnabled) return this
    val blurOpacity by Style.blurOpacity.observe()
    return copy(alpha = alpha * blurOpacity)
}

/**
 * Window background that shows the game blurred behind the window (frosted glass), falling
 * back to a plain [background] when blur is disabled or no game snapshot is available.
 *
 * The blur samples the real framebuffer pixels around the window edge, so the glass does not
 * darken toward its borders the way blurring only the clipped region would.
 */
fun Modifier.frostedBackground(color: Color, shape: Shape = RectangleShape): Modifier = composed {
    val blurEnabled by Style.enableBlur.observe()
    if (!blurEnabled) return@composed background(color, shape)

    val blurRadius by Style.blurRadius.observe()
    val blurOpacity by Style.blurOpacity.observe()
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }

    onGloballyPositioned { windowOrigin = it.positionInRoot() }
        .drawBehind {
            val game = GameBackdrop.frame.value
            val outline = shape.createOutline(size, layoutDirection, this)
            if (game == null) {
                drawOutline(outline, color)
                return@drawBehind
            }
            // Minecraft's framebuffer carries junk alpha, which the snapshot inherits. Drawing
            // onto an opaque black base makes the window fully opaque with the snapshot's rgb
            // kept as-is (premultiplied SrcOver), so windows below cannot ghost through.
            drawOutline(outline, Color.Black)
            withOutlineClip(outline) {
                drawGameBlurred(game, windowOrigin, blurRadius)
            }
            drawOutline(outline, color.copy(alpha = color.alpha * blurOpacity))
        }
}

private inline fun DrawScope.withOutlineClip(outline: Outline, crossinline block: DrawScope.() -> Unit) {
    when (outline) {
        is Outline.Rectangle -> clipRect(
            outline.rect.left,
            outline.rect.top,
            outline.rect.right,
            outline.rect.bottom
        ) { block() }
        is Outline.Rounded -> clipPath(Path().apply { addRoundRect(outline.roundRect) }) { block() }
        is Outline.Generic -> clipPath(outline.path) { block() }
    }
}

/**
 * Draws the fullscreen game snapshot through a blur filter, shifted so the window's clip
 * lands on the framebuffer pixels directly behind it. Root coordinates equal framebuffer
 * pixels because the scene is sized to the framebuffer.
 */
private fun DrawScope.drawGameBlurred(game: Image, windowOrigin: Offset, radiusDp: Float) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val sigma = radiusDp.dp.toPx() / 2f
        ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP).use { blur ->
            org.jetbrains.skia.Paint().use { paint ->
                paint.imageFilter = blur
                native.save()
                native.translate(-windowOrigin.x, -windowOrigin.y)
                native.drawImage(game, 0f, 0f, paint)
                native.restore()
            }
        }
    }
}
