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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;
import org.w3c.dom.Document;

/**
 * A number of utility methods around {@link CharSequence} handling, which
 * adds methods that are available on Strings (such as {@code indexOf},
 * {@code startsWith} and {@code regionMatches} and provides equivalent methods
 * for character sequences.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class CharSequences {

    public static int indexOf(@NonNull CharSequence sequence, char c) {
        return indexOf(sequence, c, 0);
    }

    public static int indexOf(@NonNull CharSequence sequence, char c, int start) {
        for (int i = start; i < sequence.length(); i++) {
            if (sequence.charAt(i) == c) {
                return i;
            }
        }

        return -1;
    }

    public static int lastIndexOf(@NonNull CharSequence haystack, @NonNull String needle,
            int start) {
        int length = haystack.length();

        int needleLength = needle.length();
        if (needleLength <= length && start >= 0) {
            if (needleLength > 0) {
                if (start > length - needleLength) {
                    start = length - needleLength;
                }
                char firstChar = needle.charAt(0);
                while (true) {
                    int i = lastIndexOf(haystack, firstChar, start);
                    if (i == -1) {
                        return -1;
                    }
                    int o1 = i, o2 = 0;

                    //noinspection StatementWithEmptyBody
                    while (++o2 < needleLength && haystack.charAt(++o1) == needle.charAt(o2)) {
                    }
                    if (o2 == needleLength) {
                        return i;
                    }
                    start = i - 1;
                }
            }
            return start < length ? start : length;
        }

        return -1;
    }

    public static int lastIndexOf(@NonNull CharSequence sequence, char c) {
        return lastIndexOf(sequence, c, sequence.length());
    }

    public static int lastIndexOf(@NonNull CharSequence sequence, int c, int start) {
        int length = sequence.length();
        if (start >= 0) {
            if (start >= length) {
                start = length - 1;
            }

            for (int i = start; i >= 0; i--) {
                if (sequence.charAt(i) == c) {
                    return i;
                }
            }
        }

        return -1;
    }

    public static int lastIndexOf(@NonNull CharSequence haystack, @NonNull String needle) {
        return lastIndexOf(haystack, needle, haystack.length());
    }

    public static boolean regionMatches(
            @NonNull CharSequence sequence,
            int thisStart,
            @NonNull CharSequence string,
            int start,
            int length) {
        if (start < 0 || string.length() - start < length) {
            return false;
        }
        if (thisStart < 0 || sequence.length() - thisStart < length) {
            return false;
        }
        if (length <= 0) {
            return true;
        }
        for (int i = 0; i < length; ++i) {
            if (sequence.charAt(thisStart + i) != string.charAt(start + i)) {
                return false;
            }
        }
        return true;
    }

    public static boolean regionMatches(
            @NonNull CharSequence sequence,
            boolean ignoreCase,
            int thisStart,
            @NonNull CharSequence string,
            int start,
            int length) {
        if (!ignoreCase) {
            return regionMatches(sequence, thisStart, string, start, length);
        }
        if (thisStart < 0 || length > sequence.length() - thisStart) {
            return false;
        }
        if (start < 0 || length > string.length() - start) {
            return false;
        }
        int end = thisStart + length;
        while (thisStart < end) {
            char c1 = sequence.charAt(thisStart++);
            char c2 = string.charAt(start++);
            if (c1 != c2 && foldCase(c1) != foldCase(c2)) {
                return false;
            }
        }
        return true;
    }

    private static char foldCase(char ch) {
        if (ch < 128) {
            if ('A' <= ch && ch <= 'Z') {
                return (char) (ch + ('a' - 'A'));
            }
            return ch;
        }
        return Character.toLowerCase(Character.toUpperCase(ch));
    }

    public static boolean startsWith(@NonNull CharSequence sequence, @NonNull CharSequence prefix) {
        return startsWith(sequence, prefix, 0);
    }

    public static boolean startsWith(@NonNull CharSequence sequence, @NonNull CharSequence prefix,
            int start) {
        int sequenceLength = sequence.length();
        int prefixLength = prefix.length();
        if (sequenceLength + start < prefixLength) {
            return false;
        }

        for (int i = start, j = 0; j < prefixLength; i++, j++) {
            if (sequence.charAt(i) != prefix.charAt(j)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true if the given character sequence ends with the given suffix
     *
     * @param sequence      the sequence to check
     * @param suffix        the suffix to check for
     * @param caseSensitive whether the check should be case sensitive
     * @return true if the sequence ends with the given suffix
     */
    public static boolean endsWith(@NonNull CharSequence sequence, @NonNull CharSequence suffix,
            boolean caseSensitive) {
        if (suffix.length() > sequence.length()) {
            return false;
        }

        int suffixLength = suffix.length();
        int sequenceLength = sequence.length();

        for (int i = sequenceLength - suffixLength, j = 0; i < sequenceLength; i++, j++) {
            char c1 = sequence.charAt(i);
            char c2 = suffix.charAt(j);
            if (c1 != c2) {
                if (caseSensitive) {
                    return false;
                } else if (Character.toLowerCase(c1) != Character.toLowerCase(c2)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns true if the given sequence contains any upper case characters
     *
     * @param s the sequence to test
     * @return true if there are any upper case characters in the string
     */
    public static boolean containsUpperCase(@Nullable CharSequence s) {
        if (s != null) {
            for (int i = 0, n = s.length(); i < n; i++) {
                if (Character.isUpperCase(s.charAt(i))) {
                    return true;
                }
            }
        }

        return false;
    }

    public static int indexOf(@NonNull CharSequence haystack, CharSequence needle) {
        return indexOf(haystack, needle, 0);
    }

    public static int indexOf(@NonNull CharSequence haystack, CharSequence needle, int start) {
        int needleLength = needle.length();
        if (needleLength == 0) {
            return start;
        }

        char first = needle.charAt(0);

        if (needleLength == 1) {
            return indexOf(haystack, first, start);
        }

        search:
        for (int i = start, max = haystack.length() - needleLength; i <= max; i++) {
            if (haystack.charAt(i) == first) {
                for (int h = i + 1, n = 1; n < needleLength; h++, n++) {
                    if (haystack.charAt(h) != needle.charAt(n)) {
                        continue search;
                    }
                }
                return i;
            }
        }

        return -1;
    }

    @NonNull
    public static CharSequence createSequence(@NonNull char[] data) {
        return new ArrayBackedCharSequence(data);
    }

    @NonNull
    public static CharSequence createSequence(@NonNull char[] data, int offset, int length) {
        return new ArrayBackedCharSequence(data, offset, length);
    }

    @NonNull
    public static char[] getCharArray(@NonNull CharSequence sequence) {
        if (sequence instanceof ArrayBackedCharSequence) {
            return ((ArrayBackedCharSequence)sequence).getCharArray();
        }

        return sequence.toString().toCharArray();
    }

    @NonNull
    public static Reader getReader(@NonNull CharSequence data, boolean stripBom) {
        CharSequenceReader reader = new CharSequenceReader(data);
        if (stripBom) {
            if (data.length() > 0 && data.charAt(0) == '\uFEFF') {
                // Skip BOM
                try {
                    //noinspection ResultOfMethodCallIgnored
                    reader.read();
                } catch (IOException ignore) {
                    // I/O errors can't happen for char sequence backed readers
                }
            }
        }

        return reader;
    }

    @Nullable
    public static Document parseDocumentSilently(@NonNull CharSequence xml, boolean namespaceAware) {
        try {
            Reader reader = getReader(xml, true);
            return XmlUtils.parseDocument(reader, namespaceAware);
        } catch (Exception e) {
            // pass
            // This method is deliberately silent; will return null
        }

        return null;
    }

    @NonNull
    public static InputStream getInputStream(CharSequence text) {
        return new ByteArrayInputStream(text.toString().getBytes(Charsets.UTF_8));
    }

    /**
     * A {@link CharSequence} intended for use by lint; it is a char[]-backed
     * {@linkplain CharSequence} which can provide its backing array to lint
     * (which is useful to avoid having duplicated data, since for example the
     * ECJ-based backend needs char[] instances of the source files instead
     * of Strings, and the String class always insists on having its own
     * private copy of the char array.
     */
    private static class ArrayBackedCharSequence implements CharSequence {
        public final char[] data;
        private final int offset;
        private final int length;

        public ArrayBackedCharSequence(@NonNull char[] data) {
            this(data, 0, data.length);
        }

        public ArrayBackedCharSequence(@NonNull char[] data, int offset, int length) {
            this.data = data;
            this.offset = offset;
            this.length = length;
        }

        @NonNull
        public char[] getCharArray() {
            if (offset == 0 && length == data.length) {
                return data;
            } else {
                return Arrays.copyOfRange(data, offset, offset + length);
            }
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public char charAt(int index) {
            return data[offset + index];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new ArrayBackedCharSequence(data, offset + start, end - start);
        }

        @NonNull
        @Override
        public String toString() {
            return new String(data, offset, length);
        }
    }

    // Copy from package private Guava implementation (com.google.common.io),
    // minus precondition checks, plus annotations
    private static final class CharSequenceReader extends Reader {

        private CharSequence seq;
        private int pos;
        private int mark;

        public CharSequenceReader(@NonNull CharSequence seq) {
            this.seq = seq;
        }

        private boolean hasRemaining() {
            return remaining() > 0;
        }

        private int remaining() {
            return seq.length() - pos;
        }

        @Override
        public synchronized int read(@NonNull CharBuffer target) throws IOException {
            if (!hasRemaining()) {
                return -1;
            }
            int charsToRead = Math.min(target.remaining(), remaining());
            for (int i = 0; i < charsToRead; i++) {
                target.put(seq.charAt(pos++));
            }
            return charsToRead;
        }

        @Override
        public synchronized int read() throws IOException {
            return hasRemaining() ? seq.charAt(pos++) : -1;
        }

        @Override
        public synchronized int read(@NonNull char[] cbuf, int off, int len) throws IOException {
            if (!hasRemaining()) {
                return -1;
            }
            int charsToRead = Math.min(len, remaining());
            for (int i = 0; i < charsToRead; i++) {
                cbuf[off + i] = seq.charAt(pos++);
            }
            return charsToRead;
        }

        @Override
        public synchronized long skip(long n) throws IOException {
            int charsToSkip = (int) Math.min(remaining(), n);
            pos += charsToSkip;
            return charsToSkip;
        }

        @Override
        public synchronized boolean ready() throws IOException {
            return true;
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public synchronized void mark(int readAheadLimit) throws IOException {
            mark = pos;
        }

        @Override
        public synchronized void reset() throws IOException {
            pos = mark;
        }

        @Override
        public synchronized void close() throws IOException {
            seq = null;
        }
    }
}
