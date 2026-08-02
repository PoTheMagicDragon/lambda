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

package com.lambda.newui.inspector

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.Lambda.LOG
import com.lambda.module.modules.client.LayoutInspector
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val HOVER_COLOR = Color(0x4D, 0xB6, 0xFF)
private val SELECT_COLOR = Color(0xFF, 0x8A, 0x3D)
private val PANEL_WIDTH = 320.dp

/**
 * The inspector proper: a highlight layer over the GUI, an optional pick mode that maps the
 * cursor to the deepest composable under it, and a docked panel with the tree and details.
 */
@Composable
fun InspectorOverlay(compositionData: CompositionData, settings: LayoutInspector.Settings) {
    val builder = remember { InspectorTree() }
    var snapshot by remember { mutableStateOf<InspectorNode?>(null) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var hoveredPath by remember { mutableStateOf<String?>(null) }
    var pickMode by remember { mutableStateOf(true) }
    var minimized by remember { mutableStateOf(false) }
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    // Relative to the panel's top-right anchor, so the resting position needs no measuring.
    var panelOffset by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(compositionData, settings.showAllGroups, settings.refreshMillis) {
        while (true) {
            // Reading the slot table inside the frame callback keeps it off the render
            // thread's back — the table is only consistent between recompositions.
            withFrameNanos {
                // A throw here would kill the effect and leave the inspector frozen with no
                // hint as to why, so keep the last good snapshot instead.
                runCatching { builder.build(compositionData, INSPECTED_ROOT, settings.showAllGroups) }
                    .onSuccess { snapshot = it }
                    .onFailure { LOG.warn("Failed to read the composable tree", it) }
            }
            delay(settings.refreshMillis)
        }
    }

    val tree = snapshot
    val selected = selectedPath?.let { tree?.find(it) }
    val hovered = hoveredPath?.let { tree?.find(it) }

    val textMeasurer = rememberTextMeasurer()

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            selected?.let { drawHighlight(it, SELECT_COLOR, settings.showLabels, textMeasurer) }
            hovered?.let { drawHighlight(it, HOVER_COLOR, settings.showLabels, textMeasurer) }
        }

        if (pickMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(tree, panelBounds) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val position = event.changes.firstOrNull()?.position ?: continue

                                // The panel sits above this layer; leave its events alone so its
                                // own controls keep working while picking.
                                if (panelBounds.contains(position)) continue

                                val point = IntOffset(position.x.roundToInt(), position.y.roundToInt())
                                val hit = tree?.deepestAt(point)

                                when (event.type) {
                                    PointerEventType.Move -> hoveredPath = hit?.path
                                    PointerEventType.Press -> {
                                        if (hit != null) {
                                            selectedPath = hit.path
                                            tree.ancestorsOf(hit.path)?.forEach { expanded[it] = true }
                                            pickMode = false
                                        }
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(panelOffset.x.roundToInt(), panelOffset.y.roundToInt()) }
                .padding(8.dp)
                .width(PANEL_WIDTH)
                .then(if (minimized) Modifier else Modifier.fillMaxHeight(0.94f))
                .onGloballyPositioned { panelBounds = it.boundsInWindow() },
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.outline),
            shape = RoundedCornerShape(6.dp),
        ) {
            Column(if (minimized) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
                InspectorToolbar(
                    nodeCount = tree?.count() ?: 0,
                    pickMode = pickMode,
                    minimized = minimized,
                    onTogglePick = {
                        pickMode = !pickMode
                        if (!pickMode) hoveredPath = null
                    },
                    onToggleMinimize = { minimized = !minimized },
                    onClear = {
                        selectedPath = null
                        hoveredPath = null
                    },
                    onDrag = { delta ->
                        // Clamp against the live panel rect so the whole panel stays reachable,
                        // including after minimizing shortens it.
                        val maxLeft = (rootSize.width - panelBounds.width).coerceAtLeast(0f)
                        val maxDown = (rootSize.height - panelBounds.height).coerceAtLeast(0f)
                        panelOffset = Offset(
                            x = (panelOffset.x + delta.x).coerceIn(-maxLeft, 0f),
                            y = (panelOffset.y + delta.y).coerceIn(0f, maxDown),
                        )
                    },
                )

                if (minimized) return@Column

                HorizontalDivider(color = colorScheme.outline)

                val rows = buildList { tree?.let { flattenInto(it, 0, expanded, this) } }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(rows, key = { it.node.path }) { row ->
                        TreeRow(
                            row = row,
                            isSelected = row.node.path == selectedPath,
                            isExpanded = expanded.isExpanded(row.node, row.depth),
                            onToggle = { expanded[row.node.path] = !expanded.isExpanded(row.node, row.depth) },
                            onSelect = { selectedPath = row.node.path },
                            onHover = { hovering ->
                                hoveredPath = when {
                                    hovering -> row.node.path
                                    // Only the row that owns the highlight may clear it.
                                    hoveredPath == row.node.path -> null
                                    else -> hoveredPath
                                }
                            },
                        )
                    }
                }

                HorizontalDivider(color = colorScheme.outline)

                NodeDetails(
                    node = selected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.9f)
                )
            }
        }
    }
}

