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

package com.android.utils;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Tests for StringHelperPOSIX
 */
public class StringHelperPOSIXTest {

    // Command line splitting tests.

    private static void checkCommandLineSplitting(
            String originalCommand, List<String> splitCommands) throws Exception {
        assertThat(StringHelperPOSIX.splitCommandLine(originalCommand)).isEqualTo(splitCommands);
    }

    @Test
    public void checkZeroCommands() throws Exception {
        checkCommandLineSplitting("", Collections.<String>emptyList());
    }

    @Test
    public void checkTrailingSingleAmpersand() throws Exception {
        checkCommandLineSplitting("&", Collections.singletonList("&"));
    }

    @Test
    public void checkOneCommand() throws Exception {
        checkCommandLineSplitting("foo bar", Collections.singletonList("foo bar"));
    }

    @Test
    public void checkTwoCommandsWithSemicolon() throws Exception {
        checkCommandLineSplitting("foo bar; baz qux", Arrays.asList("foo bar", " baz qux"));
    }

    @Test
    public void checkTwoCommandsWithDoubleAmpersands() throws Exception {
        checkCommandLineSplitting("foo bar&& baz qux", Arrays.asList("foo bar", " baz qux"));
    }

    @Test
    public void checkOneCommandWithQuotedSemicolon() throws Exception {
        checkCommandLineSplitting(
                "foo bar\";\" baz qux", Collections.singletonList("foo bar\";\" baz qux"));
    }

    @Test
    public void checkOneCommandWithQuotedDoubleAmpersands() throws Exception {
        checkCommandLineSplitting(
                "foo bar\"&&\" baz qux", Collections.singletonList("foo bar\"&&\" baz qux"));
    }

    @Test
    public void checkOneCommandWithEscapedSemicolon() throws Exception {
        checkCommandLineSplitting(
                "foo bar\\; baz qux", Collections.singletonList("foo bar\\; baz qux"));
    }

    @Test
    public void checkOneCommandWithEscapedDoubleAmpersands() throws Exception {
        checkCommandLineSplitting(
                "foo bar\\&\\& baz qux", Collections.singletonList("foo bar\\&\\& baz qux"));
    }

    // Tokenization.
    private static void checkTokenizationToCompilerFlags(
            String originalString, List<String> escapedExpected, List<String> rawExpected)
            throws Exception {
        List<String> tokenizedRaw = StringHelperPOSIX.tokenizeCommandLineToRaw(originalString);
        List<String> tokenizedEscaped =
                StringHelperPOSIX.tokenizeCommandLineToEscaped(originalString);
        assertThat(tokenizedRaw).containsExactlyElementsIn(rawExpected);
        assertThat(tokenizedEscaped).containsExactlyElementsIn(escapedExpected);
    }

    private static void checkTokenization(String originalString, List<String> expected)
            throws Exception {
        checkTokenizationToCompilerFlags(originalString, expected, expected);
    }

    @Test
    public void checkZeroTokens() throws Exception {
        checkTokenization("", Collections.<String>emptyList());
    }

    @Test
    public void checkSingleToken() throws Exception {
        checkTokenization("a", Collections.singletonList("a"));
    }

    @Test
    public void checkMultipleTokens() throws Exception {
        checkTokenization("a b\tc", Arrays.asList("a", "b", "c"));
    }

    @Test
    public void checkDoubleQuote() throws Exception {
        checkTokenizationToCompilerFlags(
                "a \"b  c\" d",
                Arrays.asList("a", "b  c", "d"),
                Arrays.asList("a", "\"b  c\"", "d"));
    }

    @Test
    public void checkNormalSlashes() throws Exception {
        checkTokenizationToCompilerFlags(
                "a\\\\\\b c", Arrays.asList("a\\b", "c"), Arrays.asList("a\\\\\\b", "c"));
    }

    @Test
    public void checkOddSlashesBeforeQuote() throws Exception {
        checkTokenizationToCompilerFlags(
                "a\\\\\\\"b c", Arrays.asList("a\\\"b", "c"), Arrays.asList("a\\\\\\\"b", ("c")));
    }

    @Test
    public void checkEvenSlashesBeforeQuote() throws Exception {
        checkTokenizationToCompilerFlags(
                "a\\\\\\\\\"b\" c",
                Arrays.asList("a\\\\b", "c"),
                Arrays.asList("a\\\\\\\\\"b\"", "c"));
    }

    @Test
    public void checkSingleQuote() throws Exception {
        checkTokenizationToCompilerFlags(
                "a 'b  c'", Arrays.asList("a", "b  c"), Arrays.asList("a", "'b  c'"));
    }

