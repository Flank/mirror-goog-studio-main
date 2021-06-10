/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle

import com.android.build.api.dsl.Bundle
import com.android.testutils.ApiTester
import com.google.common.collect.ImmutableSet
import com.google.common.io.Resources
import com.google.common.reflect.ClassPath
import com.google.common.reflect.Invokable
import org.junit.Test
import org.objectweb.asm.Type
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.util.stream.Collectors

class DecoratedApiChangesTest {

    @Test
    fun undecoratedClassesTest() {
        val dslInterfaces = getDslInterfaces().map { it.load() }

        getApiTester().checkApiElements {
            getApiElements(it, dslInterfaces)
        }
    }
}

val API_LIST_URL: URL = Resources.getResource(
    DecoratedApiChangesTest::class.java,
    "undecorated-dsl-api.txt"
)

private fun getSignature(method: Method): String {
    return method.name + ": " + Type.getMethodDescriptor(method)
}

fun getApiElements(klass: Class<*>, dslInterfaces: List<Class<*>>): List<String> {
    if (!Modifier.isPublic(klass.modifiers)) {
        return emptyList()
    }

    val superInterfaces = dslInterfaces.filter {
        it.isAssignableFrom(klass)
    }

    if (superInterfaces.isEmpty()) {
        return emptyList()
    }

    val interfaceMethods = superInterfaces.flatMap {
        it.methods.map(::getSignature)
    }.toSet()

    val implementedMethods = klass.methods
        .filter { interfaceMethods.contains(getSignature(it)) }
        .map { Invokable.from(it) }
        .filter { it.isPublic && !it.isAbstract }
        .mapNotNull { ApiTester.getApiElement(it) }

    val innerClassesElements = klass.declaredClasses.flatMap {
        getApiElements(it, dslInterfaces)
    }
    return mutableListOf<String>().apply {
        addAll(implementedMethods)
        addAll(innerClassesElements)
    }
}

fun getApiTester(): ApiTester {
    val classes = getApiClasses()
    return ApiTester(
        "The Android Gradle Plugin internal implementation classes.",
        classes,
        ApiTester.Filter.ALL,
        """
            The internal implementation classes have changed, either revert the change or re-run DecoratedApiChangesUpdater.main[] from the IDE to update the API file.
            DecoratedApiChangesUpdater will apply the following changes if run:

            """.trimIndent(),
        API_LIST_URL,
        ApiTester.Flag.OMIT_HASH
    )
}

fun getDslInterfaces(): List<ClassPath.ClassInfo> {
    return ClassPath.from(
        Bundle::class.java.classLoader
    ).getTopLevelClassesRecursive("com.android.build.api.dsl")
        .filter { !it.simpleName.endsWith("Test") }
}

private fun getApiClasses(): Set<ClassPath.ClassInfo> {
    val classPath = ClassPath.from(BaseExtension::class.java.classLoader)
    val builder = ImmutableSet.builder<ClassPath.ClassInfo>()
    builder.addAll(classPath.getTopLevelClasses("com.android.build.gradle"))
    builder.addAll(classPath.getTopLevelClasses("com.android.builder.core"))
    builder.addAll(classPath.getTopLevelClasses("com.android.build.gradle.internal.dsl"))
    builder.addAll(classPath.getTopLevelClassesRecursive("com.android.build.gradle.api"))
    builder.addAll(classPath.getTopLevelClasses("com.android.builder.signing").stream()
        .filter { classInfo: ClassPath.ClassInfo -> classInfo.simpleName == "DefaultSigningConfig" }
        .collect(Collectors.toList()))

    val allClasses = builder.build().filter { (!it.simpleName.endsWith("Test")
            && it.simpleName != "StableApiUpdater") }

    val dslInterfaces = getDslInterfaces()

    val dslInterfacesImplClasses = getImplClasses(allClasses, dslInterfaces)

    // return only leaf classes
    return dslInterfacesImplClasses.filter {
        val clazz = it.load()
        !dslInterfacesImplClasses.any { other ->
            val otherClazz = other.load()
            clazz != otherClazz && clazz.isAssignableFrom(otherClazz)
        }
    }.toSet()
}

private fun getImplClasses(
    allClasses: List<ClassPath.ClassInfo>,
    interfaces: List<ClassPath.ClassInfo>
): List<ClassPath.ClassInfo> {
    val interfaceClasses = interfaces.map { it.load() }

    return allClasses.filter {
        val clazz = it.load()
        interfaceClasses.any { iface ->
            iface.isAssignableFrom(clazz)
        }
    }
}
