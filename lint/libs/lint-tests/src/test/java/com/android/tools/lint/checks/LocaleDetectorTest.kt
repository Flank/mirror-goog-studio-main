/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Detector

class LocaleDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return LocaleDetector()
    }

    fun testBasic() {
        val expected =
            """
            src/test/pkg/LocaleTest.java:11: Warning: Implicitly using the default locale is a common source of bugs: Use toUpperCase(Locale) instead. For strings meant to be internal use Locale.ROOT, otherwise Locale.getDefault(). [DefaultLocale]
                    System.out.println("WRONG".toUpperCase());
                                               ~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:16: Warning: Implicitly using the default locale is a common source of bugs: Use toLowerCase(Locale) instead. For strings meant to be internal use Locale.ROOT, otherwise Locale.getDefault(). [DefaultLocale]
                    System.out.println("WRONG".toLowerCase());
                                               ~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:20: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]
                    String.format("WRONG: %f", 1.0f); // Implies locale
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:21: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]
                    String.format("WRONG: %1＄f", 1.0f);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:22: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]
                    String.format("WRONG: %e", 1.0f);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:23: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]
                    String.format("WRONG: %d", 1.0f);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:24: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]
                    String.format("WRONG: %g", 1.0f);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:25: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]
                    String.format("WRONG: %g", 1.0f);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:26: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]
                    String.format("WRONG: %1＄tm %1＄te,%1＄tY",
                    ^
            0 errors, 9 warnings
            """

        lint().files(
            java(
                "src/test/pkg/LocaleTest.java",
                """
                    package test.pkg;

                    import java.text.*;
                    import java.util.*;
                    @SuppressWarnings({"ResultOfMethodCallIgnored", "MalformedFormatString", "MethodMayBeStatic", "ResultOfObjectAllocationIgnored", "SimpleDateFormatWithoutLocale", "StringToUpperCaseOrToLowerCaseWithoutLocale", "ClassNameDiffersFromFileName"})
                    public class LocaleTest {
                        public void testStrings() {
                            System.out.println("OK".toUpperCase(Locale.getDefault()));
                            System.out.println("OK".toUpperCase(Locale.US));
                            System.out.println("OK".toUpperCase(Locale.CHINA));
                            System.out.println("WRONG".toUpperCase());

                            System.out.println("OK".toLowerCase(Locale.getDefault()));
                            System.out.println("OK".toLowerCase(Locale.US));
                            System.out.println("OK".toLowerCase(Locale.CHINA));
                            System.out.println("WRONG".toLowerCase());

                            String.format(Locale.getDefault(), "OK: %f", 1.0f);
                            String.format("OK: %x %A %c %b %B %h %n %%", 1, 2, 'c', true, false, 5);
                            String.format("WRONG: %f", 1.0f); // Implies locale
                            String.format("WRONG: %1＄f", 1.0f);
                            String.format("WRONG: %e", 1.0f);
                            String.format("WRONG: %d", 1.0f);
                            String.format("WRONG: %g", 1.0f);
                            String.format("WRONG: %g", 1.0f);
                            String.format("WRONG: %1＄tm %1＄te,%1＄tY",
                                    new GregorianCalendar(2012, GregorianCalendar.AUGUST, 27));
                        }

                        @android.annotation.SuppressLint("NewApi") // DateFormatSymbols requires API 9
                        public void testSimpleDateFormat() {
                            new SimpleDateFormat(); // WRONG
                            new SimpleDateFormat("yyyy-MM-dd"); // WRONG
                            new SimpleDateFormat("yyyy-MM-dd", DateFormatSymbols.getInstance()); // WRONG
                            new SimpleDateFormat("yyyy-MM-dd", Locale.US); // OK
                        }
                    }
                    """
            ).indented()
        ).run().expect(expected)
    }

    fun testStudio() {
        val expected =
            """
            src/test/pkg/LocaleTest.java:8: Warning: Implicitly using the default locale is a common source of bugs: Use String.format(Locale, ...) instead [DefaultLocale]
                    String.format("WRONG: %f", 1.0f); // Implies locale
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """

        lint().files(
            java(
                "src/test/pkg/LocaleTest.java",
                """
                    package test.pkg;

                    @SuppressWarnings({"ResultOfMethodCallIgnored", "MalformedFormatString", "MethodMayBeStatic", "ResultOfObjectAllocationIgnored", "SimpleDateFormatWithoutLocale", "StringToUpperCaseOrToLowerCaseWithoutLocale", "ClassNameDiffersFromFileName"})
                    public class LocaleTest {
                        public void testStrings() {
                            System.out.println("WRONG BUT HANDLED SEPARATELY".toUpperCase());
                            System.out.println("WRONG BUT HANDLED SEPARATELY".toLowerCase());
                            String.format("WRONG: %f", 1.0f); // Implies locale
                        }
                    }
                    """
            ).indented()
        ).client(TestLintClient(LintClient.CLIENT_STUDIO)).run().expect(expected)
    }

    fun testKotlinCapitalize() {
        val expected =
            """
            src/test/pkg/LocaleTest.kt:2: Warning: Implicitly using the default locale is a common source of bugs: Use capitalize(Locale) instead. For strings meant to be internal use Locale.ROOT, otherwise Locale.getDefault(). [DefaultLocale]
                "wrong".capitalize()
                        ~~~~~~~~~~
            src/test/pkg/LocaleTest.kt:4: Warning: Implicitly using the default locale is a common source of bugs: Use decapitalize(Locale) instead. For strings meant to be internal use Locale.ROOT, otherwise Locale.getDefault(). [DefaultLocale]
                "Wrong".decapitalize()
                        ~~~~~~~~~~~~
            src/test/pkg/LocaleTest.kt:6: Warning: Implicitly using the default locale is a common source of bugs: Use toUpperCase(Locale) instead. For strings meant to be internal use Locale.ROOT, otherwise Locale.getDefault(). [DefaultLocale]
                "wrong".toUpperCase()
                        ~~~~~~~~~~~
            src/test/pkg/LocaleTest.kt:8: Warning: Implicitly using the default locale is a common source of bugs: Use toLowerCase(Locale) instead. For strings meant to be internal use Locale.ROOT, otherwise Locale.getDefault(). [DefaultLocale]
                "WRONG".toLowerCase()
                        ~~~~~~~~~~~
            0 errors, 4 warnings
            """

        val expectedFixDiffs =
            """
            Fix for src/test/pkg/LocaleTest.kt line 2: Replace with `capitalize(Locale.ROOT)`:
            @@ -2 +2
            -     "wrong".capitalize()
            +     "wrong".capitalize(java.util.Locale.ROOT)
            Fix for src/test/pkg/LocaleTest.kt line 2: Replace with `capitalize(Locale.getDefault())`:
            @@ -2 +2
            -     "wrong".capitalize()
            +     "wrong".capitalize(java.util.Locale.getDefault())
            Fix for src/test/pkg/LocaleTest.kt line 4: Replace with `decapitalize(Locale.ROOT)`:
            @@ -4 +4
            -     "Wrong".decapitalize()
            +     "Wrong".decapitalize(java.util.Locale.ROOT)
            Fix for src/test/pkg/LocaleTest.kt line 4: Replace with `decapitalize(Locale.getDefault())`:
            @@ -4 +4
            -     "Wrong".decapitalize()
            +     "Wrong".decapitalize(java.util.Locale.getDefault())
            Fix for src/test/pkg/LocaleTest.kt line 6: Replace with `toUpperCase(Locale.ROOT)`:
            @@ -6 +6
            -     "wrong".toUpperCase()
            +     "wrong".toUpperCase(java.util.Locale.ROOT)
            Fix for src/test/pkg/LocaleTest.kt line 6: Replace with `toUpperCase(Locale.getDefault())`:
            @@ -6 +6
            -     "wrong".toUpperCase()
            +     "wrong".toUpperCase(java.util.Locale.getDefault())
            Fix for src/test/pkg/LocaleTest.kt line 8: Replace with `toLowerCase(Locale.ROOT)`:
            @@ -8 +8
            -     "WRONG".toLowerCase()
            +     "WRONG".toLowerCase(java.util.Locale.ROOT)
            Fix for src/test/pkg/LocaleTest.kt line 8: Replace with `toLowerCase(Locale.getDefault())`:
            @@ -8 +8
            -     "WRONG".toLowerCase()
            +     "WRONG".toLowerCase(java.util.Locale.getDefault())
        """

        lint().files(
            kotlin(
                "src/test/pkg/LocaleTest.kt",
                """
                    fun useMethods() {
                        "wrong".capitalize()
                        "ok".capitalize(Locale.US)
                        "Wrong".decapitalize()
                        "Ok".decapitalize(Locale.US)
                        "wrong".toUpperCase()
                        "ok".toUpperCase(Locale.US)
                        "WRONG".toLowerCase()
                        "ok".toLowerCase(Locale.US)
                    }
                    """
            ).indented()
        ).run().expect(expected).expectFixDiffs(expectedFixDiffs)
    }

    fun testIgnoreLoggingWithoutLocale() {
        lint().files(
            java(
                "src/test/pkg/LogTest.java",
                """
                package test.pkg;

                import android.util.Log;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class LogTest {
                    private static final String TAG = "mytag";

                    // Don't flag String.format inside logging calls
                    public void test(String dataItemName, int eventStatus) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, String.format("CQS:Event=%s, keeping status=%d", dataItemName,
                                    eventStatus));
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testIgnoreLoggingWithinThrow() {
        // Regression test for 63859789
        // 63859789: Can DefaultLocale Inspection be disabled for Exception messages and even Log

        lint().files(
            java(
                "src/test/pkg/LogTest.java",
                """
                package test.pkg;

                import android.util.Log;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class LogTest2 {

                    public void test3(int argument) {
                        throw new NullPointerException(String.format("This message isn't user-facing %d", argument));
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testFinalLocaleField() {
        // Regression test for https://issuetracker.google.com/73981396
        val expected =
            """
            src/test/pkg/MainActivity.kt:10: Warning: Assigning Locale.getDefault() to a final static field is suspicious; this code will not work correctly if the user changes locale while the app is running [ConstantLocale]
                    val PROBLEMATIC_DESCRIPTION_DATE_FORMAT = SimpleDateFormat("MMM dd", Locale.getDefault())
                                                                                         ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestLocaleJava.java:13: Warning: Assigning Locale.getDefault() to a final static field is suspicious; this code will not work correctly if the user changes locale while the app is running [ConstantLocale]
                static final Locale errorLocale = Locale.getDefault();
                                                  ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestLocaleKotlin.kt:9: Warning: Assigning Locale.getDefault() to a final static field is suspicious; this code will not work correctly if the user changes locale while the app is running [ConstantLocale]
                    val errorLocale = Locale.getDefault()
                                      ~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """

        lint().files(
            java(
                """
                package test.pkg;

                import java.util.Locale;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class TestLocaleJava {
                    public static Locale okLocale1 = Locale.getDefault();
                    public Locale okLocale2 = Locale.getDefault();
                    public final Locale okLocale3 = Locale.getDefault();
                    public void test() {
                        final Locale okLocale4 = Locale.getDefault();
                    }
                    static final Locale errorLocale = Locale.getDefault();
                }
                """
            ).indented(),
            kotlin(
                """
                    package test.pkg

                    import java.util.Locale

                    @Suppress("HasPlatformType","JoinDeclarationAndAssignment")
                    class TestLocaleKotlin {
                        companion object {
                            var okLocale1 = Locale.getDefault()
                            val errorLocale = Locale.getDefault()
                        }
                        var okLocale2 = Locale.getDefault()
                        val okLocale3 = Locale.getDefault()
                        fun test() {
                            val okLocale4 = Locale.getDefault()
                        }
                        val okLocale5: Locale
                        init {
                            okLocale5 = Locale.getDefault()
                        }
                    }
                """
            ).indented(),
            // From https://issuetracker.google.com/73981396
            kotlin(
                """
                package test.pkg

                import android.app.Activity
                import android.util.Log
                import java.text.SimpleDateFormat
                import java.util.*

                class MainActivity : Activity() {
                    companion object {
                        val PROBLEMATIC_DESCRIPTION_DATE_FORMAT = SimpleDateFormat("MMM dd", Locale.getDefault())
                        //same for the single parameter CTOR : SimpleDateFormat("MMM dd")
                    }

                    @Suppress("PropertyName")
                    val SAFE_DESCRIPTION_DATE_FORMAT = SimpleDateFormat("MMM dd", Locale.getDefault())

                    override fun onResume() {
                        super.onResume()
                        val today = Calendar.getInstance().time
                        Log.d("AppLog", "problematic:" + PROBLEMATIC_DESCRIPTION_DATE_FORMAT.format(today))
                        Log.d("AppLog", "safe:" + SAFE_DESCRIPTION_DATE_FORMAT.format(today))
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testNonFields() {
        // Regression test for https://issuetracker.google.com/122769438
        lint().files(
            java(
                """
                package test.pkg;
                import java.util.Locale;
                public enum JavaEnum {
                    VALUE {
                        public Locale getDefaultLocale() {
                            return Locale.getDefault();
                        }
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                import java.util.Locale
                enum class KotlinEnum {
                    VALUE {
                        fun getDefaultLocale() = Locale.getDefault()
                    }
                }

                class Test {
                    companion object {
                        val someField = object : Any() {
                            fun defaultLocale() = Locale.getDefault()
                        }
                        val localeLookup: ()->Locale = { Locale.getDefault() }
                    }
                }
                """
            )
        ).run().expectClean()
    }
}
