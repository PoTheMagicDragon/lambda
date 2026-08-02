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

import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.tooling.data.ContextCache
import androidx.compose.ui.tooling.data.ParameterInformation
import androidx.compose.ui.tooling.data.SourceLocation
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.mapTree
import androidx.compose.ui.unit.IntRect
import kotlin.math.max
import kotlin.math.min

/**
 * Turns the click GUI's slot table into a readable composable tree.
 *
 * The raw slot table has a group for every call the Compose compiler emits — `remember`s,
 * inline function bodies, and other plumbing that has no meaning to someone looking at the
 * UI. Those groups are spliced out and their children hoisted to the nearest call that does
 * name a composable, which is what makes the result resemble a widget tree rather than a
 * compiler artifact. Passing `showAllGroups` keeps everything.
 *
 * Being inline is not on its own grounds to splice a group out: `Column`, `Row` and `Box` are
 * all inline, and they are the containers you most want to see. An inline call earns its place
 * by emitting a layout node. That alone would also keep the `Layout` / `ReusableComposeNode`
 * pair every container wraps its node in, so those two are named explicitly — they are stable
 * Compose internals rather than a guess about what looks noisy.
 */
@OptIn(UiToolingDataApi::class)
class InspectorTree {
    // Keyed by the raw sourceInfo strings, which repeat heavily across a tree, so this saves
    // re-parsing them on every rebuild.
    private val cache = ContextCache()

    fun build(data: CompositionData, rootMarker: String, showAllGroups: Boolean): InspectorNode? {
        val root = data.mapTree({ group, context, children: List<Draft> ->
            val name = context.name
            // Children are built first, so their own verdicts are already final here.
            val ownLayout = (group.node as? LayoutInfo)
                ?: children.firstNotNullOfOrNull { if (it.interesting) null else it.ownLayout }
            val interesting = showAllGroups || (
                name != null &&
                    name !in PLUMBING_NAMES &&
                    (!context.isInline || ownLayout != null)
                )
            Draft(
                name = name,
                bounds = context.bounds,
                location = context.location,
                interesting = interesting,
                ownLayout = ownLayout,
                // Reading parameters walks the group's slots, so skip it for groups that
                // will be spliced out anyway.
                parameters = if (interesting) context.parameters else emptyList(),
                children = children,
            )
        }, cache) ?: return null

        val marker = root.findByName(rootMarker) ?: return null
        val counter = intArrayOf(0)
        val children = childNodes(marker.children, parentPath = "", counter = counter, showAllGroups = showAllGroups)
        if (children.isEmpty()) return null

        return InspectorNode(
            id = counter[0]++,
            path = "",
            name = rootMarker,
            bounds = children.fold(EMPTY_BOUNDS) { acc, node -> acc.union(node.bounds) },
            sourceFile = null,
            lineNumber = -1,
            isLayoutNode = false,
            parameters = emptyList(),
            modifiers = emptyList(),
            children = children,
        )
    }

    private fun childNodes(
        drafts: List<Draft>,
        parentPath: String,
        counter: IntArray,
        showAllGroups: Boolean,
    ): List<InspectorNode> {
        val visible = mutableListOf<Draft>()
        collectVisible(drafts, visible)

        return visible.mapIndexedNotNull { index, draft ->
            val name = draft.displayName()
            val path = "$parentPath/$name[$index]"
            val children = childNodes(draft.children, path, counter, showAllGroups)
            val layoutInfo = draft.ownLayout

            // A named call that neither laid anything out nor contains one is a wrapper with
            // nothing to show; it only makes the tree taller.
            if (!showAllGroups && draft.bounds.isEmpty && children.isEmpty()) return@mapIndexedNotNull null

            InspectorNode(
                id = counter[0]++,
                path = path,
                name = name,
                bounds = draft.bounds,
                sourceFile = draft.location?.sourceFile,
                lineNumber = draft.location?.lineNumber ?: -1,
                isLayoutNode = layoutInfo != null,
                parameters = draft.parameters.map { it.toInspectorParameter() },
                modifiers = layoutInfo?.modifierNames().orEmpty(),
                children = children,
            )
        }
    }

    private fun collectVisible(drafts: List<Draft>, out: MutableList<Draft>) {
        for (draft in drafts) {
            if (draft.interesting) out += draft else collectVisible(draft.children, out)
        }
    }

    private class Draft(
        val name: String?,
        val bounds: IntRect,
        val location: SourceLocation?,
        val interesting: Boolean,
        /**
         * The layout node this call emitted, resolved through spliced-out groups only —
         * anything below another visible call belongs to that call instead.
         */
        val ownLayout: LayoutInfo?,
        val parameters: List<ParameterInformation>,
        val children: List<Draft>,
    ) {
        fun displayName(): String = name ?: if (ownLayout != null) "<LayoutNode>" else "<group>"

        fun findByName(target: String): Draft? {
            if (name == target) return this
            for (child in children) child.findByName(target)?.let { return it }
            return null
        }
    }

    private companion object {
        /**
         * The wrappers every container emits its layout node through. They are named calls
         * that own a node, so nothing else in the filter would drop them.
         */
        val PLUMBING_NAMES = setOf("Layout", "ReusableComposeNode", "ComposeNode")

        val EMPTY_BOUNDS = IntRect(0, 0, 0, 0)

        fun IntRect.union(other: IntRect): IntRect = when {
            isEmpty -> other
            other.isEmpty -> this
            else -> IntRect(
                left = min(left, other.left),
                top = min(top, other.top),
                right = max(right, other.right),
                bottom = max(bottom, other.bottom),
            )
        }

        fun LayoutInfo.modifierNames(): List<String> = runCatching {
            getModifierInfo().map { info ->
                val modifier = info.modifier
                (modifier as? InspectableValue)?.nameFallback
                    ?: modifier::class.simpleName?.removeSuffix("Element")?.removeSuffix("Node")
                    ?: "modifier"
            }
        }.getOrDefault(emptyList())

        fun ParameterInformation.toInspectorParameter() =
            InspectorParameter(name = name, value = formatValue(value), fromDefault = fromDefault)

        fun formatValue(value: Any?): String = when (value) {
            null -> "null"
            is Function<*> -> "λ"
            is String -> "\"$value\""
            else -> runCatching { value.toString() }.getOrDefault("<unprintable>").let {
                if (it.length > MAX_VALUE_LENGTH) it.take(MAX_VALUE_LENGTH - 1) + "…" else it
            }
        }

        const val MAX_VALUE_LENGTH = 120
    }
}
