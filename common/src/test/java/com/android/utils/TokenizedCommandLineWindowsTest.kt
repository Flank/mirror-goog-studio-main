/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.utils.StringHelperWindows.splitCommandLine
import com.google.common.truth.Truth
import org.junit.Test

/**
 * Tests for StringHelperWindows
 */
class TokenizedCommandLineWindowsTest {

    @Test
    @Throws(Exception::class)
    fun checkZeroCommands() {
        checkCommandLineSplitting("", emptyList())
    }

    @Test
    @Throws(Exception::class)
    fun checkTrailingSingleAmpersand() {
        checkCommandLineSplitting("&", listOf(""))
    }

    @Test
    @Throws(Exception::class)
    fun checkTrailingDoubleAmpersand() {
        checkCommandLineSplitting("&&", listOf(""))
    }

    @Test
    @Throws(Exception::class)
    fun checkOneCommand() {
        checkCommandLineSplitting(
            "foo bar",
            listOf("foo bar")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkTwoCommandsWithSingleAmpersand() {
        checkCommandLineSplitting(
            "foo bar& baz qux",
            listOf("foo bar", " baz qux")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkTwoCommandsWithDoubleAmpersands() {
        checkCommandLineSplitting(
            "foo bar&& baz qux",
            listOf("foo bar", " baz qux")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkOneCommandWithQuotedSingleAmpersand() {
        checkCommandLineSplitting(
            "foo bar\"&\" baz qux", listOf("foo bar\"&\" baz qux")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkOneCommandWithQuotedDoubleAmpersands() {
        checkCommandLineSplitting(
            "foo bar\"&&\" baz qux", listOf("foo bar\"&&\" baz qux")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkOneCommandWithEscapedSingleAmpersand() {
        checkCommandLineSplitting(
            "foo bar^& baz qux", listOf("foo bar^& baz qux")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkOneCommandWithEscapedDoubleAmpersands() {
        checkCommandLineSplitting(
            "foo bar^&^& baz qux", listOf("foo bar^&^& baz qux")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkZeroTokens() {
        checkTokenization("", emptyList<String>())
    }

    @Test
    @Throws(Exception::class)
    fun checkSingleToken() {
        checkTokenization("a", listOf("a"))
    }

    @Test
    @Throws(Exception::class)
    fun checkMultipleTokens() {
        checkTokenization(
            "a b\tc",
            listOf("a", "b", "c")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkDoubleQuote() {
        checkTokenizationToCompilerFlags(
            "a \"b  c\" d",
            listOf("a", "b  c", "d"),
            listOf("a", "\"b  c\"", "d")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkNormalSlashes() {
        checkTokenization(
            "a\\\\\\b c",
            listOf("a\\\\\\b", "c")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkOddSlashesBeforeQuote() {
        checkTokenizationToCompilerFlags(
            "a\\\\\\\"b c",
            listOf("a\\\"b", "c"),
            listOf("a\\\\\\\"b", "c")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkEvenSlashesBeforeQuote() {
        checkTokenizationToCompilerFlags(
            "a\\\\\\\\\"b\" c",
            listOf("a\\\\b", "c"),
            listOf("a\\\\\\\\\"b\"", "c")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkSingleQuote() {
        checkTokenization(
            "a 'b  c'",
            listOf("a", "'b", "c'")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkSingleQuoteWithinDoubleQuotes() {
        checkTokenizationToCompilerFlags(
            "a \"b's  c\"",
            listOf("a", "b's  c"),
            listOf("a", "\"b's  c\"")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkDoubleQuoteWithinSingleQuotes() {
        checkTokenizationToCompilerFlags(
            "a 'b\"s\"  c'",
            listOf("a", "'bs", "c'"),
            listOf("a", "'b\"s\"", "c'")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkEscapedSpace() {
        checkTokenization(
            "a b\\ c d",
            listOf("a", "b\\", "c", "d")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkEscapedQuoteWithinDoubleQuotes() {
        checkTokenizationToCompilerFlags(
            "a \"b\\\"c\" d",
            listOf("a", "b\"c", "d"),
            listOf("a", "\"b\\\"c\"", "d")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkSlashWithinSingleQuotes() {
        checkTokenization(
            "a 'b\\c' d",
            listOf("a", "'b\\c'", "d")
        )
    }

    @Test
    fun caseFromCommandLineParserTest() {
        checkTokenizationToCompilerFlags(
            "a\\\\\\b d\"e f\"g h",
            listOf("a\\\\\\b", "de fg", "h"),
            listOf("a\\\\\\b", "d\"e f\"g", "h")
        )
    }

    @Test
    @Throws(Exception::class)
    fun singleSlash() {
        checkTokenizationToCompilerFlags(
            "\\",
            listOf("\\"),
            listOf("\\")
        )
    }

    @Test
    @Throws(Exception::class)
    fun singleCaret() {
        checkTokenizationToCompilerFlags(
            "^",
            listOf("^"),
            listOf("^")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkAlternatingQuotes() {
        checkTokenizationToCompilerFlags(
            "a 'b\\'\"c d\"",
            listOf("a", "'b\\'c d"),
            listOf("a", "'b\\'\"c d\"")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkDoubleQuotesTwice() {
        checkTokenizationToCompilerFlags(
            "\"a \"b\" c\" d",
            listOf("a b c", "d"),
            listOf("\"a \"b\" c\"", "d")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkSingleQuotesTwice() {
        checkTokenization(
            "'a 'b' c' d",
            listOf("'a", "'b'", "c'", "d")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkDoubleQuotedNewline() {
        checkTokenizationToCompilerFlags(
            "\"a\nb\"", listOf("a\nb"), listOf("\"a\nb\"")
        )
    }

    // Slash escaping tests.
    @Test
    @Throws(Exception::class)
    fun checkSlashEscapedSlash() {
        checkTokenization(
            "a\\\\b",
            listOf("a\\\\b")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkSlashEscapedNewline() {
        checkTokenization(
            "a\\\nb",
            listOf("a\\", "b")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkDoubleQuotedSlashEscapedNewline() {
        checkTokenizationToCompilerFlags(
            "\"a\\\nb\"", listOf("a\\\nb"), listOf("\"a\\\nb\"")
        )
    }

    // Caret escaping tests.
    @Test
    @Throws(Exception::class)
    fun checkCaretEscapedCaret() {
        checkTokenizationToCompilerFlags(
            "a^^b", listOf("a^b"), listOf("a^^b")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkCaretEscapedNewline() {
        checkTokenizationToCompilerFlags(
            "a^\nb", listOf("ab"), listOf("a^b")
        )
    }

    @Test
    @Throws(Exception::class)
    fun checkDoubleQuotedCaretEscapedNewline() {
        checkTokenizationToCompilerFlags(
            "\"a^\nb\"", listOf("a^\nb"), listOf("\"a^\nb\"")
        )
    }

    companion object {
        // Command line splitting tests.
        @Throws(Exception::class)
        private fun checkCommandLineSplitting(
            originalCommand: String, splitCommands: List<String>
        ) {
            Truth.assertThat(splitCommandLine(originalCommand))
                .isEqualTo(splitCommands)
        }

        private fun tokenizeCommandLineToEscaped(commandLine: String) =
            TokenizedCommandLine(
                commandLine = commandLine,
                raw = false,
                platform = SdkConstants.PLATFORM_WINDOWS)
                .toTokenList()

        private fun tokenizeCommandLineToRaw(commandLine: String) =
            TokenizedCommandLine(
                commandLine = commandLine,
                raw = true,
                platform = SdkConstants.PLATFORM_WINDOWS)
                .toTokenList()

        // Tokenization tests.
        @Throws(Exception::class)
        private fun checkTokenizationToCompilerFlags(
            originalString: String,
            escapedExpected: List<String?>,
            rawExpected: List<String?>
        ) {
            val tokenizedEscaped = tokenizeCommandLineToEscaped(originalString)
            val tokenizedRaw = tokenizeCommandLineToRaw(originalString)
            Truth.assertThat(tokenizedEscaped)
                .named("escaped tokens (1st)")
                .containsExactlyElementsIn(escapedExpected)
            Truth.assertThat(tokenizedRaw)
                .named("raw tokens (2nd)")
                .containsExactlyElementsIn(rawExpected)
        }

        @Throws(Exception::class)
        private fun checkTokenization(
            originalString: String,
            expected: List<String?>
        ) {
            checkTokenizationToCompilerFlags(
                originalString,
                expected,
                expected
            )
        }
    }
}