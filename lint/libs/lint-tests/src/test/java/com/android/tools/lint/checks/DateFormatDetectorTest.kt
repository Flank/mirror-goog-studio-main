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

    fun test() {
        val expected = """
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
                        String.format("WRONG: %1${"$"}f", 1.0f);
                        String.format("WRONG: %e", 1.0f);
                        String.format("WRONG: %d", 1.0f);
                        String.format("WRONG: %g", 1.0f);
                        String.format("WRONG: %g", 1.0f);
                        String.format("WRONG: %1${"$"}tm %1${"$"}te,%1${"$"}tY",
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
}
