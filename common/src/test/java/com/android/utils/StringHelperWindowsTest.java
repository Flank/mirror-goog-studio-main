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
 * Tests for StringHelperWindows
 */
public class StringHelperWindowsTest {

    // Command line splitting tests.

    private static void checkCommandLineSplitting(
            String originalCommand, List<String> splitCommands) throws Exception {
        assertThat(StringHelperWindows.splitCommandLine(originalCommand)).isEqualTo(splitCommands);
    }

    @Test
    public void checkZeroCommands() throws Exception {
        checkCommandLineSplitting("", Collections.<String>emptyList());
    }

    @Test
    public void checkTrailingSingleAmpersand() throws Exception {
        checkCommandLineSplitting("&", Collections.singletonList(""));
    }

    @Test
    public void checkTrailingDoubleAmpersand() throws Exception {
        checkCommandLineSplitting("&&", Collections.singletonList(""));
    }

    @Test
    public void checkOneCommand() throws Exception {
        checkCommandLineSplitting("foo bar", Collections.singletonList("foo bar"));
    }

    @Test
    public void checkTwoCommandsWithSingleAmpersand() throws Exception {
        checkCommandLineSplitting("foo bar& baz qux", Arrays.asList("foo bar", " baz qux"));
    }

    @Test
    public void checkTwoCommandsWithDoubleAmpersands() throws Exception {
        checkCommandLineSplitting("foo bar&& baz qux", Arrays.asList("foo bar", " baz qux"));
    }

    @Test
    public void checkOneCommandWithQuotedSingleAmpersand() throws Exception {
        checkCommandLineSplitting("foo bar\"&\" baz qux", Collections.singletonList("foo bar\"&\" baz qux"));
    }

    @Test
    public void checkOneCommandWithQuotedDoubleAmpersands() throws Exception {
        checkCommandLineSplitting("foo bar\"&&\" baz qux", Collections.singletonList("foo bar\"&&\" baz qux"));
    }

    @Test
    public void checkOneCommandWithEscapedSingleAmpersand() throws Exception {
        checkCommandLineSplitting("foo bar^& baz qux", Collections.singletonList("foo bar^& baz qux"));
    }

    @Test
    public void checkOneCommandWithEscapedDoubleAmpersands() throws Exception {
        checkCommandLineSplitting("foo bar^&^& baz qux", Collections.singletonList("foo bar^&^& baz qux"));
    }

    // Tokenization and stringization tests.

    private static void checkTokenizationAndQuotingAndJoining(
            String originalString, List<String> tokenizedString, String quotedAndJoinedTokens) throws Exception {
        assertThat(StringHelperWindows.tokenizeString(originalString)).isEqualTo(tokenizedString);
        assertThat(StringHelperWindows.quoteAndJoinTokens(tokenizedString)).isEqualTo(quotedAndJoinedTokens);
        assertThat(StringHelperWindows.tokenizeString(quotedAndJoinedTokens)).isEqualTo(tokenizedString);
    }

    @Test
    public void checkZeroTokens() throws Exception {
        checkTokenizationAndQuotingAndJoining("", Collections.<String>emptyList(), "");
    }

    @Test
    public void checkSingleToken() throws Exception {
        checkTokenizationAndQuotingAndJoining("a", Collections.singletonList("a"), "\"a\"");
    }

    @Test
    public void checkMultipleTokens() throws Exception {
        checkTokenizationAndQuotingAndJoining("a b\tc", Arrays.asList("a", "b", "c"), "\"a\" \"b\" \"c\"");
    }

    @Test
    public void checkDoubleQuote() throws Exception {
        checkTokenizationAndQuotingAndJoining("a \"b  c\" d", Arrays.asList("a", "b  c", "d"), "\"a\" \"b  c\" \"d\"");
    }

    @Test
    public void checkNormalSlashes() throws Exception {
        checkTokenizationAndQuotingAndJoining("a\\\\\\b c", Arrays.asList("a\\\\\\b", "c"), "\"a\\\\\\b\" \"c\"");
    }

    @Test
    public void checkOddSlashesBeforeQuote() throws Exception {
        checkTokenizationAndQuotingAndJoining("a\\\\\\\"b c", Arrays.asList("a\\\"b", "c"), "\"a\\\\\\\"b\" \"c\"");
    }

