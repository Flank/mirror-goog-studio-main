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
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.C_TEST_WAS_RUN
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeBinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeGeneratorName
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeListsPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.DefineProperty
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.NdkBuildAppendProperty
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.NdkBuildJobs
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.UnknownArgument
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildSystemCommandLineKtTest {

    @Test
    fun `ndk-build check define property`() {
        val parsed = listOf("X=Y").toNdkBuildArguments()
        assertThat(parsed).containsExactly(
                DefineProperty("X=Y", "X", "Y")
        )
        val arg = parsed.single() as DefineProperty
        assertThat(arg.sourceArgument).isEqualTo("X=Y")
        assertThat(arg.propertyName).isEqualTo("X")
        assertThat(arg.propertyValue).isEqualTo("Y")
    }

    @Test
    fun `ndk-build check subsumed doesn't removed additive`() {
        val parsed = listOf("X+=Y", "X+=Z").toNdkBuildArguments().removeSubsumedArguments()
        assertThat(parsed).containsExactly(
                NdkBuildAppendProperty("X+=Y", "X", "Y"),
                NdkBuildAppendProperty("X+=Z", "X", "Z"),
        )
        val arg = parsed.first() as NdkBuildAppendProperty
        assertThat(arg.sourceArgument).isEqualTo("X+=Y")
        assertThat(arg.listProperty).isEqualTo("X")
        assertThat(arg.flagValue).isEqualTo("Y")
    }

    @Test
    fun `ndk-build --jobs flag`() {
        for (jobsFlag in listOf("--jobs=5", "--jobs 5", "-j5", "-j 5")) {
            val args = listOf("A=B", jobsFlag, "C=D").toNdkBuildArguments()
            assertThat((args[1] as NdkBuildJobs).jobs).isEqualTo("5")
            val removed = args.removeNdkBuildJobs().toStringList()
            assertThat(removed).containsExactly("A=B", "C=D")
        }
    }

    @Test
    fun cmakeCheckDefine() {
        val parsed = listOf("-DX=Y").toCmakeArguments()
        assertThat(parsed).containsExactly(
            DefineProperty("-DX=Y", "X", "Y")
        )
        val arg = parsed.single() as DefineProperty
        assertThat(arg.sourceArgument).isEqualTo("-DX=Y")
        assertThat(arg.propertyName).isEqualTo("X")
        assertThat(arg.propertyValue).isEqualTo("Y")
    }

    @Test
    fun cmakeCheckCmakeListsPath() {
        val parsed = listOf("-H<path-to-cmakelists>").toCmakeArguments()
        assertThat(parsed).containsExactly(
            CmakeListsPath("-H<path-to-cmakelists>", "<path-to-cmakelists>"))
        val arg = parsed.single() as CmakeListsPath
        assertThat(arg.sourceArgument).isEqualTo("-H<path-to-cmakelists>")
        assertThat(arg.path).isEqualTo("<path-to-cmakelists>")
    }

    @Test
    fun cmakeCheckBinaryOutputPath() {
        val parsed = listOf("-B<path-to-binary>").toCmakeArguments()
        assertThat(parsed).containsExactly(
            CmakeBinaryOutputPath("-B<path-to-binary>", "<path-to-binary>"))
        val arg = parsed.single() as CmakeBinaryOutputPath
        assertThat(arg.sourceArgument).isEqualTo("-B<path-to-binary>")
        assertThat(arg.path).isEqualTo("<path-to-binary>")
    }

    @Test
    fun cmakeCheckGeneratorName() {
        val parsed = listOf("-GAndroid Gradle - Ninja").toCmakeArguments()
        assertThat(parsed).containsExactly(
            CmakeGeneratorName("-GAndroid Gradle - Ninja", "Android Gradle - Ninja"))
        val arg = parsed.single() as CmakeGeneratorName
        assertThat(arg.sourceArgument).isEqualTo("-GAndroid Gradle - Ninja")
        assertThat(arg.generator).isEqualTo("Android Gradle - Ninja")
    }

    @Test
    fun cmakeCheckUnknownArgument() {
        val parsed = listOf("-X").toCmakeArguments()
        assertThat(parsed).containsExactly(
            UnknownArgument("-X"))
        val arg = parsed.single() as UnknownArgument
        assertThat(arg.sourceArgument).isEqualTo("-X")
    }

    @Test
    fun cmakeDefinePropertyFrom() {
        val property = DefineProperty.from(ANDROID_NDK, "xyz")
        assertThat(property.sourceArgument).isEqualTo("-DANDROID_NDK=xyz")
    }

    @Test
    fun cmakeListsFrom() {
        val property = CmakeListsPath.from("xyz")
        assertThat(property.sourceArgument).isEqualTo("-Hxyz")
    }

    @Test
    fun cmakeHasBooleanPropertySet() {
        val prop = C_TEST_WAS_RUN
        val definedTrue = DefineProperty.from(prop, "true")
        val definedFalse = DefineProperty.from(prop, "false")
        assertThat(listOf<CommandLineArgument>().getCmakeBooleanProperty(prop)).isNull()
        assertThat(listOf(definedTrue).getCmakeBooleanProperty(prop)).isTrue()
        assertThat(listOf(definedFalse).getCmakeBooleanProperty(prop)).isFalse()
        assertThat(listOf(definedFalse, definedTrue).getCmakeBooleanProperty(prop)).isTrue()
        assertThat(listOf(definedTrue, definedFalse).getCmakeBooleanProperty(prop)).isFalse()
    }

    @Test
    fun cmakeGetProperty() {
        val toolchain = CmakeListsPath.from("path")
        val buildType = DefineProperty.from(CMAKE_BUILD_TYPE, "type")
        val got = listOf(toolchain, buildType).getCmakeProperty(CMAKE_BUILD_TYPE)
        assertThat(got).isEqualTo("type")
    }

    @Test
    fun cmakeGetCmakeListsPathValue() {
        val toolchain = CmakeListsPath.from("path")
        val buildType = DefineProperty.from(CMAKE_BUILD_TYPE, "type")
        val got = listOf(toolchain, buildType).getCmakeListsFolder()
        assertThat(got).isEqualTo("path")
    }

    @Test
    fun cmakeRemoveProperty() {
        val toolchain = CmakeListsPath.from("path")
        val buildType = DefineProperty.from(CMAKE_BUILD_TYPE, "type")
        val got = listOf(toolchain, buildType).removeCmakeProperty(CMAKE_BUILD_TYPE)
        assertThat(got).isEqualTo(listOf(toolchain))
    }

    @Test
    fun convertCmakeCommandLineArgumentsToStringList() {
        val arguments = listOf(
            DefineProperty.from(CMAKE_ANDROID_NDK, "ndk"))
        assertThat(arguments.toStringList())
            .isEqualTo(listOf("-D$CMAKE_ANDROID_NDK=ndk"))
    }

    @Test
    fun `CMake parse command-line -D`() {
        val arguments = parseCmakeCommandLine("-DA=1 -D B=2")
        assertThat(arguments.getProperty("A")).isEqualTo("1")
        assertThat(arguments.getProperty("B")).isEqualTo("2")
        assertThat(arguments).hasSize(2)
    }

    @Test
    fun `CMake bug 159434435--check known and unknown flag combinability`() {
        assertThat(cmakeFlagLooksCombinable("-N")).isFalse()
        assertThat(cmakeFlagLooksCombinable("-D")).isTrue()
        assertThat(cmakeFlagLooksCombinable("-X")).isTrue()
        assertThat(cmakeFlagLooksCombinable("--XYZ")).isFalse()
        assertThat(cmakeFlagLooksCombinable("-x")).isFalse()
        assertThat(cmakeFlagLooksCombinable("--")).isFalse()
    }

    @Test
    fun `CMake bug 159434435--test unknown command-line arg`() {
        val arguments = parseCmakeCommandLine("-CD:\\Test\\TargetProperties.cmake")
        println(arguments)
        assertThat(arguments[0]).isEqualTo(UnknownArgument(sourceArgument = "-CD:\\Test\\TargetProperties.cmake"))
    }

    @Test
    fun `CMake bug 159434435--test unknown command-line arg with space`() {
        val arguments = parseCmakeCommandLine("-C D:\\Test\\TargetProperties.cmake")
        println(arguments)
        assertThat(arguments[0]).isEqualTo(UnknownArgument(sourceArgument = "-C D:\\Test\\TargetProperties.cmake"))
    }

    @Test
    fun `CMake parse command-line -G`() {
        val arguments = parseCmakeCommandLine("-DX=Y -G Ninja -GNinja")
        assertThat(arguments.getCmakeGenerator()).isEqualTo("Ninja")
    }

    @Test
    fun `CMake parse command-line -B`() {
        val arguments = parseCmakeCommandLine("-B/usr/path -DX=Y")
        assertThat(arguments.getCmakeBinaryOutputPath()).isEqualTo("/usr/path")
    }

    @Test
    fun `CMake parse command-line -H`() {
        val arguments = parseCmakeCommandLine("-H/usr/path -G Ninja")
        assertThat(arguments.getCmakeListsFolder()).isEqualTo("/usr/path")
    }

    @Test
    fun `CMake keep CMake Server arguments`() {
        val arguments =
            parseCmakeCommandLine("-H/usr/path -G Ninja -DX=Y")
                .onlyKeepCmakeServerArguments()
        assertThat(arguments.getProperty("X")).isEqualTo("Y")
        assertThat(arguments).hasSize(1)
    }

    @Test
    fun `CMake remove subsumed properties`() {
        val arguments =
            parseCmakeCommandLine("-D X=1 -DX=2")
                .removeSubsumedArguments()
        assertThat(arguments.getProperty("X")).isEqualTo("2")
        assertThat(arguments).hasSize(1)
    }

    @Test
    fun `CMake remove subsumed generator`() {
        val arguments =
            parseCmakeCommandLine("-GA -GB")
                .removeSubsumedArguments()
        assertThat(arguments.getCmakeGenerator()).isEqualTo("B")
        assertThat(arguments).hasSize(1)
    }

    @Test
    fun `CMake remove blank properties`() {
        val arguments =
            parseCmakeCommandLine("-DX=\"\"")
                .removeBlankProperties()
        assertThat(arguments).hasSize(0)
    }

    @Test
    fun `CMake property with quotes gets unquoted`() {
        val arguments =
            parseCmakeCommandLine("-DX=\"1\" -D Y=\"2\"")
        assertThat(arguments.getProperty("X")).isEqualTo("1")
        assertThat(arguments.getProperty("Y")).isEqualTo("2")
        assertThat(arguments).hasSize(2)
    }

    @Test
    fun `fuzz ndk-build command-line parser`() {
        // Ideally, this test should cover almost all lines in CMakeCommandLine.kt
        RandomInstanceGenerator().strings(10000).forEach { argument ->
            argument.toNdkBuildArgument()
            listOf(argument).toNdkBuildArguments()
        }
    }

    @Test
    fun `fuzz CMake command-line parser`() {
        // Ideally, this test should cover almost all lines in CMakeCommandLine.kt
        RandomInstanceGenerator().strings(10000).forEach { commandLine ->
            parseCmakeCommandLine(commandLine) // Host conventions
            val windows = parseCmakeCommandLine(commandLine, WindowsFileConventions())
            val posix = parseCmakeCommandLine(commandLine, PosixFileConventions())
            windows.onlyKeepUnknownArguments()
            posix.onlyKeepUnknownArguments()
            windows.onlyKeepProperties()
            posix.onlyKeepProperties()
            windows.onlyKeepCmakeServerArguments()
            posix.onlyKeepCmakeServerArguments()
            windows.removeSubsumedArguments()
            posix.removeSubsumedArguments()
            windows.removeBlankProperties()
            posix.removeBlankProperties()
            windows.getCmakeBooleanProperty(C_TEST_WAS_RUN)
            posix.getCmakeBooleanProperty(C_TEST_WAS_RUN)
            windows.removeCmakeProperty(C_TEST_WAS_RUN)
            posix.removeCmakeProperty(C_TEST_WAS_RUN)
            windows.getCmakeGenerator()
            posix.getCmakeGenerator()
            windows.getCmakeBinaryOutputPath()
            posix.getCmakeBinaryOutputPath()
            windows.getCmakeListsFolder()
            posix.getCmakeListsFolder()
            val windowsStrings = windows.toStringList()
            val posixStrings = posix.toStringList()
            windowsStrings.toCmakeArguments()
            posixStrings.toCmakeArguments()
        }
    }
}
