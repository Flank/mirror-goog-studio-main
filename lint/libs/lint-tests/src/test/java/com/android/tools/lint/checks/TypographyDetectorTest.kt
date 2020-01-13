/*
 * Copyright (C) 2011 The Android Open Source Project
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

class TypographyDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return TypographyDetector()
    }

    fun test() {
        lint().files(
            xml(
                "res/values/typography.xml",
                """

                <resources>
                    <string name="home_title">Home 'Sample'</string>
                    <string name="show_all_apps">"All"</string>
                    <string name="show_all_apps2">Show "All"</string>
                    <string name="escaped">Skip \"All\"</string>
                    <string name="single">Android's</string>
                    <string name="copyright">(c) 2011</string>
                    <string name="badquotes1">`First'</string>
                    <string name="badquotes2">``second''</string>
                    <string name="notbadquotes">Type Option-` then 'Escape'</string>
                    <string name="fraction1">5 1/2 times</string>
                    <string name="fraction4">1/4 times</string>
                    <string name="notfraction">51/2 times, 1/20</string>
                    <string name="ellipsis">40 times...</string>
                    <string name="notellipsis">40 times.......</string>
                    <string name="ndash">For ages 3-5</string>
                    <string name="ndash2">Copyright 2007 - 2011</string>
                    <string name="nontndash">x-y</string>
                    <string name="mdash">Not found -- please try again</string>
                    <string name="nontndash">----</string>
                    <string name="notdirectional">A's and B's</string>
                    <string-array name="typography">
                        <item>Ages 3-5</item>
                        <item>Age 5 1/2</item>
                    </string-array>
                    <string name="ndash">X Y Z: 10 10 -1</string>
                    <string name="ga_trackingId">UA-0000-0</string>
                </resources>

                """
            ).indented()
        ).run().expect(
            """
            res/values/typography.xml:17: Warning: Replace "-" with an "en dash" character (–, &#8211;) ? [TypographyDashes]
                <string name="ndash">For ages 3-5</string>
                                     ^
            res/values/typography.xml:18: Warning: Replace "-" with an "en dash" character (–, &#8211;) ? [TypographyDashes]
                <string name="ndash2">Copyright 2007 - 2011</string>
                                      ^
            res/values/typography.xml:20: Warning: Replace "--" with an "em dash" character (—, &#8212;) ? [TypographyDashes]
                <string name="mdash">Not found -- please try again</string>
                                     ^
            res/values/typography.xml:24: Warning: Replace "-" with an "en dash" character (–, &#8211;) ? [TypographyDashes]
                    <item>Ages 3-5</item>
                          ^
            res/values/typography.xml:15: Warning: Replace "..." with ellipsis character (…, &#8230;) ? [TypographyEllipsis]
                <string name="ellipsis">40 times...</string>
                                        ^
            res/values/typography.xml:12: Warning: Use fraction character ½ (&#189;) instead of 1/2 ? [TypographyFractions]
                <string name="fraction1">5 1/2 times</string>
                                         ^
            res/values/typography.xml:13: Warning: Use fraction character ¼ (&#188;) instead of 1/4 ? [TypographyFractions]
                <string name="fraction4">1/4 times</string>
                                         ^
            res/values/typography.xml:25: Warning: Use fraction character ½ (&#189;) instead of 1/2 ? [TypographyFractions]
                    <item>Age 5 1/2</item>
                          ^
            res/values/typography.xml:3: Warning: Replace straight quotes ('') with directional quotes (‘’, &#8216; and &#8217;) ? [TypographyQuotes]
                <string name="home_title">Home 'Sample'</string>
                                          ^
            res/values/typography.xml:5: Warning: Replace straight quotes (") with directional quotes (“”, &#8220; and &#8221;) ? [TypographyQuotes]
                <string name="show_all_apps2">Show "All"</string>
                                              ^
            res/values/typography.xml:6: Warning: Replace straight quotes (") with directional quotes (“”, &#8220; and &#8221;) ? [TypographyQuotes]
                <string name="escaped">Skip \"All\"</string>
                                       ^
            res/values/typography.xml:7: Warning: Replace apostrophe (') with typographic apostrophe (’, &#8217;) ? [TypographyQuotes]
                <string name="single">Android's</string>
                                      ^
            res/values/typography.xml:9: Warning: Replace apostrophe (') with typographic apostrophe (’, &#8217;) ? [TypographyQuotes]
                <string name="badquotes1">`First'</string>
                                          ^
            res/values/typography.xml:10: Warning: Avoid quoting with grave accents; use apostrophes or better yet directional quotes instead [TypographyQuotes]
                <string name="badquotes2">``second''</string>
                                          ^
            res/values/typography.xml:11: Warning: Replace straight quotes ('') with directional quotes (‘’, &#8216; and &#8217;) ? [TypographyQuotes]
                <string name="notbadquotes">Type Option-` then 'Escape'</string>
                                            ^
            res/values/typography.xml:8: Warning: Replace (c) with copyright symbol © (&#169;) ? [TypographyOther]
                <string name="copyright">(c) 2011</string>
                                         ^
            0 errors, 16 warnings
            """
        )
    }

    fun testAnalytics() {
        //noinspection all // Sample code
        lint().files(
            xml(
                "res/values/analytics.xml",
                """
                <resources>
                  <!--Replace placeholder ID with your tracking ID-->
                  <string name="ga_trackingId">UA-12345678-1</string>

                  <!--Enable Activity tracking-->
                  <bool name="ga_autoActivityTracking">true</bool>

                  <!--Enable automatic exception tracking-->
                  <bool name="ga_reportUncaughtExceptions">true</bool>

                  <!-- The screen names that will appear in your reporting -->
                  <string name="com.example.app.BaseActivity">Home</string>
                  <string name="com.example.app.PrefsActivity">Preferences</string>
                  <string name="test.pkg.OnClickActivity">Clicks</string>
                </resources>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testPlurals() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=82588
        lint().files(
            xml(
                "res/values/plurals_typography.xml",
                """

                <resources>
                    <plurals name="ndash">
                        <!-- Nonsensical plural, just adding strings for typography check in plurals -->
                        <item quantity="one">For ages 3-5</item>
                        <item quantity="few">1/4 times</item>
                        <!-- ..and a typo -->
                        <item quantity="other">Andriod</item>
                    </plurals>

                    <!-- Nonsensical string array, just adding strings for typography check in arrays -->
                    <string-array name="security_questions">
                        <item>Seach</item>
                        <item>``second''</item>
                    </string-array>
                </resources>
                """
            ).indented()
        ).run().expect(
            """
            res/values/plurals_typography.xml:5: Warning: Replace "-" with an "en dash" character (–, &#8211;) ? [TypographyDashes]
                    <item quantity="one">For ages 3-5</item>
                                         ^
            res/values/plurals_typography.xml:6: Warning: Use fraction character ¼ (&#188;) instead of 1/4 ? [TypographyFractions]
                    <item quantity="few">1/4 times</item>
                                         ^
            res/values/plurals_typography.xml:14: Warning: Avoid quoting with grave accents; use apostrophes or better yet directional quotes instead [TypographyQuotes]
                    <item>``second''</item>
                          ^
            0 errors, 3 warnings
            """
        )
    }

    fun testDashesInUntranslatable() {
        // Regression test for
        //    https://code.google.com/p/android/issues/detail?id=214088
        // Don't flag service keys and untranslatable keys
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                <resources>
                    <string name="untranslatable" translatable="false">12345-1abcd.1234abcd.apps.googleusercontent.com</string>
                    <string name="default_web_client_id">12345-1abcd.1234abcd.apps.googleusercontent.com</string>
                </resources>
                """
            ).indented()
        ).run().expectClean()
    }
}
