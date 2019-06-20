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

/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.FileWriteMode
import com.google.common.io.Files
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test full splits are properly supported by unit testing packaging.
 */
class FullSplitsHandlingTest {

    @Test
    @Throws(Exception::class)
    fun checkDensityOnlySplits() {
        Files.asCharSink(sProject.buildFile, Charsets.UTF_8, FileWriteMode.APPEND).write(
            "android {\n"
                    + "    splits {\n"
                    + "        density {\n"
                    + "            enable true\n"
                    + "            reset()\n"
                    + "            include \"ldpi\", \"hdpi\"\n"
                    + "        }\n"
                    + "    }\n"
                    + "    testOptions {\n" +
                    "        unitTests.includeAndroidResources true\n" +
                    "    }"
                    + "}")

       runAndCheckBuild()
    }

    @Test
    @Throws(Exception::class)
    fun checkCombinedSplits() {
        Files.asCharSink(sProject.buildFile, Charsets.UTF_8, FileWriteMode.APPEND).write(
            "android {\n"
                    + "    splits {\n"
                    + "        abi {\n"
                    + "            enable true\n"
                    + "            reset()\n"
                    + "            include 'x86', 'armeabi-v7a'\n"
                    + "            universalApk false\n"
                    + "        }\n"
                    + "        density {\n"
                    + "            enable true\n"
                    + "            reset()\n"
                    + "            include \"ldpi\", \"hdpi\"\n"
                    + "        }\n"
                    + "    }\n"
                    + "    testOptions {\n" +
                    "        unitTests.includeAndroidResources true\n" +
                    "    }"
                    + "}"
        )

        runAndCheckBuild()
    }

    @Test
    @Throws(Exception::class)
    fun checkNoSplits() {
        Files.asCharSink(sProject.buildFile, Charsets.UTF_8, FileWriteMode.APPEND).write(
            "android {\n"
                    + "    testOptions {\n" +
                    "        unitTests.includeAndroidResources true\n" +
                    "    }"
                    + "}")

        runAndCheckBuild()
    }

    @Test
    @Throws(Exception::class)
    fun checkAbiOnlySplits() {
        Files.asCharSink(sProject.buildFile, Charsets.UTF_8, FileWriteMode.APPEND).write(
            "android {\n"
                    + "    splits {\n"
                    + "        abi {\n"
                    + "            enable true\n"
                    + "            reset()\n"
                    + "            include 'x86', 'armeabi-v7a'\n"
                    + "            universalApk false\n"
                    + "        }\n"
                    + "    }\n"
                    + "    testOptions {\n" +
                    "        unitTests.includeAndroidResources true\n" +
                    "    }"
                    + "}")

        runAndCheckBuild()

    }

    private fun runAndCheckBuild() {
        sProject.execute("clean", "packageDebugUnitTestForUnitTest")

        val resourcesForLocalTest =
            FileUtils.join(
                InternalArtifactType.APK_FOR_LOCAL_TEST.getOutputDir(sProject.buildDir),
                "debugUnitTest")

        assertThat(resourcesForLocalTest.isDirectory).isTrue()
        assertThat(resourcesForLocalTest.listFiles()).hasLength(1)
    }

    @get:Rule
    val sProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        FileUtils.createFile(sProject.file("src/main/jniLibs/x86/libprebuilt.so"), "")
        FileUtils.createFile(sProject.file("src/main/jniLibs/armeabi-v7a/libprebuilt.so"), "")
    }
}
