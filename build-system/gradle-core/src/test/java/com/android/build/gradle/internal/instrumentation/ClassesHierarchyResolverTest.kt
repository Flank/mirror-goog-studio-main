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

package com.android.build.gradle.internal.instrumentation

import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils.resolvePlatformPath
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.objectweb.asm.Type
import java.io.File

@RunWith(Parameterized::class)
class ClassesHierarchyResolverTest(private val testMode: TestMode) {

    enum class TestMode {
        DIR,
        JAR
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "testMode_{0}")
        fun modes(): List<TestMode> {
            return listOf(TestMode.DIR, TestMode.JAR)
        }
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val androidJar = resolvePlatformPath("android.jar").toFile()

    private lateinit var classesHierarchyResolver: ClassesHierarchyResolver

    @Before
    fun setUp() {
        val inputDir = temporaryFolder.newFolder()

        val srcClasses = listOf(
            I::class.java,
            InterfaceExtendsI::class.java,
            ClassImplementsI::class.java,
            ClassWithNoInterfacesOrSuperclasses::class.java,
            ClassExtendsOneClassAndImplementsTwoInterfaces::class.java,
            ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces::class.java
        )

        val builder =
            ClassesHierarchyResolver.Builder(ClassesDataCache())
            .addDependenciesSources(androidJar)

        if (testMode == TestMode.DIR) {
            TestInputsGenerator.pathWithClasses(inputDir.toPath(), srcClasses)
            builder.addProjectSources(inputDir)
        } else {
            val inputJar = File(inputDir, "classes.jar")
            TestInputsGenerator.pathWithClasses(inputJar.toPath(), srcClasses)
            builder.addProjectSources(inputJar)
        }

        classesHierarchyResolver = builder.build()
    }

    @Test
    fun testClassesHierarchyData() {
        assertClassDataIsCorrect(
            clazz = Object::class.java,
            expectedAnnotations = emptyList(),
            expectedSuperclasses = emptyList(),
            expectedInterfaces = emptyList()
        )
        assertClassDataIsCorrect(
            clazz = I::class.java,
            expectedAnnotations = emptyList(),
            expectedSuperclasses = listOf(Object::class.java),
            expectedInterfaces = emptyList()
        )
        assertClassDataIsCorrect(
            clazz = InterfaceExtendsI::class.java,
            expectedAnnotations = listOf(Instrument::class.java),
            expectedSuperclasses = listOf(Object::class.java),
            expectedInterfaces = listOf(I::class.java)
        )
        assertClassDataIsCorrect(
            clazz = ClassImplementsI::class.java,
            expectedAnnotations = listOf(Instrument::class.java),
            expectedSuperclasses = listOf(Object::class.java),
            expectedInterfaces = listOf(I::class.java)
        )
        assertClassDataIsCorrect(
            clazz = ClassWithNoInterfacesOrSuperclasses::class.java,
            expectedAnnotations = emptyList(),
            expectedSuperclasses = listOf(Object::class.java),
            expectedInterfaces = emptyList()
        )
        assertClassDataIsCorrect(
            clazz = ClassExtendsOneClassAndImplementsTwoInterfaces::class.java,
            expectedAnnotations = emptyList(),
            expectedSuperclasses = listOf(
                ClassWithNoInterfacesOrSuperclasses::class.java,
                Object::class.java
            ),
            expectedInterfaces = listOf(I::class.java, InterfaceExtendsI::class.java)
        )
        assertClassDataIsCorrect(
            clazz = ClassExtendsAClassThatExtendsAnotherClassAndImplementsTwoInterfaces::class.java,
            expectedAnnotations = listOf(Instrument::class.java),
            expectedSuperclasses = listOf(
                ClassExtendsOneClassAndImplementsTwoInterfaces::class.java,
                ClassWithNoInterfacesOrSuperclasses::class.java,
                Object::class.java
            ),
            expectedInterfaces = listOf(I::class.java, InterfaceExtendsI::class.java)
        )
    }

    private fun assertClassDataIsCorrect(
        clazz: Class<*>,
        expectedAnnotations: List<Class<*>>,
        expectedSuperclasses: List<Class<*>>,
        expectedInterfaces: List<Class<*>>
    ) {
        val classData =
                classesHierarchyResolver.maybeLoadClassDataForClass(clazz.name)!!

        assertThat(classData.className).isEqualTo(clazz.name)

        assertThat(classesHierarchyResolver.getAnnotations(Type.getInternalName(clazz))).isEqualTo(
            expectedAnnotations.map(Class<*>::getName)
        )
        assertThat(classData.classAnnotations).isEqualTo(
            expectedAnnotations.map(Class<*>::getName)
        )

        assertThat(classesHierarchyResolver.getAllSuperClasses(Type.getInternalName(clazz))).isEqualTo(
            expectedSuperclasses.map(Class<*>::getName)
        )
        assertThat(classData.superClasses).isEqualTo(
            expectedSuperclasses.map(Class<*>::getName)
        )

        assertThat(classesHierarchyResolver.getAllInterfaces(Type.getInternalName(clazz))).isEqualTo(
            expectedInterfaces.map(Class<*>::getName)
        )
        assertThat(classData.interfaces).isEqualTo(
            expectedInterfaces.map(Class<*>::getName)
        )
    }
}
