/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.fixtures

import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.JavaReflectionUtil
import java.lang.reflect.Constructor

/**
 * a fake [Instantiator] to used in tests.
 *
 * This just calls the constructor directly.
 *
 */
class FakeInstantiator: Instantiator {

    override fun <T : Any?> newInstance(theClass: Class<out T>, vararg constructorParams: Any?): T {
        @Suppress("UNCHECKED_CAST")
        val constructors: Array<out Constructor<T>> = theClass.declaredConstructors as Array<out Constructor<T>>

        val actualParamsTypes = getParamTypes(constructorParams)

        for (constructor in constructors) {
            if (checkCompatibility(actualParamsTypes, constructor.parameterTypes)) {
                return constructor.newInstance(*constructorParams)
            }
        }

        throw RuntimeException("Failed to find matching constructor for $actualParamsTypes")
    }

    private fun getParamTypes(params: Array<out Any?>): Array<Class<*>?> {
        val result = arrayOfNulls<Class<*>>(params.size)

        for (i in result.indices) {
            val param = params[i]
            if (param != null) {
                var pType: Class<*> = param.javaClass
                if (pType.isPrimitive) {
                    pType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(pType)
                }

                result[i] = pType
            }
        }

        return result
    }

    private fun checkCompatibility(argumentTypes: Array<Class<*>?>, parameterTypes: Array<Class<*>?>): Boolean {
        if (argumentTypes.size != parameterTypes.size) {
            return false
        }

        for (i in argumentTypes.indices) {
            val argumentType = argumentTypes[i]
            var parameterType: Class<*>? = parameterTypes[i]

            val primitive = parameterType?.isPrimitive ?: false
            if (primitive) {
                if (argumentType == null) {
                    return false
                }

                parameterType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(parameterType)
            }

            if (argumentType != null && !parameterType!!.isAssignableFrom(argumentType)) {
                return false
            }
        }

        return true
    }

}