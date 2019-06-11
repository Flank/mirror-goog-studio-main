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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.external.gnumake.PosixFileConventions
import com.android.build.gradle.external.gnumake.WindowsFileConventions
import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.*
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.BinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeListsPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.DefineProperty
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.GeneratorName
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.UnknownArgument
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CmakeCommandLineKtTest {

    @Test
    fun checkDefine() {
        val parsed = parseCmakeArguments(listOf("-DX=Y"))
        assertThat(parsed).containsExactly(
            DefineProperty("-DX=Y", "X", "Y")
        )
        val arg = parsed.single() as DefineProperty
        assertThat(arg.sourceArgument).isEqualTo("-DX=Y")
        assertThat(arg.propertyName).isEqualTo("X")
        assertThat(arg.propertyValue).isEqualTo("Y")
    }

    @Test
    fun checkCmakeListsPath() {
        val parsed = parseCmakeArguments(listOf("-H<path-to-cmakelists>"))
        assertThat(parsed).containsExactly(
            CmakeListsPath("-H<path-to-cmakelists>", "<path-to-cmakelists>"))
        val arg = parsed.single() as CmakeListsPath
        assertThat(arg.sourceArgument).isEqualTo("-H<path-to-cmakelists>")
        assertThat(arg.path).isEqualTo("<path-to-cmakelists>")
    }

    @Test
    fun checkBinaryOutputPath() {
        val parsed = parseCmakeArguments(listOf("-B<path-to-binary>"))
        assertThat(parsed).containsExactly(
            BinaryOutputPath("-B<path-to-binary>", "<path-to-binary>"))
        val arg = parsed.single() as BinaryOutputPath
        assertThat(arg.sourceArgument).isEqualTo("-B<path-to-binary>")
        assertThat(arg.path).isEqualTo("<path-to-binary>")
    }

    @Test
    fun checkGeneratorName() {
        val parsed = parseCmakeArguments(listOf("-GAndroid Gradle - Ninja"))
        assertThat(parsed).containsExactly(
            GeneratorName("-GAndroid Gradle - Ninja", "Android Gradle - Ninja"))
        val arg = parsed.single() as GeneratorName
        assertThat(arg.sourceArgument).isEqualTo("-GAndroid Gradle - Ninja")
        assertThat(arg.generator).isEqualTo("Android Gradle - Ninja")
    }

    @Test
    fun checkUnknownArgument() {
        val parsed = parseCmakeArguments(listOf("-X"))
        assertThat(parsed).containsExactly(
            UnknownArgument("-X"))
        val arg = parsed.single() as UnknownArgument
        assertThat(arg.sourceArgument).isEqualTo("-X")
    }

    @Test
    fun definePropertyFrom() {
        val property = DefineProperty.from(ANDROID_NDK, "xyz")
        assertThat(property.sourceArgument).isEqualTo("-DANDROID_NDK=xyz")
    }

    @Test
    fun cmakeListsFrom() {
        val property = CmakeListsPath.from("xyz")
        assertThat(property.sourceArgument).isEqualTo("-Hxyz")
    }

    @Test
    fun hasBooleanPropertySet() {
        val prop = ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED
        val definedTrue = DefineProperty.from(prop, "true")
        val definedFalse = DefineProperty.from(prop, "false")
        assertThat(listOf<CommandLineArgument>().getCmakeBooleanProperty(prop)).isNull()
        assertThat(listOf(definedTrue).getCmakeBooleanProperty(prop)).isTrue()
        assertThat(listOf(definedFalse).getCmakeBooleanProperty(prop)).isFalse()
        assertThat(listOf(definedFalse, definedTrue).getCmakeBooleanProperty(prop)).isTrue()
        assertThat(listOf(definedTrue, definedFalse).getCmakeBooleanProperty(prop)).isFalse()
    }

    @Test
    fun getCmakeProperty() {
        val toolchain = CmakeListsPath.from("path")
        val buildType = DefineProperty.from(CMAKE_BUILD_TYPE, "type")
        val got = listOf(toolchain, buildType).getCmakeProperty(CMAKE_BUILD_TYPE)
        assertThat(got).isEqualTo("type")
    }

    @Test
    fun getCmakeListsPathValue() {
        val toolchain = CmakeListsPath.from("path")
        val buildType = DefineProperty.from(CMAKE_BUILD_TYPE, "type")
        val got = listOf(toolchain, buildType).getCmakeListsFolder()
        assertThat(got).isEqualTo("path")
    }

    @Test
    fun removeProperty() {
        val toolchain = CmakeListsPath.from("path")
        val buildType = DefineProperty.from(CMAKE_BUILD_TYPE, "type")
        val got = listOf(toolchain, buildType).removeProperty(CMAKE_BUILD_TYPE)
        assertThat(got).isEqualTo(listOf(toolchain))
    }

    @Test
    fun convertCmakeCommandLineArgumentsToStringList() {
        val arguments = listOf(
            DefineProperty.from(CMAKE_ANDROID_NDK, "ndk"))
        assertThat(arguments.convertCmakeCommandLineArgumentsToStringList())
            .isEqualTo(listOf("-D$CMAKE_ANDROID_NDK=ndk"))
    }

    @Test
    fun `parse command-line -D`() {
        val arguments = parseCmakeCommandLine("-DA=1 -D B=2")
        assertThat(arguments.getCmakeProperty("A")).isEqualTo("1")
        assertThat(arguments.getCmakeProperty("B")).isEqualTo("2")
        assertThat(arguments).hasSize(2)
    }

    @Test
    fun `parse command-line -G`() {
        val arguments = parseCmakeCommandLine("-DX=Y -G Ninja -GNinja")
        assertThat(arguments.getGenerator()).isEqualTo("Ninja")
    }

    @Test
    fun `parse command-line -B`() {
        val arguments = parseCmakeCommandLine("-B/usr/path -DX=Y")
        assertThat(arguments.getBuildRootFolder()).isEqualTo("/usr/path")
    }

    @Test
    fun `parse command-line -H`() {
        val arguments = parseCmakeCommandLine("-H/usr/path -G Ninja")
        assertThat(arguments.getCmakeListsFolder()).isEqualTo("/usr/path")
    }

    @Test
    fun `keep CMake Server arguments`() {
        val arguments =
            parseCmakeCommandLine("-H/usr/path -G Ninja -DX=Y")
                .onlyKeepServerArguments()
        assertThat(arguments.getCmakeProperty("X")).isEqualTo("Y")
        assertThat(arguments).hasSize(1)
    }

    @Test
    fun `remove subsumed properties`() {
        val arguments =
            parseCmakeCommandLine("-D X=1 -DX=2")
                .removeSubsumedArguments()
        assertThat(arguments.getCmakeProperty("X")).isEqualTo("2")
        assertThat(arguments).hasSize(1)
    }

    @Test
    fun `remove subsumed generator`() {
        val arguments =
            parseCmakeCommandLine("-GA -GB")
                .removeSubsumedArguments()
        assertThat(arguments.getGenerator()).isEqualTo("B")
        assertThat(arguments).hasSize(1)
    }

    @Test
    fun `remove blank properties`() {
        val arguments =
            parseCmakeCommandLine("-DX=\"\"")
                .removeBlankProperties()
        assertThat(arguments).hasSize(0)
    }

    @Test
    fun `property with quotes gets unquoted`() {
        val arguments =
            parseCmakeCommandLine("-DX=\"1\" -D Y=\"2\"")
        assertThat(arguments.getCmakeProperty("X")).isEqualTo("1")
        assertThat(arguments.getCmakeProperty("Y")).isEqualTo("2")
        assertThat(arguments).hasSize(2)
    }

    @Test
    fun `fuzz CMake command-line parser`() {
        // Ideally, this test should cover almost all lines in CMakeCommandLine.kt
        RandomInstanceGenerator().strings(10000).forEach { commandLine ->
            val windows = parseCmakeCommandLine(commandLine, WindowsFileConventions())
            val posix = parseCmakeCommandLine(commandLine, PosixFileConventions())
            windows.onlyKeepServerArguments()
            posix.onlyKeepServerArguments()
            windows.removeSubsumedArguments()
            posix.removeSubsumedArguments()
            windows.removeBlankProperties()
            posix.removeBlankProperties()
            windows.getCmakeBooleanProperty(C_TEST_WAS_RUN)
            posix.getCmakeBooleanProperty(C_TEST_WAS_RUN)
            windows.removeProperty(C_TEST_WAS_RUN)
            posix.removeProperty(C_TEST_WAS_RUN)
            windows.getGenerator()
            posix.getGenerator()
            windows.getBuildRootFolder()
            posix.getBuildRootFolder()
            windows.getCmakeListsFolder()
            posix.getCmakeListsFolder()
            val windowsStrings = windows.convertCmakeCommandLineArgumentsToStringList()
            val posixStrings = posix.convertCmakeCommandLineArgumentsToStringList()
            parseCmakeArguments(windowsStrings)
            parseCmakeArguments(posixStrings)
        }
    }
}