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
package com.android.build.gradle.internal.cxx.cmake

import com.android.testutils.TestUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CmakeFileApiV1Test {

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    private val bazelFolderBase = "tools/base/build-system/gradle-core/src/test/data/cmake-file-api-samples"

    private fun prepareReplyFolder(subFolder : String) : File {
        val base = TestUtils.getWorkspaceFile(bazelFolderBase)

        val result = base.resolve(subFolder)
        if (!result.isDirectory) {
            error(result.absolutePath)
        }
        return result
    }

    @Test
    fun prefabPublish() {
        val replyFolder = prepareReplyFolder("prefab-publish")
        val sources = mutableListOf<CmakeFileApiSourceFile>()
        val config =
                readCmakeFileApiReply(replyFolder) {
                    sources.add(it)
                }
        val library =
                config.libraries!!.values
                        .map { it.output!!.toString().replace("\\", "/") }
                        .single { it.endsWith(".a") }

        Truth.assertThat(library).endsWith(".cxx/cmake/debug/x86/libfoo_static.a")
    }

    @Test
    fun prefabExtractModelData() {
        val replyFolder = prepareReplyFolder("prefab")
        val sources = mutableListOf<CmakeFileApiSourceFile>()
        val config =
                readCmakeFileApiReply(replyFolder) {
                    sources.add(it)
                }
        val runtimeFiles = config.libraries!!.values
                .flatMap { it.runtimeFiles!!.toList() }
                .map { it.path.replace("\\", "/") }
                .distinct()
                .sorted()
                .joinToString("\n")

        Truth.assertThat(runtimeFiles).isEqualTo("""
            /{PREFAB}/jetified-curl/prefab/modules/curl/libs/android.x86_64/libcurl.so
            /{PREFAB}/jetified-jsoncpp/prefab/modules/jsoncpp/libs/android.x86_64/libjsoncpp.so
            /{PREFAB}/jetified-openssl/prefab/modules/crypto/libs/android.x86_64/libcrypto.so
            /{PREFAB}/jetified-openssl/prefab/modules/ssl/libs/android.x86_64/libssl.so
        """.trimIndent())
    }

    @Test
    fun simpleExtractModelData() {
        val replyFolder = prepareReplyFolder("simple")
        val sources = mutableListOf<CmakeFileApiSourceFile>()
        val config =
                readCmakeFileApiReply(replyFolder) {
                    sources.add(it)
                }

        val includes = sources.flatMap { it.compileGroup?.includes ?: listOf() }.distinct().sorted()
        Truth.assertThat(includes).isEmpty()

        val defines = sources.flatMap { it.compileGroup?.defines ?: listOf("(none)") }.distinct().sorted()
        Truth.assertThat(defines).contains("hello_jni_EXPORTS")

        val sysroots = sources.map { it.compileGroup?.sysroot ?: "(none)" }.distinct().sorted()
        Truth.assertThat(sysroots).hasSize(1)

        // The "(none)" language is "Header Files"
        val languageGroups = sources.map { it.compileGroup?.language ?: "(none)" }.distinct().sorted()
        Truth.assertThat(languageGroups).containsExactly("C")

        val sourceGroups = sources.map { it.sourceGroup }.distinct().sorted()
        Truth.assertThat(sourceGroups).containsExactly("Source Files")

        val symbolFoldersIndexContent = config
                .libraries!!
                .values
                .map { it.output!!.parent }
                .distinct()
                .sorted()
                .joinToString("\n")
                .replace("/Users/jomof/projects/dolphin/", "{PROJECT}/")
                .replace("\\", "/")
        Truth.assertThat(symbolFoldersIndexContent).isEqualTo("""
            /{PROJECT}/build/intermediates/cmake/debug/obj/x86_64
        """.trimIndent())

        val buildFilesIndexContent = config.buildFiles!!
            .map { it.path.replace("\\", "/") }
            .joinToString("\n")

        Truth.assertThat(buildFilesIndexContent).isEqualTo("""
            /{PROJECT}/CMakeLists.txt
        """.trimIndent())
    }

    @Test
    fun checkInferToolExeFromExistingTool() {
        Truth.assertThat(
                inferToolExeFromExistingTool("/path/to/ld.exe", "clang++")
                        .path.replace("\\", "/"))
                .isEqualTo("/path/to/clang++.exe")
        Truth.assertThat(
                inferToolExeFromExistingTool("/path/to/ld", "clang++")
                        .path.replace("\\", "/"))
                .isEqualTo("/path/to/clang++")
    }

    @Test
    fun dolphinExtractModelData() {
        val replyFolder = prepareReplyFolder("dolphin")
        val sources = mutableListOf<CmakeFileApiSourceFile>()
        val config =
                readCmakeFileApiReply(replyFolder) {
                    sources.add(it)
                }

        val includes = sources.flatMap { it.compileGroup?.includes ?: listOf() }.distinct().sorted()
        Truth.assertThat(includes).contains("/{PROJECT}/External/minizip")

        val compileCommandFragments = sources.flatMap { it.compileGroup?.compileCommandFragments ?: listOf() }.distinct().sorted()
        Truth.assertThat(compileCommandFragments).contains("-Wall")

        val defines = sources.flatMap { it.compileGroup?.defines ?: listOf("(none)") }.distinct().sorted()
        Truth.assertThat(defines).contains("ANDROID")
        Truth.assertThat(defines).contains("CIFACE_USE_ANDROID")

        // The "(none)" sysroot is "Header Files"
        val sysroots = sources.map { it.compileGroup?.sysroot ?: "(none)" }.distinct().sorted()
        Truth.assertThat(sysroots).containsExactly(
            "(none)",
            "/{SDK}/ndk/21.1.6352462/toolchains/llvm/prebuilt/darwin-x86_64/sysroot")

        // The "(none)" language is "Header Files"
        val languageGroups = sources.map { it.compileGroup?.language ?: "(none)" }.distinct().sorted()
        Truth.assertThat(languageGroups).containsExactly("(none)", "C", "CXX")

        val sourceGroups = sources.map { it.sourceGroup }.distinct().sorted()
        Truth.assertThat(sourceGroups).containsExactly("Header Files", "Source Files")

        val symbolFoldersIndexContent = config
                .libraries!!
                .values
                .mapNotNull { it.output?.parent?.replace("\\", "/") }
                .distinct()
                .sorted()
                .joinToString("\n")

        Truth.assertThat(symbolFoldersIndexContent).isEqualTo("""
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Binaries
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Binaries/Tests
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/FreeSurround
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/LZO
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/SFML
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/bzip2
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/cpp-optparse
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/cubeb
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/cubeb/CMakeFiles/speex.dir/src/speex
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/curl/lib
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/enet
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/fmt
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/glslang
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/gtest
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/imgui
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/libiconv-1.14
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/liblzma
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/libpng
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/mbedtls/library
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/minizip
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/pugixml
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/soundtouch
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Externals/xxhash
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/AudioCommon
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/Common
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/Core
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/DiscIO
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/InputCommon
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/UICommon
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/VideoBackends/Null
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/VideoBackends/OGL
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/VideoBackends/Software
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/VideoBackends/Vulkan
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/Core/VideoCommon
            /{PROJECT}/Source/Android/app/.cxx/cmake/debug/arm64-v8a/Source/UnitTests/CMakeFiles/unittests_stubhost.dir
            /{PROJECT}/Source/Android/app/build/intermediates/cmake/debug/obj/arm64-v8a
        """.trimIndent())

        val buildFilesIndexContent = config.buildFiles!!
            .map { it.toString().replace("\\", "/") }
            .distinct()
            .sorted()
            .joinToString("\n")

        Truth.assertThat(buildFilesIndexContent).isEqualTo("""
            /{PROJECT}/CMake/CCache.cmake
            /{PROJECT}/CMake/CheckAndAddFlag.cmake
            /{PROJECT}/CMake/CheckLib.cmake
            /{PROJECT}/CMake/DolphinCompileDefinitions.cmake
            /{PROJECT}/CMake/FindALSA.cmake
            /{PROJECT}/CMake/FindCubeb.cmake
            /{PROJECT}/CMake/FindEGL.cmake
            /{PROJECT}/CMake/FindFFmpeg.cmake
            /{PROJECT}/CMake/FindLibsystemd.cmake
            /{PROJECT}/CMake/FindMbedTLS.cmake
            /{PROJECT}/CMake/FindOpenSLES.cmake
            /{PROJECT}/CMake/FindPulseAudio.cmake
            /{PROJECT}/CMake/FindSFML.cmake
            /{PROJECT}/CMake/Findpugixml.cmake
            /{PROJECT}/CMakeLists.txt
            /{PROJECT}/Externals/FreeSurround/CMakeLists.txt
            /{PROJECT}/Externals/LZO/CMakeLists.txt
            /{PROJECT}/Externals/SFML/CMakeLists.txt
            /{PROJECT}/Externals/bzip2/CMakeLists.txt
            /{PROJECT}/Externals/cpp-optparse/CMakeLists.txt
            /{PROJECT}/Externals/cubeb/CMakeLists.txt
            /{PROJECT}/Externals/cubeb/Config.cmake.in
            /{PROJECT}/Externals/cubeb/cmake/sanitizers-cmake/cmake/FindASan.cmake
            /{PROJECT}/Externals/cubeb/cmake/sanitizers-cmake/cmake/FindMSan.cmake
            /{PROJECT}/Externals/cubeb/cmake/sanitizers-cmake/cmake/FindSanitizers.cmake
            /{PROJECT}/Externals/cubeb/cmake/sanitizers-cmake/cmake/FindTSan.cmake
            /{PROJECT}/Externals/cubeb/cmake/sanitizers-cmake/cmake/FindUBSan.cmake
            /{PROJECT}/Externals/cubeb/cmake/sanitizers-cmake/cmake/sanitize-helpers.cmake
            /{PROJECT}/Externals/curl/CMakeLists.txt
            /{PROJECT}/Externals/curl/lib/CMakeLists.txt
            /{PROJECT}/Externals/enet/CMakeLists.txt
            /{PROJECT}/Externals/fmt/CMakeLists.txt
            /{PROJECT}/Externals/fmt/support/cmake/cxx14.cmake
            /{PROJECT}/Externals/glslang/CMakeLists.txt
            /{PROJECT}/Externals/gtest/CMakeLists.txt
            /{PROJECT}/Externals/gtest/cmake/internal_utils.cmake
            /{PROJECT}/Externals/imgui/CMakeLists.txt
            /{PROJECT}/Externals/libiconv-1.14/CMakeLists.txt
            /{PROJECT}/Externals/liblzma/CMakeLists.txt
            /{PROJECT}/Externals/libpng/CMakeLists.txt
            /{PROJECT}/Externals/mbedtls/CMakeLists.txt
            /{PROJECT}/Externals/mbedtls/library/CMakeLists.txt
            /{PROJECT}/Externals/minizip/CMakeLists.txt
            /{PROJECT}/Externals/pugixml/CMakeLists.txt
            /{PROJECT}/Externals/soundtouch/CMakeLists.txt
            /{PROJECT}/Externals/xxhash/CMakeLists.txt
            /{PROJECT}/Source/Android/jni/CMakeLists.txt
            /{PROJECT}/Source/CMakeLists.txt
            /{PROJECT}/Source/Core/AudioCommon/CMakeLists.txt
            /{PROJECT}/Source/Core/CMakeLists.txt
            /{PROJECT}/Source/Core/Common/CMakeLists.txt
            /{PROJECT}/Source/Core/Core/CMakeLists.txt
            /{PROJECT}/Source/Core/DiscIO/CMakeLists.txt
            /{PROJECT}/Source/Core/InputCommon/CMakeLists.txt
            /{PROJECT}/Source/Core/UICommon/CMakeLists.txt
            /{PROJECT}/Source/Core/VideoBackends/CMakeLists.txt
            /{PROJECT}/Source/Core/VideoBackends/Null/CMakeLists.txt
            /{PROJECT}/Source/Core/VideoBackends/OGL/CMakeLists.txt
            /{PROJECT}/Source/Core/VideoBackends/Software/CMakeLists.txt
            /{PROJECT}/Source/Core/VideoBackends/Vulkan/CMakeLists.txt
            /{PROJECT}/Source/Core/VideoCommon/CMakeLists.txt
            /{PROJECT}/Source/UnitTests/CMakeLists.txt
            /{PROJECT}/Source/UnitTests/Common/CMakeLists.txt
            /{PROJECT}/Source/UnitTests/Core/CMakeLists.txt
            /{PROJECT}/Source/UnitTests/VideoCommon/CMakeLists.txt
        """.trimIndent())
    }

    @Test
    fun runtimeFilesExtractModelData() {
        val replyFolder = prepareReplyFolder("runtimefiles")
        val sources = mutableListOf<CmakeFileApiSourceFile>()
        val config =
                readCmakeFileApiReply(replyFolder) {
                    sources.add(it)
                }
        val content = config.libraries!!.values
                .flatMap { it.runtimeFiles?:listOf() }
                .map { it.toString().replace("\\", "/") }
                .distinct()
                .sorted()
                .joinToString("\n")

        Truth.assertThat(content).isEqualTo("""
            /{PROJECT}/build/intermediates/cmake/debug/obj/x86_64/libfoo.so
        """.trimIndent())
    }
}
