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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.fixture.model.FileNormalizer
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.model.cxxFileVariantSegmentTranslator
import com.android.build.gradle.integration.common.fixture.model.dumpCompileCommandsJsonBin
import com.android.build.gradle.integration.common.fixture.model.recoverExistingCxxAbiModels
import com.android.build.gradle.integration.common.fixture.model.withCxxFileNormalizer
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(Parameterized::class)
class V2NativeModelTest(private val cmakeVersion: String) : ModelComparator() {

    @get:Rule
    var project = builder()
      .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cxx").build())
      .addFile(HelloWorldJniApp.cmakeListsWithExecutables(".", "blah.h", "blah.txt"))
      .addFile(TestSourceFile(".", "blah.h", "int i = 3;"))
      .addFile(TestSourceFile(".", "blah.txt", "foobar"))
      .addFile(HelloWorldJniApp.executableCpp("src/main/cxx/executable", "main.cpp"))
      .setCmakeVersion(cmakeVersion)
      .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
      .setWithCmakeDirInLocalProp(true)
      .create()

    companion object {
        @Parameterized.Parameters(name = "version={0}")
        @JvmStatic
        fun data() = arrayOf(
          arrayOf("3.18.1"),
          // With the GradleTestProject fixture, CMake 3.10.2 is not available.
          arrayOf("3.10.4819442")
        )
    }

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
                        version "3.18.1"
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
          .fetchNativeModules(NativeModuleParams(emptyList(), emptyList()))
          .withCxxFileNormalizer()
        val nativeModule = result.container.singleNativeModule
        val normalizer = result.normalizer
        for (variant in nativeModule.variants) {
            for (abi in variant.abis) {
                assertFileDoesNotExist(abi.buildFileIndexFile, normalizer)
                assertFileDoesNotExist(abi.symbolFolderIndexFile, normalizer)
                assertFileDoesNotExist(abi.sourceFlagsFile, normalizer)
                assertFileDoesNotExist(abi.additionalProjectFilesIndexFile, normalizer)
            }
        }

