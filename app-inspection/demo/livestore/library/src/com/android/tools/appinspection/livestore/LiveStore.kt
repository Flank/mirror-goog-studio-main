/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.appinspection.livestore

import com.android.tools.appinspection.livestore.protocol.IdeHint

/**
 * A key-value store whose entries will be exposed, via app inspection, to the IDE.
 *
 * This will allow an app to define a bunch of key-value entries with default values that can then
 * be modified in the Android Studio App Inspector tool window.
 *
 * @param name A human-readable name for this table. While it is not used by this library itself,
 *   the name will be shared with the IDE.
 */
@Suppress("UNCHECKED_CAST") // Key/Value types kept in sync using generic type matching
class LiveStore(@Suppress("unused") val name: String) {
    companion object {
        val enumReferences = mutableSetOf<Class<out Enum<*>>>()
    }

    private val _keyValues = mutableMapOf<String, ValueEntry<*>>()
    val keyValues: Map<String, ValueEntry<*>>
        get() = _keyValues

    private fun <T> add(name: String, valueEntry: ValueEntry<T>): ValueEntry<T> {
        _keyValues[name] = valueEntry
        return valueEntry
    }

    operator fun get(name: String): ValueEntry<*>? = _keyValues[name]

    @JvmOverloads
    fun addString(name: String, value: String = ""): ValueEntry<String> {
        return add(name, StringValueEntry(value))
    }

    @JvmOverloads
    fun addBool(name: String, value: Boolean = false): ValueEntry<Boolean> {
        return add(name, BoolValueEntry(value))
    }

    @JvmOverloads
    fun addInt(name: String, value: Int = 0, range: IntRange? = null): ValueEntry<Int> {
        return add(name, IntValueEntry(value, range))
    }

    @JvmOverloads
    fun addFloat(name: String, value: Float = 0f, range: ClosedRange<Float>? = null): ValueEntry<Float> {
        return add(name, FloatValueEntry(value, range))
    }

    fun <E : Enum<E>> addEnum(name: String, value: E): ValueEntry<E> {
        return add(name, EnumValueEntry(value))
    }

    /**
     * Add a custom value to the store of a type not directly supported.
     *
     * This value must be able to convert to / from a String, so that its value can be shown in and
     * editable from the IDE.
     */
    @JvmOverloads
    fun <T> addCustom(
        name: String,
        value: T,
        fromString: (String) -> T?,
        toString: (T) -> String = { it.toString() },
        ideHint: IdeHint = IdeHint.NONE
    ): ValueEntry<T> {
        return add(name, CustomValueEntry(value, fromString, toString, ideHint))
    }
}
