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

import java.lang.RuntimeException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.Random
import java.util.UUID

/**
 * Test helper class. Used to generate a random instance of a given class.
 * Useful for automatically testing data classes when new fields are added.
 */
@Suppress("UNCHECKED_CAST")
class RandomInstanceGenerator {
    private val random = Random()
    private val factoryMap = mutableMapOf<Type, (RandomInstanceGenerator) -> Any>()

    /**
     * Add a new factory for the given type.
     */
    fun addFactory(type : Type, factory: (RandomInstanceGenerator) -> Any) : RandomInstanceGenerator {
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
        when (type) {
            Boolean::class.java -> return random.nextBoolean() as T
            String::class.java -> return UUID.randomUUID().toString() as T
            Int::class.java -> return random.nextInt() as T
        }

        val constructor = type.constructors
            .filter { !it.isSynthetic }
            .maxBy { it.parameterCount }!!
        val params = constructor.genericParameterTypes
            .map { syntheticOfType(it) }
            .toTypedArray()
        return constructor.newInstance(*params) as T
    }

    private fun syntheticOfType(type: Type): Any? {
        return when (type) {
            is Class<*> -> synthetic(type)
            is ParameterizedType -> when (type.rawType) {
                List::class.java -> {
                    listOf(syntheticOfType(type.actualTypeArguments[0]))
                }
                Map::class.java -> {
                    mapOf(
                        syntheticOfType(type.actualTypeArguments[0]) to
                                syntheticOfType(type.actualTypeArguments[1])
                    )
                }
                else -> throw RuntimeException(type.toString())
            }
            is WildcardType -> {
                if (type.upperBounds.isEmpty()) {
                    throw RuntimeException()
                }
                syntheticOfType(type.upperBounds[0])
            }
            else -> throw RuntimeException(type.typeName)
        }
    }
}