@Composable
private fun InspectorToolbar(
    nodeCount: Int,
    pickMode: Boolean,
    minimized: Boolean,
    onTogglePick: () -> Unit,
    onToggleMinimize: () -> Unit,
    onClear: () -> Unit,
    onDrag: (Offset) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Only the title drags. Putting the gesture on the whole row would race the buttons
        // for the initial press.
        Text(
            text = "≡  Layout Inspector",
            style = typography.titleMedium,
            color = colorScheme.onPrimaryContainer,
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                },
        )
        Text(
            text = "$nodeCount",
            style = typography.bodySmall,
            color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        ToolbarButton(label = "Pick", active = pickMode, onClick = onTogglePick)
        ToolbarButton(label = "Clear", active = false, onClick = onClear)
        ToolbarButton(label = if (minimized) "+" else "–", active = false, onClick = onToggleMinimize)
    }
}

@Composable
private fun ToolbarButton(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = typography.bodySmall,
        color = if (active) colorScheme.onSecondaryContainer else colorScheme.onPrimaryContainer,
        modifier = Modifier
            .background(
                color = if (active) colorScheme.secondaryContainer else colorScheme.surfaceContainer,
                shape = RoundedCornerShape(3.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

private class TreeRowData(val node: InspectorNode, val depth: Int)

// Auto-expand through single-child chains, so the theme and provider wrappers between the
// root and the first real container don't each need a click.
private fun Map<String, Boolean>.isExpanded(node: InspectorNode, depth: Int): Boolean =
    this[node.path] ?: (depth < 3 || node.children.size == 1)

private fun flattenInto(
    node: InspectorNode,
    depth: Int,
    expanded: Map<String, Boolean>,
    out: MutableList<TreeRowData>,
) {
    out += TreeRowData(node, depth)
    if (!expanded.isExpanded(node, depth)) return
    node.children.forEach { flattenInto(it, depth + 1, expanded, out) }
}

@Composable
private fun TreeRow(
    row: TreeRowData,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onHover: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(hovered) { onHover(hovered) }

    val background = when {
        isSelected -> colorScheme.secondaryContainer
        hovered -> colorScheme.surfaceVariant
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .hoverable(interactionSource)
            .clickable(onClick = onSelect)
            .padding(start = (4 + row.depth * 10).dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (row.node.children.isEmpty()) " " else if (isExpanded) "▾" else "▸",
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onToggle),
        )
        Text(
            text = row.node.name,
            style = typography.bodySmall,
            color = if (row.node.isLayoutNode) colorScheme.onSurface else colorScheme.onSurfaceVariant,
        )
        if (!row.node.bounds.isEmpty) {
            Text(
                text = "${row.node.bounds.width}×${row.node.bounds.height}",
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun NodeDetails(node: InspectorNode?, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (node == null) {
            Text(
                text = "Pick a composable, or select one from the tree.",
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(node.name, style = typography.titleMedium, color = colorScheme.onSurface)

        node.sourceLabel?.let { label ->
            Text(
                text = label,
                style = typography.bodySmall,
                color = colorScheme.primary,
                modifier = Modifier.clickable {
                    runCatching { clipboard.setText(AnnotatedString(label)) }
                },
            )
        }

        val bounds = node.bounds
        DetailRow("Position", "${bounds.left}, ${bounds.top} px")
        DetailRow("Size", "${bounds.width} × ${bounds.height} px")
        with(density) {
            DetailRow("Size (dp)", "${bounds.width.toDp().value.roundToInt()} × ${bounds.height.toDp().value.roundToInt()} dp")
        }

        if (node.modifiers.isNotEmpty()) {
            SectionLabel("Modifiers")
            node.modifiers.forEach { name ->
                Text("· $name", style = typography.bodySmall, color = colorScheme.onSurfaceVariant)
            }
        }

        if (node.parameters.isNotEmpty()) {
            SectionLabel("Parameters")
            node.parameters.forEach { parameter ->
                DetailRow(
                    label = parameter.name,
                    value = parameter.value,
                    dim = parameter.fromDefault,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = colorScheme.onSurface,
        modifier = Modifier.padding(top = 5.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String, dim: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = typography.bodySmall,
            color = if (dim) colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun DrawScope.drawHighlight(
    node: InspectorNode,
    color: Color,
    showLabel: Boolean,
    textMeasurer: TextMeasurer,
) {
    val bounds = node.bounds
    if (bounds.isEmpty) return

    val topLeft = Offset(bounds.left.toFloat(), bounds.top.toFloat())
    val size = Size(bounds.width.toFloat(), bounds.height.toFloat())

    drawRect(color = color.copy(alpha = 0.16f), topLeft = topLeft, size = size)
    drawRect(color = color, topLeft = topLeft, size = size, style = Stroke(width = 3f))

    if (!showLabel) return

    val label = "${node.name}  ${bounds.width}×${bounds.height}"
    val layout = textMeasurer.measure(
        text = AnnotatedString(label),
        style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.SansSerif),
    )

    val padding = 4f
    val labelHeight = layout.size.height + padding * 2
    // Sit above the box when there is room, otherwise tuck inside its top edge.
    val labelTop = if (topLeft.y - labelHeight >= 0f) topLeft.y - labelHeight else topLeft.y
    val labelOrigin = Offset(topLeft.x, labelTop)

    drawRect(
        color = color,
        topLeft = labelOrigin,
        size = Size(layout.size.width + padding * 2, labelHeight),
    )
    drawText(
        textLayoutResult = layout,
        color = Color.Black,
        topLeft = Offset(labelOrigin.x + padding, labelOrigin.y + padding),
    )
}
