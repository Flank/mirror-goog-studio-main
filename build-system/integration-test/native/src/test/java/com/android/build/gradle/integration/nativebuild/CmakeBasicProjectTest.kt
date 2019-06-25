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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils.join
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
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
class CmakeBasicProjectTest(private val cmakeVersionInDsl: String) {
    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .fromTestApp(
            HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build())
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    companion object {
        @Parameterized.Parameters(name = "model = {0}")
        @JvmStatic
        fun data() = arrayOf(
                arrayOf("3.6.0"),
                arrayOf("3.10.2"))
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
    fun checkModel() {
        project.model().fetchAndroidProjects() // Make sure we can successfully get AndroidProject
        val model = project.model().fetch(NativeAndroidProject::class.java)
        assertThat(model.buildSystems).containsExactly(NativeBuildSystem.CMAKE.tag)
        assertThat(model).hasAtLeastBuildFilesShortNames("CMakeLists.txt")
        assertThat(model.name).isEqualTo("project")
        val abiCount = 2
        assertThat(model.artifacts).hasSize(abiCount * 2)
        assertThat(model.fileExtensions).hasSize(1)

        for (file in model.buildFiles) {
            assertThat(file).isFile()
        }

        val groupToArtifacts = ArrayListMultimap.create<String, NativeArtifact>()

        for (artifact in model.artifacts) {
            val pathElements = TestFileUtils.splitPath(artifact.outputFile)
            assertThat(pathElements).contains("obj")
            assertThat(pathElements).doesNotContain("lib")
            groupToArtifacts.put(artifact.groupName, artifact)
        }

        assertThat(model).hasArtifactGroupsNamed("debug", "release")
        assertThat(model).hasArtifactGroupsOfSize(abiCount.toLong())

        assertThat(model).hasVariantInfoBuildFolderForEachAbi()
    }

    @Test
    fun checkClean() {
        project.execute("clean", "assembleDebug", "assembleRelease")
        val model = project.model().fetch(NativeAndroidProject::class.java)
        assertThat(model).hasBuildOutputCountEqualTo(4)
        assertThat(model).allBuildOutputsExist()
        // CMake .o files are kept in -B folder which is under .externalNativeBuild/
        assertThat(model).hasExactObjectFilesInCxxFolder("hello-jni.c.o")
        // CMake .so files are kept in -DCMAKE_LIBRARY_OUTPUT_DIRECTORY folder which is under build/
        assertThat(model).hasExactSharedObjectFilesInBuildFolder("libhello-jni.so")
        project.execute("clean")
        assertThat(model).noBuildOutputsExist()
        assertThat(model).hasExactObjectFilesInBuildFolder()
        assertThat(model).hasExactSharedObjectFilesInBuildFolder()
    }

    @Test
    fun checkCleanAfterAbiSubset() {
        project.execute("clean", "assembleDebug", "assembleRelease")
        val model = project.model().fetch(NativeAndroidProject::class.java)
        assertThat(model).hasBuildOutputCountEqualTo(4)

        val allBuildOutputs = Lists.newArrayList<File>()
        for (artifact in model.artifacts) {
            assertThat(artifact.outputFile).isFile()
            allBuildOutputs.add(artifact.outputFile)
        }

        // Change the build file to only have "x86_64"
        TestFileUtils.appendToFile(
            project.buildFile,
            "\n"
                    + "apply plugin: 'com.android.application'\n"
                    + "\n"
                    + "    android {\n"
                    + "        defaultConfig {\n"
                    + "          externalNativeBuild {\n"
                    + "              cmake {\n"
                    + "                abiFilters.clear();\n"
                    + "                abiFilters.addAll(\"x86_64\");\n"
                    + "              }\n"
                    + "          }\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
        )
        project.execute("clean")

        // All build outputs should no longer exist, even the non-x86 outputs
        for (output in allBuildOutputs) {
            assertThat(output).doesNotExist()
        }
    }

    @Test
    fun generatedChromeTraceFileContainsNativeBuildInformation() {
        // Disable this test for Gradle since it somehow fails if multiple tests are executed
        // at the same time. See b/133222337
        Assume.assumeTrue(TestUtils.runningFromBazel())
        project.executor()
            .with(BooleanOption.ENABLE_PROFILE_JSON, true)
            .run("clean", "assembleDebug")
        val traceFile = join(project.testDir, "build", "android-profile").listFiles()!!
            .first { it.name.endsWith("json.gz") }
        Truth.assertThat(InputStreamReader(GZIPInputStream(FileInputStream(traceFile))).readText())
            .contains("CMakeFiles/hello-jni.dir/src/main/cxx/hello-jni.c.o")
    }
}
