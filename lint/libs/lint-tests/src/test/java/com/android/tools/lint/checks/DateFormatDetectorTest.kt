/*
 * Copyright (C) 2014 The Android Open Source Project
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

class DateFormatDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return DateFormatDetector()
    }

    fun testSpecifyingLocale() {
        val expected =
            """
            src/test/pkg/LocaleTest.java:32: Warning: To get local formatting use getDateInstance(), getDateTimeInstance(), or getTimeInstance(), or use new SimpleDateFormat(String template, Locale locale) with for example Locale.US for ASCII dates. [SimpleDateFormat]
                    new SimpleDateFormat(); // WRONG
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:33: Warning: To get local formatting use getDateInstance(), getDateTimeInstance(), or getTimeInstance(), or use new SimpleDateFormat(String template, Locale locale) with for example Locale.US for ASCII dates. [SimpleDateFormat]
                    new SimpleDateFormat("yyyy-MM-dd"); // WRONG
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/LocaleTest.java:34: Warning: To get local formatting use getDateInstance(), getDateTimeInstance(), or getTimeInstance(), or use new SimpleDateFormat(String template, Locale locale) with for example Locale.US for ASCII dates. [SimpleDateFormat]
                    new SimpleDateFormat("yyyy-MM-dd", DateFormatSymbols.getInstance()); // WRONG
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(
            manifest().minSdk(19),
            java(
                "src/test/pkg/LocaleTest.java",
                """
                package test.pkg;

                import java.text.*;
                import java.util.*;

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

    fun testEraYear() {
        lint().files(
            manifest().minSdk(19),
            kotlin(
                """
                @file:Suppress("unused")

                package test.pkg

                import android.annotation.SuppressLint
                import android.icu.text.SimpleDateFormat
                import android.os.Build
                import androidx.annotation.RequiresApi
                import java.time.format.DateTimeFormatter
                import java.util.*

                @SuppressLint("SimpleDateFormat")
                @RequiresApi(Build.VERSION_CODES.O)
                class DateFormatTest {
                    private val PROFILE_FILE_NAME: DateTimeFormatter =
                        DateTimeFormatter.ofPattern("'profile-'YYYY-MM-dd-HH-mm-ss-SSS'.rawproto'", Locale.US) // ERROR

                    fun testOk() {
                        val s1 = DateTimeFormatter.ofPattern("'Year'dd-MM-yy") // OK
                        val s2 = DateTimeFormatter.ofPattern("'''Y'dd-MM-yy") // OK
                        val s3 = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss") // OK
                        val s4 = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss") // OK
                        val s5 = SimpleDateFormat("HH:mm, MMM dd, yyyy") // OK
                        val s6 = java.text.SimpleDateFormat("HH:mm, MMM dd, yyyy") // OK
                        val s7 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()) // OK
                    }

                    fun testProblems(something: String) {
                        val s1 =  DateTimeFormatter.ofPattern("MMMM d, YYYY") // ERROR
                        val s2 =  SimpleDateFormat("MMMM d, YYYY") // ERROR
                        val s3 = DateTimeFormatter.ofPattern(""${'"'}dd-MM-YYYY""${'"'}) // ERROR
                        val s4 = DateTimeFormatter.ofPattern("'＄something'dd-MM-YYYY") // ERROR
                        val s5 = DateTimeFormatter.ofPattern("'\u1234'dd-MM-YYYY") // ERROR
                        val constant = "dd-MM-YYYY"
                        val s6 = DateTimeFormatter.ofPattern(constant) // ERROR
                        val s7 = DateTimeFormatter.ofPattern(""${'"'}dd-YY-MM""${'"'}) // ERROR
                        val s7 = DateTimeFormatter.ofPattern("YYYY-dd-MM versus yyyy-dd-MM") // OK -- both, probably okay
                        val s7 = DateTimeFormatter.ofPattern("YYYY-WW-FF") // OK No days or months
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/DateFormatTest.kt:16: Warning: DateFormat character 'Y' in YYYY is the week-era-year; did you mean 'y' ? [WeekBasedYear]
                    DateTimeFormatter.ofPattern("'profile-'YYYY-MM-dd-HH-mm-ss-SSS'.rawproto'", Locale.US) // ERROR
                                                           ~~~~
            src/test/pkg/DateFormatTest.kt:29: Warning: DateFormat character 'Y' in YYYY is the week-era-year; did you mean 'y' ? [WeekBasedYear]
                    val s1 =  DateTimeFormatter.ofPattern("MMMM d, YYYY") // ERROR
                                                                   ~~~~
            src/test/pkg/DateFormatTest.kt:30: Warning: DateFormat character 'Y' in YYYY is the week-era-year; did you mean 'y' ? [WeekBasedYear]
                    val s2 =  SimpleDateFormat("MMMM d, YYYY") // ERROR
                                                        ~~~~
            src/test/pkg/DateFormatTest.kt:31: Warning: DateFormat character 'Y' in YYYY is the week-era-year; did you mean 'y' ? [WeekBasedYear]
                    val s3 = DateTimeFormatter.ofPattern(""${'"'}dd-MM-YYYY""${'"'}) // ERROR
                                                                  ~~~~
            src/test/pkg/DateFormatTest.kt:32: Warning: DateFormat character 'Y' in YYYY is the week-era-year; did you mean 'y' ? [WeekBasedYear]
                    val s4 = DateTimeFormatter.ofPattern("'＄something'dd-MM-YYYY") // ERROR
                                                         ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/DateFormatTest.kt:33: Warning: DateFormat character 'Y' in YYYY is the week-era-year; did you mean 'y' ? [WeekBasedYear]
                    val s5 = DateTimeFormatter.ofPattern("'\u1234'dd-MM-YYYY") // ERROR
                                                         ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/DateFormatTest.kt:35: Warning: DateFormat character 'Y' in YYYY is the week-era-year; did you mean 'y' ? [WeekBasedYear]
                    val s6 = DateTimeFormatter.ofPattern(constant) // ERROR
                                                         ~~~~~~~~
            src/test/pkg/DateFormatTest.kt:36: Warning: DateFormat character 'Y' in YY is the week-era-year; did you mean 'y' ? [WeekBasedYear]
                    val s7 = DateTimeFormatter.ofPattern(""${'"'}dd-YY-MM""${'"'}) // ERROR
                                                               ~~
            0 errors, 8 warnings
            """
        )
    }
}
