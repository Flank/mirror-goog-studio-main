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
import com.android.tools.appinspection.livestore.protocol.ValueType

sealed class ValueEntry<T>(val type: ValueType, initialValue: T) {
    private val listeners = mutableListOf<(T) -> Unit>()

    var value = initialValue
        set(value) {
            val constrainedValue = constrain(value)
            if (field != constrainedValue) {
                field = constrainedValue
                listeners.forEach { listener -> listener(field) }
            }
        }

    val valueAsString: String
        get() = valueToString(value)

    open val constraintAsString: String? = null

    protected open fun valueToString(value: T): String = value.toString()
    protected abstract fun valueFromString(strValue: String): T?

    protected open fun constrain(value: T): T = value
    fun updateFromString(strValue: String): Boolean {
        val newValue = valueFromString(strValue) ?: return false
        val prevValue = value
        value = newValue
        return prevValue != value
    }

    fun addListener(listener: (T) -> Unit) {
        listeners.add(listener)
    }
}

class StringValueEntry(value: String) : ValueEntry<String>(ValueType.STRING, value) {
    override fun valueFromString(strValue: String) = strValue
}

class BoolValueEntry(value: Boolean) : ValueEntry<Boolean>(ValueType.BOOL, value) {
    override fun valueFromString(strValue: String) = strValue.toBoolean()
}

class IntValueEntry constructor(value: Int, val range: IntRange?) : ValueEntry<Int>(ValueType.INT, value.clampTo(range)) {
    override val constraintAsString = range?.let { "${it.first} .. ${it.last}"}

    companion object {
        private fun Int.clampTo(range: IntRange?): Int {
            return if (range != null) this.coerceIn(range) else this
        }
    }

    override fun valueFromString(strValue: String) = strValue.toInt()
    override fun constrain(value: Int): Int = value.clampTo(range)
}

class FloatValueEntry constructor(value: Float, val range: ClosedRange<Float>?) : ValueEntry<Float>(ValueType.FLOAT, value.clampTo(range)) {
    override val constraintAsString = range?.let { "${it.start} .. ${it.endInclusive}"}

    companion object {
        private fun Float.clampTo(range: ClosedRange<Float>?): Float {
            return if (range != null) this.coerceIn(range) else this
        }
    }

    override fun valueFromString(strValue: String) = strValue.toFloat()
    override fun constrain(value: Float): Float = value.clampTo(range)
}

class EnumValueEntry<E : Enum<E>>(value: E) : ValueEntry<E>(ValueType.ENUM, value) {
    init {
        LiveStore.enumReferences.add(value::class.java)
    }

    override val constraintAsString: String = value::class.java.name

    override fun valueToString(value: E) = value.toString()
    override fun valueFromString(strValue: String): E = value::class.java.enumConstants.first { it.toString() == strValue }
}

class CustomValueEntry<T>(
    value: T,
    val fromString: (String) -> T?,
    val toString: (T) -> String,
    ideHint: IdeHint) : ValueEntry<T>(ValueType.CUSTOM, value) {

    override fun valueToString(value: T) = toString(value)
    override fun valueFromString(strValue: String) = fromString(strValue)
    override val constraintAsString = ideHint.takeUnless { it == IdeHint.NONE }?.toString()
}

