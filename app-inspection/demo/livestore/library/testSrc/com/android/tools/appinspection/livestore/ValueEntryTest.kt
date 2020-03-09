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
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ValueEntryTest {
    @Test
    fun stringValueMethodsWork() {
        val strEntry = StringValueEntry("")
        assertThat(strEntry.type).isEqualTo(ValueType.STRING)
        assertThat(strEntry.value).isEmpty()

        val values = mutableListOf<String>()
        strEntry.addListener { value -> values.add(value) }

        // Duplicates ignored
        strEntry.value = ""
        strEntry.value = "X"
        strEntry.value = "X"
        strEntry.value = "Y"
        strEntry.value = "Y"
        assertThat(values).containsExactly("X", "Y").inOrder()
    }

    @Test
    fun boolValueMethodsWork() {
        val boolEntry = BoolValueEntry(true)
        assertThat(boolEntry.type).isEqualTo(ValueType.BOOL)
        assertThat(boolEntry.value).isTrue()

        val values = mutableListOf<Boolean>()
        boolEntry.addListener { value -> values.add(value) }

        // Duplicates ignored
        boolEntry.value = true
        boolEntry.value = false
        boolEntry.value = false
        boolEntry.value = true
        boolEntry.value = true
        boolEntry.value = true
        boolEntry.value = false

        assertThat(values).containsExactly(false, true, false).inOrder()
    }

    @Test
    fun intValueMethodsWork() {
        val intEntry = IntValueEntry(50, 0..100)
        assertThat(intEntry.type).isEqualTo(ValueType.INT)
        assertThat(intEntry.value).isEqualTo(50)

        val values = mutableListOf<Int>()
        intEntry.addListener { value -> values.add(value) }

        intEntry.value = 50
        intEntry.value = 101 // Should get clamped to 100
        intEntry.value = 100
        intEntry.value = -100 // Clamped to 0
        intEntry.value = -50 // Clamped to 0
        intEntry.value = 0

        assertThat(values).containsExactly(100, 0).inOrder()
    }

    @Test
    fun floatValueMethodsWork() {
        val floatEntry = FloatValueEntry(50f, 0f..100f)
        assertThat(floatEntry.type).isEqualTo(ValueType.FLOAT)
        assertThat(floatEntry.value).isEqualTo(50f)

        val values = mutableListOf<Float>()
        floatEntry.addListener { value -> values.add(value) }

        floatEntry.value = 50f
        floatEntry.value = 101f // Should get clamped to 100
        floatEntry.value = 100f
        floatEntry.value = -100f // Clamped to 0
        floatEntry.value = -50f // Clamped to 0
        floatEntry.value = 0f

        assertThat(values).containsExactly(100f, 0f).inOrder()
    }

    enum class TimePeriod { AM, PM }

    @Test
    fun enumValueMethodsWork() {
        val enumEntry = EnumValueEntry(TimePeriod.AM)
        assertThat(enumEntry.type).isEqualTo(ValueType.ENUM)
        assertThat(enumEntry.value).isEqualTo(TimePeriod.AM)

        val values = mutableListOf<TimePeriod>()
        enumEntry.addListener { value -> values.add(value) }

        enumEntry.value = TimePeriod.AM
        enumEntry.value = TimePeriod.PM
        enumEntry.value = TimePeriod.PM
        enumEntry.value = TimePeriod.AM
        enumEntry.value = TimePeriod.AM

        assertThat(values).containsExactly(TimePeriod.PM, TimePeriod.AM).inOrder()
    }

    private fun ColorValueEntry(color: Color) =
        CustomValueEntry(color, { Color.fromString(it) }, { it.toString() }, IdeHint.COLOR)

    private val RED = Color(255, 0, 0)
    private val GREEN = Color(0, 255, 0)
    private val BLUE = Color(0, 0, 255)

    @Test
    fun customValueMethodsWork() {
        val colorEntry = ColorValueEntry(RED)
        assertThat(colorEntry.type).isEqualTo(ValueType.CUSTOM)
        assertThat(colorEntry.value).isEqualTo(RED)

        val values = mutableListOf<Color>()
        colorEntry.addListener { value -> values.add(value) }

        colorEntry.value = RED
        colorEntry.value = GREEN
        colorEntry.value = GREEN
        colorEntry.value = RED
        colorEntry.value = BLUE
        colorEntry.value = BLUE

        assertThat(values).containsExactly(GREEN, RED, BLUE).inOrder()
    }

    @Test
    fun stringValueStringConversionsWork() {
        val strEntry = StringValueEntry("X")
        assertThat(strEntry.valueAsString).isEqualTo("X")
        assertThat(strEntry.constraintAsString).isNull()

        assertThat(strEntry.updateFromString("X")).isFalse()
        assertThat(strEntry.updateFromString("Y")).isTrue()
        assertThat(strEntry.value).isEqualTo("Y")
    }

    @Test
    fun boolValueStringConversionsWork() {
        val boolEntry = BoolValueEntry(false)
        assertThat(boolEntry.valueAsString).isEqualTo("false")
        assertThat(boolEntry.constraintAsString).isNull()

        assertThat(boolEntry.updateFromString("false")).isFalse()
        assertThat(boolEntry.updateFromString("true")).isTrue()
        assertThat(boolEntry.value).isTrue()
    }

    @Test
    fun intValueStringConversionsWork() {
        // without a range
        IntValueEntry(123, null).let { intEntry ->
            assertThat(intEntry.valueAsString).isEqualTo("123")
            assertThat(intEntry.constraintAsString).isNull()

            assertThat(intEntry.updateFromString("123")).isFalse()
            assertThat(intEntry.updateFromString("321")).isTrue()
            assertThat(intEntry.value).isEqualTo(321)
        }

        // with a range
        IntValueEntry(50, 0..100).let { intEntry ->
            assertThat(intEntry.valueAsString).isEqualTo("50")
            assertThat(intEntry.constraintAsString).isEqualTo("0 .. 100")

            assertThat(intEntry.updateFromString("50")).isFalse()
            assertThat(intEntry.updateFromString("123")).isTrue()
            assertThat(intEntry.value).isEqualTo(100) // clamped!
        }
    }

    @Test
    fun floatValueStringConversionsWork() {
        // without a range
        FloatValueEntry(123.4f, null).let { floatEntry ->
            assertThat(floatEntry.valueAsString).isEqualTo("123.4")
            assertThat(floatEntry.constraintAsString).isNull()

            assertThat(floatEntry.updateFromString("123.4")).isFalse()
            assertThat(floatEntry.updateFromString("321")).isTrue()
            assertThat(floatEntry.value).isEqualTo(321f)
        }

        // with a range
        FloatValueEntry(432.1f, 0f..1000f).let { floatEntry ->
            assertThat(floatEntry.valueAsString).isEqualTo("432.1")
            assertThat(floatEntry.constraintAsString).isEqualTo("0.0 .. 1000.0")

            assertThat(floatEntry.updateFromString("432.1")).isFalse()
            assertThat(floatEntry.updateFromString("1234.5")).isTrue()
            assertThat(floatEntry.value).isEqualTo(1000f) // clamped!
        }
    }

    enum class Confirmation { YES, NO }

    @Test
    fun enumValueStringConversionsWork() {
        val enumEntry = EnumValueEntry(Confirmation.NO)
        assertThat(enumEntry.valueAsString).isEqualTo("NO")
        assertThat(enumEntry.constraintAsString).isEqualTo("com.android.tools.appinspection.livestore.ValueEntryTest\$Confirmation")

        assertThat(enumEntry.updateFromString("NO")).isFalse()
        assertThat(enumEntry.updateFromString("YES")).isTrue()
        assertThat(enumEntry.value).isEqualTo(Confirmation.YES)
    }

    @Test
    fun customValueStringConversionsWork() {
        val colorEntry = ColorValueEntry(Color(255, 255, 0))
        assertThat(colorEntry.valueAsString).isEqualTo("#FFFF00")
        assertThat(colorEntry.constraintAsString).isEqualTo(IdeHint.COLOR.toString())

        assertThat(colorEntry.updateFromString("Invalid")).isFalse()
        assertThat(colorEntry.updateFromString("#FFFF00")).isFalse()
        assertThat(colorEntry.updateFromString("#XXYYZZ")).isFalse()
        assertThat(colorEntry.updateFromString("#FFFFFFFF")).isFalse()
        assertThat(colorEntry.updateFromString("#FFFFFF")).isTrue()
        assertThat(colorEntry.updateFromString("#FFFFFF")).isFalse()
        assertThat(colorEntry.value).isEqualTo(Color(255, 255, 255))
    }
}