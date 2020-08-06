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

import com.android.tools.lint.checks.RestrictionsDetector.Companion.MAX_NESTING_DEPTH
import com.android.tools.lint.checks.RestrictionsDetector.Companion.MAX_NUMBER_OF_NESTED_RESTRICTIONS

import com.android.tools.lint.detector.api.Detector

class RestrictionsDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector {
        return RestrictionsDetector()
    }

    fun testSample() {
        // Sample from https://developer.android.com/samples/AppRestrictionSchema/index.html
        // We expect no warnings.
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            package="com.example.android.apprestrictionschema"
                            android:versionCode="1"
                            android:versionName="1.0">

                            <!-- uses-sdk android:minSdkVersion="21" android:targetSdkVersion="21" /-->

                            <application
                                android:allowBackup="true"
                                android:icon="@drawable/ic_launcher"
                                android:label="@string/app_name"
                                android:theme="@style/AppTheme">

                                <meta-data
                                    android:name="android.content.APP_RESTRICTIONS"
                                    android:resource="@xml/app_restrictions" />

                                <activity
                                    android:name=".MainActivity"
                                    android:label="@string/app_name">
                                    <intent-filter>
                                        <action android:name="android.intent.action.MAIN" />
                                        <category android:name="android.intent.category.LAUNCHER" />
                                    </intent-filter>
                                </activity>
                            </application>


                        </manifest>"""
            ).indented(),
            xml(
                "res/xml/app_restrictions.xml",
                """
                    <restrictions xmlns:android="http://schemas.android.com/apk/res/android">

                        <!--
                        Refer to the javadoc of RestrictionsManager for detail of this file.
                        https://developer.android.com/reference/android/content/RestrictionsManager.html
                        -->

                        <restriction
                            android:defaultValue="@bool/default_can_say_hello"
                            android:description="@string/description_can_say_hello"
                            android:key="can_say_hello"
                            android:restrictionType="bool"
                            android:title="@string/title_can_say_hello"/>

                        <restriction
                            android:defaultValue="@string/default_message"
                            android:description="@string/description_message"
                            android:key="message"
                            android:restrictionType="string"
                            android:title="@string/title_message"/>

                        <restriction
                            android:defaultValue="@integer/default_number"
                            android:description="@string/description_number"
                            android:key="number"
                            android:restrictionType="integer"
                            android:title="@string/title_number"/>

                        <restriction
                            android:defaultValue="@string/default_rank"
                            android:description="@string/description_rank"
                            android:entries="@array/entries_rank"
                            android:entryValues="@array/entry_values_rank"
                            android:key="rank"
                            android:restrictionType="choice"
                            android:title="@string/title_rank"/>

                        <restriction
                            android:defaultValue="@array/default_approvals"
                            android:description="@string/description_approvals"
                            android:entries="@array/entries_approvals"
                            android:entryValues="@array/entry_values_approvals"
                            android:key="approvals"
                            android:restrictionType="multi-select"
                            android:title="@string/title_approvals"/>

                        <restriction
                            android:defaultValue="@string/default_secret_code"
                            android:description="@string/description_secret_code"
                            android:key="secret_code"
                            android:restrictionType="hidden"
                            android:title="@string/title_secret_code"/>

                    </restrictions>"""
            ).indented()
        ).run().expectClean()
    }

    fun testMissingRequiredAttributes() {
        val expected =
            """
            res/xml/app_restrictions.xml:2: Error: Missing required attribute android:key [ValidRestrictions]
                <restriction />
                ~~~~~~~~~~~~~~~
            res/xml/app_restrictions.xml:2: Error: Missing required attribute android:restrictionType [ValidRestrictions]
                <restriction />
                ~~~~~~~~~~~~~~~
            res/xml/app_restrictions.xml:2: Error: Missing required attribute android:title [ValidRestrictions]
                <restriction />
                ~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                            <restriction />
                        </restrictions>"""
            ).indented()
        ).run().expect(expected)
    }

    fun testNewSample() {
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                    <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                        <restriction android:key="key_bool"
                                android:restrictionType="bool"
                                android:title="@string/title_bool"
                                android:description="@string/desc_bool"
                                android:defaultValue="true"
                                />
                        <restriction android:key="key_int"
                                android:restrictionType="integer"
                                android:title="@string/title_int"
                                android:defaultValue="15"
                                />
                        <restriction android:key="key_string"
                                android:restrictionType="string"
                                android:defaultValue="@string/string_value"
                                android:title="@string/missing_title"
                                />
                        <restriction android:key="components"
                                     android:restrictionType="bundle_array"
                                     android:title="@string/title_bundle_array"
                                     android:description="@string/desc_bundle_array">
                            <restriction android:restrictionType="bundle"
                                         android:key="someKey"
                                         android:title="@string/title_bundle_comp"
                                         android:description="@string/desc_bundle_comp">
                                <restriction android:key="enabled"
                                             android:restrictionType="bool"
                                             android:defaultValue="true"
                                             android:title="@string/missing_title"
                                             />
                                <restriction android:key="name"
                                             android:restrictionType="string"
                                             android:title="@string/missing_title"
                                             />
                            </restriction>

                        </restriction>
                        <restriction android:key="connection_settings"
                                     android:restrictionType="bundle"
                                     android:title="@string/title_bundle"
                                     android:description="@string/desc_bundle">
                            <restriction android:key="max_wait_time_ms"
                                         android:restrictionType="integer"
                                         android:title="@string/title_int"
                                         android:defaultValue="1000"
                                         />
                            <restriction android:key="host"
                                         android:restrictionType="string"
                                         android:title="@string/missing_title"
                                         />
                        </restriction>
                    </restrictions>
                    """
            ).indented()
        ).run().expectClean()
    }

    fun testMissingRequiredAttributesForChoice() {
        val expected =
            """
            res/xml/app_restrictions.xml:2: Error: Missing required attribute android:entries [ValidRestrictions]
                <restriction
                 ~~~~~~~~~~~
            res/xml/app_restrictions.xml:2: Error: Missing required attribute android:entryValues [ValidRestrictions]
                <restriction
                 ~~~~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                            <restriction
                                android:description="@string/description_number"
                                android:key="number"
                                android:restrictionType="choice"
                                android:title="@string/title_number"/>
                        </restrictions>"""
            ).indented()
        ).run().expect(expected)
    }

    fun testMissingRequiredAttributesForHidden() {
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                    <restriction
                        android:description="@string/description_number"
                        android:key="number"
                        android:restrictionType="hidden"
                        android:title="@string/title_number"/>
                </restrictions>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/app_restrictions.xml:2: Error: Missing required attribute android:defaultValue [ValidRestrictions]
                <restriction
                 ~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testValidNumber() {
        val expected =
            """
            res/xml/app_restrictions.xml:3: Error: Invalid number [ValidRestrictions]
                    android:defaultValue="abc"
                                          ~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                            <restriction
                                android:defaultValue="abc"
                                android:description="@string/description_number"
                                android:key="message1"
                                android:restrictionType="integer"
                                android:title="@string/title_number"/>
                            <restriction
                                android:defaultValue="@integer/default_number"
                                android:description="@string/description_message"
                                android:key="message2"
                                android:restrictionType="integer"
                                android:title="@string/title_number2"/>
                            <restriction
                                android:defaultValue="123"
                                android:description="@string/description_message2"
                                android:key="message3"
                                android:restrictionType="integer"
                                android:title="@string/title_number3"/>
                        </restrictions>"""
            ).indented()
        ).run().expect(expected)
    }

    fun testUnexpectedTag() {
        val expected =
            """
            res/xml/app_restrictions.xml:3: Error: Unexpected tag <wrongtag>, expected <restriction> [ValidRestrictions]
                <wrongtag />
                 ~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                            <!-- Comments are okay -->
                            <wrongtag />
                        </restrictions>
                        """
            ).indented()
        ).run().expect(expected)
    }

    fun testLocalizedKey() {
        val expected =
            """
            res/xml/app_restrictions.xml:5: Error: Keys cannot be localized, they should be specified with a string literal [ValidRestrictions]
                    android:key="@string/can_say_hello"
                                 ~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                            <restriction
                                android:defaultValue="@bool/default_can_say_hello"
                                android:description="@string/description_can_say_hello"
                                android:key="@string/can_say_hello"
                                android:restrictionType="bool"
                                android:title="@string/title_can_say_hello"/>
                        </restrictions>
                        """
            ).indented()
        ).run().expect(expected)
    }

    fun testDuplicateKeys() {
        val expected =
            """
            res/xml/app_restrictions.xml:19: Error: Duplicate key can_say_hello [ValidRestrictions]
                    android:key="can_say_hello"
                                 ~~~~~~~~~~~~~
                res/xml/app_restrictions.xml:5: Previous use of key here
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                            <restriction
                                android:defaultValue="@bool/default_can_say_hello"
                                android:description="@string/description_can_say_hello"
                                android:key="can_say_hello"
                                android:restrictionType="bool"
                                android:title="@string/title_can_say_hello"/>

                            <restriction
                                android:defaultValue="@string/default_message"
                                android:description="@string/description_message"
                                android:key="message"
                                android:restrictionType="string"
                                android:title="@string/title_message"/>

                            <restriction
                                android:defaultValue="@integer/default_number"
                                android:description="@string/description_number"
                                android:key="can_say_hello"
                                android:restrictionType="integer"
                                android:title="@string/title_number"/>
                        </restrictions>
                        """
            ).indented()
        ).run().expect(expected)
    }

    fun testNoDefaultValueForBundles() {
        val expected =
            """
            res/xml/app_restrictions.xml:3: Error: Restriction type bundle_array should not have a default value [ValidRestrictions]
                    android:defaultValue="@string/default_message"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                            <restriction
                                android:defaultValue="@string/default_message"
                                android:description="@string/description_message"
                                android:key="message"
                                android:restrictionType="bundle_array"
                                android:title="@string/title_message">
                              <restriction
                                  android:defaultValue="@bool/default_can_say_hello"
                                  android:description="@string/description_can_say_hello"
                                  android:key="can_say_hello"
                                  android:restrictionType="string"
                                  android:title="@string/title_can_say_hello"/>
                            </restriction>
                        </restrictions>
                    """
            ).indented()
        ).run().expect(expected)
    }

    fun testNoChildrenForBundle() {
        val expected =
            """
            res/xml/app_restrictions.xml:2: Error: Restriction type bundle should have at least one nested restriction [ValidRestrictions]
                <restriction
                 ~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                            <restriction
                                android:description="@string/description_message"
                                android:key="message"
                                android:restrictionType="bundle"
                                android:title="@string/title_message"/>
                        </restrictions>
                        """
            ).indented()
        ).run().expect(expected)
    }

    fun testNoChildrenForBundleArray() {
        val expected =
            """
            res/xml/app_restrictions.xml:2: Error: Expected exactly one child for restriction of type bundle_array [ValidRestrictions]
                <restriction
                 ~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                            <restriction
                                android:description="@string/description_message"
                                android:key="message"
                                android:restrictionType="bundle_array"
                                android:title="@string/title_message"/>
                        </restrictions>
                        """
            ).indented()
        ).run().expect(expected)
    }

    fun testTooManyChildren() {
        val sb = StringBuilder()
        for (i in 0 until MAX_NUMBER_OF_NESTED_RESTRICTIONS + 2) {

            sb.append(
                """
                <restriction
                        android:defaultValue="@bool/default_can_say_hello$i"
                        android:description="@string/description_can_say_hello$i"
                        android:key="can_say_hello$i"
                        android:restrictionType="bool"
                        android:title="@string/title_can_say_hello$i"/>

                """.trimIndent()
            )
        }

        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                        $sb
                        </restrictions>
                    """
            ).indented()
        ).run().expect(
            """
            res/xml/app_restrictions.xml:1: Error: Invalid nested restriction: too many nested restrictions (was 1002, max 1000) [ValidRestrictions]
                                    <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                                     ~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testNestingTooDeep() {
        val sb = StringBuilder()
        val maxDepth = MAX_NESTING_DEPTH + 1
        for (i in 0 until maxDepth) {

            sb.append(
                """
                    <restriction
                            android:description="@string/description_can_say_hello$i"
                            android:key="can_say_hello$i"
                            android:restrictionType="bundle"
                            android:title="@string/title_can_say_hello$i">

                """.trimIndent()
            )
        }
        sb.append(
            """
                <restriction
                        android:defaultValue="@string/default_message"
                        android:description="@string/description_message"
                        android:key="message"
                        android:restrictionType="string"
                        android:title="@string/title_message"/>

            """.trimIndent()
        )
        for (i in 0 until maxDepth) {
            sb.append("    </restriction>\n")
        }

        lint().files(
            xml(
                "res/xml/app_restrictions.xml",
                """
                        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                        $sb
                        </restrictions>
                    """
            )
        ).run().expect(
            """
                res/xml/app_restrictions.xml:103: Error: Invalid nested restriction: nesting depth 21 too large (max 20 [ValidRestrictions]
                <restriction
                 ~~~~~~~~~~~
                1 errors, 0 warnings
            """
        )
    }
}
