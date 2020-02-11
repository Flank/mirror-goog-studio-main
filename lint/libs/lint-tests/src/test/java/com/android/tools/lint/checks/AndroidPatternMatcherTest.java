/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_ADVANCED_GLOB;
import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_LITERAL;
import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_PREFIX;
import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_SIMPLE_GLOB;

import junit.framework.TestCase;

public class AndroidPatternMatcherTest extends TestCase {
    public void testLiteral() {
        AndroidPatternMatcher matcher = new AndroidPatternMatcher("foo", PATTERN_LITERAL);
        assertEquals("literal foo", matcher.toString());
        assertEquals(PATTERN_LITERAL, matcher.getType());
        assertEquals("foo", matcher.getPath());
        assertFalse(matcher.match("fo"));
        assertTrue(matcher.match("foo"));
        assertFalse(matcher.match("bar"));
    }

    public void testPrefix() {
        AndroidPatternMatcher matcher = new AndroidPatternMatcher("foo", PATTERN_PREFIX);
        assertEquals("prefix foo", matcher.toString());
        assertFalse(matcher.match("fo"));
        assertTrue(matcher.match("foo"));
        assertTrue(matcher.match("foooo"));
        assertFalse(matcher.match("bar"));
    }

    public void testSimpleGlob() {
        AndroidPatternMatcher matcher = new AndroidPatternMatcher("foo*bar", PATTERN_SIMPLE_GLOB);
        assertEquals("glob foo*bar", matcher.toString());
        assertFalse(matcher.match("fo"));
        assertTrue(matcher.match("foobar"));
        assertTrue(matcher.match("fooooobar"));
        assertFalse(matcher.match("fooooobbar"));
        assertFalse(matcher.match("foobarf"));
        assertFalse(matcher.match("bar"));
    }

    public void testAdvancedGlob() {
        AndroidPatternMatcher matcher =
                new AndroidPatternMatcher("f[ou]o*bar", PATTERN_ADVANCED_GLOB);
        assertEquals("advanced f[ou]o*bar", matcher.toString());
        assertFalse(matcher.match("fo"));
        assertTrue(matcher.match("foobar"));
        assertTrue(matcher.match("fuobar"));
        assertTrue(matcher.match("fooooobar"));
        assertFalse(matcher.match("foooobbar"));
        assertFalse(matcher.match("foobarf"));
        assertFalse(matcher.match("bar"));
    }

    public void testFromFramework() {
        // These are a selection of test cases from the framework to verify that the port
        // didn't break anything:
        //  frameworks/base/core/tests/coretests/src/android/os/PatternMatcherTest.java
        AndroidPatternMatcher matcher = new AndroidPatternMatcher("[a]", PATTERN_ADVANCED_GLOB);
        assertMatches("a", matcher);
        assertNotMatches("b", matcher);

        matcher = new AndroidPatternMatcher("[.*+{}\\]\\\\[]", PATTERN_ADVANCED_GLOB);
        assertMatches(".", matcher);
        assertMatches("*", matcher);
        assertMatches("+", matcher);
        assertMatches("{", matcher);
        assertMatches("}", matcher);
        assertMatches("]", matcher);
        assertMatches("\\", matcher);
        assertMatches("[", matcher);

        matcher = new AndroidPatternMatcher("[a-z][0-9]", PATTERN_ADVANCED_GLOB);
        assertMatches("a1", matcher);
        assertNotMatches("1a", matcher);
        assertNotMatches("aa", matcher);

        matcher = new AndroidPatternMatcher("[z-a]", PATTERN_ADVANCED_GLOB);
        assertNotMatches("a", matcher);
        assertNotMatches("z", matcher);
        assertNotMatches("A", matcher);

        matcher = new AndroidPatternMatcher("[^0-9]", PATTERN_ADVANCED_GLOB);
        assertMatches("a", matcher);
        assertMatches("z", matcher);
        assertMatches("A", matcher);
        assertNotMatches("9", matcher);
        assertNotMatches("5", matcher);
        assertNotMatches("0", matcher);

        matcher =
                new AndroidPatternMatcher(
                        "/[0-9]{4}/[0-9]{2}/[0-9]{2}/[a-zA-Z0-9_]+\\.html", PATTERN_ADVANCED_GLOB);

        assertNotMatches("", matcher);
        assertMatches("/2016/09/07/got_this_working.html", matcher);
        assertMatches("/2016/09/07/got_this_working2.html", matcher);
        assertNotMatches("/2016/09/07/got_this_working2dothtml", matcher);
        assertNotMatches("/2016/9/7/got_this_working.html", matcher);

        matcher = new AndroidPatternMatcher("/b*a*bar.*", PATTERN_ADVANCED_GLOB);

        assertMatches("/babar", matcher);
        assertMatches("/babarfff", matcher);
        assertMatches("/bbaabarfff", matcher);
        assertMatches("/babar?blah", matcher);
        assertMatches("/baaaabar?blah", matcher);
        assertNotMatches("?bar", matcher);
        assertNotMatches("/bar", matcher);
        assertNotMatches("/baz", matcher);
        assertNotMatches("/ba/bar", matcher);
        assertNotMatches("/barf", matcher);
        assertNotMatches("/", matcher);
        assertNotMatches("?blah", matcher);

        matcher = new AndroidPatternMatcher("\\.", PATTERN_ADVANCED_GLOB);
        assertMatches(".", matcher);
        assertNotMatches("a", matcher);
        assertNotMatches("1", matcher);

        matcher = new AndroidPatternMatcher("a\\+", PATTERN_ADVANCED_GLOB);
        assertMatches("a+", matcher);
        assertNotMatches("a", matcher);
    }

    private static void assertMatches(String string, AndroidPatternMatcher matcher) {
        assertTrue(
                "'" + string + "' should match '" + matcher.toString() + "'",
                matcher.match(string));
    }

    private static void assertNotMatches(String string, AndroidPatternMatcher matcher) {
        assertFalse(
                "'" + string + "' should not match '" + matcher.toString() + "'",
                matcher.match(string));
    }
}
