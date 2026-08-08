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

package com.lambda.module.tag

import com.lambda.module.tag.ModuleTag.Companion.onShownTagsChanged
import com.lambda.module.tag.ModuleTag.Companion.shownTags
import com.lambda.module.tag.ModuleTag.Companion.toggleTag
import com.lambda.util.Nameable

/**
 * The [ModuleTag] class represents a tag, that can be associated in any cardinality with a [Module].
 *
 * Tags are used to categorize and organize modules, making them easier to find.
 * They can be custom created as per the user's needs.
 *
 * Additionally, [ModuleTag] can be used to create groups of tags, which can be useful for creating new GUI windows.
 *
 * The companion object provides a set of predefined `ModuleTag` instances for common categories like "Combat",
 * "Movement", "Render", etc.
 *
 * @param name The name of the tag.
 */
data class ModuleTag(override val name: String) : Nameable {
    // Totally needs to be reworked
    // ToDo: Add registry for tags
    companion object {
        val COMBAT = ModuleTag("Combat")
        val MOVEMENT = ModuleTag("Movement")
        val RENDER = ModuleTag("Render")
        val PLAYER = ModuleTag("Player")
        val WORLD = ModuleTag("World")
        val CHAT = ModuleTag("Chat")
        val CLIENT = ModuleTag("Client")
        val NETWORK = ModuleTag("Network")
        val DEBUG = ModuleTag("Debug")
        val HUD = ModuleTag("Hud")

        val defaults = setOf(COMBAT, MOVEMENT, RENDER, PLAYER, WORLD, NETWORK, CHAT, CLIENT, HUD)

        private val mutableShownTags = defaults.toMutableSet()
        private val shownTagListeners = mutableListOf<(Set<ModuleTag>) -> Unit>()

        /**
         * Read-only view of the tags the ClickGui should display.
         *
         * Mutate only through [toggleTag] — direct mutation would skip the
         * [onShownTagsChanged] notification that retained-state GUIs rely on to
         * know when to redraw.
         */
        val shownTags: Set<ModuleTag> get() = mutableShownTags

        /**
         * Registers [block] to run whenever [shownTags] changes.
         *
         * Listeners are never removed, so only register from a permanent owner
         * (a [com.lambda.core.Loadable], an object, or a cached-per-key registry).
         */
        fun onShownTagsChanged(block: (Set<ModuleTag>) -> Unit) {
            shownTagListeners.add(block)
        }

        fun toggleTag(tag: ModuleTag) {
            if (mutableShownTags.contains(tag)) {
                mutableShownTags.remove(tag)
            } else {
                mutableShownTags.add(tag)
            }
            val snapshot = mutableShownTags.toSet()
            shownTagListeners.forEach { it(snapshot) }
        }

        fun isTagShown(tag: ModuleTag) = mutableShownTags.contains(tag)
    }
}
