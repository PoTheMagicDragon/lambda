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
import androidx.compose.ui.geometry.Size
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
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Paint as SkPaint

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
 * True when a window lies entirely outside the scene, so there is nothing to frost. The scene's
 * bounds are only trusted once known; before the first render everything draws.
 */
private fun isOffScreen(origin: Offset, size: Size): Boolean {
    val scene = ComposeRenderer.sceneSize.value
    return origin.x + size.width <= 0f ||
        origin.y + size.height <= 0f ||
        (scene.width > 0 && origin.x >= scene.width) ||
        (scene.height > 0 && origin.y >= scene.height)
}

/**
 * Blur filters are not free to build, and every frosted window in a frame shares one radius, so
 * the filter is built once and handed out until the setting changes.
 *
 * A superseded filter is dropped rather than closed: draw commands recorded on an earlier frame
 * may still reference it, and skiko's cleaner frees it once both they and this cache have let go.
 */
private var cachedBlur: ImageFilter? = null
private var cachedSigma = Float.NaN

private fun blurFilter(sigma: Float): ImageFilter {
    cachedBlur?.let { if (cachedSigma == sigma) return it }
    return ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP).also {
        cachedBlur = it
        cachedSigma = sigma
    }
}

/**
 * Reused across windows so each frosted draw does not allocate one. Skia copies a path into its
 * clip stack, and copy-on-write into a recorded picture, so rewinding this for the next window
 * cannot disturb a clip already in effect.
 */
private val roundedClipPath = Path()

/**
 * Ceiling on the blur's sigma in framebuffer pixels. The scene's density scales with framebuffer
 * height, so without this the same radius setting costs half again as much at 4K as at 1440p.
 * Only takes effect past ~2x density at a high radius.
 */
private const val MAX_BLUR_SIGMA = 32f

/**
 * Window background that shows the game *and any windows already drawn beneath this one*
 * blurred behind the window (frosted glass), falling back to a plain [background] when blur is
 * disabled or no game snapshot is available.
 *
 * The blur samples the real framebuffer pixels around the window edge, so the glass does not
 * darken toward its borders the way blurring only the clipped region would.
 */
fun Modifier.frostedBackground(color: Color, shape: Shape = RectangleShape): Modifier = composed {
    val blurEnabled by Style.enableBlur.observe()
    if (!blurEnabled) return@composed background(color, shape)

    val blurRadius by Style.blurRadius.observe()
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }

    onGloballyPositioned { windowOrigin = it.positionInRoot() }
        .drawBehind {
            // Collapsed settings panels stay in the tree at full size, parked far off-screen by
            // CategoryPanel until they animate open. Culling them here skips the glass work for
            // every module in every shown category, which is most of the frosted nodes in a frame.
            if (isOffScreen(windowOrigin, size)) return@drawBehind

            val game = GameBackdrop.frame.value
            val outline = shape.createOutline(size, layoutDirection, this)
            if (game == null) {
                drawOutline(outline, color)
                return@drawBehind
            }
            withOutlineClip(outline) {
                drawFrostedGlass(game, windowOrigin, blurRadius)
            }
            drawOutline(outline, color)
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
        is Outline.Rounded -> clipPath(
            roundedClipPath.apply {
                rewind()
                addRoundRect(outline.roundRect)
            }
        ) { block() }
        is Outline.Generic -> clipPath(outline.path) { block() }
    }
}

/**
 * Replaces the clipped region with frosted glass: the windows already drawn beneath this one,
 * blurred, over the blurred game, over opaque black.
 *
 * The lower windows are blurred by a *backdrop* layer, which filters the destination pixels the
 * scene has already drawn. Those pixels are only readable at [SkCanvas.saveLayer] time, so the
 * layer has to be built downward — the game and the black base go in afterwards with
 * [BlendMode.DST_OVER], which slides them underneath the blurred content instead of over it.
 * Skia samples the backdrop from the whole device rather than just the layer, so a window sitting
 * just outside this one's edge still bleeds in and the glass does not darken at its borders.
 *
 * The layer is composited back with [BlendMode.SRC] so the finished glass *replaces* the region:
 * blending it would mix the lower windows in a second time, and the sharp originals would show
 * through the blur.
 */
private fun DrawScope.drawFrostedGlass(game: Image, windowOrigin: Offset, radiusDp: Float) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val blur = blurFilter((radiusDp.dp.toPx() / 2f).coerceAtMost(MAX_BLUR_SIGMA))
        SkPaint().use { layerPaint ->
            layerPaint.blendMode = BlendMode.SRC
            native.saveLayer(
                SkCanvas.SaveLayerRec(Rect.makeWH(size.width, size.height), layerPaint, blur)
            )
        }
        SkPaint().use { paint ->
            paint.blendMode = BlendMode.DST_OVER
            paint.imageFilter = blur
            // Root coordinates equal framebuffer pixels because the scene is sized to the
            // framebuffer, so shifting by the window origin lands the fullscreen snapshot on
            // the pixels directly behind this window.
            native.save()
            native.translate(-windowOrigin.x, -windowOrigin.y)
            native.drawImage(game, 0f, 0f, paint)
            native.restore()
        }
        // Minecraft's framebuffer carries junk alpha, which the snapshot inherits. An opaque
        // black base keeps the snapshot's rgb as-is while forcing alpha to 1, so the glass is
        // fully opaque and the unblurred framebuffer underneath cannot ghost back through it.
        SkPaint().use { paint ->
            paint.blendMode = BlendMode.DST_OVER
            paint.color = BLACK
            native.drawPaint(paint)
        }
        native.restore()
    }
}

private const val BLACK = 0xFF000000.toInt()
