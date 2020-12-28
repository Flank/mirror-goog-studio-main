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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Test behavior of [MergeNativeLibsTask]*/
class MergeNativeLibsTaskTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("multiproject").create()

    @Test
    fun testTaskSkippedWhenNoNativeLibs() {
        val result1 = project.executor().run("app:mergeDebugNativeLibs")
        assertThat(result1.skippedTasks).contains(":app:mergeDebugNativeLibs")
        // then test that the task does work after adding native libraries.
        createAbiFile(project.getSubproject(":baseLibrary"), ABI_ARMEABI_V7A, "foo.so")
        val result2 = project.executor().run("app:mergeDebugNativeLibs")
        assertThat(result2.didWorkTasks).contains(":app:mergeDebugNativeLibs")
    }

    private fun createAbiFile(
        project: GradleTestProject,
        abiName: String,
        libName: String
    ) {
        val abiFolder = File(project.getMainSrcDir("jniLibs"), abiName)
        FileUtils.mkdirs(abiFolder)
        MergeNativeLibsTaskTest::class.java.getResourceAsStream(
            "/nativeLibs/libhello-jni.so"
        ).use { inputStream ->
            File(abiFolder, libName).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
}
