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

import com.lambda.Lambda.LOG
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.impl.Library
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads the part of the Compose GUI that does not need the client thread: skiko's native
 * library, Skia's font manager, and the classes with slow static initialisers. Runs on a daemon
 * worker, so it adds nothing to startup time and never stalls the frame the main menu is
 * animating.
 *
 * Composition and anything touching the GL context are deliberately absent - both are bound to
 * the render thread, so [ComposeClickGui] still does those on an idle frame once [finished].
 *
 * Racing the client thread here is safe: if it reaches one of these classes first, JVM
 * class-initialisation locks make it wait rather than load anything twice.
 */
object ComposePreloader {
    /** Set once the worker has stopped, whether or not every step succeeded. */
    val finished = AtomicBoolean(false)

    private val started = AtomicBoolean(false)

    /**
     * Entry points whose own static setup is expensive. Loading a class does not resolve
     * everything it references, so this is not an attempt to enumerate the whole graph - just
     * the initialisers worth paying for early.
     */
    private val CLASS_NAMES = listOf(
        "androidx.compose.runtime.Recomposer",
        "androidx.compose.runtime.SnapshotStateKt",
        "androidx.compose.ui.node.LayoutNode",
        "androidx.compose.ui.graphics.Paint",
        "androidx.compose.ui.graphics.Canvas",
        "androidx.compose.ui.scene.CanvasLayersComposeSceneImpl",
        "androidx.compose.ui.text.TextStyle",
        "androidx.compose.ui.text.font.FontFamily",
        "androidx.compose.material3.MaterialTheme",
    )

    /** Starts the worker. Repeat calls are ignored. */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        Thread({
            try {
                preload()
            } finally {
                finished.set(true)
            }
        }, "Lambda Compose Preload").apply {
            isDaemon = true
            // The client thread's work matters more than finishing this early.
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private fun preload() {
        // Extracting and dlopen-ing the skiko native library is the single slowest step, and
        // every Skia class below needs it. Idempotent, so the client thread can also call it.
        step("skiko native library") { Library.staticLoad() }

        // Enumerating the system's fonts, which text layout would otherwise do mid-frame.
        step("font manager") { FontMgr.default }

        // Constructed rather than named: touching a class object does not run its initialiser,
        // whereas actually using one does.
        step("skia objects") {
            Paint().close()
            Path().close()
            ImageFilter.makeBlur(1f, 1f, FilterTileMode.CLAMP).close()
        }

        val loader = ComposePreloader::class.java.classLoader
        CLASS_NAMES.forEach { name ->
            step(name) { Class.forName(name, true, loader) }
        }
    }

    /**
     * A failed step only costs the stutter it was meant to avoid, so log it and carry on
     * rather than skipping the rest.
     */
    private inline fun step(what: String, block: () -> Unit) {
        runCatching(block).onFailure { LOG.debug("Compose preload step '$what' failed", it) }
    }
}
