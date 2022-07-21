/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib.tools.debugging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Allows synchronized updates to the [value][MutableStateFlow.value] of a [MutableStateFlow].
 */
@JvmInline
internal value class AtomicStateFlow<T>(private val sourceFlow: MutableStateFlow<T>) {

    val value: T
        get() { return sourceFlow.value }

    /**
     * Atomically updates the value of [sourceFlow], calling [updater] to compute the new
     * [value][MutableStateFlow.value] given the current [value][MutableStateFlow.value].
     *
     * This method is **thread-safe** and can be safely invoked from concurrent coroutines without
     * external synchronization.
     */
    fun update(updater: (T) -> T) {
        synchronized(sourceFlow) {
            val currentValue = sourceFlow.value
            val newValue = updater(currentValue)
            if (newValue != currentValue) {
                sourceFlow.value = newValue
            }
        }
    }

    fun asStateFlow(): StateFlow<T> {
        return sourceFlow.asStateFlow()
    }
}
