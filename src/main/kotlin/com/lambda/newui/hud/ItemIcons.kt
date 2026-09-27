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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import net.minecraft.util.Identifier
import org.jetbrains.skia.Image
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Item sprites as Compose images, for HUD elements that show items. Minecraft's own item
 * renderer cannot draw into the Skia surface, so the flat `textures/item` sprite is decoded from
 * the active resource packs instead; that covers armor and any other item with a 2D icon, while
 * 3D block items fall back to their `textures/block` face or nothing.
 *
 * Decoded once per texture and kept; lookups that found nothing are remembered too so a missing
 * texture is not searched for on every frame.
 */
object ItemIcons {
    private val cache = ConcurrentHashMap<Identifier, Optional<ImageBitmap>>()

    /** The item's icon, or null when it has no flat sprite. */
    fun sprite(itemId: Identifier): ImageBitmap? =
        load(Identifier.of(itemId.namespace, "textures/item/${itemId.path}.png"))
            ?: load(Identifier.of(itemId.namespace, "textures/block/${itemId.path}.png"))

    /** The untinted overlay layer dyeable items draw over their tinted base, if the item has one. */
    fun overlay(itemId: Identifier): ImageBitmap? =
        load(Identifier.of(itemId.namespace, "textures/item/${itemId.path}_overlay.png"))

    /** Any texture from the resource packs by its full id, such as `textures/misc/...png`. */
    fun texture(textureId: Identifier): ImageBitmap? =
        cache.computeIfAbsent(textureId) { Optional.ofNullable(decode(it)) }.orElse(null)

    /** Forgets every decoded sprite, so the next lookup reads the current resource packs. */
    fun clear() = cache.clear()

    private fun load(textureId: Identifier): ImageBitmap? =
        cache.computeIfAbsent(textureId) { Optional.ofNullable(decode(it)) }.orElse(null)

    private fun decode(textureId: Identifier): ImageBitmap? {
        val resource = mc.resourceManager.getResource(textureId).orElse(null) ?: return null
        return runCatching {
            resource.inputStream.use { Image.makeFromEncoded(it.readAllBytes()).toComposeImageBitmap() }
        }.onFailure { LOG.warn("Could not decode item sprite $textureId", it) }.getOrNull()
    }
}
