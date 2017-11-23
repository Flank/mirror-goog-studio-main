/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Range;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class AndroidVersionMatcher {

    public static Matcher<AndroidVersion> thatUsesArt() {
        return atLeast(21);
    }

    public static Matcher<AndroidVersion> atLeast(int version) {
        return forRange(Range.atLeast(version));
    }

    public static Matcher<AndroidVersion> atMost(int version) {
        return forRange(Range.atMost(version));
    }

    public static Matcher<AndroidVersion> anyAndroidVersion() {
        return new BaseMatcher<AndroidVersion>() {
            @Override
            public boolean matches(Object item) {
                return true;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("All android versions");
            }
        };
    }

    public static Matcher<AndroidVersion> forRange(Range<Integer> range) {
        return new BaseMatcher<AndroidVersion>() {
            @Override
            public boolean matches(Object item) {
                return item instanceof AndroidVersion &&
                        range.contains(((AndroidVersion) item).getApiLevel());
            }

            @Override
            public void describeTo(Description description) {
                description
                        .appendText("Android versions in the ")
                        .appendText(range.toString())
                        .appendText(" range.");
            }
        };
    }

    public static Matcher<AndroidVersion> exactly(int value) {
        return new BaseMatcher<AndroidVersion>() {
            @Override
            public boolean matches(Object item) {
                return item instanceof AndroidVersion &&
                        ((AndroidVersion) item).getApiLevel() == value;
            }

            @Override
            public void describeTo(Description description) {
                description
                        .appendText("Android version ")
                        .appendValue(value)
                        .appendText(".");
            }
        };
    }
}