    @Test
    public void checkSingleQuoteWithinDoubleQuotes() throws Exception {
        checkTokenizationToCompilerFlags(
                "a \"b's  c\"", Arrays.asList("a", "b's  c"), Arrays.asList("a", "\"b's  c\""));
    }

    @Test
    public void checkDoubleQuoteWithinSingleQuotes() throws Exception {
        checkTokenizationToCompilerFlags(
                "a 'b\"s\"  c'",
                Arrays.asList("a", "b\"s\"  c"),
                Arrays.asList("a", "'b\"s\"  c'"));
    }

    @Test
    public void checkEscapedSpace() throws Exception {
        checkTokenizationToCompilerFlags(
                "a b\\ c d", Arrays.asList("a", "b c", "d"), Arrays.asList("a", "b\\c", "d"));
    }

    @Test
    public void checkEscapedQuoteWithinDoubleQuotes() throws Exception {
        checkTokenizationToCompilerFlags(
                "a \"b\\\"c\" d",
                Arrays.asList("a", "b\"c", "d"),
                Arrays.asList("a", "\"b\\\"c\"", "d"));
    }

    @Test
    public void checkSlashWithinSingleQuotes() throws Exception {
        checkTokenizationToCompilerFlags(
                "a 'b\\c' d", Arrays.asList("a", "b\\c", "d"), Arrays.asList("a", "'b\\c'", "d"));
    }

    @Test
    public void checkAlternatingQuotes() throws Exception {
        checkTokenizationToCompilerFlags(
                "a 'b\\'\"c d\"", Arrays.asList("a", "b\\c d"), Arrays.asList("a", "'b\\'\"c d\""));
    }

    @Test
    public void checkDoubleQuotesTwice() throws Exception {
        checkTokenizationToCompilerFlags(
                "\"a \"b\" c\" d",
                Arrays.asList("a b c", "d"),
                Arrays.asList("\"a \"b\" c\"", "d"));
    }

    @Test
    public void checkSingleQuotesTwice() throws Exception {
        checkTokenizationToCompilerFlags(
                "'a 'b' c' d", Arrays.asList("a b c", "d"), Arrays.asList("'a 'b' c'", "d"));
    }

    @Test
    public void checkDoubleQuotedNewline() throws Exception {
        checkTokenizationToCompilerFlags(
                "\"a\nb\"",
                Collections.singletonList("a\nb"),
                Collections.singletonList("\"a\nb\""));
    }

    // Slash escaping tests.

    @Test
    public void checkSlashEscapedSlash() throws Exception {
        checkTokenizationToCompilerFlags(
                "a\\\\b", Collections.singletonList("a\\b"), Collections.singletonList("a\\\\b"));
    }

    @Test
    public void checkSlashEscapedNewline() throws Exception {
        checkTokenizationToCompilerFlags(
                "a\\\nb", Collections.singletonList("ab"), Collections.singletonList("a\\b"));
    }

    @Test
    public void checkDoubleQuotedSlashEscapedNewline() throws Exception {
        checkTokenizationToCompilerFlags(
                "\"a\\\nb\"",
                Collections.singletonList("ab"),
                Collections.singletonList("\"a\\\nb\""));
    }

    // Caret escaping tests.

    @Test
    public void checkCaretEscapedCaret() throws Exception {
        checkTokenization("a^^b", Collections.singletonList("a^^b"));
    }

    @Test
    public void checkCaretEscapedNewline() throws Exception {
        checkTokenization("a^\nb", Arrays.asList("a^", "b"));
    }

    @Test
    public void checkDoubleQuotedCaretEscapedNewline() throws Exception {
        checkTokenizationToCompilerFlags(
                "\"a^\nb\"",
                Collections.singletonList("a^\nb"),
                Collections.singletonList("\"a^\nb\""));
    }

    // See issuetracker.google.com/69110338
    @Test
    public void checkPreserveTicks() throws Exception {
        checkTokenizationToCompilerFlags(
                "-DX='Y Z'",
                Collections.singletonList("-DX=Y Z"),
                Collections.singletonList("-DX='Y Z'"));
    }

    // See issuetracker.google.com/69110338
    @Test
    public void checkDoesNotPreserveQuotes() throws Exception {
        checkTokenizationToCompilerFlags(
                "-DX=\"Y Z\"",
                Collections.singletonList("-DX=Y Z"),
                Collections.singletonList("-DX=\"Y Z\""));
    }
}
