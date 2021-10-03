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

package com.android.build.gradle.internal.cxx

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.*
import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.EnvironmentVariable
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.repository.Revision
import org.gradle.api.file.FileCollection
import java.io.File
import java.lang.Math.abs
import java.lang.RuntimeException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.Random

/**
 * Test helper class. Used to generate a random instance of a given class.
 * Useful for automatically testing data classes when new fields are added.
 */
@Suppress("UNCHECKED_CAST")
class RandomInstanceGenerator {
    private val defaultSequenceLength = 100
    private val random = Random(192)
    private val factoryMap = mutableMapOf<Type, (RandomInstanceGenerator) -> Any?>()

    init {
        // Revision can't handle negative values
        provide(Revision::class.java) { revision() }
        provide(String::class.java) { string() }
        provide(Int::class.java) { int() }
        provide(java.lang.Integer::class.java) { int() }
        provide(Boolean::class.java) { boolean() }
        provide(AbiInfo::class.java) {
            val abi = synthetic(Abi::class.java)
            AbiInfo(
                abi = synthetic(Abi::class.java),
                bitness = if (abi.supports64Bits()) 64 else 32,
                isDefault = boolean(),
                isDeprecated = boolean()
            )
        }
        provide(File::class.java) { file() }
        provideNull(FileCollection::class.java)
        provide(BuildSettingsConfiguration::class.java) { buildSettings() }
    }
    fun <T> oneOf(vararg creators : () -> T) = creators[abs(int()) % creators.size]()
    fun <T> makeListOf(create : ()->T) = (0 until sample(LIST_SIZE_DOMAIN)).map { create() }
    fun file() = File(string().trimEnd('/', '\\'))
    fun string() = (0 until sample(LIST_SIZE_DOMAIN)).joinToString("") { sample(SEGMENT_DOMAIN) }
    fun strings(sequenceLength : Int = defaultSequenceLength) = (0 until sequenceLength).map { string() }
    fun cmakeSettingsJson() = sample(PARSABLE_CMAKE_SETTINGS_JSON_DOMAIN)
    fun cmakeSettingsJsons(sequenceLength : Int = defaultSequenceLength) = (0 until sequenceLength).map { cmakeSettingsJson() }

    fun nullableString(chanceOfNull : Int = 5) : String? {
        val roll = abs(int()) % 100
        return if (roll < chanceOfNull) null else string()
    }
    private fun unsignedInt() = sample(UNSIGNED_INT_DOMAIN)
    fun int() = sample(INT_DOMAIN)
    fun boolean() = sample(BOOLEAN_DOMAIN)
    fun revision() = Revision(unsignedInt(), unsignedInt(), unsignedInt(), unsignedInt())
    private fun <T> sample(domain : List<T>) = domain[abs(random.nextInt()) % domain.size]
    fun list(type: Type) = (0 until sample(LIST_SIZE_DOMAIN)).map { syntheticOfType(type) }
    fun set(type: Type) = list(type).toSet()
    fun map(key: Type, value: Type) =
        (0 until sample(LIST_SIZE_DOMAIN))
            .map { syntheticOfType(key) to syntheticOfType(value) }
            .toMap()
    private fun buildSettings() = BuildSettingsConfiguration(
        (0 until sample(LIST_SIZE_DOMAIN)).map {
            EnvironmentVariable(
                name = string(),
                value = sample(listOf(string(), null))
            )
        }
    )

    /**
     * Add a new factory for the given type.
     */
    fun provide(type : Type, factory: (RandomInstanceGenerator) -> Any) : RandomInstanceGenerator {
        factoryMap[type] = factory
        return this
    }

    /**
     * Add a new factory for the given type.
     */
    private fun provideNull(type : Type) : RandomInstanceGenerator {
        factoryMap[type] = { null }
        return this
    }


    /**
     * Invoke to create a random instance of T.
     */
    fun <T> synthetic(type: Class<T>): T {
        if (factoryMap.containsKey(type)) {
            return factoryMap[type]!!(this) as T
        }

        if (type.isEnum) {
            return type.enumConstants.toList().shuffled(random)[0]
        }

        val constructor = type.constructors
            .filter { !it.isSynthetic }
            .maxByOrNull { it.parameterCount }
            ?: throw RuntimeException("No usable constructor ${type.name}: " +
                    "${type.constructors.map { it.toGenericString() }}")

        val params = constructor.genericParameterTypes
            .map { syntheticOfType(it) }
            .toTypedArray()
        return constructor.newInstance(*params) as T
    }

    /**
     * Invoke to create a random instance of T.
     */
    fun <T> synthetics(
        type: Class<T>,
        sequenceLength : Int = defaultSequenceLength) =
        (0 until sequenceLength).map { synthetic(type) }


    private fun syntheticOfType(type: Type): Any? {
        return when (type) {
            is Class<*> -> synthetic(type)
            is ParameterizedType -> when (type.rawType) {
                List::class.java -> list(type.actualTypeArguments[0])
                Map::class.java -> map(type.actualTypeArguments[0], type.actualTypeArguments[1])
                Set::class.java -> set(type.actualTypeArguments[0])
                else -> throw RuntimeException(type.toString())
            }
            is WildcardType -> {
                if (type.upperBounds.isEmpty()) {
                    throw RuntimeException()
                }
                syntheticOfType(type.upperBounds[0])
            }
            else -> throw RuntimeException(type.typeName ?: type.toString())
        }
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
        private val NINJA_DOMAIN = listOf(
            "# comment", "include", "subninja", "build", "rule", "pool", "default",
            "description", "comment", "generator", "restat", "deps", "depfile",
            "CLEAN", "HELP", "ninja_required_version", "rules.ninja", "phony",
            "libnative-lib.so", "native-lib", "\n  ", "prop = x", "\n  restat = 1",
            "RULE input.txt\n", "build output.txt: ",
            "|", "||", "=", ":", "\n  ", "\\", "/", "C${'$'}:/"
        )
        private val COMMAND_LINE_DOMAIN =
            listOf("B", "D", "H", "G", "W", "help", "?")
                .flatMap { expandCommandLine(it) }
                .flatMap { expandPropertyEquals(it) }
        val SEGMENT_DOMAIN = listOf("\u0000", "\u0000") +
                HUMAN_READABLE_SEGMENT_DOMAIN +
                COMMAND_LINE_DOMAIN +
                VERSION_DOMAIN +
                POSIX_FILE_PATHS_DOMAIN +
                NINJA_DOMAIN
        val LIST_SIZE_DOMAIN = listOf(0, 1, 2, 3, 10, 50)
        val BOOLEAN_DOMAIN = listOf(true, false)
        val UNSIGNED_INT_DOMAIN = listOf(0, 1, 32, 64, 256, Int.MAX_VALUE)
        val INT_DOMAIN = UNSIGNED_INT_DOMAIN + UNSIGNED_INT_DOMAIN.map { -it }

        private fun expandCommandLine(value : String) =
            listOf("-$value", "--$value", " -$value", " --$value", "-$value ", "--$value ")

        private fun expandPropertyEquals(value : String) =
                listOf(value, "$value$C_TEST_WAS_RUN=", "$value $C_TEST_WAS_RUN=",
                    "$value$C_TEST_WAS_RUN =", "$value $C_TEST_WAS_RUN =")
    }
}
