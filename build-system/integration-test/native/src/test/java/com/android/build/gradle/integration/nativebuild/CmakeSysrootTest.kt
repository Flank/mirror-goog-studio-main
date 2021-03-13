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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.model.readCompileCommandsJsonBin
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * This test ensures that CMake server codepath returns sufficient information for Android Studio
 * to accept source files as targeting Android.
 */
class CmakeSysrootTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder()
      .fromTestApp(
        HelloWorldJniApp.builder()
          .withNativeDir("cpp")
          .useCppSource(true)
          .build()
      )
        .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .setWithCmakeDirInLocalProp(true)
        .create()

    @Before
    fun setup() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            apply plugin: 'com.android.application'
            android.compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
            android.ndkPath "${project.ndkPath}"
            android.externalNativeBuild.cmake.path "src/main/cpp/CMakeLists.txt"
            """.trimIndent()

        )

        val cmakeLists = File(project.buildFile.parent, "src/main/cpp/CMakeLists.txt")
        TestFileUtils.appendToFile(
            cmakeLists,
            """
            cmake_minimum_required(VERSION 3.4.1)
            add_library(native-lib SHARED hello-jni.cpp)
            find_library(log-lib log)
            target_link_libraries(native-lib ${'$'}{log-lib})
            """.trimIndent()
        )
    }

    @Test
    fun testThatFlagsLooksLikeAndroidProject() {
        val nativeModules = project.modelV2().fetchNativeModules(NativeModuleParams())
        val nativeModule = nativeModules.container.singleNativeModule
        assertThat(nativeModule.variants.map { it.name }).containsExactly("debug", "release")
        for (variant in nativeModule.variants) {
            for (abi in variant.abis) {
                val flags =
                  abi.sourceFlagsFile.readCompileCommandsJsonBin(nativeModules.normalizer)
                    .single().flags
                assertThat(flags.any { it.startsWith("--target") }).named("one of the following flags starts with '--target': $flags").isTrue()
                assertThat(flags.any { it.startsWith("--sysroot") }).named("one of the following flags starts with '--sysroot': $flags").isTrue()
            }
        }
    }
}
