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
import com.android.utils.cxx.STRIP_FLAGS_WITHOUT_ARG
import com.android.utils.cxx.STRIP_FLAGS_WITH_ARG
import com.android.utils.cxx.STRIP_FLAGS_WITH_IMMEDIATE_ARG
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

    @Test
    fun emptyTest() {
        fun check(raw: Boolean, platform: Int) {
            val empty =
                TokenizedCommandLine("", raw, platform)
                    .toString()
            assertThat("[$empty]")
                .named("raw = $raw platform = $platform")
                .isEqualTo("[]")
        }
        check(false, 1)
        check(true, SdkConstants.PLATFORM_WINDOWS)
        check(false, SdkConstants.PLATFORM_WINDOWS)
        check(false, SdkConstants.PLATFORM_LINUX)
        check(true, SdkConstants.PLATFORM_LINUX)
    }

    @Test
    fun removeNth() {
        fun check(
            commandLine: String,
            n: Int,
            expectedToken: String?,
            expectedCommandLine: String,
            raw: Boolean,
            platform: Int) {
            val tokens = TokenizedCommandLine(commandLine, raw, platform)
            val token = tokens.removeNth(n)
            assertThat(token).named("Removed token").isEqualTo(expectedToken)
            val expectedCommandLineTokens = TokenizedCommandLine(expectedCommandLine, raw, platform)
            assertThat("[$tokens]").named("Remaining").isEqualTo("[$expectedCommandLineTokens]")
        }
        fun check(commandLine: String, n: Int, expectedToken: String?, expectedCommandLine: String) {
            check(commandLine, n, expectedToken, expectedCommandLine, false, SdkConstants.PLATFORM_WINDOWS)
            check(commandLine, n, expectedToken, expectedCommandLine, true, SdkConstants.PLATFORM_WINDOWS)
            check(commandLine, n, expectedToken, expectedCommandLine, true, SdkConstants.PLATFORM_LINUX)
            check(commandLine, n, expectedToken, expectedCommandLine, false, SdkConstants.PLATFORM_LINUX)
        }

        check("", 1, null, "")
        check("-c", 0, "-c", "")
        check("", 0, null, "")
        check("clang.exe -c my-file.cpp", 0, "clang.exe", "-c my-file.cpp")
    }

    @Test
    fun tokenMatches() {
        fun checkMatches(
            commandLine: String,
            matchString: String,
            offset: Int,
            matchPrefix: Boolean
        ) {
            val tokens = TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_WINDOWS)
            assertThat(tokens.tokenMatches(matchString, offset, matchPrefix))
                .named("'$matchString' in '$commandLine' at $offset")
                .isTrue()
        }

        fun checkDoesntMatch(
            commandLine: String,
            matchString: String,
            offset: Int,
            matchPrefix: Boolean
        ) {
            val tokens = TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_WINDOWS)
            assertThat(tokens.tokenMatches(matchString, offset, matchPrefix))
                .named("'$matchString' in '$commandLine' at $offset")
                .isFalse()
        }

        checkMatches("-c", "-c", 1, matchPrefix = false)
        checkDoesntMatch("-cat", "-c", 1, matchPrefix = false)
        checkDoesntMatch("-c", "-cat", 1, matchPrefix = false)
        checkDoesntMatch("-car", "-cat", 1, matchPrefix = false)
        checkDoesntMatch("", "-cat", 1, matchPrefix = false)
        checkDoesntMatch("-c", "-x", 4, matchPrefix = false)
        checkMatches("--output=blah", "--output=", 1, matchPrefix = true)
        checkMatches("-cat", "-c", 1, matchPrefix = true)
        checkDoesntMatch("-c", "-cat", 1, matchPrefix = true)
        checkDoesntMatch("-car", "-cat", 1, matchPrefix = true)
        checkDoesntMatch("", "-cat", 1, matchPrefix = true)
        checkDoesntMatch("-c", "-x", 4, matchPrefix = true)
    }

    @Test
    fun nextTokenAfter() {
        fun checkNextTokenAfter(commandLine: String, offset: Int, expected: Int) {
            val tokens = TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_WINDOWS)
            val next = tokens.nextTokenAfter(offset)
            assertThat(next).isEqualTo(expected)
        }

        checkNextTokenAfter("-c a", 1, 4)
        checkNextTokenAfter("", 1, 2)
    }

    @Test
    fun removeTokenGroup() {
        fun checkRemove(commandLine: String, token: String, extra: Int, expect: String) {
            val tokens = TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_WINDOWS)
            tokens.removeTokenGroup(token, extra)
            assertThat(tokens.toString("|"))
                .named("Removing ${extra + 1} tokens starting with '$token' in '$commandLine'")
                .isEqualTo(expect)
        }

        checkRemove("a -c", "-c", 1, "a")
        checkRemove("-c -c", "-c", 1, "")
        checkRemove("-c", "-c", 1, "")
        checkRemove("-c a", "-c", 0, "a")
        checkRemove("-c a", "-c", 1, "")
        checkRemove("-c", "-c", 0, "")
        checkRemove("-c", "-x", 0, "-c")
        checkRemove("-c", "-c", 0, "")
    }

    @Test
    fun `remove group return extra`() {
        fun checkRemove(commandLine: String, token: String, extra: Int, expect: String, expectLastExtra: String) {
            val tokens = TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_WINDOWS)
            val lastExtra = tokens.removeTokenGroup(token, extra, returnFirstExtra = true)
            assertThat(tokens.toString("|"))
                .named("Removing ${extra + 1} tokens starting with '$token' in '$commandLine'")
                .isEqualTo(expect)
            assertThat("[$lastExtra]").isEqualTo("[$expectLastExtra]")
        }

        checkRemove("-o bob", "-o", 1, "", "bob")
        checkRemove("a -o bob", "-o", 1, "a", "bob")
        checkRemove("-o bob carrot", "-o", 1, "carrot", "bob")
        checkRemove("a -o bob carrot", "-o", 1, "a|carrot", "bob")
    }

    @Test
    fun `remove group return extra match prefix`() {
        fun checkRemove(commandLine: String, token: String, extra: Int, expect: String, expectLastExtra: String) {
            val tokens = TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_WINDOWS)
            val lastExtra = tokens.removeTokenGroup(token, extra, matchPrefix = true, returnFirstExtra = true)
            assertThat(tokens.toString("|"))
                .named("Removing ${extra + 1} tokens starting with '$token' in '$commandLine'")
                .isEqualTo(expect)
            assertThat("[$lastExtra]").isEqualTo("[$expectLastExtra]")
        }

        checkRemove("--output=bob", "--output=", 0, "", "bob")
        checkRemove("a --output=bob", "--output=", 0, "a", "bob")
        checkRemove("a --output=bob carrot", "--output=", 0, "a|carrot", "bob")
        checkRemove("--output=bob carrot", "--output=", 0, "carrot", "bob")
    }

    @Test
    fun testIdentity() {
        fun checkSame(c1: String, c2: String) {
            val t1 = TokenizedCommandLine(c1, true)
            val t2 = TokenizedCommandLine(c2, true)
            t1.removeTokenGroup("-c", 1)
            t2.removeTokenGroup("-c", 1)
            assertThat(t1.computeNormalizedCommandLineHashCode())
                .isEqualTo(t2.computeNormalizedCommandLineHashCode())
            assertThat(t1.toString()).isEqualTo(t2.toString())
        }
        fun checkNotSame(c1: String, c2: String) {
            val t1 = TokenizedCommandLine(c1, true)
            val t2 = TokenizedCommandLine(c2, true)
            t1.removeTokenGroup("-c", 1)
            t2.removeTokenGroup("-c", 1)
            assertThat(t1.computeNormalizedCommandLineHashCode())
                .isNotEqualTo(t2.computeNormalizedCommandLineHashCode())
            assertThat(t1.toString()).isNotEqualTo(t2.toString())
        }
        checkSame("", "")
        checkSame("abc", "abc")
        checkSame("abc xyz", "abc    xyz") // Whitespace between tokens is ignored
        checkNotSame("abc", "xyz")
        checkSame("clang.exe -c my-file-1.cpp", "clang.exe -c my-file-2.cpp")
        checkSame("-c my-file-1.cpp -fPIC", "-c my-file-2.cpp -fPIC")
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

    @Test
    fun `ensure STRIP_FLAGS_XXX don't strip -o`() {
        val interner =
            TokenizedCommandLineMap<Triple<String, List<String>, String?>>(raw = false) { tokens, sourceFile ->
                tokens.removeTokenGroup(sourceFile, 0)

                for (flag in STRIP_FLAGS_WITH_ARG) {
                    tokens.removeTokenGroup(flag, 1)
                }
                for (flag in STRIP_FLAGS_WITH_IMMEDIATE_ARG) {
                    tokens.removeTokenGroup(flag, 0, matchPrefix = true)
                }
                for (flag in STRIP_FLAGS_WITHOUT_ARG) {
                    tokens.removeTokenGroup(flag, 0)
                }
            }
        val command = "clang  -fPIC   -o hello-jni.c.o   -c /project/src/main/cxx/hello-jni.c"
        interner.computeIfAbsent(command, "") {
            val outputFile =
                it.removeTokenGroup("-o", 1, returnFirstExtra = true) ?:
                it.removeTokenGroup("--output=", 0, matchPrefix = true, returnFirstExtra = true) ?:
                it.removeTokenGroup("--output", 1, returnFirstExtra = true) ?:
                error("Could not determine output file from ${it.toTokenList()}")
            assertThat(outputFile).isEqualTo("hello-jni.c.o")
            val tokenList = it.toTokenList()
            Triple(tokenList[0], tokenList.subList(1, tokenList.size), outputFile)
        }

    }

    @Test
    fun fuzzFoundSingleDot() {
        TokenizedCommandLine(".", false, SdkConstants.PLATFORM_WINDOWS)
            .computeNormalizedCommandLineHashCode()
    }

    @Test
    fun checkLeadingWhitespaceDoesntCreateToken() {
        val check2 = TokenizedCommandLine(
            " ls",
            raw = false,
            platform = SdkConstants.PLATFORM_WINDOWS).toTokenList()
        assertThat(check2).hasSize(1)
    }

    @Test
    fun fuzzFoundTokenDelimiterIsAlsoSpaceInToString() {
        val t1 = TokenizedCommandLine(".", raw = true, platform = 2)
        val t2 = TokenizedCommandLine("", raw = true, platform = 1)
        val l1 = t1.toTokenList()
        val l2 = t2.toTokenList()

        assertThat(t1.toString()).isNotEqualTo(t2.toString())
        assertThat(l1).isNotEqualTo(l2)
    }

    @Test
    fun fuzzFoundEmpties() {
        val t1 = TokenizedCommandLine("", raw = true, platform = 2)
        val t2 = TokenizedCommandLine("", raw = true, platform = 1)
        val l1 = t1.toTokenList()
        val l2 = t2.toTokenList()

        assertThat(t1.toString()).isEqualTo(t2.toString())
        assertThat(l1).isEqualTo(l2)
    }

    @Test
    fun fuzzFoundEqualsStoppedTooSoon() {
        val t1 = TokenizedCommandLine(" --help= ", raw = false, platform = 2)
        val t2 = TokenizedCommandLine("--help= ", raw = false, platform = 1)
        assertThat(t1.toString()).isEqualTo(t2.toString())
    }

    @Test
    fun fuzzFound() {
        val t1 = TokenizedCommandLine("' --DC_TEST_WAS_RUN=", raw = true, platform = 2)
        val t2 = TokenizedCommandLine("' --DC_TEST_WAS_RUN=", raw = true, platform = 1)
        val l1 = t1.toTokenList()
        val l2 = t2.toTokenList()

        assertThat(t1.toString("][")).isNotEqualTo(t2.toString("]["))
        assertThat(l1 == l2).isFalse()
    }

    @Test
    fun fuzz() {
        var checks = 0
        var collisions = 0
        var stringHashCollisions = 0
        var shortestFault: String? = null
        fun ci(tokens : TokenizedCommandLine) =
            "TokenizedCommandLine(\"${tokens}\", raw = ${tokens.raw}, platform = ${tokens.platform})"
        fun fault(named : String, respond: ()->Unit = { }) {
            if (shortestFault == null || shortestFault!!.length > named.length) {
                shortestFault = named
                respond()
            }
        }
        fun check(tokens1: TokenizedCommandLine, tokens2: TokenizedCommandLine) {
            val named = "${ci(tokens1)} ${ci(tokens2)}"
            assertThat(tokens1.normalizedCommandLineLength()).isEqualTo(tokens1.toString().length)
            assertThat(tokens2.normalizedCommandLineLength()).isEqualTo(tokens2.toString().length)
            val equalityTruth = tokens1.toString("][") == tokens2.toString("][")
            if (equalityTruth) {
                if (tokens1.normalizedCommandLineLength() != tokens2.normalizedCommandLineLength()) fault("expect length equals: $named")
                if (tokens1.computeNormalizedCommandLineHashCode() != tokens2.computeNormalizedCommandLineHashCode()) fault("expect hashCode equals: $named")
                if (tokens1.toTokenList() != tokens2.toTokenList()) fault("expect toTokenList equals: $named")
            } else {
                if (tokens1.computeNormalizedCommandLineHashCode() == tokens2.computeNormalizedCommandLineHashCode()) collisions++
                if (tokens1.toString().hashCode() == tokens2.toString().hashCode()) stringHashCollisions++
                if (tokens1.toTokenList() == tokens2.toTokenList()) fault("expect toTokenList not equals: $named")
                checks++
            }
        }

        RandomInstanceGenerator().strings(3000).forEach { commandLine ->
            val t1 = TokenizedCommandLine(commandLine, false, SdkConstants.PLATFORM_WINDOWS)
            val t2 = TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_WINDOWS)
            val t3 = TokenizedCommandLine(commandLine, false, SdkConstants.PLATFORM_LINUX)
            val t4 = TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_LINUX)
            for (h1 in listOf(t1, t2, t3, t4)) {
                for (h2 in listOf(t1, t2, t3, t4)) {
                    check(h1, h2)
                }
            }
            RandomInstanceGenerator().strings(20).forEach { commandLine2 ->
                val t12 = TokenizedCommandLine(commandLine2, false, SdkConstants.PLATFORM_WINDOWS)
                val t22 = TokenizedCommandLine(commandLine2, true, SdkConstants.PLATFORM_WINDOWS)
                val t32 = TokenizedCommandLine(commandLine2, false, SdkConstants.PLATFORM_LINUX)
                val t42 = TokenizedCommandLine(commandLine2, true, SdkConstants.PLATFORM_LINUX)
                for (h1 in listOf(t1, t2, t3, t4)) {
                    for (h2 in listOf(t12, t22, t32, t42)) {
                        check(h1, h2)
                    }
                }
            }
        }

        assertThat(shortestFault).isNull()
        val hashCollisionRate = (0.0 + collisions) / checks
        val stringHashCollisionRate = (0.0 + stringHashCollisions) / checks
        assertThat(hashCollisionRate)
            .named("Hash collision too large. Compare to string hash collision rate $stringHashCollisionRate.")
            .isLessThan(0.0001)
    }

}
