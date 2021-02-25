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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.application.testData.TestDependency
import com.android.build.gradle.integration.application.testData.TestTransform
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.AabSubject.Companion.assertThat
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.testutils.TestInputsGenerator
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.ByteStreams
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests for transforms that are passed through [StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS].
 */
class CustomClassTransformTest {

    @Rule
    @JvmField
    val project =
        GradleTestProject.builder().fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                .create()

    private fun assertProjectClassWasTransformed() {
        val transformedHelloWorldClass = FileUtils.join(
            project.intermediatesDir,
            "asm_instrumented_project_classes",
            "debug",
            "com",
            "example",
            "helloworld",
            "HelloWorld.class"
        )
        Truth.assertThat(transformedHelloWorldClass.readText(StandardCharsets.UTF_8)).endsWith("*")
    }

    @Test
    fun testCustomClassTransform() {
        val jarFile = createProfilersJar()
        // run with ENABLE_DEXING_ARTIFACT_TRANSFORM = true to ensure it gets disabled by
        // android.advanced.profiling.transforms=<path to jar file>
        project.executor()
            .with(StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS, jarFile.absolutePath)
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, true)
            .run("assembleDebug")
        assertProjectClassWasTransformed()
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(apk)
            .containsClass(
                "Lcom/android/build/gradle/integration/application/testData/TestDependency;")
        assertThatApk(apk).containsFile("lib/x86/foo.so")
    }

    @Test
    fun testBundle() {
        val jarFile = createProfilersJar()
        // run with ENABLE_DEXING_ARTIFACT_TRANSFORM = true to ensure it gets disabled by
        // android.advanced.profiling.transforms=<path to jar file>
        project.executor()
            .with(StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS, jarFile.absolutePath)
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, true)
            .run("bundleDebug")
        project.getBundle(GradleTestProject.ApkType.DEBUG).use {
            assertThat(it).containsClass("base",
                "Lcom/android/build/gradle/integration/application/testData/TestDependency;")
            assertThat(it).contains("base/lib/x86/foo.so")
        }
    }

    private fun createProfilersJar(): File {
        // Create a fake dependency jar file
        val dependencyJarFile = project.projectDir.resolve("fake_dependency.jar")
        TestInputsGenerator.pathWithClasses(
            dependencyJarFile.toPath(), listOf(TestDependency::class.java))

        // Create a fake dependency jar file with a native library
        val nativeDependencyJarFile = project.projectDir.resolve("fake_native_dependency.jar")
        ZipOutputStream(FileOutputStream(nativeDependencyJarFile)).use { zip ->
            val e = ZipEntry("lib/x86/foo.so")
            zip.putNextEntry(e)
            zip.write("foo".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        // Create a custom class transform jar file
        val name = TestTransform::class.java.name
        val entry = name.replace('.', '/') + ".class"
        val resource = "/$entry"
        val url = TestTransform::class.java.getResource(resource)
        val jarFile = File(project.projectDir, "transform.jar")
        ZipOutputStream(FileOutputStream(jarFile)).use { zip ->
            var e = ZipEntry(entry)
            zip.putNextEntry(e)
            url.openStream().use {
                ByteStreams.copy(it, zip)
            }
            zip.closeEntry()

            e = ZipEntry("META-INF/services/java.util.function.BiConsumer")
            zip.putNextEntry(e)
            zip.write(name.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            e = ZipEntry("dependencies/fake_dependency.jar")
            zip.putNextEntry(e)
            BufferedInputStream(FileInputStream(dependencyJarFile)).use {
                ByteStreams.copy(it, zip)
            }
            zip.closeEntry()

            e = ZipEntry("dependencies/fake_native_dependency.jar")
            zip.putNextEntry(e)
            BufferedInputStream(FileInputStream(nativeDependencyJarFile)).use {
                ByteStreams.copy(it, zip)
            }
            zip.closeEntry()
        }
        return jarFile
    }
}
