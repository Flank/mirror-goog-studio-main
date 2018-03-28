/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

public class StringHelper {

    private static final CharMatcher CR = CharMatcher.is('\r');
    private static final Pattern LF = Pattern.compile("\n", Pattern.LITERAL);

    /**
     * Appends the given <var>word</var> to the specified {@link StringBuilder}.
     *
     * <p>The word is capitalized before being appended.
     *
     * @param sb the StringBuilder
     * @param word the word to add
     */
    public static void appendCapitalized(@NonNull StringBuilder sb, @NonNull String word) {
        if (word.isEmpty()) {
            return;
        }
        // manually compute the upper char of the first letter.
        // This avoids doing a substring(1).toUpperChar(Locale.US) which would create additional
        // objects.
        // This does not support characters that requires 2 char to be represented but the previous
        // code didn't either.
        // This catches possible errors and fallbacks to the less efficient way.
        int c = (int) word.charAt(0);

        // see if the letter is using more than one char.
        if ((c >= Character.MIN_HIGH_SURROGATE) && (c <= Character.MAX_HIGH_SURROGATE)) {
            c = word.codePointAt(0);
            int charCount = Character.charCount(c);

            String upperString = word.substring(0, charCount).toUpperCase(Locale.US);
            sb.append(upperString);
            sb.append(word, charCount, word.length());
        } else {
            int result = Character.toUpperCase(c);
            char upperChar;

            // it's not clear where non surrogate-pair values can trigger this but this is safer.
            if (result != 0xFFFFFFFF) { //Character.ERROR (internal!)
                upperChar = (char) result;
            } else {
                upperChar = word.substring(0, 1).toUpperCase(Locale.US).charAt(0);
            }

            sb.append(upperChar);
            sb.append(word, 1, word.length());
        }
    }

    /**
     * Returns a capitalized version of the given <var>word</var>.
     *
     * <p>This is unlikely to be what you need. Prefer to use {@link
     * #appendCapitalized(StringBuilder, String)} or one of {@link #appendCapitalized(String,
     * String)}, {@link #capitalizeWithSuffix(String, String)} (String, String, String)}, or {@link
     * #capitalizeWithSuffix(String, String)}.
     *
     * @param word the word to be capitalized
     * @return the capitalized word.
     */
    @NonNull
    public static String capitalize(@NonNull String word) {
        StringBuilder sb = new StringBuilder(word.length());
        appendCapitalized(sb, word);
        return sb.toString();
    }

    /**
     * Returns a string containing the given prefix, as is, and a capitalized version of the given
     * <var>word</var>.
     *
     * @param prefix the prefix to add before the word
     * @param word the word to be capitalized
     * @return the capitalized word.
     */
    @NonNull
    public static String appendCapitalized(@NonNull String prefix, @NonNull String word) {
        StringBuilder sb = new StringBuilder(prefix.length() + word.length());
        sb.append(prefix);
        appendCapitalized(sb, word);
        return sb.toString();
    }

    /**
     * Returns a string containing the given prefix, as is, and a capitalized version of the given
     * <var>word1</var> and <var>word2</var>.
     *
     * @param prefix the prefix to add before the word
     * @param word1 the word to be capitalized
     * @param word2 the word to be capitalized
     * @return the capitalized word.
     */
    @NonNull
    public static String appendCapitalized(
            @NonNull String prefix, @NonNull String word1, @NonNull String word2) {
        StringBuilder sb = new StringBuilder(prefix.length() + word1.length() + word2.length());
        sb.append(prefix);
        appendCapitalized(sb, word1);
        appendCapitalized(sb, word2);
        return sb.toString();
    }

    /**
     * Returns a string containing the given prefix, as is, and a capitalized version of the given
     * <var>words</var>.
     *
     * @param prefix the prefix to add before the words
     * @param words the words to be capitalized
     * @return the capitalized word.
     */
    @NonNull
    public static String appendCapitalized(@NonNull String prefix, @NonNull String... words) {
        int length = prefix.length();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, max = words.length; i < max; i++) {
            length += words[i].length();
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append(prefix);
        for (String word : words) {
            appendCapitalized(sb, word);
        }
        return sb.toString();
    }