        with(result).compareNativeModule(
          modelAction = { container.singleNativeModule },
          goldenFile = "nativeModule"
        )
    }

    @Test
    fun `test generate build information`() {
        val modelBuilder: ModelBuilderV2 = project.modelV2()
          .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
        val result = modelBuilder
          .fetchNativeModules(NativeModuleParams(listOf("debug"), listOf("x86")))
        val nativeModule = result.container.singleNativeModule
        for (variant in nativeModule.variants) {
            for (abi in variant.abis) {
                if (variant.name == "debug" && abi.name == "x86") continue
                assertFileDoesNotExist(abi.buildFileIndexFile, result.normalizer)
                assertFileDoesNotExist(abi.symbolFolderIndexFile, result.normalizer)
                assertFileDoesNotExist(abi.sourceFlagsFile, result.normalizer)
                assertFileDoesNotExist(abi.additionalProjectFilesIndexFile, result.normalizer)
            }
        }
        val syncedAbi =
          nativeModule.variants.first { it.name == "debug" }.abis.first { it.name == "x86" }

        Truth.assertThat(syncedAbi.buildFileIndexFile.readLines(StandardCharsets.UTF_8)
          .map { result.normalizer.normalize(File(it)) }
        ).containsExactly("{PROJECT}/CMakeLists.txt{F}")

        val abi = project.recoverExistingCxxAbiModels().single()
        Truth.assertThat(syncedAbi.symbolFolderIndexFile.readLines(StandardCharsets.UTF_8)
        ).containsExactly(abi.soFolder.toString())

        Truth.assertThat(syncedAbi.additionalProjectFilesIndexFile.readLines(StandardCharsets.UTF_8)
          .map { result.normalizer.normalize(File(it)) }
        ).containsExactly("{PROJECT}/blah.h{F}", "{PROJECT}/blah.txt{F}")

        val translate = result.cxxFileVariantSegmentTranslator()
        when (CURRENT_PLATFORM) {
            PLATFORM_LINUX -> Truth.assertThat(
                translate(syncedAbi.sourceFlagsFile.dumpCompileCommandsJsonBin(
                    result.normalizer
                  ))
            ).isEqualTo(
              """
                sourceFile: {PROJECT}/src/main/cxx/executable/main.cpp{F}
                compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++{F}
                workingDir: {PROJECT}/.cxx/{DEBUG}/x86{D}
                flags:      [--target=i686-none-linux-android16, --gcc-toolchain={ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64, --sysroot={ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot, -Dhello_jni_EXPORTS, -g, -DANDROID, -fdata-sections, -ffunction-sections, -funwind-tables, -fstack-protector-strong, -no-canonical-prefixes, -mstackrealign, -D_FORTIFY_SOURCE=2, -Wformat, -Werror=format-security, -O0, -fno-limit-debug-info, -fPIC]

                sourceFile: {PROJECT}/src/main/cxx/executable/main.cpp{F}
                compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++{F}
                workingDir: {PROJECT}/.cxx/{DEBUG}/x86{D}
                flags:      [--target=i686-none-linux-android16, --gcc-toolchain={ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64, --sysroot={ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot, -g, -DANDROID, -fdata-sections, -ffunction-sections, -funwind-tables, -fstack-protector-strong, -no-canonical-prefixes, -mstackrealign, -D_FORTIFY_SOURCE=2, -Wformat, -Werror=format-security, -O0, -fno-limit-debug-info, -fPIE]

                sourceFile: {PROJECT}/src/main/cxx/hello-jni.c{F}
                compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang{F}
                workingDir: {PROJECT}/.cxx/{DEBUG}/x86{D}
                flags:      [--target=i686-none-linux-android16, --gcc-toolchain={ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64, --sysroot={ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot, -Dhello_jni_EXPORTS, -g, -DANDROID, -fdata-sections, -ffunction-sections, -funwind-tables, -fstack-protector-strong, -no-canonical-prefixes, -mstackrealign, -D_FORTIFY_SOURCE=2, -Wformat, -Werror=format-security, -O0, -fno-limit-debug-info, -fPIC]
                """.trimIndent()
            )
            PLATFORM_WINDOWS -> Truth.assertThat(
                    translate(syncedAbi.sourceFlagsFile.dumpCompileCommandsJsonBin(
                        result.normalizer
                      ))
            ).isEqualTo(
              """
                sourceFile: {PROJECT}/src/main/cxx/executable/main.cpp{F}
                compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe{F}
                workingDir: {PROJECT}/.cxx/{DEBUG}/x86{D}
                flags:      [--target=i686-none-linux-android16, --gcc-toolchain={ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64, --sysroot={ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/sysroot, -Dhello_jni_EXPORTS, -g, -DANDROID, -fdata-sections, -ffunction-sections, -funwind-tables, -fstack-protector-strong, -no-canonical-prefixes, -mstackrealign, -D_FORTIFY_SOURCE=2, -Wformat, -Werror=format-security, -O0, -fno-limit-debug-info, -fPIC, {PROJECT}/src/main/cxx/executable/main.cpp]

                sourceFile: {PROJECT}/src/main/cxx/executable/main.cpp{F}
                compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe{F}
                workingDir: {PROJECT}/.cxx/{DEBUG}/x86{D}
                flags:      [--target=i686-none-linux-android16, --gcc-toolchain={ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64, --sysroot={ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/sysroot, -g, -DANDROID, -fdata-sections, -ffunction-sections, -funwind-tables, -fstack-protector-strong, -no-canonical-prefixes, -mstackrealign, -D_FORTIFY_SOURCE=2, -Wformat, -Werror=format-security, -O0, -fno-limit-debug-info, -fPIE, {PROJECT}/src/main/cxx/executable/main.cpp]

                sourceFile: {PROJECT}/src/main/cxx/hello-jni.c{F}
                compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe{F}
                workingDir: {PROJECT}/.cxx/{DEBUG}/x86{D}
                flags:      [--target=i686-none-linux-android16, --gcc-toolchain={ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64, --sysroot={ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/sysroot, -Dhello_jni_EXPORTS, -g, -DANDROID, -fdata-sections, -ffunction-sections, -funwind-tables, -fstack-protector-strong, -no-canonical-prefixes, -mstackrealign, -D_FORTIFY_SOURCE=2, -Wformat, -Werror=format-security, -O0, -fno-limit-debug-info, -fPIC, {PROJECT}/src/main/cxx/hello-jni.c]
                """.trimIndent()

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
