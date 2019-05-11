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
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.repository.Revision
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
    private val defaultSequenceLength = 50
    private val random = Random(192)
    private val factoryMap = mutableMapOf<Type, (RandomInstanceGenerator) -> Any>()

    init {
        // Revision can't handle negative values
        provide(Revision::class.java) { revision() }
        provide(String::class.java) { string() }
        provide(Int::class.java) { int() }
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
    }

    fun file() = File(string().trimEnd('/', '\\'))
    fun string() = (0 until sample(LIST_SIZE_DOMAIN)).map { sample(CHAR_DOMAIN) }.joinToString("")
    fun strings(sequenceLength : Int = defaultSequenceLength) = (0 until sequenceLength).map { string() }
    private fun humanReadableString() = (0 until sample(LIST_SIZE_DOMAIN)).map { sample(HUMAN_READABLE_CHAR_DOMAIN) }.joinToString("")
    fun humanReadableStrings(sequenceLength : Int = defaultSequenceLength) = (0 until sequenceLength).map { humanReadableString() }
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

    /**
     * Add a new factory for the given type.
     */
    fun provide(type : Type, factory: (RandomInstanceGenerator) -> Any) : RandomInstanceGenerator {
        factoryMap[type] = factory
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
            .maxBy { it.parameterCount }
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
        val HUMAN_READABLE_CHAR_DOMAIN = listOf(
            '*', '\'', '\"', '0', '.', ',', ';', '<', '>', '&', '{', '}',
            'a', 'A', '花', '_', 'I', '$', '[', ']', ' ', '\\', '/'
        )
        val CHAR_DOMAIN = listOf('\u0000', '\uffff') + HUMAN_READABLE_CHAR_DOMAIN
        val LIST_SIZE_DOMAIN = listOf(0, 1, 2, 3, 10, 50)
        val BOOLEAN_DOMAIN = listOf(true, false)
        val UNSIGNED_INT_DOMAIN = listOf(0, 1, 32, 64, 256, Int.MAX_VALUE)
        val INT_DOMAIN = UNSIGNED_INT_DOMAIN + UNSIGNED_INT_DOMAIN.map { -it }
    }
}