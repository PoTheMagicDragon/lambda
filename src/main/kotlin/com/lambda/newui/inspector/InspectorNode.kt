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

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

/**
 * One composable call in the inspected tree, as read back out of the slot table.
 *
 * [bounds] is in scene pixels (the same space pointer events arrive in), not dp.
 */
data class InspectorNode(
    /** Stable within a single snapshot; used as a list key. */
    val id: Int,
    /**
     * Structural identity of this node, built from the names and sibling indices of its
     * ancestors. Selection and expansion are keyed by this so they survive the periodic
     * rebuild of the tree, which [id] would not.
     */
    val path: String,
    val name: String,
    val bounds: IntRect,
    val sourceFile: String?,
    val lineNumber: Int,
    /** True when this call emitted a layout node rather than only wrapping other calls. */
    val isLayoutNode: Boolean,
    val parameters: List<InspectorParameter>,
    val modifiers: List<String>,
    val children: List<InspectorNode>,
) {
    val sourceLabel: String?
        get() = sourceFile?.let { if (lineNumber >= 0) "$it:$lineNumber" else it }

    /** Deepest descendant whose bounds contain [point], searched front-to-back. */
    fun deepestAt(point: IntOffset): InspectorNode? {
        for (child in children.asReversed()) {
            child.deepestAt(point)?.let { return it }
        }
        return if (!bounds.isEmpty && bounds.contains(point)) this else null
    }

    fun find(path: String): InspectorNode? {
        if (this.path == path) return this
        for (child in children) child.find(path)?.let { return it }
        return null
    }

    /** Paths of every ancestor of [path], so revealing a node can expand the chain above it. */
    fun ancestorsOf(path: String): List<String>? {
        if (this.path == path) return emptyList()
        for (child in children) {
            child.ancestorsOf(path)?.let { return it + this.path }
        }
        return null
    }

    fun count(): Int = 1 + children.sumOf { it.count() }
}

data class InspectorParameter(
    val name: String,
    val value: String,
    val fromDefault: Boolean,
)
