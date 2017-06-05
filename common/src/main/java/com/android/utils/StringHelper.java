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
import com.android.annotations.Nullable;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class StringHelper {

    private static final CharMatcher CR = CharMatcher.is('\r');
    private static final Pattern LF = Pattern.compile("\n", Pattern.LITERAL);

    @NonNull
    public static String capitalize(@NonNull String string) {
        return string.substring(0, 1).toUpperCase(Locale.US) + string.substring(1);
    }

    @NonNull
    public static String combineAsCamelCase(@NonNull Iterable<String> stringList) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String str : stringList) {
            if (first) {
                sb.append(str);
                first = false;
            } else {
                sb.append(StringHelper.capitalize(str));
            }
        }
        return sb.toString();
    }

    /**
     * Returns a list of Strings containing the objects passed in argument.
     *
     * If the objects are strings, they are directly added to the list.
     * If the objects are collections of strings, the strings are added.
     * For other objects, the result of their toString() is added.
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

    public static void appendCamelCase(@NonNull StringBuilder sb, @Nullable String word) {
        if (word != null) {
            if (sb.length() == 0) {
                sb.append(word);
            } else {
                sb.append(StringHelper.capitalize(word));
            }
        }
    }

    /**
     * Quote and join a list of tokens with platform specific rules.
     *
     * @param tokens the token to be quoted and joined
     * @return the string
     */
    @NonNull
    public static String quoteAndJoinTokens(@NonNull List<String> tokens) {
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
            return StringHelperWindows.quoteAndJoinTokens(tokens);
        else return StringHelperPOSIX.quoteAndJoinTokens(tokens);
    }

    /**
     * Tokenize a string with platform specific rules.
     *
     * @param string the string to be tokenized
     * @return the list of tokens
     */
    @NonNull
    public static List<String> tokenizeString(@NonNull String string) {
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
            return StringHelperWindows.tokenizeString(string);
        else return StringHelperPOSIX.tokenizeString(string);
    }

    public static String toSystemLineSeparator(@NonNull String input) {
        return toLineSeparator(System.lineSeparator(), input);
    }

    private static String toLineSeparator(String separator, @NonNull String input) {
        String unixStyle = CR.matchesAnyOf(input) ? CR.removeFrom(input) : input;
        return separator.equals("\n") ? unixStyle : LF.matcher(unixStyle).replaceAll("\r\n");
    }
}
