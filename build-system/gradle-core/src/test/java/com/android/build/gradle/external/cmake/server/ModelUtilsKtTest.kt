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

package com.android.build.gradle.external.cmake.server

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test
import java.io.File

class ModelUtilsKtTest {

    /**
     * The repro configuration is:
     * 1) 'buildDirectory' is missing
     * 2) 'linkLibraries' contains a path that is not absolute
     */
    @Test
    fun `bug 188860472 NullReferenceException`() {
        // buildDirectory is intentionally missing
        val target = Gson().fromJson("""
            {
                "linkLibraries": "../relative/path/to/lib.so",
                "type": "SHARED_LIBRARY",
                "sysroot": "[ndk]/toolchains/llvm/prebuilt/darwin-x86_64/sysroot"
            }
        """.trimIndent(), Target::class.java)
        val result = target.findRuntimeFiles()
        assertThat(result).isEmpty()
    }

    @Test
    fun `findRuntimeFiles absolute library`() {
        val target = Gson().fromJson("""
            {
                "artifacts": [
                  "[tests]/VulkanTest/assembleDebug/vulkan/build/intermediates/cxx/Debug/305y6h22/obj/x86_64/libvktuts.so"
                ],
                "buildDirectory": "[tests]/VulkanTest/assembleDebug/vulkan/.cxx/Debug/305y6h22/x86_64",
                "fileGroups": [
                  {
                    "compileFlags": "-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE\u003d2 -Wformat -Werror\u003dformat-security  -std\u003dc++11 -Werror                     -Wno-unused-variable                     -DVK_USE_PLATFORM_ANDROID_KHR -O0 -fno-limit-debug-info -fPIC ",
                    "isGenerated": false,
                    "language": "CXX",
                    "sources": [
                      "src/main/jni/VulkanMain.cpp",
                      "src/main/jni/AndroidMain.cpp",
                      "src/main/jni/vulkan_wrapper.cpp"
                    ],
                    "defines": [
                      "vktuts_EXPORTS"
                    ],
                    "includePath": [
                      {
                        "path": "[ndk]/sources/android/native_app_glue"
                      }
                    ]
                  }
                ],
                "fullName": "libvktuts.so",
                "linkLibraries": "libapp-glue.a -llog -landroid -latomic -lm",
                "linkerLanguage": "CXX",
                "name": "vktuts",
                "sourceDirectory": "[tests]/VulkanTest/assembleDebug/vulkan",
                "type": "SHARED_LIBRARY",
                "linkFlags": "-Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgcc_real.a -Wl,--exclude-libs,libatomic.a -static-libstdc++ -Wl,--build-id -Wl,--fatal-warnings -Wl,--no-undefined -Qunused-arguments -u ANativeActivity_onCreate",
                "sysroot": "[ndk]/toolchains/llvm/prebuilt/darwin-x86_64/sysroot"
              }
        """.trimIndent(), Target::class.java)
        val result = target.findRuntimeFiles()
        assertThat(result).isEmpty()
    }

    @Test
    fun `findRuntimeFiles link library missing`() {
        val target = Gson().fromJson("""
            {
                "artifacts": [
                  "[tests]/VulkanTest/assembleDebug/vulkan/.cxx/Debug/305y6h22/x86_64/libapp-glue.a"
                ],
                "buildDirectory": "[tests]/VulkanTest/assembleDebug/vulkan/.cxx/Debug/305y6h22/x86_64",
                "fileGroups": [
                  {
                    "compileFlags": "-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE\u003d2 -Wformat -Werror\u003dformat-security -O0 -fno-limit-debug-info -fPIC ",
                    "isGenerated": false,
                    "language": "C",
                    "sources": [
                      "../../../../ndk/21.4.7075529/sources/android/native_app_glue/android_native_app_glue.c"
                    ],
                    "includePath": [
                      {
                        "path": "[ndk]/sources/android/native_app_glue"
                      }
                    ]
                  }
                ],
                "fullName": "libapp-glue.a",
                "linkerLanguage": "C",
                "name": "app-glue",
                "sourceDirectory": "[tests]/VulkanTest/assembleDebug/vulkan",
                "type": "STATIC_LIBRARY",
                "sysroot": "[ndk]/toolchains/llvm/prebuilt/darwin-x86_64/sysroot"
              }
        """.trimIndent(), Target::class.java)
        val result = target.findRuntimeFiles()
        assertThat(result).isNull()
    }

    @Test
    fun `findRuntimeFiles flag field`() {
        val target = Gson().fromJson("""
            {
                "artifacts": [
                  "[tests]/CmakeSettingsTest/checkJsonRegeneratedForDifferentBuildCommands_version_3_10_2_/project/build/intermediates/cxx/RelWithDebInfo/1q6m3k2k/obj/x86_64/libhello-jni.so"
                ],
                "buildDirectory": "[tests]/CmakeSettingsTest/checkJsonRegeneratedForDifferentBuildCommands_version_3_10_2_/project/cmake/android/release/x86_64",
                "fileGroups": [
                  {
                    "compileFlags": "-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE\u003d2 -Wformat -Werror\u003dformat-security -DTEST_C_FLAG -DTEST_C_FLAG_2 -O2 -g -DNDEBUG -fPIC ",
                    "isGenerated": false,
                    "language": "C",
                    "sources": [
                      "src/main/cxx/hello-jni.c"
                    ],
                    "defines": [
                      "hello_jni_EXPORTS"
                    ]
                  }
                ],
                "fullName": "libhello-jni.so",
                "linkLibraries": "-llog -latomic -lm",
                "linkerLanguage": "C",
                "name": "hello-jni",
                "sourceDirectory": "[tests]/CmakeSettingsTest/checkJsonRegeneratedForDifferentBuildCommands_version_3_10_2_/project",
                "type": "SHARED_LIBRARY",
                "linkFlags": "-Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgcc_real.a -Wl,--exclude-libs,libatomic.a -static-libstdc++ -Wl,--build-id -Wl,--fatal-warnings -Wl,--no-undefined -Qunused-arguments",
                "sysroot": "[ndk]toolchains/llvm/prebuilt/darwin-x86_64/sysroot"
              }
        """.trimIndent(), Target::class.java)
        val result = target.findRuntimeFiles()
        assertThat(result).isEmpty()
    }
}
