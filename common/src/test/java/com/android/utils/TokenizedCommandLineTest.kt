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

package com.android.utils

import com.android.SdkConstants
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

class TokenizedCommandLineTest {

    /**
    * This is a very basic test just to confirm toTokenList can be called.
    * The extensive platform-specific tests are in [TokenizedCommandLineWindowsTest]
    * and [TokenizedCommandLinePOSIXTest].
    */
    @Test
    fun simpleTest() {
        val abc = TokenizedCommandLine("abc", false).toTokenList()
        assertThat(abc).containsExactly("abc")
    }

    /**
     * This tests whether generation checking is working.
     */
    @Test
    fun checkBufferSharingException() {
        val shared = allocateTokenizeCommandLineBuffer("123")
        val abc = TokenizedCommandLine("abc", false, indexes = shared)
        val xyz = TokenizedCommandLine("xyz", false, indexes = shared)
        xyz.toTokenList()
        try {
            // Access abc after handing off the buffer to xyz
            abc.toTokenList()
        } catch(e: Exception) {
            // Expect an exception because this is invalid sharing of the index buffer
            return
        }
        fail("Expected an exception")
    }

    /**
     * No real test here. It just prints tokenization rate for Windows tokenizer.
     * On my Macbook this printed:
     *
     *   Windows: zero allocation rate is 186 parses/ms
     *   Windows: string list allocation rate is 44 parses/ms
     */
    @Test
    fun printWindowsTokenizeRates() {
        // This string intentionally has whitespace at the end of lines
        val commandLine = """
        \usr\local\google\home\jomof\Android\Sdk\ndk-bundle\toolchains\llvm\prebuilt\linux-x86_64\bin\clang++ 
        --target=aarch64-none-linux-android 
        --gcc-toolchain=\usr\local\google\home\jomof\Android\Sdk\ndk-bundle\toolchains\aarch64-linux-android-4.9\prebuilt\linux-x86_64 
        --sysroot=\usr\local\google\home\jomof\Android\Sdk\ndk-bundle\sysroot  
        -Dnative_lib_EXPORTS 
        -isystem \usr\local\google\home\jomof\Android\Sdk\ndk-bundle\sources\cxx-stl\gnu-libstdc++\4.9\include 
        -isystem \usr\local\google\home\jomof\Android\Sdk\ndk-bundle\sources\cxx-stl\gnu-libstdc++\4.9\libs\arm64-v8a\include 
        -isystem \usr\local\google\home\jomof\Android\Sdk\ndk-bundle\sources\cxx-stl\gnu-libstdc++\4.9\include\backward  
        -isystem \usr\local\google\home\jomof\Android\Sdk\ndk-bundle\sysroot\usr\include\aarch64-linux-android 
        -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables 
        -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack 
        -Wformat -Werror=format-security   -O0 -fno-limit-debug-info  -fPIC   
        -o CMakeFiles\native-lib.dir\src\main\cpp\native-lib.cpp.o 
        -c \usr\local\google\home\jomof\projects\MyApplication22\app\src\main\cpp\native-lib.cpp"""
            .trimIndent()
            .replace('\n', ' ')

        fun time(tag: String, target: (Int) -> Unit) {
            val repeats = 20000

            repeat(repeats, target) // Warm up
            val start = System.nanoTime()
            repeat(repeats, target)
            val end = System.nanoTime()
            val elapsedMillis = (end - start) / 1000000
            val rate = (repeats + 0.0) / elapsedMillis
            println("$tag rate is ${rate.toInt()} parses/ms")
        }
        val indexes = allocateTokenizeCommandLineBuffer(commandLine)
        time("Windows: zero allocation") {
            TokenizedCommandLine(
                commandLine = commandLine,
                raw = true,
                platform = SdkConstants.PLATFORM_WINDOWS,
                indexes = indexes)
        }
        time("Windows: string list allocation") {
            TokenizedCommandLine(
                commandLine = commandLine,
                raw = true,
                platform = SdkConstants.PLATFORM_WINDOWS)
                .toTokenList()
        }
    }

    /**
     * No real test here. It just prints tokenization rate for POSIX tokenizer.
     * On my Macbook this printed:
     *
     *   POSIX: zero allocation rate is 240 parses/ms
     *   POSIX: string list allocation rate is 85 parses/ms
     */
    @Test
    fun printPOSIXTokenizeRates() {
        // This string intentionally has whitespace at the end of lines
        val commandLine = """
        /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ 
        --target=aarch64-none-linux-android 
        --gcc-toolchain=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64 
        --sysroot=/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot  
        -Dnative_lib_EXPORTS 
        -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include 
        -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/libs/arm64-v8a/include 
        -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sources/cxx-stl/gnu-libstdc++/4.9/include/backward  
        -isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/aarch64-linux-android 
        -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables 
        -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack 
        -Wformat -Werror=format-security   -O0 -fno-limit-debug-info  -fPIC   
        -o CMakeFiles/native-lib.dir/src/main/cpp/native-lib.cpp.o 
        -c /usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp"""
            .trimIndent()
            .replace('\n', ' ')

        fun time(tag: String, target: (Int) -> Unit) {
            val repeats = 20000

            repeat(repeats, target) // Warm up
            val start = System.nanoTime()
            repeat(repeats, target)
            val end = System.nanoTime()
            val elapsedMillis = (end - start) / 1000000
            val rate = (repeats + 0.0) / elapsedMillis
            println("$tag rate is ${rate.toInt()} parses/ms")
        }
        val indexes = allocateTokenizeCommandLineBuffer(commandLine)
        time("POSIX: zero allocation") {
            TokenizedCommandLine(
                commandLine = commandLine,
                raw = true,
                platform = SdkConstants.PLATFORM_LINUX,
                indexes = indexes)
        }
        time("POSIX: string list allocation") {
            TokenizedCommandLine(
                commandLine = commandLine,
                raw = true,
                platform = SdkConstants.PLATFORM_LINUX)
                .toTokenList()
        }
    }
}