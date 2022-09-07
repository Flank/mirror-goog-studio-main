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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.IntegerOption
import com.android.testutils.apk.AndroidArchive.checkValidClassName
import com.android.testutils.apk.Dex
import com.android.testutils.truth.DexSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

class L8DexDesugarTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestApp(
        MinimalSubProject.app("com.example.test")).create()

    private val desugarClass = "Lj$/util/stream/Stream;"
    private val desugarClassCompanion = "Lj$/util/stream/Stream$-CC;"
    private val normalClass = "Lcom/example/test/BuildConfig;"

    @Before
    fun setUp() {
        project.buildFile.appendText("\n" +
            """
            android.compileOptions.coreLibraryDesugaringEnabled = true
            dependencies {
                coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
            }
        """.trimIndent()
        )
    }

    @Test
    fun testNativeMultiDexWithoutKeepRule() {
        normalSetUp()
        project.executor().run("assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        val desugarLibDex: Dex =
            getDexWithDesugarClass(desugarClass, apk.allDexes)
                ?: fail("Failed to find the dex with desugar lib classes")
        assertThat(desugarLibDex).doesNotContainClasses(normalClass)
    }

    @Test
    fun testLegacyMultidexMainDexIsNotDesugarLib() {
        normalSetUp()
        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.minSdkVersion = 19
        """.trimIndent()
        )
        project.executor().run("assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(apk).doesNotContainMainClass(desugarClass)
        assertThatApk(apk).hasSecondaryClass(desugarClass)
    }

    @Test
    fun testNativeMultiDexWithoutKeepRuleBundle() {
        normalSetUp()
        project.executor().run("bundleDebug")
        project.getBundle(GradleTestProject.ApkType.DEBUG).use {

            val desugarLibDex: Dex =
                getDexWithDesugarClass(desugarClass, it.getDexListForModule("base"))
                    ?: fail("Failed to find the dex with desugar lib classes")
            assertThat(desugarLibDex).doesNotContainClasses(normalClass)
        }
    }

    /**
     * Java 8 language desugaring is always required for core library desugaring.
     */
    @Test
    fun testLangDesugarPrerequisite() {
        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.minSdkVersion = 21
            android.compileOptions.sourceCompatibility 1.7
            android.compileOptions.targetCompatibility 1.7
        """.trimIndent()
        )
        // check error message in debug build
        val debugResult = project.executor().expectFailure().run("assembleDebug")
        TruthHelper.assertThat(debugResult.failureMessage).contains(
            "In order to use core library desugaring, please enable java 8 language desugaring " +
                    "with D8 or R8."
        )
        // check error message in release build
        val releaseResult =
            project.executor().expectFailure().run("clean", "assembleRelease")
        TruthHelper.assertThat(releaseResult.failureMessage).contains(
            "In order to use core library desugaring, please enable java 8 language desugaring " +
                    "with D8 or R8."
        )

        project.buildFile.appendText("\n" +
            """
            android.compileOptions.sourceCompatibility = JavaVersion.VERSION_1_8
            android.compileOptions.targetCompatibility = JavaVersion.VERSION_1_8
        """.trimIndent()
        )
        project.executor().run("assembleRelease")
    }

    /**
     * MultidexEnabled is required for core library desugaring when minSdkVersion is less than 21.
     */
    @Test
    fun testMultidexEnabledPrerequisite() {
        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.minSdkVersion = 19
            android.compileOptions.sourceCompatibility = JavaVersion.VERSION_1_8
            android.compileOptions.targetCompatibility = JavaVersion.VERSION_1_8
        """.trimIndent()
        )

        val result = project.executor().expectFailure().run("assembleDebug")
        TruthHelper.assertThat(result.failureMessage).contains(
            "In order to use core library desugaring, please enable multidex."
        )

        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.multiDexEnabled = true
        """.trimIndent()
        )
        project.executor().run("assembleDebug")
    }

    @Test
    fun testMissingCoreLibraryDependency() {
        normalSetUp()
        // make sure core library dependency is missing
        TestFileUtils.searchAndReplace(
            project.buildFile,
            """coreLibraryDesugaring "$DESUGAR_DEPENDENCY"""",
            "")
        // check error message when L8DexDesugarLibTransform runs
        var result = project.executor().expectFailure().run("assembleDebug")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(MISSING_DEPS_ERROR)
        }
        // check error message when L8DexDesugarLibTask runs
        result = project.executor().expectFailure().run("clean", "assembleRelease")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(MISSING_DEPS_ERROR)
        }
    }

    /** Regression test for b/204795033. */
    @Test
    fun testL8HandlesTargetDevice() {
        normalSetUp()
        project.buildFile.appendText("\n" +
                """
            android.defaultConfig.minSdkVersion = 21
        """.trimIndent()
        )

        project.executor()
                .with(IntegerOption.IDE_TARGET_DEVICE_API, 24)
                .run("assembleDebug")
        val apkApi24 = project.getApk(
                GradleTestProject.ApkType.DEBUG,
                GradleTestProject.ApkLocation.Intermediates)
        assertThatApk(apkApi24).doesNotContainClass(desugarClassCompanion)

        project.executor()
                .with(IntegerOption.IDE_TARGET_DEVICE_API, 23)
                .run("assembleDebug")
        val apkApi23 = project.getApk(
                GradleTestProject.ApkType.DEBUG,
                GradleTestProject.ApkLocation.Intermediates)
        assertThatApk(apkApi23).hasClass(desugarClassCompanion)
    }

    private fun normalSetUp() {
        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.minSdkVersion = 21
            android.defaultConfig.multiDexEnabled = true
            android.compileOptions.sourceCompatibility = JavaVersion.VERSION_1_8
            android.compileOptions.targetCompatibility = JavaVersion.VERSION_1_8
        """.trimIndent()
        )

    }

    private fun getDexWithDesugarClass(desugarClass: String, dexes: Collection<Dex>) : Dex? =
        dexes.find {
            checkValidClassName(desugarClass)
            it.classes.keys.contains(desugarClass)
        }

    companion object {
        private const val DESUGAR_DEPENDENCY
                = "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
        private const val MISSING_DEPS_ERROR = "coreLibraryDesugaring configuration contains no " +
                "dependencies. If you intend to enable core library desugaring, please add " +
                "dependencies to coreLibraryDesugaring configuration."
    }
}