    /**
     * Returns a capitalized version of the given <var>word</var>, including the given
     * <var>suffix</var>
     *
     * @param word the word to be capitalized
     * @param suffix the suffix to add after the word
     * @return the capitalized word.
     */
    @NonNull
    public static String capitalizeWithSuffix(@NonNull String word, @NonNull String suffix) {
        StringBuilder sb = new StringBuilder(word.length() + suffix.length());
        appendCapitalized(sb, word);
        sb.append(suffix);
        return sb.toString();
    }

    /**
     * Appends the given <var>work</var> to the specified StringBuilder in a camel case version.
     *
     * <p>if the builder is empty, the word is added as-is. If it's not then the work is capitalized
     *
     * @param sb the StringBuilder
     * @param word the word to add
     */
    public static void appendCamelCase(@NonNull StringBuilder sb, @NonNull String word) {
        if (sb.length() == 0) {
            sb.append(word);
        } else {
            appendCapitalized(sb, word);
        }
    }

    @NonNull
    public static String combineAsCamelCase(@NonNull Iterable<String> stringList) {
        int count = 0;
        for (String s : stringList) {
            count += s.length();
        }

        StringBuilder sb = new StringBuilder(count);
        boolean first = true;
        for (String str : stringList) {
            if (first) {
                sb.append(str);
                first = false;
            } else {
                appendCapitalized(sb, str);
            }
        }
        return sb.toString();
    }

    @NonNull
    public static <T> String combineAsCamelCase(
            @NonNull Collection<T> objectList, @NonNull Function<T, String> mapFunction) {
        StringBuilder sb = new StringBuilder(objectList.size() * 20);
        combineAsCamelCase(sb, objectList, mapFunction);
        return sb.toString();
    }

    public static <T> void combineAsCamelCase(
            @NonNull StringBuilder sb,
            @NonNull Collection<T> objectList,
            @NonNull Function<T, String> mapFunction) {

        boolean first = true;
        for (T object : objectList) {
            if (first) {
                sb.append(mapFunction.apply(object));
                first = false;
            } else {
                appendCapitalized(sb, mapFunction.apply(object));
            }
        }
    }


    /**
     * Returns a list of Strings containing the objects passed in argument.
     *
     * <p>If the objects are strings, they are directly added to the list. If the objects are
     * collections of strings, the strings are added. For other objects, the result of their
     * toString() is added.
     *
     * @param objects the objects to add
     * @return the list of objects.
     */
    @NonNull
    public static List<String> toStrings(@NonNull Object... objects) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (Object path : objects) {
            if (path instanceof String) {
                builder.add((String) path);
            } else if (path instanceof Collection) {
                Collection pathCollection = (Collection) path;
                for (Object item : pathCollection) {
                    if (item instanceof String) {
                        builder.add((String) item);
                    } else {
                        builder.add(path.toString());
                    }
                }
            } else {
                builder.add(path.toString());
            }
        }

        return builder.build();
    }

    public static List<String> tokenizeCommandLineToEscaped(@NonNull String commandLine) {
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            return StringHelperWindows.tokenizeCommandLineToEscaped(commandLine);
        } else {
            return StringHelperPOSIX.tokenizeCommandLineToEscaped(commandLine);
        }
    }

    public static List<String> tokenizeCommandLineToRaw(@NonNull String commandLine) {
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            return StringHelperWindows.tokenizeCommandLineToRaw(commandLine);
        } else {
            return StringHelperPOSIX.tokenizeCommandLineToRaw(commandLine);
        }
    }

    public static String toSystemLineSeparator(@NonNull String input) {
        return toLineSeparator(System.lineSeparator(), input);
    }

    private static String toLineSeparator(String separator, @NonNull String input) {
        String unixStyle = CR.matchesAnyOf(input) ? CR.removeFrom(input) : input;
        return separator.equals("\n") ? unixStyle : LF.matcher(unixStyle).replaceAll("\r\n");
    }
}
