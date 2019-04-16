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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import java.io.File

@Ignore("Disabled pending outcome of b/130363042")
class UnresolveableNdkVersionTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .fromTestApp(
            HelloWorldJniApp.builder()
                .withNativeDir("cpp")
                .useCppSource(true)
                .build()
        )
        .setCmakeVersion("3.10.4819442")
        .setWithCmakeDirInLocalProp(true)
        .create()

    @Before
    fun setup() {
        TestFileUtils.appendToFile(
            project.buildFile, """
                apply plugin: 'com.android.application'
                android.compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                android.ndkVersion '192'
                android.externalNativeBuild.cmake.path "src/main/cpp/CMakeLists.txt"
            """.trimIndent()
        )

        val cmakeLists = File(project.buildFile.parent, "src/main/cpp/CMakeLists.txt")
        TestFileUtils.appendToFile(
            cmakeLists, """
                cmake_minimum_required(VERSION 3.4.1)
                add_library(native-lib SHARED hello-jni.cpp)
                find_library(log-lib log)
                target_link_libraries(native-lib ${'$'}{log-lib})
            """.trimIndent()
        )
    }

    @Test
    fun testAndroidProjectModelHasNativeSyncIssues() {
        // Caution !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // Caution !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // The point of this test is to ensure that a sync error from C/C++ will get
        // reported to Android Studio. Don't make any change that would trigger this in a
        // way that's different from Android Studio just to get this test to pass.
        val androidProject = project.model().fetchAndroidProjectsAllowSyncIssues().onlyModel
        assertThat(androidProject.syncIssues).hasSize(1)
        // Caution !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // Caution !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    }
}