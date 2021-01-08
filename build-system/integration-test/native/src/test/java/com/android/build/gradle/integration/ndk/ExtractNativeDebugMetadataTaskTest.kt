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
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel.FULL
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel.NONE
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel.SYMBOL_TABLE
import com.android.build.gradle.internal.tasks.ExtractNativeDebugMetadataTask
import com.android.build.gradle.internal.tasks.PackageBundleTask
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
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

/** Test behavior of [ExtractNativeDebugMetadataTask] and [PackageBundleTask]*/
@RunWith(FilterableParameterized::class)
class ExtractNativeDebugMetadataTaskTest(private val debugSymbolLevel: DebugSymbolLevel?) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "debugSymbolLevel_{0}")
        fun params() = listOf(null, NONE, SYMBOL_TABLE, FULL)
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("dynamicApp")
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
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
                android.buildTypes.release.ndk.debugSymbolLevel '$debugSymbolLevel'

                """.trimIndent()
        )
    }

    @Test
    fun testNativeDebugMetadataInBundle() {
        // add native libs to app and feature modules
        listOf("app", "feature1", "feature2").forEach {
            val subProject = project.getSubproject(":$it")
            createUnstrippedAbiFile(subProject, ABI_ARMEABI_V7A, "$it.so")
            createUnstrippedAbiFile(subProject, ABI_INTEL_ATOM, "$it.so")
            createUnstrippedAbiFile(subProject, ABI_INTEL_ATOM64, "$it.so")
        }

        val bundleTaskName = getBundleTaskName("release")
        project.executor().run("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        assertThat(bundleFile).exists()

        val bundleEntryPrefix = "BUNDLE-METADATA/com.android.tools.build.debugsymbols"
        val expectedFullEntries = listOf(
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/app.so.dbg",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature2.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/app.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature2.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/app.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature2.so.dbg"
        )
        val expectedSymbolTableEntries = listOf(
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/app.so.sym",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature2.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/app.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature2.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/app.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature2.so.sym"
        )
        val expectedNativeLibEntries = listOf(
            "base/lib/$ABI_ARMEABI_V7A/app.so",
            "feature1/lib/$ABI_ARMEABI_V7A/feature1.so",
            "feature2/lib/$ABI_ARMEABI_V7A/feature2.so",
            "base/lib/$ABI_INTEL_ATOM/app.so",
            "feature1/lib/$ABI_INTEL_ATOM/feature1.so",
            "feature2/lib/$ABI_INTEL_ATOM/feature2.so",
            "base/lib/$ABI_INTEL_ATOM64/app.so",
            "feature1/lib/$ABI_INTEL_ATOM64/feature1.so",
            "feature2/lib/$ABI_INTEL_ATOM64/feature2.so"
        )
        val entryMap = ZipArchive.listEntries(bundleFile.toPath())
        assertThat(entryMap.keys).containsAtLeastElementsIn(expectedNativeLibEntries)
        when (debugSymbolLevel) {
            null -> {
                assertThat(entryMap.keys).containsNoneIn(expectedFullEntries)
                // the default debugSymbolLevel for release build is SYMBOL_TABLE
                assertThat(entryMap.keys).containsAtLeastElementsIn(expectedSymbolTableEntries)
            }
            NONE -> {
                assertThat(entryMap.keys).containsNoneIn(expectedFullEntries)
                assertThat(entryMap.keys).containsNoneIn(expectedSymbolTableEntries)
            }
            SYMBOL_TABLE -> {
                assertThat(entryMap.keys).containsNoneIn(expectedFullEntries)
                assertThat(entryMap.keys).containsAtLeastElementsIn(expectedSymbolTableEntries)
                // check that the .so.sym entries are larger than the .so entries.
                for (i in expectedNativeLibEntries.indices) {
                    assertThat(entryMap[expectedSymbolTableEntries[i]]?.uncompressedSize)
                        .isGreaterThan(entryMap[expectedNativeLibEntries[i]]?.uncompressedSize)
                }
            }
            FULL -> {
                assertThat(entryMap.keys).containsAtLeastElementsIn(expectedFullEntries)
                assertThat(entryMap.keys).containsNoneIn(expectedSymbolTableEntries)
                // check that the .so.dbg entries are larger than the .so entries.
                for (i in expectedNativeLibEntries.indices) {
                    assertThat(entryMap[expectedFullEntries[i]]?.uncompressedSize)
                        .isGreaterThan(entryMap[expectedNativeLibEntries[i]]?.uncompressedSize)
                }
            }
        }
    }

    @Test
    fun testNativeDebugMetadataInBundleWithAbiFilters() {
        // add native libs to app and feature modules
        listOf("app", "feature1", "feature2").forEach {
            val subProject = project.getSubproject(":$it")
            createUnstrippedAbiFile(subProject, ABI_ARMEABI_V7A, "$it.so")
            createUnstrippedAbiFile(subProject, ABI_INTEL_ATOM, "$it.so")
            createUnstrippedAbiFile(subProject, ABI_INTEL_ATOM64, "$it.so")
        }

        // Add abiFilters
        project.getSubproject(":app").buildFile.appendText(
            """
                android.defaultConfig.ndk.abiFilters "$ABI_INTEL_ATOM"

                """.trimIndent()
        )

        val bundleTaskName = getBundleTaskName("release")
        project.executor().run("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        assertThat(bundleFile).exists()

        val bundleEntryPrefix = "BUNDLE-METADATA/com.android.tools.build.debugsymbols"
        val expectedFullEntries = listOf(
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/app.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature2.so.dbg",
        )
        val expectedExcludedFullEntries = listOf(
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/app.so.dbg",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature2.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/app.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature2.so.dbg"
        )
        val expectedSymbolTableEntries = listOf(
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/app.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature2.so.sym",
        )
        val expectedExcludedSymbolTableEntries = listOf(
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/app.so.sym",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature2.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/app.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature2.so.sym"
        )
        val expectedNativeLibEntries = listOf(
            "base/lib/$ABI_INTEL_ATOM/app.so",
            "feature1/lib/$ABI_INTEL_ATOM/feature1.so",
            "feature2/lib/$ABI_INTEL_ATOM/feature2.so",
        )
        val expectedExcludedNativeLibEntries = listOf(
            "base/lib/$ABI_ARMEABI_V7A/app.so",
            "feature1/lib/$ABI_ARMEABI_V7A/feature1.so",
            "feature2/lib/$ABI_ARMEABI_V7A/feature2.so",
            "base/lib/$ABI_INTEL_ATOM64/app.so",
            "feature1/lib/$ABI_INTEL_ATOM64/feature1.so",
            "feature2/lib/$ABI_INTEL_ATOM64/feature2.so"
        )
        val entryMap = ZipArchive.listEntries(bundleFile.toPath())
        assertThat(entryMap.keys).containsNoneIn(expectedExcludedFullEntries)
        assertThat(entryMap.keys).containsNoneIn(expectedExcludedSymbolTableEntries)
        assertThat(entryMap.keys).containsAtLeastElementsIn(expectedNativeLibEntries)
        assertThat(entryMap.keys).containsNoneIn(expectedExcludedNativeLibEntries)
        when (debugSymbolLevel) {
            null -> {
                assertThat(entryMap.keys).containsNoneIn(expectedFullEntries)
                // the default debugSymbolLevel for release build is SYMBOL_TABLE
                assertThat(entryMap.keys).containsAtLeastElementsIn(expectedSymbolTableEntries)
            }
            NONE -> {
                assertThat(entryMap.keys).containsNoneIn(expectedFullEntries)
                assertThat(entryMap.keys).containsNoneIn(expectedSymbolTableEntries)
            }
            SYMBOL_TABLE -> {
                assertThat(entryMap.keys).containsNoneIn(expectedFullEntries)
                assertThat(entryMap.keys).containsAtLeastElementsIn(expectedSymbolTableEntries)
            }
            FULL -> {
                assertThat(entryMap.keys).containsAtLeastElementsIn(expectedFullEntries)
                assertThat(entryMap.keys).containsNoneIn(expectedSymbolTableEntries)
            }
        }
    }

    @Test
    fun testNoNativeDebugMetadataInBundleIfAlreadyStripped() {
        // add native libs to app and feature modules
        listOf("app", "feature1", "feature2").forEach {
            val subProject = project.getSubproject(":$it")
            createStrippedAbiFile(subProject, ABI_ARMEABI_V7A, "$it.so")
            createStrippedAbiFile(subProject, ABI_INTEL_ATOM, "$it.so")
            createStrippedAbiFile(subProject, ABI_INTEL_ATOM64, "$it.so")
        }

        val bundleTaskName = getBundleTaskName("release")
        project.executor().run("app:$bundleTaskName")

        val bundleFile = getApkFolderOutput("release").bundleFile
        assertThat(bundleFile).exists()

        val bundleEntryPrefix = "BUNDLE-METADATA/com.android.tools.build.debugsymbols"
        val expectedExcludedEntries = listOf(
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/app.so.dbg",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature2.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/app.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature2.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/app.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature1.so.dbg",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature2.so.dbg",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/app.so.sym",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature2.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/app.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM/feature2.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/app.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature1.so.sym",
            "$bundleEntryPrefix/$ABI_INTEL_ATOM64/feature2.so.sym"
        )
        val expectedNativeLibEntries = listOf(
            "base/lib/$ABI_ARMEABI_V7A/app.so",
            "feature1/lib/$ABI_ARMEABI_V7A/feature1.so",
            "feature2/lib/$ABI_ARMEABI_V7A/feature2.so",
            "base/lib/$ABI_INTEL_ATOM/app.so",
            "feature1/lib/$ABI_INTEL_ATOM/feature1.so",
            "feature2/lib/$ABI_INTEL_ATOM/feature2.so",
            "base/lib/$ABI_INTEL_ATOM64/app.so",
            "feature1/lib/$ABI_INTEL_ATOM64/feature1.so",
            "feature2/lib/$ABI_INTEL_ATOM64/feature2.so"
        )
        val entryMap = ZipArchive.listEntries(bundleFile.toPath())
        assertThat(entryMap.keys).containsAtLeastElementsIn(expectedNativeLibEntries)
        assertThat(entryMap.keys).containsNoneIn(expectedExcludedEntries)
    }

    @Test
    fun testErrorIfCollidingNativeLibs() {
        Assume.assumeTrue(debugSymbolLevel != NONE)
        // add native libs to app and feature modules
        listOf("app", "feature1").forEach {
            val subProject = project.getSubproject(":$it")
            createUnstrippedAbiFile(subProject, ABI_ARMEABI_V7A, "collide.so")
        }

        val bundleTaskName = getBundleTaskName("release")
        try {
            project.executor().run("app:$bundleTaskName")
        } catch (e: BuildException) {
            assertThat(Throwables.getRootCause(e).message).startsWith(
                "Multiple entries with same key"
            )
            return
        }
        fail("expected build error because of native libraries with same name.")
    }

    @Test
    fun testTaskSkippedWhenNoNativeLibs() {
        // first test that the task is skipped in all modules when there are no native libraries.
        val bundleTaskName = getBundleTaskName("release")
        val result1 = project.executor().run("app:$bundleTaskName")
        // if mode is NONE, the task should not be part of the task graph at all.
        // the default debugSymbolLevel is SYMBOL_TABLE for release builds.
        if (debugSymbolLevel == NONE) {
            assertThat(result1.tasks).containsNoneIn(
                listOf(
                    ":app:extractReleaseNativeDebugMetadata",
                    ":feature1:extractReleaseNativeDebugMetadata",
                    ":feature2:extractReleaseNativeDebugMetadata",
                    ":app:extractReleaseNativeSymbolTables",
                    ":feature1:extractReleaseNativeSymbolTables",
                    ":feature2:extractReleaseNativeSymbolTables"
                )
            )
            return
        }
        // otherwise, the task should be skipped in all modules
        val taskName = if (debugSymbolLevel == FULL) {
            "extractReleaseNativeDebugMetadata"
        } else {
            "extractReleaseNativeSymbolTables"
        }
        assertThat(result1.skippedTasks).containsAtLeastElementsIn(
            listOf(":app:$taskName", ":feature1:$taskName", ":feature2:$taskName")
        )
        // then test that the task only does work for modules with native libraries.
        createUnstrippedAbiFile(project.getSubproject(":feature1"), ABI_ARMEABI_V7A, "feature1.so")
        val result2 = project.executor().run("app:$bundleTaskName")
        assertThat(result2.skippedTasks).containsAtLeastElementsIn(
            listOf(":app:$taskName", ":feature2:$taskName")
        )
        assertThat(result2.didWorkTasks).containsAtLeastElementsIn(
            listOf(":feature1:$taskName")
        )
    }

    @Test
    fun testErrorWhenInvalidString() {
        Assume.assumeTrue(debugSymbolLevel == null)
        project.getSubproject(":app").buildFile.appendText(
            """
                android.defaultConfig.ndk.debugSymbolLevel 'INVALID'
                """.trimIndent()
        )
        try {
            project.executor().run("app:assembleDebug")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message).startsWith(
                "Unknown DebugSymbolLevel value 'INVALID'. Possible values are 'full', " +
                        "'symbol_table', 'none'."
            )
            return
        }
        fail("expected error because of invalid debugSymbolLevel value.")
    }

    @Test
    fun testNativeDebugMetadataInDefaultDebugBuild() {
        // assert that debug build does not have a default debugSymbolLevel
        Assume.assumeTrue(debugSymbolLevel == null)
        val bundleTaskName = getBundleTaskName("debug")
        createUnstrippedAbiFile(project.getSubproject(":feature1"), ABI_ARMEABI_V7A, "feature1.so")
        val result = project.executor().run("app:$bundleTaskName")
        val bundleFile = getApkFolderOutput("debug").bundleFile
        assertThat(bundleFile).exists()
        val bundleEntryPrefix = "/BUNDLE-METADATA/com.android.tools.build.debugsymbols"
        Zip(bundleFile).use { zip ->
            assertThat(zip.entries.map { it.toString() })
                .containsNoneIn(
                    listOf(
                        "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.dbg",
                        "$bundleEntryPrefix/$ABI_ARMEABI_V7A/feature1.so.sym"
                    )
                )
        }
        assertThat(result.didWorkTasks).doesNotContain(":feature1:extractDebugNativeSymbolTables")
        assertThat(result.didWorkTasks).doesNotContain(":feature1:extractDebugNativeDebugMetadata")
    }

    private fun getBundleTaskName(name: String): String {
        // query the model to get the task name
        val syncModels = project.model().fetchAndroidProjectsAllowSyncIssues()
        val appModel =
            syncModels.rootBuildModelMap[":app"] ?: fail("Failed to get sync model for :app module")

        val debugArtifact = appModel.getVariantByName(name).mainArtifact
        return debugArtifact.bundleTaskName ?: fail("Module App does not have bundle task name")
    }

    private fun getApkFolderOutput(variantName: String): AppBundleVariantBuildOutput {
        val outputModels = project.model()
            .fetchContainer(AppBundleProjectBuildOutput::class.java)

        val outputAppModel = outputModels.rootBuildModelMap[":app"]
                ?: fail("Failed to get output model for :app module")

        return outputAppModel.getOutputByName(variantName)
    }

    private fun createUnstrippedAbiFile(
        project: GradleTestProject,
        abiName: String,
        libName: String
    ) {
        val abiFolder = File(project.getMainSrcDir("jniLibs"), abiName)
        FileUtils.mkdirs(abiFolder)
        ExtractNativeDebugMetadataTaskTest::class.java.getResourceAsStream(
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
        ExtractNativeDebugMetadataTaskTest::class.java.getResourceAsStream(
            "/nativeLibs/libhello-jni.so"
        ).use { inputStream ->
            File(abiFolder, libName).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
