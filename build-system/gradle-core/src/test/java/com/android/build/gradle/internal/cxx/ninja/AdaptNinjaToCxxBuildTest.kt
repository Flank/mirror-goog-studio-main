/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.ninja

import com.android.SdkConstants.PLATFORM_LINUX
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.build.gradle.internal.cxx.explainLineDifferences
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini
import com.android.testutils.TestUtils
import com.android.utils.cxx.streamCompileCommands
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import com.android.build.gradle.internal.cxx.string.StringTable
import com.android.utils.cxx.CompileCommand
import java.util.Locale

class AdaptNinjaToCxxBuildTest {

    @Test
    fun `target with multiple passthroughs`() {
        val (config, _) = adaptNinja("""
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             rule MSBUILD
               command = msbuild ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build archive.a : CLANG source.cpp
             build libfoo.so : CLANG archive.a
             build libbar.so : CLANG archive.a
             build libfoo.so.passthrough : MSBUILD libfoo.so
             build libbar.so.passthrough : MSBUILD libbar.so
        """.trimIndent())

        assertThat(config.libraries.keys).containsExactly("bar", "foo", "archive")

        config.libraries.getValue("bar").apply {
            assertThat(artifactName).isEqualTo("bar")
            assertThat(hasPassthrough).isTrue()
            assertThat(output).isEqualTo(File("path/to/cxx/build/libbar.so"))
        }

        config.libraries.getValue("foo").apply {
            assertThat(artifactName).isEqualTo("foo")
            assertThat(hasPassthrough).isTrue()
            assertThat(output).isEqualTo(File("path/to/cxx/build/libfoo.so"))
        }

        config.libraries.getValue("archive").apply {
            assertThat(artifactName).isEqualTo("archive")
            assertThat(hasPassthrough).isFalse()
            assertThat(output).isEqualTo(File("path/to/cxx/build/archive.a"))
        }
    }

    @Test
    fun `'all' target may have another name'`() {
        val (config, _) = adaptNinja("""
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             rule MSBUILD
               command = msbuild ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build archive.a : CLANG source.cpp
             build libfoo.so : CLANG archive.a
             build libbar.so : CLANG archive.a
             build muiltiple: phony libfoo.so libbar.so
        """.trimIndent())

        assertThat(config.libraries.keys).containsExactly("bar", "foo", "archive")
    }

    @Test
    fun `'all' target may be in a subfolder'`() {
        val (config, _) = adaptNinja("""
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             rule MSBUILD
               command = msbuild ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build archive.a : CLANG source.cpp
             build libfoo.so : CLANG archive.a
             build subfolder/all: phony libfoo.so
        """.trimIndent())

        assertThat(config.libraries.keys).containsExactly("foo", "archive")
    }

    @Test
    fun `utility targets are discarded'`() {
        val (config, _) = adaptNinja("""
             rule CLEAN
               command = /path/tn/ninja -t clean
               description = Cleaning all built files...
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build libfoo.so : CLANG archive.o
             build clean : CLEAN
        """.trimIndent())

        assertThat(config.libraries.keys).containsExactly("foo")
    }

    @Test
    fun `simplest viable build dot ninja`() {
        val (config, compileCommandsSummary) = adaptNinja("""
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build lib.so : CLANG source.o
        """.trimIndent())

        config.libraries.getValue("lib").apply {
            assertThat(artifactName).isEqualTo("lib")
            assertThat(hasPassthrough).isFalse()
            assertThat(abi).isEqualTo("x86")
            assertThat(output).isEqualTo(File("path/to/cxx/build/lib.so"))
            assertThat(runtimeFiles).isEmpty()
        }
        assertThat(compileCommandsSummary).contains("Source-File: path/to/cxx/build/source.cpp")
    }

    @Test
    fun `simplest viable archive`() {
        val (config, compileCommandsSummary) = adaptNinja("""
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build archive.a : CLANG source.o
             build lib.so : CLANG archive.a
        """.trimIndent())

        config.libraries.getValue("archive").apply {
            assertThat(artifactName).isEqualTo("archive")
            assertThat(output).isEqualTo(File("path/to/cxx/build/archive.a"))
        }

        config.libraries.getValue("lib").apply {
            assertThat(artifactName).isEqualTo("lib")
            assertThat(output).isEqualTo(File("path/to/cxx/build/lib.so"))
        }
        assertThat(compileCommandsSummary).contains("Source-File: path/to/cxx/build/source.cpp")
    }