    @Test
    public void checkEvenSlashesBeforeQuote() throws Exception {
        checkTokenizationAndQuotingAndJoining("a\\\\\\\\\"b\" c", Arrays.asList("a\\\\b", "c"), "\"a\\\\b\" \"c\"");
    }

    @Test
    public void checkSingleQuote() throws Exception {
        checkTokenizationAndQuotingAndJoining("a 'b  c'", Arrays.asList("a", "'b", "c'"), "\"a\" \"'b\" \"c'\"");
    }

    @Test
    public void checkSingleQuoteWithinDoubleQuotes() throws Exception {
        checkTokenizationAndQuotingAndJoining("a \"b's  c\"", Arrays.asList("a", "b's  c"), "\"a\" \"b's  c\"");
    }

    @Test
    public void checkDoubleQuoteWithinSingleQuotes() throws Exception {
        checkTokenizationAndQuotingAndJoining("a 'b\"s\"  c'", Arrays.asList("a", "'bs", "c'"), "\"a\" \"'bs\" \"c'\"");
    }

    @Test
    public void checkEscapedSpace() throws Exception {
        checkTokenizationAndQuotingAndJoining("a b\\ c d", Arrays.asList("a", "b\\", "c", "d"), "\"a\" \"b\\\\\" \"c\" \"d\"");
    }

    @Test
    public void checkEscapedQuoteWithinDoubleQuotes() throws Exception {
        checkTokenizationAndQuotingAndJoining("a \"b\\\"c\" d", Arrays.asList("a", "b\"c", "d"), "\"a\" \"b\\\"c\" \"d\"");
    }

    @Test
    public void checkSlashWithinSingleQuotes() throws Exception {
        checkTokenizationAndQuotingAndJoining("a 'b\\c' d", Arrays.asList("a", "'b\\c'", "d"), "\"a\" \"'b\\c'\" \"d\"");
    }

    @Test
    public void checkAlternatingQuotes() throws Exception {
       checkTokenizationAndQuotingAndJoining("a 'b\\'\"c d\"", Arrays.asList("a", "'b\\'c d"), "\"a\" \"'b\\'c d\"");
    }

    @Test
    public void checkDoubleQuotesTwice() throws Exception {
        checkTokenizationAndQuotingAndJoining("\"a \"b\" c\" d", Arrays.asList("a b c", "d"), "\"a b c\" \"d\"");
    }

    @Test
    public void checkSingleQuotesTwice() throws Exception {
        checkTokenizationAndQuotingAndJoining("'a 'b' c' d", Arrays.asList("'a", "'b'", "c'", "d"), "\"'a\" \"'b'\" \"c'\" \"d\"");
    }

    @Test
    public void checkDoubleQuotedNewline() throws Exception {
        checkTokenizationAndQuotingAndJoining("\"a\nb\"", Collections.singletonList("a\nb"), "\"a\nb\"");
    }

    // Slash escaping tests.

    @Test
    public void checkSlashEscapedSlash() throws Exception {
        checkTokenizationAndQuotingAndJoining("a\\\\b", Collections.singletonList("a\\\\b"), "\"a\\\\b\"");
    }

    @Test
    public void checkSlashEscapedNewline() throws Exception {
        checkTokenizationAndQuotingAndJoining("a\\\nb", Arrays.asList("a\\", "b"), "\"a\\\\\" \"b\"");
    }

    @Test
    public void checkDoubleQuotedSlashEscapedNewline() throws Exception {
        checkTokenizationAndQuotingAndJoining("\"a\\\nb\"", Collections.singletonList("a\\\nb"), "\"a\\\nb\"");
    }

    // Caret escaping tests.

    @Test
    public void checkCaretEscapedCaret() throws Exception {
        checkTokenizationAndQuotingAndJoining("a^^b", Collections.singletonList("a^b"), "\"a^b\"");
    }

    @Test
    public void checkCaretEscapedNewline() throws Exception {
        checkTokenizationAndQuotingAndJoining("a^\nb", Collections.singletonList("ab"), "\"ab\"");
    }

    @Test
    public void checkDoubleQuotedCaretEscapedNewline() throws Exception {
        checkTokenizationAndQuotingAndJoining("\"a^\nb\"", Collections.singletonList("a^\nb"), "\"a^\nb\"");
    }
}
