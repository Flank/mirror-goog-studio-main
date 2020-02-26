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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class PrefabTest(private val buildSystem: NativeBuildSystem) {
    private val expectedAbis = listOf(Abi.ARMEABI_V7A, Abi.ARM64_V8A, Abi.X86, Abi.X86_64)

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestProject("prefabApp")
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION).create()

    companion object {
        @Parameterized.Parameters(name = "build system = {0}")
        @JvmStatic
        fun data() = arrayOf(NativeBuildSystem.CMAKE, NativeBuildSystem.NDK_BUILD)
    }

    private fun execute(vararg tasks: String) {
        when (buildSystem) {
            NativeBuildSystem.NDK_BUILD -> project.execute(mutableListOf("-PndkBuild"), *tasks)
            else -> project.execute(*tasks)
        }
    }

    private fun verifyNdkBuildPackage(pkg: String, abiDir: File) {
        assertThat(abiDir.resolve("prefab/$pkg/Android.mk")).exists()
    }

    private fun verifyNdkBuildArgs(buildCommand: String, abiDir: File) {
        val expectedArgument = "NDK_GRADLE_INJECTED_IMPORT_PATH=$abiDir"
        Truth.assertThat(buildCommand).contains(expectedArgument)
    }

    private fun verifyCMakePackage(pkg: String, abiDir: File, abi: Abi) {
        assertThat(
            abiDir.resolve(
                "prefab/lib/${abi.gccExecutablePrefix}/cmake/$pkg/$pkg-config.cmake"
            )
        ).exists()
    }

    private fun verifyCMakeArgs(buildCommand: String, abiDir: File) {
        val findRootPath = abiDir.resolve("prefab")
        val expectedArgument = "-DCMAKE_FIND_ROOT_PATH=$findRootPath"
        Truth.assertThat(buildCommand).contains(expectedArgument)
    }

    @Test
    fun `build integrations are passed to build system`() {
        execute("assembleDebug")
        val prefabDir = project.file("app/.cxx/${buildSystem.tag}/debug/prefab")
        assertThat(prefabDir).exists()
        for (abi in expectedAbis) {
            val abiDir = prefabDir.resolve(abi.tag)
            assertThat(abiDir).exists()
            val packages = listOf("curl", "jsoncpp", "openssl")
            for (pkg in packages) {
                when (buildSystem) {
                    NativeBuildSystem.CMAKE -> verifyCMakePackage(pkg, abiDir, abi)
                    NativeBuildSystem.NDK_BUILD -> verifyNdkBuildPackage(pkg, abiDir)
                }
            }

            val buildCommand =
                project.file("app/.cxx/${buildSystem.tag}/debug/${abi.tag}/build_command.txt")
                    .readText()
            when (buildSystem) {
                NativeBuildSystem.CMAKE -> verifyCMakeArgs(buildCommand, abiDir)
                NativeBuildSystem.NDK_BUILD -> verifyNdkBuildArgs(buildCommand, abiDir)
            }
        }
    }

    @Test
    fun `build integrations are not cleaned up`() {
        execute("assembleDebug")
        execute("clean")
        val prefabDir = project.file("app/.cxx/${buildSystem.tag}/debug/prefab")
        assertThat(prefabDir).exists()
    }

    @Test
    fun `cleaning a cleaned project works`() {
        execute("clean")
        execute("clean")
    }

    @Test
    fun `project builds`() {
        execute("clean", "assembleDebug")
    }

    @Test
    fun `dependencies are copied to the APK`() {
        execute("clean", "assembleDebug")
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk.file.toFile()).exists()
        for (abi in expectedAbis) {
            assertThatApk(apk).contains("lib/${abi.tag}/libapp.so")
            assertThatApk(apk).contains("lib/${abi.tag}/libcrypto.so")
            assertThatApk(apk).contains("lib/${abi.tag}/libcurl.so")
            assertThatApk(apk).contains("lib/${abi.tag}/libjsoncpp.so")
            assertThatApk(apk).contains("lib/${abi.tag}/libssl.so")
        }
    }
}