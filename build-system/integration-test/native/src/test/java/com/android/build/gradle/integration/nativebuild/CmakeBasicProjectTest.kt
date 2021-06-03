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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.model.cartesianOf
import com.android.build.gradle.integration.common.fixture.model.dump
import com.android.build.gradle.integration.common.fixture.model.enableCxxStructuredLogging
import com.android.build.gradle.integration.common.fixture.model.findAbiSegment
import com.android.build.gradle.integration.common.fixture.model.findConfigurationSegment
import com.android.build.gradle.integration.common.fixture.model.findCxxSegment
import com.android.build.gradle.integration.common.fixture.model.goldenBuildProducts
import com.android.build.gradle.integration.common.fixture.model.goldenConfigurationFlags
import com.android.build.gradle.integration.common.fixture.model.readAsFileIndex
import com.android.build.gradle.integration.common.fixture.model.readCompileCommandsJsonBin
import com.android.build.gradle.integration.common.fixture.model.readStructuredLogs
import com.android.build.gradle.integration.common.fixture.model.recoverExistingCxxAbiModels
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.attribution.decodeBuildTaskAttributions
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.configure.OFF_STAGE_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.getNativeBuildMiniConfig
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.jsonGenerationLoggingRecordFile
import com.android.build.gradle.internal.cxx.model.miniConfigFile
import com.android.build.gradle.internal.cxx.model.ninjaDepsFile
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils.join
import com.android.utils.cxx.streamCompileCommands
import com.android.utils.cxx.streamCompileCommandsV2
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** Assemble tests for Cmake.  */
@RunWith(Parameterized::class)
class CmakeBasicProjectTest(
    private val cmakeVersionInDsl: String
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
        fun data() = arrayOf("3.6.0", OFF_STAGE_CMAKE_VERSION, DEFAULT_CMAKE_VERSION)
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

    /**
     * Helper function that controls arguments when running a task
     */
    private fun runTasks(vararg tasks : String): GradleBuildResult? {
        return project.executor().withArgument("--build-cache").run(*tasks)
    }

    // Regression test for b/179062268
    @Test
    fun `check clean task and extract proguard files task run together`() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    buildTypes {
                        release {
                            minifyEnabled true
                            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                        }
                    }
                }
            """.trimIndent()
        )
        project.execute("clean", "assembleRelease")
        assertThat(project.getIntermediateFile("default_proguard_files/global")).exists()
    }

    // Regression test for b/184060944
    @Test
    fun `ninja verbosity respects CMAKE_VERBOSE_MAKEFILE=1`() {
        Assume.assumeFalse(cmakeVersionInDsl == "3.6.0") // We don't control this for fork CMake
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            android.defaultConfig.externalNativeBuild.cmake.arguments.addAll("-DCMAKE_VERBOSE_MAKEFILE=1")
            """.trimIndent()
        )
        project.execute("generateJsonModelDebug")
        val abi = project.recoverExistingCxxAbiModels().single { it.abi == Abi.ARMEABI_V7A }
        val config = getNativeBuildMiniConfig(abi, null)
        val commands = config.buildTargetsCommandComponents
        assertThat(commands)
            .named(abi.miniConfigFile.path)
            .contains("-v")
    }

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
        val abi = project.recoverExistingCxxAbiModels().single { it.abi == Abi.ARMEABI_V7A }
        project.projectDir.resolve(".cxx").deleteRecursively()
        assertThat(abi.soFolder.resolve("libfoo.so")).isFile()
        project.execute("assembleDebug")
    }


    @Test
    fun `bug 187134648 OBJECT-library`() {
        // https://issuetracker.google.com/158317988
        // runtimeFiles doesn't work pre-3.7 because there's no CMake server.
        if (cmakeVersionInDsl == "3.6.0") return
        if (cmakeVersionInDsl == "3.10.2") return
        project.buildFile.resolveSibling("object_src1.cpp").writeText("void foo() {}")
        project.buildFile.resolveSibling("object_src2.cpp").writeText("void bar() {}")
        val cmakeLists = project.buildFile.resolveSibling("CMakeLists.txt")
        assertThat(cmakeLists).isFile()
        cmakeLists.writeText("""
            cmake_minimum_required(VERSION 3.18 FATAL_ERROR)

            add_library(object_dependency OBJECT)
            target_sources(object_dependency PRIVATE object_src1.cpp object_src2.cpp)

            add_library(native_lib SHARED)
            target_link_libraries(native_lib PRIVATE object_dependency)
            """.trimIndent())

        project.execute("generateJsonModelDebug")

        val abi = project.recoverExistingCxxAbiModels().single { it.abi == Abi.X86_64 }
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

        val abi = project.recoverExistingCxxAbiModels().single { it.abi == Abi.X86_64 }
        val fooPath = abi.soFolder.resolve("libfoo.so")
        assertThat(fooPath).doesNotExist()

        val json = abi.jsonFile
        assertThat(json).isFile()

        val config = AndroidBuildGradleJsons.getNativeBuildMiniConfig(abi, null)
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

    /**
     * In this bug, the file metadata_generation_command.txt was deleted by clean task.
     * This caused configure task to delete the module/.cxx folder as 'stale'.
     * This test checks for a build side effect: the presence of .ninja_deps file.
     */
    @Test
    fun `clean should not trigger stale CMake folder deletion`() {
        runTasks("buildCMakeDebug")
        val abi = project.recoverExistingCxxAbiModels().first()
        assertThat(abi.ninjaDepsFile.isFile).isTrue()
        runTasks("clean")
        assertThat(abi.ninjaDepsFile.isFile).isTrue()
        runTasks("configureCMakeDebug")
        assertThat(abi.ninjaDepsFile.isFile).isTrue()
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
    fun `build product golden locations`() {
        project.execute("assembleDebug")
        val golden = project.goldenBuildProducts()
        println(golden)
        Truth.assertThat(golden).isEqualTo("""
            {PROJECT}/.cxx/{DEBUG}/armeabi-v7a/CMakeFiles/hello-jni.dir/src/main/cxx/hello-jni.c.o{F}
            {PROJECT}/.cxx/{DEBUG}/x86_64/CMakeFiles/hello-jni.dir/src/main/cxx/hello-jni.c.o{F}
            {PROJECT}/build/intermediates/cmake/debug/obj/armeabi-v7a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/cmake/debug/obj/x86_64/libhello-jni.so{F}
            {PROJECT}/build/intermediates/merged_native_libs/debug/out/lib/armeabi-v7a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/merged_native_libs/debug/out/lib/x86_64/libhello-jni.so{F}
            {PROJECT}/build/intermediates/stripped_native_libs/debug/out/lib/armeabi-v7a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/stripped_native_libs/debug/out/lib/x86_64/libhello-jni.so{F}
            {PROJECT}/build/intermediates/{DEBUG}/obj/armeabi-v7a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/{DEBUG}/obj/x86_64/libhello-jni.so{F}
            """.trimIndent())
    }

    @Test
    fun `configuration build command golden flags`() {
        val golden = project.goldenConfigurationFlags(Abi.X86_64)
        println(golden)
        Truth.assertThat(golden).isEqualTo("""
            -B{PROJECT}/.cxx/{DEBUG}/x86_64
            -DANDROID_ABI=x86_64
            -DANDROID_NDK={NDK}
            -DANDROID_PLATFORM=android-16
            -DCMAKE_ANDROID_ARCH_ABI=x86_64
            -DCMAKE_ANDROID_NDK={NDK}
            -DCMAKE_BUILD_TYPE=Debug
            -DCMAKE_CXX_FLAGS=-DTEST_CPP_FLAG
            -DCMAKE_C_FLAGS=-DTEST_C_FLAG -DTEST_C_FLAG_2
            -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
            -DCMAKE_LIBRARY_OUTPUT_DIRECTORY={PROJECT}/build/intermediates/{DEBUG}/obj/x86_64
            -DCMAKE_MAKE_PROGRAM={NINJA}
            -DCMAKE_RUNTIME_OUTPUT_DIRECTORY={PROJECT}/build/intermediates/{DEBUG}/obj/x86_64
            -DCMAKE_SYSTEM_NAME=Android
            -DCMAKE_SYSTEM_VERSION=16
            -DCMAKE_TOOLCHAIN_FILE={NDK}/build/cmake/android.toolchain.cmake
            -G{Generator}
            -H{PROJECT}
        """.trimIndent())
    }

    @Test
    fun checkModelSingleVariant() {
        // Request build details for debug-x86_64
        val fetchResult =
          project.modelV2().fetchNativeModules(NativeModuleParams(listOf("debug"), listOf("x86_64")))

        val additionalProjectFileStatus = if (cmakeVersionInDsl == "3.6.0") {
          // CMake 3.6 does not populate additional files known by it.
            "!"
        } else {
            "F"
        }
        // note that only build files for the requested variant and ABI exists.
        Truth.assertThat(fetchResult.dump()).isEqualTo(
          """[:]
> NativeModule:
   - name                    = "project"
   > variants:
      > debug:
         > abis:
            - armeabi-v7a:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{!}
            - x86_64:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/x86_64/compile_commands.json.bin{F}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{F}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{F}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{$additionalProjectFileStatus}
         < abis
      < debug
      > release:
         > abis:
            - armeabi-v7a:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}
            - x86_64:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/x86_64/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/additional_project_files.txt{!}
         < abis
      < release
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
        val fetchResult = project.modelV2().fetchNativeModules(NativeModuleParams(emptyList(), emptyList()))
        Truth.assertThat(fetchResult.dump()).isEqualTo(
          """[:]
> NativeModule:
   - name                    = "project"
   > variants:
      > debug:
         > abis:
            - armeabi-v7a:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{!}
            - x86_64:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/x86_64/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{!}
         < abis
      < debug
      > release:
         > abis:
            - armeabi-v7a:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}
            - x86_64:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/x86_64/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/additional_project_files.txt{!}
         < abis
      < release
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
        val result = project.modelV2().fetchNativeModules(NativeModuleParams(emptyList(), emptyList()))

        // TODO(tgeng): Update this when CMake server supports populating additional project files.
        val additionalProjectFileStatus = if (cmakeVersionInDsl == "3.6.0") {
            // CMake 3.6 does not populate additional files known by it.
            "!"
        } else {
            "F"
        }
        // The files still appear to exist because we have already built the project.
        Truth.assertThat(result.dump()).isEqualTo(
          """[:]
> NativeModule:
   - name                    = "project"
   > variants:
      > debug:
         > abis:
            - armeabi-v7a:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{F}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{F}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{F}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{$additionalProjectFileStatus}
            - x86_64:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/x86_64/compile_commands.json.bin{F}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{F}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{F}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{$additionalProjectFileStatus}
         < abis
      < debug
      > release:
         > abis:
            - armeabi-v7a:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{F}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{F}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{F}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{$additionalProjectFileStatus}
            - x86_64:
               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/x86_64/compile_commands.json.bin{F}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/symbol_folder_index.txt{F}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/build_file_index.txt{F}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/additional_project_files.txt{$additionalProjectFileStatus}
         < abis
      < release
   < variants
   - nativeBuildSystem       = CMAKE
   - ndkVersion              = "{DEFAULT_NDK_VERSION}"
   - defaultNdkVersion       = "{DEFAULT_NDK_VERSION}"
   - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}
< NativeModule"""
        )
        modelV2 = result.container.singleNativeModule
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

        outputFiles.forEach { file -> assertThat(File(file)).doesNotExist() }
    }

    @Test
    fun checkCleanAfterAbiSubset() {
        project.execute("clean", "assembleDebug", "assembleRelease")
        val buildOutputs = run {
            val result = project.modelV2().fetchNativeModules(NativeModuleParams(emptyList(), emptyList()))
            val nativeModule = result.container.singleNativeModule
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
    fun `build attributions are captured in chrome trace log`() {
        project.executor()
            .with(BooleanOption.ENABLE_PROFILE_JSON, true)
            .run("clean", "assembleDebug")
        val traceFolder = join(project.projectDir, "build", "android-profile")
        val traceFile = traceFolder.listFiles()!!.first { it.name.endsWith("json.gz") }
        Truth.assertThat(InputStreamReader(GZIPInputStream(FileInputStream(traceFile))).readText())
            .contains("CMakeFiles/hello-jni.dir/src/main/cxx/hello-jni.c.o")
    }

    @Test
    fun `build attributions are captured in structured log`() {
        enableCxxStructuredLogging(project)
        project.executor().run("assembleDebug")
        println(project.readStructuredLogs(::decodeBuildTaskAttributions))
        val events = project.readStructuredLogs(::decodeBuildTaskAttributions)
            .flatMap { it.attributionList }
            .groupBy { File(it.outputFile).name }
            .map { group -> group.key to group.value.count() }
            .toMap()
        assertThat(events).hasSize(2) // One for .o and one for .so
        assertThat(events["hello-jni.c.o"]).isEqualTo(2) // One each for two ABIs
        assertThat(events["libhello-jni.so"]).isEqualTo(2) // One each for two ABIs
    }

    @Test
    fun `generateJsonModel task always runs`() {
        project.executor().run("assembleDebug")
        val abi = project.recoverExistingCxxAbiModels().first()
        val generationRecord = abi.jsonGenerationLoggingRecordFile
        assertThat(generationRecord).exists()
        val stateModificationTime = generationRecord.lastModified()
        project.executor().run("assembleDebug")
        assertThat(stateModificationTime).isNotEqualTo(0)
        assertThat(generationRecord).exists()
        assertThat(generationRecord.lastModified()).isGreaterThan(stateModificationTime)
    }

    // https://issuetracker.google.com/187448826
    @Test
    fun `bug 187448826 precompiled header works`() {
        // Precompiled header support was added in CMake 3.16
        if (cmakeVersionInDsl == "3.6.0") return // Unknown CMake command "target_precompile_headers".
        if (cmakeVersionInDsl == "3.10.2") return // Unknown CMake command "target_precompile_headers".
        project.buildFile.resolveSibling("stdheader.h").writeText("")
        project.buildFile.resolveSibling("src1.cpp").writeText("void foo() {}")
        val cmakeLists = project.buildFile.resolveSibling("CMakeLists.txt")
        assertThat(cmakeLists).isFile()
        cmakeLists.writeText("""
            cmake_minimum_required(VERSION 3.4.1)
            add_library(foo SHARED src1.cpp)
            find_library(log-lib log)
            target_precompile_headers(foo PUBLIC stdheader.h)
            target_link_libraries(foo ${'$'}{log-lib})
            """.trimIndent())
        project.execute("assembleDebug")

        val abi = project.recoverExistingCxxAbiModels().single { it.abi == Abi.ARMEABI_V7A }
        val outputFiles = mutableListOf<String>()
        streamCompileCommandsV2(abi.compileCommandsJsonBinFile) {
            outputFiles.add(outputFile.name)
        }
        assertThat(outputFiles).contains("cmake_pch.hxx.pch")
    }

    @Test
    fun `validate some utility functions`() {
        val file = join(File("."), ".cxx", "cmake", "debug","armeabi-v7a","symbol_folder_index.txt")
        val abiSegment = findAbiSegment(file)
        val cxxSegment = findCxxSegment(file)
        val configurationSegment = findConfigurationSegment(file)
        assertThat(abiSegment).named(abiSegment).isEqualTo("armeabi-v7a")
        assertThat(cxxSegment).named(cxxSegment).isEqualTo(".cxx")
        assertThat(configurationSegment).named(configurationSegment).isEqualTo(join("cmake/debug"))
        val cartesian = cartesianOf(arrayOf(1,2), arrayOf("b", "c"), arrayOf(1.1, 1.2))
        assertThat(cartesian[0]).isEqualTo(arrayOf(1, "b", 1.1))
        assertThat(cartesian[7]).isEqualTo(arrayOf(2, "c", 1.2))
        assertThat(cartesian).hasLength(8)
    }

    @Test
    fun `ensure compile_commands json bin is created for each native ABI in model`() {
        val nativeModules = project.modelV2().fetchNativeModules(NativeModuleParams())
        val nativeModule = nativeModules.container.singleNativeModule
        for (variant in nativeModule.variants) {
            for (abi in variant.abis) {
                Truth.assertThat(abi.sourceFlagsFile.readCompileCommandsJsonBin(nativeModules.normalizer))
                        .hasSize(1)
            }
        }
    }
}
