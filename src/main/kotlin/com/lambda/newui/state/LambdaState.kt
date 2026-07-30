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

package com.lambda.newui.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.lambda.config.entries.Setting
import com.lambda.config.entries.Setting.Companion.onValueChangeUnsafe
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges Lambda's push-based config system onto Compose's snapshot state.
 *
 * ImGui re-read every property every frame, so nothing had to be observable. Compose
 * only redraws what it is told changed, so anything a composable reads must be backed
 * by snapshot state or the UI silently goes stale — a module toggled by keybind,
 * command, or config load would leave the GUI showing the old value.
 *
 * Use [observe] for any [Setting], and [observeEnabled] for a [Module]'s enabled flag
 * (which is deliberately not exposed as a [Setting], to keep writes going through
 * [Module.toggle] and its events).
 *
 * ### Why the states are cached rather than per-composition
 *
 * [Setting.listeners] has no removal API, and `Setting.setValue` iterates it without
 * synchronization, so registering per-composition (in a `DisposableEffect`) would both
 * leak a listener on every recomposition and risk a `ConcurrentModificationException`
 * against a concurrent config write. Instead exactly one listener is registered per
 * source, the first time it is observed, and the resulting state is cached forever.
 * Settings and modules are process-lifetime singletons, so this holds no memory the
 * client was not already holding.
 *
 * ### Threading
 *
 * Config writes arrive from arbitrary threads (commands, packet handlers, config load).
 * Snapshot state writes are safe from any thread — they take the global snapshot lock —
 * and the recomposer picks them up on its own dispatcher, so listeners write directly
 * with no marshalling.
 */
object LambdaState {
    private val settingStates = ConcurrentHashMap<Setting<*>, MutableState<*>>()
    private val enabledStates = ConcurrentHashMap<Module, MutableState<Boolean>>()

    private val shownTagsState: MutableState<Set<ModuleTag>> =
        mutableStateOf(ModuleTag.shownTags.toSet()).also { state ->
            ModuleTag.onShownTagsChanged { tags -> state.value = tags }
        }

    /**
     * Observes this setting's value as snapshot state. Reading the returned state from a
     * composable subscribes it to future changes.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> Setting<T>.observe(): State<T> =
        settingStates.computeIfAbsent(this) { _ ->
            val state = mutableStateOf(value)
            onValueChangeUnsafe { _, to -> state.value = to }
            // The listener is registered after the initial read, so re-sync in case the
            // value changed in between.
            state.value = value
            state
        } as State<T>

    /**
     * Observes [Module.isEnabled] as snapshot state.
     *
     * Goes through [Module.onToggleUnsafe] rather than the backing setting so that the
     * setting stays private and every write keeps flowing through
     * [Module.enable]/[Module.disable] and their events.
     */
    fun Module.observeEnabled(): State<Boolean> =
        enabledStates.computeIfAbsent(this) { module ->
            val state = mutableStateOf(module.isEnabled)
            module.onToggleUnsafe { to -> state.value = to }
            state.value = module.isEnabled
            state
        }

    /** Observes [ModuleTag.shownTags] as snapshot state. */
    fun observeShownTags(): State<Set<ModuleTag>> = shownTagsState
}
