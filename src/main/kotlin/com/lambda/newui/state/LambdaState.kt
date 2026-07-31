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

object LambdaState {
    private val settingStates = ConcurrentHashMap<Setting<*>, MutableState<*>>()
    private val enabledStates = ConcurrentHashMap<Module, MutableState<Boolean>>()

    private val shownTagsState: MutableState<Set<ModuleTag>> =
        mutableStateOf(ModuleTag.shownTags.toSet()).also { state ->
            ModuleTag.onShownTagsChanged { tags -> state.value = tags }
        }

    @Suppress("UNCHECKED_CAST")
    fun <T> Setting<T>.observe(): State<T> =
        settingStates.computeIfAbsent(this) { _ ->
            val state = mutableStateOf(value)
            onValueChangeUnsafe { _, to -> state.value = to }
            state
        } as State<T>

    fun Module.observeEnabled(): State<Boolean> =
        enabledStates.computeIfAbsent(this) { module ->
            val state = mutableStateOf(module.isEnabled)
            module.onToggleUnsafe { to -> state.value = to }
            state
        }

    fun observeShownTags(): State<Set<ModuleTag>> = shownTagsState
}
