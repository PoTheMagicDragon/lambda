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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lambda.Lambda.mc
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.ComposeRenderer
import com.lambda.newui.frostedBackground
import com.lambda.newui.state.LambdaState.observe
import com.lambda.newui.theme.Radius
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val GRID_SIZE_DP = 4f

/** The settings panel never gets narrower than this, even for short labels. */
private val SETTINGS_MIN_WIDTH = 150.dp

/** ...nor wider than this, so one long label cannot swallow the screen. */
private val SETTINGS_MAX_WIDTH = 300.dp

private fun snapToGrid(value: Float, gridSize: Float) =
    (value / gridSize).roundToInt() * gridSize

@Composable
fun CategoryPanel(
    tag: ModuleTag,
    zIndex: Float = 0f,
    onFocus: () -> Unit = {},
    selectedSettingsModule: Module? = null,
    onSettingsModuleChange: (Module?) -> Unit = {}
) {
    val tagged = remember(tag) { ModuleRegistry.modules.filter { it.tag == tag } }
    val modules = tagged.filter { it.showInClickGui.observe().value }
    if (modules.isEmpty()) return

    var expanded by remember { mutableStateOf(true) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val yOffsets = remember { androidx.compose.runtime.mutableStateMapOf<String, Float>() }
    var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Kept as plain Floats so dragging the panel actually recomposes the settings panels;
    // positionInRoot() on the cached coordinates would silently go stale.
    var panelWindowX by remember { mutableStateOf(0f) }
    var panelWindowY by remember { mutableStateOf(0f) }

    // Root coordinates equal framebuffer pixels, so these are the screen bounds the settings
    // panels have to stay inside. The height must stay finite: it bounds a scrollable
    // container, and an infinite maximum height makes verticalScroll throw.
    val sceneSize = ComposeRenderer.sceneSize.value
    val screenHeightPx = sceneSize.height.takeIf { it > 0 }
        ?: mc.window.framebufferHeight.coerceAtLeast(1)
    val screenWidth = (sceneSize.width.takeIf { it > 0 }
        ?: mc.window.framebufferWidth.coerceAtLeast(1)).toFloat()
    val screenHeight = screenHeightPx.toFloat()

    val screenDensity = LocalDensity.current
    val maxSettingsHeight = with(screenDensity) { screenHeightPx.toDp() }
    // Anchors overlap the category list by 1dp on the joining edge, so the list's own border
    // covers the seam instead of leaving a double line.
    val rightAnchor = with(screenDensity) { 99.dp.toPx() }
    val leftAnchor = with(screenDensity) { 1.dp.toPx() }

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.ExtraSmall)
    // Square only where the panel butts against the category list, so the seam reads as a join.
    val settingsShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = Radius.Large,
        bottomEnd = Radius.Large,
        bottomStart = Radius.Large
    )
    // Mirrored for a panel sitting left of the category list: the seam moves to its top right.
    val flippedSettingsShape = RoundedCornerShape(
        topStart = Radius.Large,
        topEnd = 0.dp,
        bottomEnd = Radius.Large,
        bottomStart = Radius.Large
    )
    // Pulled off the card to fit on screen, so there is no seam left to square off.
    val liftedSettingsShape = RoundedCornerShape(Radius.Large)

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .zIndex(zIndex)
            .width(100.dp)
            .offset {
                IntOffset(
                    snapToGrid(dragOffset.x, GRID_SIZE_DP * density).roundToInt(),
                    snapToGrid(dragOffset.y, GRID_SIZE_DP * density).roundToInt()
                )
            }
    ) {
        // Settings Panels - Defined first so they render behind the main panel
        Box(modifier = Modifier.matchParentSize()) {
            modules.forEach { module ->
                key(module.name) {
                    val yOffset = yOffsets[module.name] ?: 0f
                    val isVisible = selectedSettingsModule == module
                    val transitionProgress by animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f,
                        animationSpec = spring(),
                        label = "SettingsAnimation"
                    )

                    var contentSize by remember { mutableStateOf(IntSize.Zero) }

                    // Growing down from the card would run off the bottom of the screen, so lift
                    // the panel by the overflow to pin its bottom edge to the screen instead. The
                    // lift stops at the top of the screen; past that the content scrolls.
                    val anchorInWindow = panelWindowY + yOffset
                    val overflow = (anchorInWindow + contentSize.height - screenHeight).coerceAtLeast(0f)
                    val lift = overflow.coerceAtMost(anchorInWindow.coerceAtLeast(0f))
                    val panelTop = yOffset - lift

                    // Opening to the right would run off that edge of the screen, so flip to the
                    // other side of the category list - but only when the panel actually fits
                    // there, otherwise it would just run off the left edge instead.
                    val flipped = contentSize.width > 0 &&
                        panelWindowX + rightAnchor + contentSize.width > screenWidth &&
                        panelWindowX + leftAnchor - contentSize.width >= 0f

                    val panelShape = when {
                        // Lifted off the card, so neither side has a seam left to square off.
                        lift > 0.5f -> liftedSettingsShape
                        flipped -> flippedSettingsShape
                        else -> settingsShape
                    }

                    Box(
                        modifier = Modifier
                            .zIndex(if (isVisible) 1f else 0f)
                            .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                            .offset {
                                if (transitionProgress == 0f) return@offset IntOffset(-9999, -9999)
                                // A flipped panel keeps its right edge pinned to the seam, so it
                                // opens outwards from the list rather than sliding in from the
                                // far side. Mirrors the width the layout below settles on.
                                val x = if (flipped) {
                                    leftAnchor - contentSize.width * transitionProgress
                                } else {
                                    rightAnchor
                                }
                                IntOffset(x.roundToInt(), panelTop.roundToInt())
                            }
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val panelHeight = panelCoordinates?.size?.height?.toFloat() ?: 9999f
                                val originalBottom = panelTop + placeable.height
                                val targetBottom = minOf(originalBottom, panelHeight)
                                val animatedBottom = targetBottom + (originalBottom - targetBottom) * transitionProgress

                                val currentHeight = maxOf(0, (animatedBottom - panelTop).roundToInt())
                                val currentWidth = (placeable.width * transitionProgress).roundToInt()

                                layout(currentWidth, currentHeight) {
                                    // Reveal from the seam: the right edge when flipped, so the
                                    // clipped-away part is the one furthest from the list.
                                    placeable.place(if (flipped) currentWidth - placeable.width else 0, 0)
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                // The panel widens to the longest label rather than wrapping it.
                                // widthIn clamps first, so width() resolves to the content's max
                                // intrinsic width coerced into that range.
                                .widthIn(min = SETTINGS_MIN_WIDTH, max = SETTINGS_MAX_WIDTH)
                                // Never taller than the screen; the content scrolls beyond that.
                                // Ahead of width() on purpose: wrapContentSize(unbounded) hands
                                // down an infinite maximum height, and width(IntrinsicSize.Max)
                                // forwards it into maxIntrinsicWidth(), where the scrollable
                                // content would reject it. Clamping first keeps both passes finite.
                                .heightIn(max = maxSettingsHeight)
                                .width(IntrinsicSize.Max)
                                .onSizeChanged { contentSize = it }
                                .clip(panelShape)
                                .frostedBackground(colors.surfaceVariant, panelShape)
                                .border(1.dp, colors.outline, panelShape)
                                .pointerInput(isVisible) {
                                    if (isVisible) {
                                        awaitEachGesture {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                if (event.type == PointerEventType.Press) {
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                SettingsTree(module.settingLayers)
                                ModuleConfigSettings(module)
                            }
                        }
                    }
                }
            }
        }

        // Main Panel
        key("MainPanel") {
            Column(
                modifier = Modifier
                    .onGloballyPositioned {
                        panelCoordinates = it
                        val root = it.positionInRoot()
                        panelWindowX = root.x
                        panelWindowY = root.y
                    }
                    .width(100.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    scope.launch { onFocus() }
                                }
                            }
                        }
                    }
                    .clip(shape)
                    .frostedBackground(colors.surface, shape)
                    .border(1.dp, colors.outline, shape)
            ) {
                Row(
                    modifier = Modifier
                        .background(colors.primaryContainer)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { scope.launch { onFocus() } },
                                onDrag = { _, dragAmount ->
                                    dragOffset += dragAmount
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    if (event.type == PointerEventType.Press) {
                                        if (event.buttons.isSecondaryPressed) {
                                            expanded = !expanded
                                            if (!expanded) {
                                                onSettingsModuleChange(null)
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tag.name,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer,
                        style = TextStyle(shadow = Shadow(color = colors.scrim, offset = Offset(2f, 2f)))
                    )
                }

                val expandProgress by animateFloatAsState(
                    targetValue = if (expanded) 1f else 0f,
                    animationSpec = spring(),
                    label = "ExpandAnimation"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val currentHeight = (placeable.height * expandProgress).roundToInt()
                            layout(placeable.width, currentHeight) {
                                placeable.place(0, currentHeight - placeable.height)
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                    ) {
                        modules.forEach { module ->
                            key(module.name) {
                                ModuleCard(
                                    module = module,
                                    isSettingsOpen = selectedSettingsModule == module,
                                    onRightClick = {
                                        if (selectedSettingsModule == module) {
                                            onSettingsModuleChange(null)
                                        } else {
                                            onSettingsModuleChange(module)
                                        }
                                    },
                                    onPositionChange = { cardCoordinates ->
                                        if (panelCoordinates != null && cardCoordinates.isAttached) {
                                            val pos = panelCoordinates!!.localPositionOf(cardCoordinates, Offset.Zero)
                                            yOffsets[module.name] = pos.y
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}