    @Test
    fun `simplest viable passthrough`() {
        val (config, _) = adaptNinja("""
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             rule MSBUILD
               command = msbuild ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build lib.so : CLANG source.o
             build lib.so.passthrough : MSBUILD lib.so
        """.trimIndent())

        config.libraries.getValue("lib").apply {
            assertThat(hasPassthrough).isTrue()
        }
    }

    @Test
    fun `shared C++ runtime file`() {
        val (config, compileCommandsSummary) = adaptNinja("""
             rule CLANG
               command = wrap.sh /ndk/clang ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build lib.so : CLANG source.o /path/to/ndk/libc++_shared.so
        """.trimIndent())

        config.libraries.getValue("lib").apply {
            assertThat(artifactName).isEqualTo("lib")
            assertThat(hasPassthrough).isFalse()
            assertThat(abi).isEqualTo("x86")
            assertThat(output).isEqualTo(File("path/to/cxx/build/lib.so"))
            assertThat(runtimeFiles.single()).isEqualTo(File("/path/to/ndk/libc++_shared.so"))
        }
        assertThat(compileCommandsSummary).contains("Compiler:    /ndk/clang")
    }

    @Test
    fun `simplest viable compiler wrapper`() {
        val (config, compileCommandsSummary) = adaptNinja("""
             rule CLANG
               command = wrap.sh /ndk/clang ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build lib.so : CLANG source.o
        """.trimIndent())

        config.libraries.getValue("lib").apply {
            assertThat(artifactName).isEqualTo("lib")
            assertThat(hasPassthrough).isFalse()
            assertThat(abi).isEqualTo("x86")
            assertThat(output).isEqualTo(File("path/to/cxx/build/lib.so"))
            assertThat(runtimeFiles).isEmpty()
        }
        assertThat(compileCommandsSummary).contains("Compiler:    /ndk/clang")
    }

    @Test
    fun `simplest viable conflict between library and archive`() {
        val (config, compileCommandsSummary) = adaptNinja("""
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build lib.a : CLANG source.o
             build lib.so : CLANG lib.a
        """.trimIndent())

        config.libraries.getValue("lib").apply {
            assertThat(artifactName).isEqualTo("lib")
            assertThat(output).isEqualTo(File("path/to/cxx/build/lib.so"))
        }
        assertThat(compileCommandsSummary).contains("Source-File: path/to/cxx/build/source.cpp")
    }

    /**
     * There is a passthrough [lib.passthrough] for a phony [lib] to the actual .so file.
     * The output file should still be 'lib.so' and not 'lib'.
     */
    @Test
    fun `simplest viable passthrough references phony`() {
        val (config, compileCommandsSummary) = adaptNinja("""
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             rule MSBUILD
               command = msbuild ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
             build lib.so : CLANG lib.a
             build alias : phony lib.so
             build alias.passthrough : MSBUILD source.cpp
        """.trimIndent())

        config.libraries.getValue("lib").apply {
            assertThat(artifactName).isEqualTo("lib")
            assertThat(hasPassthrough).isTrue()
            assertThat(output).isEqualTo(File("path/to/cxx/build/lib.so"))
        }
        assertThat(compileCommandsSummary).contains("Source-File: path/to/cxx/build/source.cpp")
    }

    @Test
    fun `check assign target name`() {
        assertThat(assignTargetName("lib.so")).isEqualTo("lib")
        assertThat(assignTargetName("LIB.so")).isEqualTo("LIB")
        assertThat(assignTargetName("lib.a")).isEqualTo("lib")
        assertThat(assignTargetName("LIB.a")).isEqualTo("LIB")
        assertThat(assignTargetName("lib")).isEqualTo("lib")
        assertThat(assignTargetName("libNAME.so")).isEqualTo("NAME")
        assertThat(assignTargetName("LIBname.SO")).isEqualTo("name")
        assertThat(assignTargetName("libNAME.a")).isEqualTo("NAME")
        assertThat(assignTargetName("LIBname.a")).isEqualTo("name")
        assertThat(assignTargetName("NAME.so")).isEqualTo("NAME")
        assertThat(assignTargetName("name.SO")).isEqualTo("name")
        assertThat(assignTargetName("NAME.a")).isEqualTo("NAME")
        assertThat(assignTargetName("name.A")).isEqualTo("name")
        assertThat(assignTargetName("executable")).isEqualTo("executable")
        assertThat(assignTargetName("/path/to/libfoo_static.a")).isEqualTo("foo_static")
        assertThat(assignTargetName("lib.xyz.so")).isEqualTo("lib.xyz")
        assertThat(assignTargetName("lib.xyz")).isEqualTo("lib.xyz")
    }


