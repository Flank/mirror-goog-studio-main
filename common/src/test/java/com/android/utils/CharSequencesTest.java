/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.utils.CharSequences.endsWith;
import static com.android.utils.CharSequences.indexOf;
import static com.android.utils.CharSequences.lastIndexOf;
import static com.android.utils.CharSequences.regionMatches;
import static com.android.utils.CharSequences.startsWith;
import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.io.Reader;
import junit.framework.TestCase;

public class CharSequencesTest extends TestCase {
    public void testIndexOf() {
        assertEquals(-1, indexOf("foo", 'a'));
        assertEquals(0, indexOf("foo", 'f'));
        assertEquals(1, indexOf("foo", 'o'));
        assertEquals(1, indexOf("foo", 'o', 0));
        assertEquals(1, indexOf("foo", 'o', 1));
        assertEquals(2, indexOf("foo", 'o', 2));
        assertEquals(-1, indexOf("foo", 'o', 3));

        assertEquals(1, indexOf("foo", "o", 1));

        assertEquals(-1, indexOf("foo", "bar"));
        assertEquals(3, indexOf("foobar", "bar"));
        assertEquals(-1, indexOf("foobar", "bat"));
        assertEquals(-1, indexOf("foobar", "bar", 4));
        assertEquals(-1, indexOf("foobar", "are"));
        assertEquals(-1, indexOf("foo", "o", 3));
    }

    public void testLastIndexOf() {
        assertEquals(-1, lastIndexOf("foo", 'b'));
        assertEquals(2, lastIndexOf("foo", 'o'));
        assertEquals(2, lastIndexOf("foo", 'o', 2));
        assertEquals(1, lastIndexOf("foo", 'o', 1));

        assertEquals(0, lastIndexOf("", ""));
        assertEquals(-1, lastIndexOf("foobar", "baz"));
        assertEquals(-1, lastIndexOf("short", "muchlonger"));
        assertEquals(3, lastIndexOf("barbar", "bar"));
        assertEquals(0, lastIndexOf("barbar", "bar", 2));
        assertEquals(-1, lastIndexOf("barbar", "barf", 2));
        assertEquals(1, lastIndexOf("foo", "o", 1));
    }

    public void testStartsWith() {
        assertTrue(startsWith("", ""));
        assertTrue(startsWith("haystack", ""));
        assertTrue(startsWith("haystack", "h"));
        assertTrue(startsWith("haystack", "hay"));
        assertTrue(startsWith("haystack", "haystack"));
        assertFalse(startsWith("haystack", "haystacks"));
        assertFalse(startsWith("haystack", "haystick"));
        assertFalse(startsWith("", "needle"));

        assertFalse(startsWith("haystack", "stack"));
        assertFalse(startsWith("haystack", "stack", 2));
        assertTrue(startsWith("haystack", "stack", 3));
    }

    public void testEndsWith() {
        assertTrue(endsWith("", "", true));
        assertFalse(endsWith("", "suffix", true));
        assertTrue(endsWith("haystack", "", true));
        assertTrue(endsWith("haystack", "k", true));
        assertFalse(endsWith("haystack", "K", true));
        assertTrue(endsWith("haystack", "K", false));
        assertTrue(endsWith("haystack", "CK", false));
        assertTrue(endsWith("haystack", "ck", true));
        assertFalse(endsWith("haystack", "hay", true));
    }

    public void testRegionMatches() {
        assertTrue(regionMatches("", 0, "", 0, 0));
        assertTrue(regionMatches("foo", 0, "foo", 0, 3));
        assertTrue(regionMatches("foo", 0, "foo", 0, 2));
        assertFalse(regionMatches("foo", 0, "foo", 0, 4));
        assertFalse(regionMatches("for", 0, "fob", 0, 3));
        assertTrue(regionMatches("for", 0, "fob", 0, 2));
        assertTrue(regionMatches("abcfoodef", 3, "xfooy", 1, 3));
        assertFalse(regionMatches("abcfoodef", 3, "xfooy", 1, 20));

        assertTrue(regionMatches("For", true, 0, "fob", 0, 2));
        assertFalse(regionMatches("For", false, 0, "fob", 0, 2));
        assertFalse(regionMatches("For", true, 0, "fob", 0, 20));
        assertFalse(regionMatches("For", 0, "fob", 0, 20));
    }

    public void testContainsUpperCase() {
        assertTrue(CharSequences.containsUpperCase("A"));
        assertTrue(CharSequences.containsUpperCase("abcA"));
        assertFalse(CharSequences.containsUpperCase("abc1235_1%"));
        assertFalse(CharSequences.containsUpperCase(""));
    }

    public void testCreateSequence() {
        String string = "Hello World";
        char[] charArray = string.toCharArray();
        CharSequence sequence = CharSequences.createSequence(charArray);
        assertEquals(string, sequence.toString());
        assertEquals(string.length(), sequence.length());
        assertSame(charArray, CharSequences.getCharArray(sequence));
        assertEquals(string.length(), CharSequences.getCharArray(sequence).length);

        assertEquals("ello World", sequence.subSequence(1, sequence.length()).toString());
        for (int i = 0, n = sequence.length(); i < n; i++) {
            assertEquals(string.charAt(i), sequence.charAt(i));
        }
    }

    public void testCreateSequenceWithOffset() {
        String string = "Hello World";
        char[] charArray = ("offset" + string).toCharArray();
        CharSequence sequence = CharSequences.createSequence(charArray, "offset".length(),
                string.length());
        assertEquals(string, sequence.toString());
        assertEquals(string.length(), sequence.length());
        assertNotSame(charArray, CharSequences.getCharArray(sequence)); // can't share with offset
        assertEquals(string.length(), CharSequences.getCharArray(sequence).length);

        assertEquals("ello World", sequence.subSequence(1, sequence.length()).toString());
    }

    public void testGetCharArray() {
        String string = "Hello World";
        char[] charArray = string.toCharArray();
        CharSequence sequence = CharSequences.createSequence(charArray);
        assertSame(charArray, CharSequences.getCharArray(sequence));

        assertNotSame(charArray, CharSequences.getCharArray(string));
        assertArrayEquals(charArray, CharSequences.getCharArray(string));
    }

    public void testGetReader() throws IOException {
        String s = "\uFEFFfoo";

        // Reader which strips byte order mark
        Reader reader = CharSequences.getReader(s, true);
        assertEquals((int)'f', reader.read());
        assertEquals((int)'o', reader.read());
        assertEquals((int)'o', reader.read());
        assertEquals(-1, reader.read());

        // Leave byte order mark in place
        reader = CharSequences.getReader(s, false);
        assertEquals((int)'\uFEFF', reader.read());
        assertEquals((int)'f', reader.read());
        assertEquals((int)'o', reader.read());
        assertEquals((int)'o', reader.read());
        assertEquals(-1, reader.read());
    }
}