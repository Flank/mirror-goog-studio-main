/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.model.dump
import com.android.build.gradle.integration.common.fixture.model.readAsFileIndex
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.android.build.gradle.internal.cxx.configure.BAKING_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils.join
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** Assemble tests for Cmake.  */
@RunWith(Parameterized::class)
class CmakeBasicProjectTest(
    private val cmakeVersionInDsl: String,
) {
    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build())
        // TODO(b/159233213) Turn to ON when release configuration is cacheable
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    companion object {
        @Parameterized.Parameters(name = "version={0}")
        @JvmStatic
        fun data() = arrayOf(
          arrayOf("3.6.0"),
          arrayOf(BAKING_CMAKE_VERSION),
          arrayOf(DEFAULT_CMAKE_VERSION)
        )
    }

    @Before
    fun setUp() {
        assertThat(project.buildFile).isNotNull()
        assertThat(project.buildFile).isFile()

        TestFileUtils.appendToFile(project.buildFile, moduleBody("CMakeLists.txt"))
    }

    private fun moduleBody(
        cmakeListsPath : String
    ) : String = ("""
        apply plugin: 'com.android.application'

        android {
            compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
            buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
            ndkPath "${project.ndkPath}"
            defaultConfig {
              externalNativeBuild {
                  cmake {
                    abiFilters.addAll("armeabi-v7a", "x86_64");
                    cFlags.addAll("-DTEST_C_FLAG", "-DTEST_C_FLAG_2")
                    cppFlags.addAll("-DTEST_CPP_FLAG")
                  }
              }
            }
            externalNativeBuild {
              cmake {
                path "$cmakeListsPath"
                version "$cmakeVersionInDsl"
              }
            }

          // -----------------------------------------------------------------------
          // See b/131857476
          // -----------------------------------------------------------------------
          applicationVariants.all { variant ->
            for (def task : variant.getExternalNativeBuildTasks()) {
                println("externalNativeBuild objFolder = " + task.objFolder)
                println("externalNativeBuild soFolder = " + task.soFolder)
            }
          }

          // ------------------------------------------------------------------------
        }
    """.trimIndent())

    // See b/134086362
    @Test
    fun `check target rename through transitive CMakeLists add_subdirectory`() {
        // This doesn't work with 3.6.0 CMake because the transitive list of build files isn't
        // produced and there's no way to fix that without reshipping fork CMake.
        if (cmakeVersionInDsl == "3.6.0") return
        val leafCmakeLists = join(project.buildFile.parentFile, "CMakeLists.txt")
        assertThat(leafCmakeLists).isFile()
        val rootCmakeLists = join(leafCmakeLists.parentFile, "root", "CMakeLists.txt")
        rootCmakeLists.parentFile.mkdirs()
        rootCmakeLists.writeText("""
            cmake_minimum_required(VERSION 3.10)
            add_subdirectory(${leafCmakeLists.parentFile.absolutePath.replace('\\','/')} bin)
            """.trimIndent())
        TestFileUtils.appendToFile(project.buildFile, moduleBody("root/CMakeLists.txt"))

        project.execute("assemble")

        // Rename the target
        leafCmakeLists.writeText(leafCmakeLists.readText().replace("hello-jni", "hello-jni-renamed"))

        // Assemble again
        project.execute("assemble")
    }

    @Test
    fun `check that duplicate runtimeFiles does not cause a build failure`() {
        // https://issuetracker.google.com/158317988
        project.buildFile.resolveSibling("foo.cpp").writeText("void foo() {}")
        project.buildFile.resolveSibling("bar.cpp").writeText("void bar() {}")
        val cmakeLists = project.buildFile.resolveSibling("CMakeLists.txt")
        assertThat(cmakeLists).isFile()
        cmakeLists.writeText("""
            cmake_minimum_required(VERSION 3.4.1)

            add_library(foo SHARED foo.cpp)
            add_library(bar SHARED bar.cpp)

            find_library(log-lib log)

            target_link_libraries(foo ${'$'}{log-lib})
            target_link_libraries(bar foo)
            """.trimIndent())

        // The bug being tested was caused by the bad ordering between handling runtimeFiles and
        // performing the build. The JSON model was being generated from a clean build directory the
        // libraries would not be present when the CMake response was handled and we would skip any
        // linkLibraries that had not been built yet.
        //
        // If the build directory *wasn't* cleaned before regenerating the JSON model the libraries
        // would be included in runtimeFiles and the install task could fail when trying to install
        // a file to itself.
        //
        // To recreate those conditions, build the project twice, purging only the .cxx directory
        // between runs.
        project.execute("assembleDebug")
        project.projectDir.resolve(".cxx").deleteRecursively()
        assertThat(project.buildDir.resolve("intermediates/cmake/debug/obj/armeabi-v7a/libfoo.so")).isFile()
        project.execute("assembleDebug")
    }

    @Test
    fun `runtimeFiles are included even if not built yet`() {
        // https://issuetracker.google.com/158317988
        // runtimeFiles doesn't work pre-3.7 because there's no CMake server.
        if (cmakeVersionInDsl == "3.6.0") return
        project.buildFile.resolveSibling("foo.cpp").writeText("void foo() {}")
        project.buildFile.resolveSibling("bar.cpp").writeText("void bar() {}")
        project.buildFile.resolveSibling("baz.cpp").writeText("void baz() {}")
        val cmakeLists = project.buildFile.resolveSibling("CMakeLists.txt")
        assertThat(cmakeLists).isFile()
        cmakeLists.writeText("""
            cmake_minimum_required(VERSION 3.7)

            add_library(foo SHARED foo.cpp)
            add_library(bar SHARED bar.cpp)
            add_library(baz STATIC baz.cpp)

            target_link_libraries(foo ${'$'}{log-lib})
            target_link_libraries(bar foo baz)
            """.trimIndent())

        project.execute("generateJsonModelDebug")

        val fooPath = project.buildDir.resolve("intermediates/cmake/debug/obj/x86_64/libfoo.so")
        assertThat(fooPath).doesNotExist()

        val json = project.projectDir.resolve(".cxx/cmake/debug/x86_64/android_gradle_build_mini.json")
        assertThat(json).isFile()

        val config = AndroidBuildGradleJsons.getNativeBuildMiniConfig(json, null)
        val library = config
                .libraries
                .asSequence()
                .filter { it.key.contains("bar") }
                .single()
                .value
        assertThat(library.runtimeFiles).containsExactly(fooPath)
    }

    // See b/131857476
    @Test
    fun checkModuleBodyReferencesObjAndSo() {
        // Checks for whether module body has references to objFolder and soFolder
        Truth.assertThat(moduleBody("CMakeLists.txt")).contains(".objFolder")
        Truth.assertThat(moduleBody("CMakeLists.txt")).contains(".soFolder")
    }

    @Test
    fun checkApkContent() {
        project.execute("clean", "assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(apk).hasVersionCode(1)
        assertThatApk(apk).contains("lib/armeabi-v7a/libhello-jni.so")
        assertThatApk(apk).contains("lib/x86_64/libhello-jni.so")

        var lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so")
        TruthHelper.assertThatNativeLib(lib).isStripped()

        lib = ZipHelper.extractFile(apk, "lib/x86_64/libhello-jni.so")
        TruthHelper.assertThatNativeLib(lib).isStripped()
    }

    @Test
    fun checkApkContentWithInjectedABI() {
        project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86_64")
            .run("clean", "assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
        assertThatApk(apk).contains("lib/x86_64/libhello-jni.so")

        val lib = ZipHelper.extractFile(apk, "lib/x86_64/libhello-jni.so")
        TruthHelper.assertThatNativeLib(lib).isStripped()
    }

    @Test
    fun checkModelSingleVariant() {
        // Request build details for debug-x86_64
        val fetchResult =
          project.modelV2().fetchNativeModules(listOf("debug"), listOf("x86_64"))

        // TODO(tgeng): Update this when CMake server supports populating additional project files.
        val additionalProjectFileStatus = if (cmakeVersionInDsl == BAKING_CMAKE_VERSION) {
            "F"
        } else {
            "!"
        }
        // note that only build files for the requested variant and ABI exists.
        Truth.assertThat(fetchResult.dump()).isEqualTo(
          """[:]
> NativeModule:
    - name                    = "project"
    > variants:
       * NativeVariant:
          * name = "debug"
          > abis:
             * NativeAbi:
                * name                            = "armeabi-v7a"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/compile_commands.json.bin{!}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/symbol_folder_index.txt{!}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/build_file_index.txt{!}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/additional_project_files.txt{!}
             * NativeAbi:
                * name                            = "x86_64"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/debug/x86_64/compile_commands.json.bin{F}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/debug/x86_64/symbol_folder_index.txt{F}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/debug/x86_64/build_file_index.txt{F}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/debug/x86_64/additional_project_files.txt{$additionalProjectFileStatus}
          < abis
       * NativeVariant:
          * name = "release"
          > abis:
             * NativeAbi:
                * name                            = "armeabi-v7a"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/release/armeabi-v7a/compile_commands.json.bin{!}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/release/armeabi-v7a/symbol_folder_index.txt{!}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/release/armeabi-v7a/build_file_index.txt{!}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/release/armeabi-v7a/additional_project_files.txt{!}
             * NativeAbi:
                * name                            = "x86_64"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/release/x86_64/compile_commands.json.bin{!}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/release/x86_64/symbol_folder_index.txt{!}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/release/x86_64/build_file_index.txt{!}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/release/x86_64/additional_project_files.txt{!}
          < abis
    < variants
    - nativeBuildSystem       = CMAKE
    - ndkVersion              = "{DEFAULT_NDK_VERSION}"
    - defaultNdkVersion       = "{DEFAULT_NDK_VERSION}"
    - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}
< NativeModule"""
        )
    }

    @Test
    fun checkModel() {
        val fetchResult = project.modelV2().fetchNativeModules(emptyList(), emptyList())
        Truth.assertThat(fetchResult.dump()).isEqualTo(
          """[:]
> NativeModule:
    - name                    = "project"
    > variants:
       * NativeVariant:
          * name = "debug"
          > abis:
             * NativeAbi:
                * name                            = "armeabi-v7a"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/compile_commands.json.bin{!}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/symbol_folder_index.txt{!}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/build_file_index.txt{!}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/additional_project_files.txt{!}
             * NativeAbi:
                * name                            = "x86_64"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/debug/x86_64/compile_commands.json.bin{!}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/debug/x86_64/symbol_folder_index.txt{!}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/debug/x86_64/build_file_index.txt{!}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/debug/x86_64/additional_project_files.txt{!}
          < abis
       * NativeVariant:
          * name = "release"
          > abis:
             * NativeAbi:
                * name                            = "armeabi-v7a"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/release/armeabi-v7a/compile_commands.json.bin{!}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/release/armeabi-v7a/symbol_folder_index.txt{!}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/release/armeabi-v7a/build_file_index.txt{!}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/release/armeabi-v7a/additional_project_files.txt{!}
             * NativeAbi:
                * name                            = "x86_64"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/release/x86_64/compile_commands.json.bin{!}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/release/x86_64/symbol_folder_index.txt{!}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/release/x86_64/build_file_index.txt{!}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/release/x86_64/additional_project_files.txt{!}
          < abis
    < variants
    - nativeBuildSystem       = CMAKE
    - ndkVersion              = "{DEFAULT_NDK_VERSION}"
    - defaultNdkVersion       = "{DEFAULT_NDK_VERSION}"
    - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}
< NativeModule"""
        )
    }

    @Test
    fun checkClean() {
        lateinit var modelV2: NativeModule
        // Build the project.
        project.execute("clean", "assembleDebug", "assembleRelease")

        // We specify to not generate the build information for any variants or ABIs here.
        val result = project.modelV2().fetchNativeModules(emptyList(), emptyList())

        // TODO(tgeng): Update this when CMake server supports populating additional project files.
        val additionalProjectFileStatus = if (cmakeVersionInDsl == BAKING_CMAKE_VERSION) {
            "F"
        } else {
            "!"
        }
        // The files still appear to exist because we have already built the project.
        Truth.assertThat(result.dump()).isEqualTo(
          """[:]
> NativeModule:
    - name                    = "project"
    > variants:
       * NativeVariant:
          * name = "debug"
          > abis:
             * NativeAbi:
                * name                            = "armeabi-v7a"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/compile_commands.json.bin{F}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/symbol_folder_index.txt{F}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/build_file_index.txt{F}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/additional_project_files.txt{$additionalProjectFileStatus}
             * NativeAbi:
                * name                            = "x86_64"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/debug/x86_64/compile_commands.json.bin{F}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/debug/x86_64/symbol_folder_index.txt{F}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/debug/x86_64/build_file_index.txt{F}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/debug/x86_64/additional_project_files.txt{$additionalProjectFileStatus}
          < abis
       * NativeVariant:
          * name = "release"
          > abis:
             * NativeAbi:
                * name                            = "armeabi-v7a"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/release/armeabi-v7a/compile_commands.json.bin{F}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/release/armeabi-v7a/symbol_folder_index.txt{F}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/release/armeabi-v7a/build_file_index.txt{F}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/release/armeabi-v7a/additional_project_files.txt{$additionalProjectFileStatus}
             * NativeAbi:
                * name                            = "x86_64"
                * sourceFlagsFile                 = {PROJECT}/.cxx/cmake/release/x86_64/compile_commands.json.bin{F}
                * symbolFolderIndexFile           = {PROJECT}/.cxx/cmake/release/x86_64/symbol_folder_index.txt{F}
                * buildFileIndexFile              = {PROJECT}/.cxx/cmake/release/x86_64/build_file_index.txt{F}
                * additionalProjectFilesIndexFile = {PROJECT}/.cxx/cmake/release/x86_64/additional_project_files.txt{$additionalProjectFileStatus}
          < abis
    < variants
    - nativeBuildSystem       = CMAKE
    - ndkVersion              = "{DEFAULT_NDK_VERSION}"
    - defaultNdkVersion       = "{DEFAULT_NDK_VERSION}"
    - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}
< NativeModule"""
        )
        modelV2 = result.container.singleModel
        val outputFiles = modelV2.variants.flatMap { variant ->
            variant.abis.flatMap { abi ->
                abi.symbolFolderIndexFile.readAsFileIndex().flatMap {
                    it.list().toList()
                }
            }
        }
        Truth.assertThat(outputFiles).hasSize(4)
        Truth.assertThat(outputFiles.toSet()).containsExactly("libhello-jni.so")

        project.execute("clean")

        modelV2.variants.forEach { variant ->
            variant.abis.forEach { abi ->
                abi.symbolFolderIndexFile.readAsFileIndex().forEach {
                    assertThat(it).doesNotExist()
                }
            }
        }
    }

    @Test
    fun checkCleanAfterAbiSubset() {
        project.execute("clean", "assembleDebug", "assembleRelease")
        val buildOutputs = run {
            val result = project.modelV2().fetchNativeModules(emptyList(), emptyList())
            val nativeModule = result.container.singleModel
            val buildOutputFolders = nativeModule.variants.flatMap { variant ->
                variant.abis.flatMap { abi ->
                    abi.symbolFolderIndexFile.readAsFileIndex()
                }
            }
            buildOutputFolders.forEach { folder ->
                Truth.assertThat(folder.list().toList()).containsExactly("libhello-jni.so")
            }
            buildOutputFolders
        }

        // Change the build file to only have "x86_64"
        TestFileUtils.appendToFile(
          project.buildFile,
          """
apply plugin: 'com.android.application'

    android {
        defaultConfig {
          externalNativeBuild {
              cmake {
                abiFilters.clear();
                abiFilters.addAll("x86_64");
              }
          }
        }
    }

"""
        )
        project.execute("clean")

        // All build outputs should no longer exist, even the non-x86 outputs
        for (output in buildOutputs) {
            assertThat(output).doesNotExist()
        }
    }

    @Test
    fun generatedChromeTraceFileContainsNativeBuildInformation() {
        project.executor()
            .with(BooleanOption.ENABLE_PROFILE_JSON, true)
            .run("clean", "assembleDebug")
        val traceFolder = join(project.projectDir, "build", "android-profile")
        val traceFile = traceFolder.listFiles()!!.first { it.name.endsWith("json.gz") }
        Truth.assertThat(InputStreamReader(GZIPInputStream(FileInputStream(traceFile))).readText())
            .contains("CMakeFiles/hello-jni.dir/src/main/cxx/hello-jni.c.o")
    }

    @Test
    fun `generateJsonModel task always runs`() {
        val generationRecord = project.file(".cxx/cmake/debug/armeabi-v7a/json_generation_record.json")
        project.executor().run("assembleDebug")
        assertThat(generationRecord).exists()
        val stateModificationTime = generationRecord.lastModified()
        project.executor().run("assembleDebug")
        assertThat(stateModificationTime).isNotEqualTo(0)
        assertThat(generationRecord).exists()
        assertThat(generationRecord.lastModified()).isGreaterThan(stateModificationTime)
    }
}