    @Test
    fun `check isPackageable`() {
        assertThat(isPackageable("libshared.so")).isTrue()
        assertThat(isPackageable("executable")).isTrue()
        assertThat(isPackageable("/path/to/ndk/sysroot/libEGL.so")).isFalse()
        assertThat(isPackageable("/path/to/ndk/runtime/libc++_shared.so")).isTrue()
        assertThat(isPackageable("libstatic.a")).isFalse()
        assertThat(isPackageable("C:/Users/jomof/AppData/Local/Android/Sdk/ndk/22.1.7171670/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")).isTrue()
        assertThat(isPackageable("/path/to/ndk/21.4.7075529/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/21/libEGL.so")).isFalse()
    }

    // Relevant test: CMakeBasicProjectTest#`bug 187448826 precompiled header works`
    @Test
    fun `check precompiled headers`() {
        val buildNinja = locate("misc/precompiled-header/build.ninja")
        adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        val body = summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "/path/to/sources",
            ndk = "/path/to/ndk"
        )
        assertThat(body).contains("Output-File: CMakeFiles/foo.dir/cmake_pch.hxx.pch")
    }

    // Relevant test: 'check target rename through transitive CMakeLists add_subdirectory'
    @Test
    fun `'all' target in sub folder`() {
        val buildNinja = locate("misc/all-target-2/build.ninja")

        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        assertThat(config.libraries.size).isEqualTo(1)
        assertThat(config.libraries["hello-jni"]).isNotNull()
        summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "/path/to/sources",
            ndk = "/path/to/ndk"
        )
    }

    @Test
    fun `missing static bug`() {
        val buildNinja = locate("misc/missing-static-bug/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        assertThat(config.libraries.size).isEqualTo(2)
        assertThat(config.libraries["foo_static"]).isNotNull()
        summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "/path/to/sources",
            ndk = "/path/to/ndk"
        )
    }

    @Test
    fun `wrapped clang bug`() {
        val buildNinja = locate("misc/compiler-wrapper/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        assertThat(config.libraries["hello-jni"]).isNotNull()
        val body = summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "../../../..",
            ndk = "/path/to/ndk"
        )
        assertThat(body).doesNotContain("wrapper.sh")
    }

    @Test
    fun `executable target works like SO target`() {
        val buildNinja = locate("misc/executable/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        assertThat(config.libraries["hello-executable"]).isNotNull()
        summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "../../../../src",
            ndk = "/path/to/ndk"
        )
    }

    @Test
    fun `sysroot files are not runtime files`() {
        val buildNinja = locate("misc/dolphin-sysroot/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        assertThat(config.libraries["main"]).isNotNull()
        assertThat(config.libraries["main"]!!.runtimeFiles).isEmpty()
        summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "../../../..",
            ndk = "/path/to/ndk"
        )
    }

    //@Test
    fun `perf test`() {
        val buildNinja = locate("misc/dolphin-sysroot/build.ninja")
        repeat(100) {
            adaptNinjaToCxxBuild(
                ninjaBuildFile = buildNinja,
                abi = "x86",
                cxxBuildFolder = File("path/to/cxx/build"),
                createNinjaCommand = ::createNinjaCommand,
                compileCommandsJsonBin = compileCommandsJsonBin,
                platform = PLATFORM_LINUX
            )
        }
    }

    @Test
    fun `runtime files should be propagated`() {
        val buildNinja = locate("misc/runtime-files/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        assertThat(config.libraries["bar"]!!.runtimeFiles.size).isEqualTo(1)
        assertThat(config.libraries["bar"]!!.runtimeFiles[0].toString().replace("\\", "/"))
            .isEqualTo("build/intermediates/cxx/Debug/6ih1n2i6/obj/x86_64/libfoo.so")
        summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "../../../..",
            ndk = "/path/to/ndk"
        )
    }

    @Test
    fun `'all' target should be eliminated`() {
        val buildNinja = locate("misc/all-target/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        assertThat(config.libraries).hasSize(1)
        assertThat(config.libraries["all"]).isNull()
        summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "../../../../src",
            ndk = "/path/to/ndk"
        )
    }

    @Test
    fun `dolphin via CMake 3 10 2`() {
        val buildNinja = locate("dolphin/3.10.2/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        assertThat(config.buildFiles.map { it.name }.distinct()).contains("CMakeLists.txt")
        val body = summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "../../../..",
            ndk = "/Users/jomof/projects/studio-main/out/build/base/build-system/integration-test/native/build/ndk"
        )
        // If [body] gets large it may be a sign that flags aren't being coalesced between different
        // compile commands.
        assertThat(body.length).isLessThan(270_000)
    }

    @Test
    fun `dolphin via CMake 3 18 1`() {
        val buildNinja = locate("dolphin/3.18.1/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        assertThat(config.buildFiles.map { it.name }.distinct()).contains("CMakeLists.txt")
        val body = summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "../../../..",
            ndk = "/Users/jomof/projects/studio-main/out/build/base/build-system/" +
                    "integration-test/native/build/ndk"
        )
        // If [body] gets large it may be a sign that flags aren't being coalesced between different
        // compile commands.
        assertThat(body.length).isLessThan(270_000)
    }

    @Test
    fun `dolphin via AGDE 21 2 canary`() {
        val buildNinja = locate("dolphin/agde-21.2-canary/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_WINDOWS
        )
        assertThat(config.buildFiles.map { it.name }.distinct()).contains("Android.vcxproj")
        assertThat(config.libraries["Android"]!!.artifactName).isEqualTo("Android")
        assertThat(config.libraries["Android"]!!.hasPassthrough).isTrue()
        val body = summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "C:/src/AndroidVisualStudioExtension/third_party/dolphin",
            ndk = "C:/Users/jomof/AppData/Local/Android/Sdk/ndk"
        )
        assertThat(body).doesNotContain(
            "Source-File: [SOURCE ROOT]/Build/Android-x86_64/Debug/pch/pch.pch")
        // If [body] gets large it may be a sign that flags aren't being coalesced between different
        // compile commands.
        assertThat(body.length).isLessThan(250_000)
    }

    @Test
    fun `teapots AGDE 21 2 canary`() {
        val buildNinja = locate("teapot/agde-21.2-canary/build.ninja")
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_WINDOWS
        )
        assertThat(config.buildFiles.map { it.name }.distinct()).contains("GameEngine.vcxproj")
        config.libraries["GameApplication"]!!.apply {
            assertThat(hasPassthrough).isTrue()
            assertThat(output!!.name).isEqualTo("libGameApplication.so")
        }
        config.libraries["GameEngine"]!!.apply {
            assertThat(hasPassthrough).isTrue()
            assertThat(output!!.name).isEqualTo("libGameEngine.a")
        }
        val body = summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "C:/src/Teapot",
            ndk = "C:/Users/jomof/AppData/Local/Android/Sdk/ndk"
        )
        assertThat(body).contains("GameApplication.cpp")
    }

    @Test
    fun `assembly code via AGDE 21 2 canary`() {
        val buildNinja = locate("misc/assembly-code/build.ninja")

        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_WINDOWS
        )
        assertThat(config.buildFiles.map { it.name }.distinct()).contains("AssemblyCode-Link-Objects.vcxproj")
        assertThat(config.libraries["AssemblyCode-Link-Objects"]!!.artifactName).isEqualTo("AssemblyCode-Link-Objects")
        assertThat(config.libraries["AssemblyCode-Link-Objects"]!!.hasPassthrough).isTrue()
        summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "C:/src/AndroidVisualStudioExtension/TestOut/0b47e838",
            ndk = "C:/Users/jomof/AppData/Local/Android/Sdk/ndk"
        )
    }

    @Test
    fun `orphan dot o should not crash`() {
        val buildNinja = tempFolder.newFile()
        buildNinja.parentFile.mkdir()
        buildNinja.writeText("""
             rule CLANG
               command = /ndk/clang ${'$'}in -o ${'$'}out
             build source.o : CLANG source.cpp
        """.trimIndent())
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        val body = summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "undefined",
            ndk = "undefined",
            compareSnapshot = false
        )
        assertThat(body).contains("Source-File: path/to/cxx/build/source.cpp")
    }

    private fun adaptNinja(ninja : String): Pair<NativeBuildConfigValueMini, String> {
        val buildNinja = File(tempFolder.newFolder(), "build.ninja")
        buildNinja.parentFile.mkdir()
        buildNinja.writeText(ninja)
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = buildNinja,
            abi = "x86",
            cxxBuildFolder = File("path/to/cxx/build"),
            createNinjaCommand = ::createNinjaCommand,
            compileCommandsJsonBin = compileCommandsJsonBin,
            platform = PLATFORM_LINUX
        )
        val compileCommandsSummary = summarizeCompileCommandsJsonBin(
            buildNinja = buildNinja,
            sourceRoot = "undefined",
            ndk = "undefined",
            compareSnapshot = false
        )
        return config to compileCommandsSummary
    }

    private fun summarizeCompileCommandsJsonBin(
        buildNinja : File,
        sourceRoot : String,
        ndk : String,
        compareSnapshot: Boolean = true
    ) : String {

        val sb = StringBuilder()
        sb.appendLine("# Generated by ${AdaptNinjaToCxxBuildTest::class.simpleName}")
        sb.appendLine("# - The first time a distinct source file appears it is assigned a sequential ID with (sourceXX)")
        sb.appendLine("# - After that, [sourceXX] is used as shorthand for that source file")
        sb.appendLine("# - There is a similar behavior for workdir, toolchain, etc.")
        sb.appendLine("# This is to shorten the file and to make it more evident when unique values appear.")

        sb.appendLine()
        val sources = StringTable()
        val out = StringTable()
        val workdir = StringTable()
        val toolchain = StringTable()
        val lists = StringTable()
        val commands = mutableListOf<CompileCommand>()
        streamCompileCommands(compileCommandsJsonBin) {
            commands.add(this)
        }
        sb.appendLine("# There are ${commands.size} entries.")
        commands.sortedBy {
            it.outputFile.path.lowercase(Locale.getDefault())
                .replace("\\", "/")
        }.forEach { command ->
            sb.appendLine("Source-File: ${sources.text(command.sourceFile, "source")}")
            sb.appendLine("Output-File: ${out.text(command.outputFile, "out")}")
            sb.appendLine("Working-Dir: ${workdir.text(command.workingDirectory, "workdir")}")
            sb.appendLine("Compiler:    ${toolchain.text(command.compiler, "toolchain")}")
            sb.append(lists.text(command.flags))
            sb.appendLine()
            sb.appendLine()
        }
        val currentText = sb.toString()
            .replace(sourceRoot, "[SOURCE ROOT]")
            .replace(ndk, "[NDK]")

        if (compareSnapshot) {
            val original = buildNinja.resolveSibling("compile_commands_summary.txt")
            if (original.isFile) {
                val originalText = original.readText()
                if (currentText != originalText) {
                    // Fail the first time but leave the file changed to the new content
                    original.writeText(currentText)
                    error(
                        "$original content changed: \n${
                            explainLineDifferences(
                                originalText,
                                currentText
                            )
                        }"
                    )
                }
            } else {
                original.parentFile.mkdirs()
                original.writeText(currentText)
            }
        }
        return currentText
    }

    private fun StringTable.text(value : File, tag : String) : String {
        var created = false
        val id = getIdCreateIfAbsent(value.path) {
            created = true
        }
        return if (created) {
            "$value ($tag$id)".replace("\\", "/")
        } else {
            "[$tag$id]"
        }
    }

    private fun StringTable.text(value : List<String>) : String {
        if (value.isEmpty()) return "             [empty flags]"
        var created = false
        val render = value
            .joinToString("\n") {
                "              $it"
            }
        val id = getIdCreateIfAbsent(render) {
            created = true
        }
        return if (created) {
            "$render (flags$id)"
        } else {
            "             [flags$id]"
        }
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val bazelFolderBase = "tools/base/build-system/gradle-core/src/test/data/ninja-build-samples"
    private val compileCommandsJsonBin by lazy { File(tempFolder.newFolder(), "compile_commands.json.bin") }

    private fun locate(subFolder : String) : File {
        val base = TestUtils.resolveWorkspacePath(bazelFolderBase).toFile()
        return base.resolve(subFolder)
    }
    private fun createNinjaCommand(args : List<String>) : List<String> = listOf("build", "targets", "command") + args
    private val NativeLibraryValueMini.hasPassthrough get() =
        buildCommandComponents?.any { it.contains(".passthrough" ) } ?: false
}
