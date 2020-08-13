/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.PLATFORM_DARWIN
import com.android.SdkConstants.PLATFORM_LINUX
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.model.FileNormalizer
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.nio.charset.StandardCharsets

class V2NativeModelTest : ModelComparator() {

    @get:Rule
    var project = builder()
        .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cxx").build())
        .addFile(HelloWorldJniApp.cmakeListsWithExecutables("."))
        .addFile(HelloWorldJniApp.executableCpp("src/main/cxx/executable", "main.cpp"))
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .setWithCmakeDirInLocalProp(true)
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile, """
                apply plugin: 'com.android.application'
                android {
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                    externalNativeBuild {
                      cmake {
                        path "CMakeLists.txt"
                      }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun `test basic model information`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .with(BooleanOption.ENABLE_V2_NATIVE_MODEL, true)
            .fetchNativeModules(emptyList(), emptyList())
        val nativeModule = result.container.singleModel
        val normalizer = result.normalizer
        for (variant in nativeModule.variants) {
            for (abi in variant.abis) {
                assertFileDoesNotExist(abi.buildFileIndexFile, normalizer)
                assertFileDoesNotExist(abi.symbolFolderIndexFile, normalizer)
                assertFileDoesNotExist(abi.sourceFlagsFile, normalizer)
            }
        }

        with(result).compare(
            model = nativeModule,
            goldenFile = "nativeModule"
        )
    }

    @Test
    fun `test generate build information`() {
        val modelBuilder: ModelBuilderV2 = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .with(BooleanOption.ENABLE_V2_NATIVE_MODEL, true)
        val result = modelBuilder
            .fetchNativeModules(listOf("debug"), listOf("x86"))
        val nativeModule = result.container.singleModel
        for (variant in nativeModule.variants) {
            for (abi in variant.abis) {
                if (variant.name == "debug" && abi.name == "x86") continue
                assertFileDoesNotExist(abi.buildFileIndexFile, result.normalizer)
                assertFileDoesNotExist(abi.symbolFolderIndexFile, result.normalizer)
                assertFileDoesNotExist(abi.sourceFlagsFile, result.normalizer)
            }
        }
        val syncedAbi =
            nativeModule.variants.first { it.name == "debug" }.abis.first { it.name == "x86" }

        Truth.assertThat(syncedAbi.buildFileIndexFile.readLines(StandardCharsets.UTF_8)
            .map { result.normalizer.normalize(File(it)) }
        ).containsExactly("{PROJECT}/CMakeLists.txt{F}")

        Truth.assertThat(syncedAbi.symbolFolderIndexFile.readLines(StandardCharsets.UTF_8)
            .map { result.normalizer.normalize(File(it)) }
        ).containsExactly("{PROJECT}/build/intermediates/cmake/debug/obj/x86{D}")

        val gson = Gson()
        // Some version of CMake (like 3.10) emit double spaces, others (like 3.18) don't.
        val jsonText = syncedAbi.sourceFlagsFile.readText()
            .replace(Regex(" +"), " ")

        val compileCommandsJsonEntries: List<JsonElement> =
            StringReader(jsonText).use {
                gson.fromJson(it, JsonArray::class.java)
                    .map { result.normalizer.normalize(it) }
            }

        when (CURRENT_PLATFORM) {
            PLATFORM_LINUX -> Truth.assertThat(compileCommandsJsonEntries).containsExactly(
                gson.fromJson(
                    """
                    {
                      "directory": "{PROJECT}/.cxx/cmake/debug/x86",
                      "command": "{NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ --target=i686-none-linux-android16 --gcc-toolchain={NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64 --sysroot={NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/sysroot -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security -O0 -fno-limit-debug-info -fPIE -o CMakeFiles/hello-executable.dir/src/main/cxx/executable/main.cpp.o -c {PROJECT}/src/main/cxx/executable/main.cpp",
                      "file": "{PROJECT}/src/main/cxx/executable/main.cpp"
                    }
                    """, JsonObject::class.java
                ),
                gson.fromJson(
                    """
                    {
                      "directory": "{PROJECT}/.cxx/cmake/debug/x86",
                      "command": "{NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang --target=i686-none-linux-android16 --gcc-toolchain={NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64 --sysroot={NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/sysroot -Dhello_jni_EXPORTS -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security -O0 -fno-limit-debug-info -fPIC -o CMakeFiles/hello-jni.dir/src/main/cxx/hello-jni.c.o -c {PROJECT}/src/main/cxx/hello-jni.c",
                      "file": "{PROJECT}/src/main/cxx/hello-jni.c"
                    }
                    """, JsonObject::class.java
                ),
                gson.fromJson(
                    """
                    {
                      "directory": "{PROJECT}/.cxx/cmake/debug/x86",
                      "command": "{NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ --target=i686-none-linux-android16 --gcc-toolchain={NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64 --sysroot={NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/sysroot -Dhello_jni_EXPORTS -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security -O0 -fno-limit-debug-info -fPIC -o CMakeFiles/hello-jni.dir/src/main/cxx/executable/main.cpp.o -c {PROJECT}/src/main/cxx/executable/main.cpp",
                      "file": "{PROJECT}/src/main/cxx/executable/main.cpp"
                    }
                    """, JsonObject::class.java
                )
            )
            PLATFORM_WINDOWS -> Truth.assertThat(compileCommandsJsonEntries).containsExactly(
                gson.fromJson(
                    """
                    {
                      "directory": "{PROJECT}/.cxx/cmake/debug/x86",
                      "command": "{NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe --target=i686-none-linux-android16 --gcc-toolchain={NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64 --sysroot={NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64/sysroot -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security -O0 -fno-limit-debug-info -fPIE -o CMakeFiles/hello-executable.dir/src/main/cxx/executable/main.cpp.o -c {PROJECT}/src/main/cxx/executable/main.cpp",
                      "file": "{PROJECT}/src/main/cxx/executable/main.cpp"
                    }
                    """, JsonObject::class.java
                ),
                gson.fromJson(
                    """
                    {
                        "directory":"{PROJECT}/.cxx/cmake/debug/x86",
                        "command":"{NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe --target=i686-none-linux-android16 --gcc-toolchain={NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64 --sysroot={NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64/sysroot -Dhello_jni_EXPORTS -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security -O0 -fno-limit-debug-info -fPIC -o CMakeFiles/hello-jni.dir/src/main/cxx/executable/main.cpp.o -c {PROJECT}/src/main/cxx/executable/main.cpp",
                        "file":"{PROJECT}/src/main/cxx/executable/main.cpp"
                    }
                    """, JsonObject::class.java
                ),
                gson.fromJson(
                    """
                    {
                        "directory":"{PROJECT}/.cxx/cmake/debug/x86",
                        "command":"{NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe --target=i686-none-linux-android16 --gcc-toolchain={NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64 --sysroot={NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64/sysroot -Dhello_jni_EXPORTS -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security -O0 -fno-limit-debug-info -fPIC -o CMakeFiles/hello-jni.dir/src/main/cxx/hello-jni.c.o -c {PROJECT}/src/main/cxx/hello-jni.c",
                        "file":"{PROJECT}/src/main/cxx/hello-jni.c"
                    }
                    """, JsonObject::class.java
                )
            )
            PLATFORM_DARWIN -> {
            } // Skip checking Mac since there is no PSQ.
        }
    }

    private fun assertFileDoesNotExist(buildFileIndexFile: File, normalizer: FileNormalizer) {
        Truth.assertThat(buildFileIndexFile.exists())
            .named("existence of " + normalizer.normalize(buildFileIndexFile))
            .isFalse()
    }
}
