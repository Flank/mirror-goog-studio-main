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

package com.android.build.gradle.integration.application

import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.Directory
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertNotNull

/**
 * Integration test that test resConfig(s) settings with full or pure splits.
 */
class SplitHandlingTest {

    @get:Rule
    val project = GradleTestProject.builder()
                .fromTestProject("combinedDensityAndLanguageSplits")
                .create()

    /**
     * It is not allowed to have density based splits and resConfig(s) with a density restriction.
     */
    @Test
    @Throws(IOException::class)
    fun testDensityInResConfigAndSplits() {
        TestFileUtils.appendToFile(
                project.buildFile,
               "android {\n"
               + "    defaultConfig {\n"
               + "        resConfig \"xxhdpi\"\n"
               + "    }\n"
               + "    splits {\n"
               + "        density {\n"
               + "            enable true\n"
               + "        }\n"
               + "        language {\n"
               + "            enable false\n"
               + "        }\n"
               + "    }\n"
               + "}\n"
               + "\n"
               + "dependencies {\n"
               + "    api 'com.android.support:appcompat-v7:" + SUPPORT_LIB_VERSION
               + "'\n"
               + "    api 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
               + "'\n"
               + "}\n")
        val failure = project.executeExpectingFailure("clean", "assembleDebug")
        val cause = getCause(failure?.cause)
        assertThat(cause?.message).contains("xxhdpi")
    }

    /**
     * It is not allowed to have density based splits and resConfig(s) with a density restriction.
     */
    @Test
    @Throws(IOException::class)
    fun testDensitySplitsAndLanguagesInResConfig() {
        TestFileUtils.appendToFile(
                project.buildFile,
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        resConfigs \"fr\", \"de\"\n"
                        + "    }\n"
                        + "    splits {\n"
                        + "        density {\n"
                        + "            enable true\n"
                        + "        }\n"
                        + "        language {\n"
                        + "            enable false\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    api 'com.android.support:appcompat-v7:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    api 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n")

        project.execute("clean", "assembleDebug")

        val apkOutputFolder = File(project.outputDir, "apk/debug")
        loadBuiltArtifacts(apkOutputFolder).elements.forEach { output ->
            assertThat(output.versionCode).isEqualTo(12)
            assertThat(output.versionName).isEqualTo("unique_string")
            val manifestContent = ApkSubject.getConfigurations(
                Paths.get(output.outputFile))
            assertThat(manifestContent).contains("fr")
            assertThat(manifestContent).contains("de")
            assertThat(manifestContent).doesNotContain("en")
        }
    }


    /**
     * Test language splits with resConfig(s) splits. The split settings will generate full or pure
     * splits for the specified languages, all other language mentioned in resConfig will be
     * packaged in the main APK. Remaining languages will be dropped.
     */
    @Test
    @Throws(IOException::class)
    fun testLanguagesInResConfigAndSplits() {
        TestFileUtils.appendToFile(
                project.buildFile,
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        resConfig \"es\"\n"
                        + "    }\n"
                        + "    splits {\n"
                        + "        language {\n"
                        + "            enable true\n"
                        + "            include \"fr,fr-rBE\", \"fr-rCA\", \"en\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    api 'com.android.support:appcompat-v7:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    api 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n")
        project.execute("clean", "assembleDebug")

        val apkOutputFolder = File(project.outputDir, "apk/debug")
        loadBuiltArtifacts(apkOutputFolder).elements
                .forEach { output ->
                    val manifestContent = ApkSubject.getConfigurations(Paths.get(output.outputFile))
                    assertThat(manifestContent).contains("es")
                    assertThat(manifestContent).doesNotContain("fr")
                    assertThat(manifestContent).doesNotContain("de")
                }
    }

    /**
     * Languages splits without resConfigs. Main APK will contain all languages not packaged in
     * pure splits.
     */
    @Test
    @Throws(IOException::class)
    fun testLanguagesInSplitsWithoutResConfigs() {
        TestFileUtils.appendToFile(
                project.buildFile,
                "android {\n"
                        + "    splits {\n"
                        + "        language {\n"
                        + "            enable true\n"
                        + "            include \"fr,fr-rBE\", \"fr-rCA\", \"en\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    api 'com.android.support:appcompat-v7:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    api 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n")
        project.execute("clean", "assembleDebug")

        val apkOutputFolder = File(project.outputDir, "apk/debug")
        val foundUniversal = AtomicBoolean(false)
        loadBuiltArtifacts(apkOutputFolder).elements
                .forEach { output ->
                    when(output.outputType) {
                        VariantOutputConfiguration.OutputType.SINGLE -> {
                            val manifestContent = ApkSubject.getConfigurations(
                                Paths.get(output.outputFile))
                            // all remaining languages are packaged in the main APK.
                            assertThat(manifestContent).contains("de")
                            assertThat(manifestContent).doesNotContain("fr")
                            assertThat(manifestContent).doesNotContain("en")

                        }
                        VariantOutputConfiguration.OutputType.ONE_OF_MANY -> {
                            // we don't do language based multi-apk so all languages should be packaged.
                            val manifestContent = ApkSubject.getConfigurations(
                                Paths.get(output.outputFile))
                            assertThat(manifestContent).contains("es")
                            assertThat(manifestContent).contains("fr")
                            assertThat(manifestContent).contains("de")
                        }
                        VariantOutputConfiguration.OutputType.UNIVERSAL -> {
                            if (foundUniversal.get()) {
                                fail("Found more than one Universal output")
                            }
                            foundUniversal.set(true)
                        }
                    }
                }
    }

    private fun getCause(t: Throwable?) : Throwable? {
        var cause = t
        while (cause?.cause != null && cause.cause != cause) {
            cause = cause.cause
        }
        return cause
    }

    private fun loadBuiltArtifacts(outputFolder: File): BuiltArtifacts {
        val directory = Mockito.mock(Directory::class.java)
        Mockito.`when`(directory.asFile).thenReturn(outputFolder)
        val builtArtifacts = BuiltArtifactsLoaderImpl().load(directory)
        assertNotNull(builtArtifacts)
        return builtArtifacts
    }
}
