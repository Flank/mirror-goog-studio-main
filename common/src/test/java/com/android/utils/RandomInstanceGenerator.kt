/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.utils

import java.lang.Math.abs
import java.lang.reflect.Type
import java.util.Random

/**
 * Test helper class. Used to generate a random instance of a given class.
 * Useful for automatically testing data classes when new fields are added.
 */
class RandomInstanceGenerator {
    private val defaultSequenceLength = 100
    private val random = Random(192)
    private val factoryMap = mutableMapOf<Type, (RandomInstanceGenerator) -> Any>()

    init {
        provide(String::class.java) { string() }
    }
    private fun string() = (0 until sample(LIST_SIZE_DOMAIN)).joinToString("") { sample(SEGMENT_DOMAIN) }
    fun strings(sequenceLength : Int = defaultSequenceLength) = (0 until sequenceLength).map { string() }
    private fun <T> sample(domain : List<T>) = domain[abs(random.nextInt()) % domain.size]

    /**
     * Add a new factory for the given type.
     */
    private fun provide(type : Type, factory: (RandomInstanceGenerator) -> Any) : RandomInstanceGenerator {
        factoryMap[type] = factory
        return this
    }

    companion object {
        private val HUMAN_READABLE_SEGMENT_DOMAIN = listOf(
            "*", "\'", "\"", "0", ".", ",", ";", "<", ">", "&", "{", "}",
            "a", "A", "花", "_", "I", "$", "[", "]", " ", "\\", "/", "^",
            "\\\\", "//", "\n", "&", "&&", "bar&", "bar&&", "bar\"&\"",
            "bar\"&&\"", "bar^&", "bar^&^&", "^&", "\"&\"", "\t",
            "a\\\\\\\\\\\\b", "a\\\\\\\\\"b\" c", "?"
        )
        private val VERSION_DOMAIN = listOf(
            "17", "17.1", "17.1.2", "17.1.2-"
        )
        private val POSIX_FILE_PATHS_DOMAIN = listOf(
            "", "/", "/usr", "/usr/"
        )
        private val COMMAND_LINE_DOMAIN =
            listOf("B", "D", "H", "G", "W", "help", "?")
                .flatMap { expandCommandLine(it) }
                .flatMap { expandPropertyEquals(it) }
        private val SEGMENT_DOMAIN = listOf("\u0000", "\u0000") +
                HUMAN_READABLE_SEGMENT_DOMAIN +
                COMMAND_LINE_DOMAIN +
                VERSION_DOMAIN +
                POSIX_FILE_PATHS_DOMAIN
        private val LIST_SIZE_DOMAIN = listOf(0, 1, 2, 3, 10, 50)

        private fun expandCommandLine(value : String) =
            listOf("-$value", "--$value", " -$value", " --$value", "-$value ", "--$value ")

        private fun expandPropertyEquals(value : String) =
            listOf(value, "${value}C_TEST_WAS_RUN=", "$value C_TEST_WAS_RUN=",
                "${value}C_TEST_WAS_RUN =", "$value C_TEST_WAS_RUN =")
    }
}