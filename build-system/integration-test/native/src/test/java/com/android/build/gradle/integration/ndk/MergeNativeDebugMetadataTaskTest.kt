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

package com.android.build.gradle.integration.ndk

import com.android.SdkConstants.ABI_ARMEABI_V7A
import com.android.SdkConstants.ABI_INTEL_ATOM
import com.android.SdkConstants.ABI_INTEL_ATOM64
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel.FULL
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel.NONE
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel.SYMBOL_TABLE
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.base.Throwables
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.BuildException
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.fail

/** Test behavior of [MergeNativeDebugMetadataTask]*/
@RunWith(FilterableParameterized::class)
class MergeNativeDebugMetadataTaskTest(private val debugSymbolLevel: DebugSymbolLevel?) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "debugSymbolLevel_{0}")
        fun params() = listOf(null, NONE, SYMBOL_TABLE, FULL)
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("dynamicApp")
            .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .create()

    @Before
    fun setUp() {
        // use lowercase and uppercase for different cases because both are supported
        val debugSymbolLevel =
            when (debugSymbolLevel) {
                null -> return
                NONE -> debugSymbolLevel.name
                SYMBOL_TABLE -> debugSymbolLevel.name.toLowerCase()
                FULL -> debugSymbolLevel.name.toUpperCase()
            }
        project.getSubproject(":app").buildFile.appendText(
            """
                android.buildTypes.debug.ndk.debugSymbolLevel '$debugSymbolLevel'
                """.trimIndent()
        )
    }

    @Test
    fun testNativeDebugSymbolsOutput() {
        // add native libs to app and feature modules
        listOf("app", "feature1", "feature2").forEach {
            val subProject = project.getSubproject(":$it")
            createUnstrippedAbiFile(subProject, ABI_ARMEABI_V7A, "$it.so")
            createUnstrippedAbiFile(subProject, ABI_INTEL_ATOM, "$it.so")
            createUnstrippedAbiFile(subProject, ABI_INTEL_ATOM64, "$it.so")
            createStrippedAbiFile(subProject, ABI_ARMEABI_V7A, "$it-stripped.so")
            createStrippedAbiFile(subProject, ABI_INTEL_ATOM, "$it-stripped.so")
            createStrippedAbiFile(subProject, ABI_INTEL_ATOM64, "$it-stripped.so")
        }

        project.executor().run("app:assembleDebug")

        val output = getNativeDebugSymbolsOutput("debug")
        if (debugSymbolLevel == null || debugSymbolLevel == NONE) {
            assertThat(output).doesNotExist()
            return
        }
        assertThat(output).exists()

        val expectedFullEntries = listOf(
            "/$ABI_ARMEABI_V7A/app.so.dbg",
            "/$ABI_ARMEABI_V7A/feature1.so.dbg",
            "/$ABI_ARMEABI_V7A/feature2.so.dbg",
            "/$ABI_INTEL_ATOM/app.so.dbg",
            "/$ABI_INTEL_ATOM/feature1.so.dbg",
            "/$ABI_INTEL_ATOM/feature2.so.dbg",
            "/$ABI_INTEL_ATOM64/app.so.dbg",
            "/$ABI_INTEL_ATOM64/feature1.so.dbg",
            "/$ABI_INTEL_ATOM64/feature2.so.dbg"
        )
        val expectedSymbolTableEntries = listOf(
            "/$ABI_ARMEABI_V7A/app.so.sym",
            "/$ABI_ARMEABI_V7A/feature1.so.sym",
            "/$ABI_ARMEABI_V7A/feature2.so.sym",
            "/$ABI_INTEL_ATOM/app.so.sym",
            "/$ABI_INTEL_ATOM/feature1.so.sym",
            "/$ABI_INTEL_ATOM/feature2.so.sym",
            "/$ABI_INTEL_ATOM64/app.so.sym",
            "/$ABI_INTEL_ATOM64/feature1.so.sym",
            "/$ABI_INTEL_ATOM64/feature2.so.sym"
        )
        val expectedExcludedEntries = listOf(
            "/$ABI_ARMEABI_V7A/app-stripped.so.dbg",
            "/$ABI_ARMEABI_V7A/feature1-stripped.so.dbg",
            "/$ABI_ARMEABI_V7A/feature2-stripped.so.dbg",
            "/$ABI_INTEL_ATOM/app-stripped.so.dbg",
            "/$ABI_INTEL_ATOM/feature1-stripped.so.dbg",
            "/$ABI_INTEL_ATOM/feature2-stripped.so.dbg",
            "/$ABI_INTEL_ATOM64/app-stripped.so.dbg",
            "/$ABI_INTEL_ATOM64/feature1-stripped.so.dbg",
            "/$ABI_INTEL_ATOM64/feature2-stripped.so.dbg",
            "/$ABI_ARMEABI_V7A/app-stripped.so.sym",
            "/$ABI_ARMEABI_V7A/feature1-stripped.so.sym",
            "/$ABI_ARMEABI_V7A/feature2-stripped.so.sym",
            "/$ABI_INTEL_ATOM/app-stripped.so.sym",
            "/$ABI_INTEL_ATOM/feature1-stripped.so.sym",
            "/$ABI_INTEL_ATOM/feature2-stripped.so.sym",
            "/$ABI_INTEL_ATOM64/app-stripped.so.sym",
            "/$ABI_INTEL_ATOM64/feature1-stripped.so.sym",
            "/$ABI_INTEL_ATOM64/feature2-stripped.so.sym"
        )
        Zip(output).use { zip ->
            assertThat(zip.entries.map { it.toString() }).containsNoneIn(expectedExcludedEntries)
            when (debugSymbolLevel) {
                SYMBOL_TABLE -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsNoneIn(expectedFullEntries)
                    assertThat(zip.entries.map { it.toString() })
                        .containsExactlyElementsIn(expectedSymbolTableEntries)
                }
                FULL -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsExactlyElementsIn(expectedFullEntries)
                    assertThat(zip.entries.map { it.toString() })
                        .containsNoneIn(expectedSymbolTableEntries)
                }
                else -> fail("Test should return early if not SYMBOL_TABLE or FULL")
            }
        }
    }

    @Test
    fun testErrorIfCollidingNativeLibs() {
        Assume.assumeTrue(debugSymbolLevel == SYMBOL_TABLE || debugSymbolLevel == FULL)
        // add native libs to app and feature modules
        listOf("app", "feature1").forEach {
            val subProject = project.getSubproject(":$it")
            createUnstrippedAbiFile(subProject, ABI_ARMEABI_V7A, "collide.so")
        }

        try {
            project.executor().run("app:assembleDebug")
        } catch (e: BuildException) {
            // message starts with, e.g., "Zip file '%s' already contains entry '%s'..."
            // See Zipflinger.ZipArchive for where this exception is thrown
            assertThat(Throwables.getRootCause(e).message).startsWith("Zip file")
            return
        }
        fail("expected build error because of native libraries with same name.")
    }

    @Test
    fun testTaskSkippedWhenNoNativeLibs() {
        val taskName = "mergeDebugNativeDebugMetadata"
        val output = getNativeDebugSymbolsOutput("debug")
        // first test that the task is skipped when there are no native libraries.
        val result1 = project.executor().run("app:assembleDebug")
        assertThat(output).doesNotExist()
        assertThat(result1.skippedTasks).contains(":app:$taskName")
        // then test that the task does work after adding native libraries.
        createUnstrippedAbiFile(project.getSubproject(":feature1"), ABI_ARMEABI_V7A, "foo.so")
        val result2 = project.executor().run("app:assembleDebug")
        // task still shouldn't do work if debugSymbolLevel null or NONE.
        if (debugSymbolLevel == null || debugSymbolLevel == NONE) {
            assertThat(output).doesNotExist()
            assertThat(result2.skippedTasks).contains(":app:$taskName")
            return
        }
        assertThat(output).exists()
        assertThat(result2.didWorkTasks).contains(":app:$taskName")
    }

    @Test
    fun testTaskRunsWhenNativeLibNameChanges() {
        Assume.assumeTrue(debugSymbolLevel == SYMBOL_TABLE || debugSymbolLevel == FULL)
        val taskName = "mergeDebugNativeDebugMetadata"
        val output = getNativeDebugSymbolsOutput("debug")
        // first add a native library, build, and check the output.
        createUnstrippedAbiFile(project.getSubproject(":feature1"), ABI_ARMEABI_V7A, "foo.so")
        val result1 = project.executor().run("app:assembleDebug")
        assertThat(output).exists()
        assertThat(result1.didWorkTasks).contains(":app:$taskName")
        Zip(output).use { zip ->
            when (debugSymbolLevel) {
                SYMBOL_TABLE -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsExactly("/$ABI_ARMEABI_V7A/foo.so.sym")
                }
                FULL -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsExactly("/$ABI_ARMEABI_V7A/foo.so.dbg")
                }
                else -> fail("Test assumes debugSymbolLevel is SYMBOL_TABLE or FULL")
            }
        }
        // then test that the task does work after changing the native library's name
        FileUtils.renameTo(
            File(
                project.getSubproject(":feature1").getMainSrcDir("jniLibs"),
                "$ABI_ARMEABI_V7A/foo.so"
            ),
            File(
                project.getSubproject(":feature1").getMainSrcDir("jniLibs"),
                "$ABI_ARMEABI_V7A/bar.so"
            )
        )
        val result2 = project.executor().run("app:assembleDebug")
        assertThat(output).exists()
        assertThat(result2.didWorkTasks).contains(":app:$taskName")
        Zip(output).use { zip ->
            when (debugSymbolLevel) {
                SYMBOL_TABLE -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsExactly("/$ABI_ARMEABI_V7A/bar.so.sym")
                }
                FULL -> {
                    assertThat(zip.entries.map { it.toString() })
                        .containsExactly("/$ABI_ARMEABI_V7A/bar.so.dbg")
                }
                else -> fail("Test assumes debugSymbolLevel is SYMBOL_TABLE or FULL")
            }
        }
    }

    private fun getNativeDebugSymbolsOutput(variantName: String): File {
        return File(
            project.getSubproject("app").buildDir,
            "/outputs/native-debug-symbols/$variantName/native-debug-symbols.zip"
        )
    }

    private fun createUnstrippedAbiFile(
        project: GradleTestProject,
        abiName: String,
        libName: String
    ) {
        val abiFolder = File(project.getMainSrcDir("jniLibs"), abiName)
        FileUtils.mkdirs(abiFolder)
        MergeNativeDebugMetadataTaskTest::class.java.getResourceAsStream(
            "/nativeLibs/unstripped.so"
        ).use { inputStream ->
            File(abiFolder, libName).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    private fun createStrippedAbiFile(
        project: GradleTestProject,
        abiName: String,
        libName: String
    ) {
        val abiFolder = File(project.getMainSrcDir("jniLibs"), abiName)
        FileUtils.mkdirs(abiFolder)
        MergeNativeDebugMetadataTaskTest::class.java.getResourceAsStream(
            "/nativeLibs/libhello-jni.so"
        ).use { inputStream ->
            File(abiFolder, libName).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
