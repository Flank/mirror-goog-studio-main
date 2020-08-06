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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class LogDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return LogDetector()
    }

    fun testBasic() {
        val expected =
            """
            src/test/pkg/LogTest.java:33: Error: Mismatched tags: the d() and isLoggable() calls typically should pass the same tag: TAG1 versus TAG2 [LogTagMismatch]
                        Log.d(TAG2, "message"); // warn: mismatched tags!
                              ~~~~
                src/test/pkg/LogTest.java:32: Conflicting tag
            src/test/pkg/LogTest.java:36: Error: Mismatched tags: the d() and isLoggable() calls typically should pass the same tag: "my_tag" versus "other_tag" [LogTagMismatch]
                        Log.d("other_tag", "message"); // warn: mismatched tags!
                              ~~~~~~~~~~~
                src/test/pkg/LogTest.java:35: Conflicting tag
            src/test/pkg/LogTest.java:80: Error: Mismatched logging levels: when checking isLoggable level DEBUG, the corresponding log call should be Log.d, not Log.v [LogTagMismatch]
                        Log.v(TAG1, "message"); // warn: wrong level
                            ~
                src/test/pkg/LogTest.java:79: Conflicting tag
            src/test/pkg/LogTest.java:83: Error: Mismatched logging levels: when checking isLoggable level DEBUG, the corresponding log call should be Log.d, not Log.v [LogTagMismatch]
                        Log.v(TAG1, "message"); // warn: wrong level
                            ~
                src/test/pkg/LogTest.java:82: Conflicting tag
            src/test/pkg/LogTest.java:86: Error: Mismatched logging levels: when checking isLoggable level VERBOSE, the corresponding log call should be Log.v, not Log.d [LogTagMismatch]
                        Log.d(TAG1, "message"); // warn? verbose is a lower logging level, which includes debug
                            ~
                src/test/pkg/LogTest.java:85: Conflicting tag
            src/test/pkg/LogTest.java:53: Error: The logging tag can be at most 23 characters, was 43 (really_really_really_really_really_long_tag) [LongLogTag]
                        Log.d("really_really_really_really_really_long_tag", "message"); // error: too long
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LogTest.java:59: Error: The logging tag can be at most 23 characters, was 24 (123456789012345678901234) [LongLogTag]
                        Log.d(TAG24, "message"); // error: too long
                              ~~~~~
            src/test/pkg/LogTest.java:60: Error: The logging tag can be at most 23 characters, was 39 (MyReallyReallyReallyReallyReallyLongTag) [LongLogTag]
                        Log.d(LONG_TAG, "message"); // error: way too long
                              ~~~~~~~~
            src/test/pkg/LogTest.java:64: Error: The logging tag can be at most 23 characters, was 39 (MyReallyReallyReallyReallyReallyLongTag) [LongLogTag]
                        Log.d(LOCAL_TAG, "message"); // error: too long
                              ~~~~~~~~~
            src/test/pkg/LogTest.java:67: Error: The logging tag can be at most 23 characters, was 28 (1234567890123456789012MyTag1) [LongLogTag]
                        Log.d(TAG22 + TAG1, "message"); // error: too long
                              ~~~~~~~~~~~~
            src/test/pkg/LogTest.java:68: Error: The logging tag can be at most 23 characters, was 27 (1234567890123456789012MyTag) [LongLogTag]
                        Log.d(TAG22 + "MyTag", "message"); // error: too long
                              ~~~~~~~~~~~~~~~
            src/test/pkg/LogTest.java:21: Warning: The log call Log.i(...) should be conditional: surround with if (Log.isLoggable(...)) or if (BuildConfig.DEBUG) { ... } [LogConditional]
                    Log.i(TAG1, "message" + m); // error: unconditional w/ computation
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LogTest.java:22: Warning: The log call Log.i(...) should be conditional: surround with if (Log.isLoggable(...)) or if (BuildConfig.DEBUG) { ... } [LogConditional]
                    Log.i(TAG1, toString()); // error: unconditional w/ computation
                    ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LogTest.java:106: Warning: The log call Log.d(...) should be conditional: surround with if (Log.isLoggable(...)) or if (BuildConfig.DEBUG) { ... } [LogConditional]
                        Log.d("Test", "Test" + getClass().toString()); // warn: unconditional
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            11 errors, 3 warnings
            """

        lint().files(
            java(
                "src/test/pkg/LogTest.java",
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import android.util.Log;
                import static android.util.Log.DEBUG;

                @SuppressWarnings({"UnusedDeclaration", "ClassNameDiffersFromFileName"})
                public class LogTest {
                    private static final String TAG1 = "MyTag1";
                    private static final String TAG2 = "MyTag2";
                    private static final String TAG22 = "1234567890123456789012";
                    private static final String TAG23 = "12345678901234567890123";
                    private static final String TAG24 = "123456789012345678901234";
                    private static final String LONG_TAG = "MyReallyReallyReallyReallyReallyLongTag";

                    public void checkConditional(String m) {
                        Log.d(TAG1, "message"); // ok: unconditional, but not performing computation
                        Log.d(TAG1, m); // ok: unconditional, but not performing computation
                        Log.d(TAG1, "ab"); // ok: unconditional, but not performing non-constant computation
                        Log.d(TAG1, Constants.MY_MESSAGE); // ok: unconditional, but constant string
                        Log.i(TAG1, "message" + m); // error: unconditional w/ computation
                        Log.i(TAG1, toString()); // error: unconditional w/ computation
                        Log.e(TAG1, toString()); // ok: only flagging debug/info messages
                        Log.w(TAG1, toString()); // ok: only flagging debug/info messages
                        Log.wtf(TAG1, toString()); // ok: only flagging debug/info messages
                        if (Log.isLoggable(TAG1, 0)) {
                            Log.d(TAG1, toString()); // ok: conditional
                        }
                    }

                    public void checkWrongTag(String tag) {
                        if (Log.isLoggable(TAG1, Log.DEBUG)) {
                            Log.d(TAG2, "message"); // warn: mismatched tags!
                        }
                        if (Log.isLoggable("my_tag", Log.DEBUG)) {
                            Log.d("other_tag", "message"); // warn: mismatched tags!
                        }
                        if (Log.isLoggable("my_tag", Log.DEBUG)) {
                            Log.d("my_tag", "message"); // ok: strings equal
                        }
                        if (Log.isLoggable(TAG1, Log.DEBUG)) {
                            Log.d(LogTest.TAG1, "message"); // OK: same tag; different access syntax
                        }
                        if (Log.isLoggable(tag, Log.DEBUG)) {
                            Log.d(tag, "message"); // ok: same variable
                        }
                    }

                    public void checkLongTag(boolean shouldLog) {
                        if (shouldLog) {
                            // String literal tags
                            Log.d("short_tag", "message"); // ok: short
                            Log.d("really_really_really_really_really_long_tag", "message"); // error: too long

                            // Resolved field tags
                            Log.d(TAG1, "message"); // ok: short
                            Log.d(TAG22, "message"); // ok: short
                            Log.d(TAG23, "message"); // ok: threshold
                            Log.d(TAG24, "message"); // error: too long
                            Log.d(LONG_TAG, "message"); // error: way too long

                            // Locally defined variable tags
                            final String LOCAL_TAG = "MyReallyReallyReallyReallyReallyLongTag";
                            Log.d(LOCAL_TAG, "message"); // error: too long

                            // Concatenated tags
                            Log.d(TAG22 + TAG1, "message"); // error: too long
                            Log.d(TAG22 + "MyTag", "message"); // error: too long
                        }
                    }

                    public void checkWrongLevel(String tag) {
                        if (Log.isLoggable(TAG1, Log.DEBUG)) {
                            Log.d(TAG1, "message"); // ok: right level
                        }
                        if (Log.isLoggable(TAG1, Log.INFO)) {
                            Log.i(TAG1, "message"); // ok: right level
                        }
                        if (Log.isLoggable(TAG1, Log.DEBUG)) {
                            Log.v(TAG1, "message"); // warn: wrong level
                        }
                        if (Log.isLoggable(TAG1, DEBUG)) { // static import of level
                            Log.v(TAG1, "message"); // warn: wrong level
                        }
                        if (Log.isLoggable(TAG1, Log.VERBOSE)) {
                            Log.d(TAG1, "message"); // warn? verbose is a lower logging level, which includes debug
                        }
                        if (Log.isLoggable(TAG1, Constants.MY_LEVEL)) {
                            Log.d(TAG1, "message"); // ok: unknown level alias
                        }
                    }

                    @SuppressLint("all")
                    public void suppressed1() {
                        Log.d(TAG1, "message"); // ok: suppressed
                    }

                    @SuppressLint("LogConditional")
                    public void suppressed2() {
                        Log.d(TAG1, "message"); // ok: suppressed
                    }

                    // Regression test for https://issuetracker.google.com/111063607
                    public void notActuallyConditional() {
                        if (true) {
                            Log.d("Test", "Test" + getClass().toString()); // warn: unconditional
                        }
                        if (false) {
                            Log.d("Test", "Test" + getClass().toString()); // ok: never called
                        }
                    }

                    private static class Constants {
                        public static final String MY_MESSAGE = "My Message";
                        public static final int MY_LEVEL = 5;
                    }
                }"""
            ).indented()
        ).run().expect(expected)
    }

    fun testNoMaxLength() {
        // As of API level 24 there's no limit of 23 chars anymore

        lint().files(
            manifest().minSdk(24),
            java(
                "src/test/pkg/LogTest.java",
                """
                    package test.pkg;

                    import android.annotation.SuppressLint;
                    import android.util.Log;
                    import static android.util.Log.DEBUG;

                    @SuppressWarnings("ClassNameDiffersFromFileName") public class LogTest2 {
                        public void checkLongTag(boolean shouldLog) {
                            if (shouldLog) {
                                // String literal tags
                                Log.d("really_really_really_really_really_long_tag", "message"); // error: too long
                            }
                        }
                    }"""
            ).indented()
        ).run().expectClean()
    }
}
