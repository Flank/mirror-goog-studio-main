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
import org.junit.Test

internal class TokenizedCommandLineMapTest {
    @Suppress("unused")
    val preload = SdkConstants.PLATFORM_LINUX // Preload some classes to remove that overhead

    @Test
    fun computeIfAbsent() {
        val map = TokenizedCommandLineMap<String>(raw = false) { tokens, sourceFile ->
            tokens.removeNth(0)
            tokens.removeTokenGroup(sourceFile, 0)
            tokens.removeTokenGroup("-o", 1)
        }
        fun check(commandLine: String, expected: String) {
            val result = map.computeIfAbsent(commandLine, "blah") { tokens ->
                tokens.toString()
            }
            assertThat(result).isEqualTo(expected)
        }

        check("", "")
    }

    @Test
    fun forceCollision() {
        val map = TokenizedCommandLineMap<String>(raw = false)
        map.hashFunction = { 1 } // A hash function that always returns one
        map.computeIfAbsent("abc def", "blah") { "Hello" }
        map.computeIfAbsent("wxyz", "blah") { "Goodbye" }
        map.computeIfAbsent("abc def", "blah") { "Hello" }
        assertThat(map.size()).isEqualTo(2)
    }

    @Test
    fun reproCaseFromCompilationDatabaseIndexer() {
        val map = TokenizedCommandLineMap<String>(raw = true) { tokens, sourceFile ->
            tokens.removeNth(0) // Remove the path to clang.exe
            tokens.removeTokenGroup(sourceFile, 0)
            tokens.removeTokenGroup("-o", 1) // Remove -o and one following token
        }
        val original =
            "/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ --target=aarch64-none-linux-android -fno-limit-debug-info  -fPIC   -o CMakeFiles/native-lib.dir/src/main/cpp/native-lib.cpp.o -c /usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp"
        val expected = "--target=aarch64-none-linux-android -fno-limit-debug-info -fPIC -c"
        val actual = map.computeIfAbsent(
            original,
            "/usr/local/google/home/jomof/projects/MyApplication22/app/src/main/cpp/native-lib.cpp"
        ) { it.toString() }
        assertThat(actual).isEqualTo(expected)
    }

    /**
     * Test highly redundant mapping where nearly all of the command-lines map to the same
     * key value in the map.
     *
     * When I ran this on my macbook, the rates were:
     *  Small command-line rate is 115384 chars/ms
     *  Large command-line rate is 78892 chars/ms
     */
    @Test
    fun highlyRedundantMapThroughput() {
        val map = TokenizedCommandLineMap<List<String>>(raw = false) { tokens, sourceFile ->
            tokens.removeNth(0)
            tokens.removeTokenGroup(sourceFile, 0)
            tokens.removeTokenGroup("-o", 1)
        }
        val smallCommandLine = "clang.exe -c my-file.cpp -o -my-file.o -DFLAG"
        val largeCommandLine = "clang.exe -c my-file.cpp -o -my-file.o -DFLAG " +
                "${repeat(1000) {'x'}}"

        var charsProcessed = 0
        fun time(tag: String, target: (Int) -> Unit) {
            val repeats = 200000
            repeat(repeats, target) // Warm up
            val start = System.nanoTime()
            repeat(repeats, target)
            val end = System.nanoTime()
            val elapsedMillis = (end - start) / 1000000
            val rate = (charsProcessed + 0.0) / elapsedMillis
            println("$tag rate is ${rate.toInt()} chars/ms")
        }
        charsProcessed = 0
        time("Small command-line") {
            map.computeIfAbsent(smallCommandLine, "my-file.cpp") { it.toTokenList() }
            charsProcessed += smallCommandLine.length
        }
        charsProcessed = 0
        time("Large command-line") {
            map.computeIfAbsent(largeCommandLine, "my-file.cpp") { it.toTokenList() }
            charsProcessed += largeCommandLine.length
        }

        assertThat(map.size()).isEqualTo(2)
    }
}