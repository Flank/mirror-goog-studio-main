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

package com.android.build.gradle.internal.cxx.model

import com.android.repository.Revision
import com.google.common.reflect.ClassPath
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.test.fail

/**
 * This test mainly enforces package interface standards for
 * [com.android.build.gradle.internal.cxx.model] For example, it tries to ensure the model is
 * immutable, that it exposes data and not services, that vals follow naming conventions, etc.
 */

private val ALLOWED_MODEL_INTERFACES = setOf(
    CxxCmakeAbiModel::class.java,
    CxxVariantModel::class.java,
    CxxAbiModel::class.java,
    CxxCmakeModuleModel::class.java,
    CxxModuleModel::class.java
)

private val ALLOWED_PARAMETER_AND_RETURN_TYPES = setOf(
    List::class.java,
    Set::class.java,
    String::class.java,
    File::class.java,
    Revision::class.java
)

class CxxModuleModelTest {

    @Test
    fun `check all top level classes in model package`() {
        val loader = Thread.currentThread().contextClassLoader
        val packageName = CxxModuleModel::class.java.`package`.name
        for (info in ClassPath.from(loader).topLevelClasses) {
            if (info.name.startsWith(packageName)) {
                checkTopLevelClass(info.load())
            }
        }
    }

    @Test
    fun `check all top level interfaces in model package`() {
        ALLOWED_MODEL_INTERFACES.map { checkTopInterface(it) }
    }

    @Test
    fun `no allowed basic types are primitive`() {
        ALLOWED_PARAMETER_AND_RETURN_TYPES.map {
            assertThat(it.isPrimitive)
                .named(it.toGenericString())
                .isFalse()
        }
    }

    @Test
    fun `all model types are interface`() {
        ALLOWED_MODEL_INTERFACES.map {
            assertThat(it.isInterface)
                .named(it.toGenericString())
                .isTrue()
        }
    }

    @Test
    fun `all model types are public`() {
        ALLOWED_MODEL_INTERFACES.map {
            assertThat(Modifier.isPublic(it.modifiers))
                .named(it.toGenericString())
                .isTrue()
        }
    }

    private fun checkTopLevelMethodParameterOrReturnType(type: Type) {
        when(type) {
            is ParameterizedType -> {
                checkTopLevelMethodParameterOrReturnType(type.rawType)
                type.actualTypeArguments.map { checkTopLevelMethodParameterOrReturnType(it) }
            }
            else -> {
                if (ALLOWED_MODEL_INTERFACES.contains(type)) {
                    checkTopInterface(type as Class<*>)
                    return
                }
                if (ALLOWED_PARAMETER_AND_RETURN_TYPES.contains(type)) {
                    return
                }
                if (type is Class<*>) {
                    if (type.isEnum) {
                        return
                    }
                    if (type.isPrimitive) {
                        return
                    }
                }
                fail("Unrecognized type: ${type.javaClass} ${type.typeName}")
            }
        }
    }

    private fun checkTopInterface(type : Class<*>) {
        assertThat(ALLOWED_MODEL_INTERFACES).contains(type)
        assertThat(type.isInterface).isTrue()
        assertThat(type.fields.size).isEqualTo(0)
        type.methods.map { checkTopInterfaceMethod(it) }
    }

    private fun checkTopLevelClass(type : Class<*>) {
        if (type.isInterface) {
            checkTopInterface(type)
            return
        }
        if (type.toGenericString().contains("Test")) return
        if (type.toGenericString().contains("Mock")) return
        val typeName = type.toGenericString()
        val isFinal = Modifier.isFinal(type.modifiers)
        val isPublic = Modifier.isPublic(type.modifiers)
        when {
            // No need to check private because they can't expose functionality
            !isPublic -> { }
            isPublic && isFinal -> {
                // public final classes expose functionality outside of the module
                // so check these more closes.
                // public non-final is not allowed and is caught by else case below
                if (typeName.endsWith("Kt")) {
                    checkFunctionClass(type)
                    return
                }
                fail(type.toGenericString())
            }
            else -> {
                fail(type.toGenericString())
            }
        }
    }

    private fun checkTopInterfaceMethod(method: Method) {
        if (method.parameterCount != 0) fail("method ${method.name} has parameters")
        assertThat(Modifier.toString(method.modifiers))
            .isEqualTo("public abstract")

        checkMethodName(method)
        checkTopLevelMethodParameterOrReturnType(method.genericReturnType)
    }

    private fun checkMethodName(method: Method) {
        when(method.returnType) {
            File::class.java -> {
                assertThat(method.name.endsWith("Folder") ||
                        method.name.endsWith("File") ||
                        method.name.endsWith("Exe"))
                    .named("vals with File type must end with Folder, File, or Exe: " +
                            method.toGenericString()
                    )
                    .isTrue()
            }
            Boolean::class.java -> {
                assertThat(method.name.startsWith("is") ||
                        method.name.startsWith("has"))
                    .named("vals with Boolean type must start with 'is' or 'has': " +
                            method.toGenericString()
                    )
                    .isTrue()
                assertThat(method.name.endsWith("Enabled"))
                    .named("vals with Boolean type must end with 'Enabled': " +
                            method.toGenericString()
                    )
                    .isTrue()
            }
            List::class.java -> {
                assertThat(method.name.endsWith("List"))
                    .named("vals with List type must end with 'List': " +
                            method.toGenericString()
                    )
                    .isTrue()
            }
            Set::class.java -> {
                assertThat(method.name.endsWith("Set"))
                    .named("vals with Set type must end with 'Set': " +
                            method.toGenericString()
                    )
                    .isTrue()
            }
        }
    }

    private fun checkStaticMethod(method: Method) {
        val modifiers = Modifier.toString(method.modifiers)
        when(modifiers) {
            "public static final",
            "public static" -> {}
            else -> {
                assertThat(false)
                    .named(method.declaringClass.toGenericString() + " " + method.toGenericString())
                    .isTrue()
            }
        }

        // Parameters are not checked and anything is allowed
        checkTopLevelMethodParameterOrReturnType(method.genericReturnType)
    }

    private fun checkFunctionClass(type : Class<*>) {
        assertThat(type.fields.filter { Modifier.isPublic(it.modifiers) }).isEmpty()
        assertThat(type.classes.filter { Modifier.isPublic(it.modifiers) }).isEmpty()
        assertThat(type.constructors.filter { Modifier.isPublic(it.modifiers) }).isEmpty()
        type.methods
            .filter { Modifier.isPublic(it.modifiers) }
            .filter { it.declaringClass != Object::class.java }
            .onEach { checkStaticMethod(it) }
    }
}