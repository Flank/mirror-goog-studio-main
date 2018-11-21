/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeTransformOutputs
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.google.common.truth.Truth.assertThat
import com.android.testutils.apk.Dex
import com.android.testutils.truth.DexSubject.assertThat
import com.android.testutils.truth.MoreTruth.assertThatDex
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.Type
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.pow

/** Tests for dexing artifact transform. */
class DexingTransformTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun testDexingJar() {
        val input = tmp.newFile("classes.jar")
        val dexingTransform = TestDexingTransform(input, parameters = TestDexingTransform.TestParameters(12, true))
        val outputs = FakeTransformOutputs(tmp)
        TestInputsGenerator.jarWithEmptyClasses(input.toPath(), listOf("test/A"))
        dexingTransform.transform(outputs)
        assertThatDex(outputs.outputDirectory.resolve("classes.dex"))
            .containsExactlyClassesIn(listOf("Ltest/A;"))
    }

    @Test
    fun testDexingDir() {
        val input = tmp.newFolder("classes")
        val dexingTransform = TestDexingTransform(input, parameters = TestDexingTransform.TestParameters(12, true))
        val outputs = FakeTransformOutputs(tmp)

        TestInputsGenerator.dirWithEmptyClasses(input.toPath(), listOf("test/A"))
        dexingTransform.transform(outputs)
        assertThatDex(outputs.outputDirectory.resolve("classes.dex"))
            .containsExactlyClassesIn(listOf("Ltest/A;"))
    }

    @Test
    fun testDexingBigJar() {
        val methodsPerClass = 200
        // more than 64K methods
        val totalMethods = (2.0.pow(16) + methodsPerClass).toInt()

        val input = tmp.newFile("classes.jar")
        ZipOutputStream(input.outputStream()).use {
            for (i in 0 until (totalMethods / methodsPerClass)) {
                val methodNames = (0 until methodsPerClass).map { "foo$it:()V" }
                val classContent = TestClassesGenerator.classWithEmptyMethods(
                    "test/A$i",
                    *methodNames.toTypedArray()
                )

                it.putNextEntry(ZipEntry("test/A$i.class"))
                it.write(classContent)
                it.closeEntry()
            }
        }
        val transform = TestDexingTransform(input, parameters = TestDexingTransform.TestParameters(12, true))
        val outputs = FakeTransformOutputs(tmp)
        transform.transform(outputs)

        assertThat(
            Dex(outputs.outputDirectory.resolve("classes.dex")).classes.size +
                    Dex(outputs.outputDirectory.resolve("classes2.dex")).classes.size
        ).isEqualTo(totalMethods / methodsPerClass)
    }

    @Test
    fun testDexingWithDesugaring() {
        val input = tmp.newFolder("classes")
        val classes =
            listOf(Animal::class.java, CarbonForm::class.java, Toy::class.java, Cat::class.java)
        TestInputsGenerator.pathWithClasses(input.toPath(), classes)
        val dexingTransform = TestDexingTransform(
            input,
            classpath = listOf(),
            parameters = TestDexingTransform.TestParameters(12, true, listOf()),
            desugaring = true
        )
        val outputs = FakeTransformOutputs(tmp)
        dexingTransform.transform(outputs)
        val dex = Dex(outputs.outputDirectory.resolve("classes.dex"))
        assertThat(dex).containsClassesIn(classes.map { Type.getDescriptor(it) })
        assertThat(dex.classes).hasSize(classes.size + 1)
        val synthesizedLambdas = dex.classes.keys.filter { it.contains("\$\$Lambda\$") }
        assertThat(synthesizedLambdas).hasSize(1)

    }

    @Test
    fun testDexingWithDesugaringBootclasspath() {
        val bootclasspath = tmp.newFile("bootclasspath.jar")
        TestInputsGenerator.pathWithClasses(
            bootclasspath.toPath(),
            listOf(Animal::class.java, CarbonForm::class.java, Toy::class.java)
        )

        val input = tmp.newFile("classes.jar")
        val classes = listOf(Cat::class.java)
        TestInputsGenerator.pathWithClasses(input.toPath(), classes)
        val dexingTransform = TestDexingTransform(
            input,
            classpath = listOf(),
            parameters = TestDexingTransform.TestParameters(12, true, listOf(bootclasspath)),
            desugaring = true
        )
        val outputs = FakeTransformOutputs(tmp)
        dexingTransform.transform(outputs)

        val dex = Dex(outputs.outputDirectory.resolve("classes.dex"))
        assertThat(dex).containsClassesIn(classes.map { Type.getDescriptor(it) })
        assertThat(dex.classes).hasSize(classes.size + 1)

        val synthesizedLambdas = dex.classes.keys.filter { it.contains("\$\$Lambda\$") }
        assertThat(synthesizedLambdas).hasSize(1)

    }


    private class TestDexingTransform(
        override val primaryInput: File,
        private val parameters: TestParameters,
        private val desugaring: Boolean = false,
        private val classpath: List<File> = listOf()
    ) : BaseDexingTransform() {

        override fun computeClasspathFiles() = classpath.map(File::toPath)

        override fun enableDesugaring() = desugaring

        class TestParameters(
            minSdkVersion: Int,
            debuggable: Boolean,
            bootClasspath: List<File> = listOf()
        ) : BaseDexingTransform.Parameters {
            override var debuggable = FakeGradleProperty(debuggable)
            override val minSdkVersion = FakeGradleProperty(minSdkVersion)
            override val bootClasspath = FakeConfigurableFileCollection(bootClasspath)
        }

        override fun getParameters(): Parameters {
            return parameters
        }

    }
}
