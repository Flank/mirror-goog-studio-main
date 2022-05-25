/*
 * Copyright (C) 2022 The Android Open Source Project
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

class MonochromeLauncherIconDetectorTest : AbstractCheckTest() {
    override fun getDetector() = MonochromeLauncherIconDetector()

    fun testDocumentationExample() {
        lint().files(
            xml(
                "res/drawable-ldpi/ic_icon.xml",
                """
                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                        <background android:drawable="@drawable/ic_launcher_background" />
                        <foreground android:drawable="@drawable/ic_launcher_foreground" />
                    </adaptive-icon>
                """
            ),
            xml(
                "res/drawable-ldpi/ic_round_icon.xml",
                """
                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                        <background android:drawable="@drawable/ic_launcher_background" />
                        <foreground android:drawable="@drawable/ic_launcher_foreground" />
                    </adaptive-icon>
                """
            ),
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                        <application
                            android:icon="@drawable/ic_icon"
                            android:roundIcon="@drawable/ic_round_icon"
                            android:label="@string/app_name" >
                        </application>
                    </manifest>"""
            ).indented()
        ).run()
            .expect(
                """
                res/drawable-ldpi/ic_icon.xml:2: Warning: The application adaptive icon is missing a monochrome tag [MonochromeLauncherIcon]
                                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                                    ^
                res/drawable-ldpi/ic_round_icon.xml:2: Warning: The application adaptive roundIcon is missing a monochrome tag [MonochromeLauncherIcon]
                                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                                    ^
                0 errors, 2 warnings
                """
            )
    }

    fun testNoIssues() {
        lint().files(
            xml(
                "res/drawable-ldpi/ic_icon.xml",
                """
                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                        <background android:drawable="@drawable/ic_launcher_background" />
                        <foreground android:drawable="@drawable/ic_launcher_foreground" />
                        <monochrome android:drawable="@drawable/myicon" />
                    </adaptive-icon>
                """
            ),
            xml(
                "res/drawable-ldpi/ic_round_icon.xml",
                """
                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                        <background android:drawable="@drawable/ic_launcher_background" />
                        <foreground android:drawable="@drawable/ic_launcher_foreground" />
                        <monochrome android:drawable="@drawable/myicon" />
                    </adaptive-icon>
                """
            ),
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                        <application
                            android:icon="@drawable/ic_icon"
                            android:roundIcon="@drawable/ic_round_icon"
                            android:label="@string/app_name" >
                        </application>
                    </manifest>"""
            ).indented()
        ).run()
            .expectClean()
    }

    fun testOnlyRoundIconMonochrome() {
        lint().files(
            xml(
                "res/drawable-ldpi/ic_icon.xml",
                """
                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                        <background android:drawable="@drawable/ic_launcher_background" />
                        <foreground android:drawable="@drawable/ic_launcher_foreground" />
                    </adaptive-icon>
                """
            ),
            xml(
                "res/drawable-ldpi/ic_round_icon.xml",
                """
                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                        <background android:drawable="@drawable/ic_launcher_background" />
                        <foreground android:drawable="@drawable/ic_launcher_foreground" />
                        <monochrome android:drawable="@drawable/myicon" />
                    </adaptive-icon>
                """
            ),
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                        <application
                            android:icon="@drawable/ic_icon"
                            android:roundIcon="@drawable/ic_round_icon"
                            android:label="@string/app_name" >
                        </application>
                    </manifest>"""
            ).indented()
        ).run()
            .expect(
                """
                res/drawable-ldpi/ic_icon.xml:2: Warning: The application adaptive icon is missing a monochrome tag [MonochromeLauncherIcon]
                                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                                    ^
                0 errors, 1 warnings
                """
            )
    }

    fun testPngIcon() {
        lint().files(
            xml(
                "res/drawable-ldpi/ic_icon.xml",
                """
                    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                        <background android:drawable="@drawable/ic_launcher_background" />
                        <foreground android:drawable="@drawable/ic_launcher_foreground" />
                        <monochrome android:drawable="@drawable/myicon" />
                    </adaptive-icon>
                """
            ),
            image("res/drawable/ic_round_icon.png", 48, 48),
            manifest(
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                        <application
                            android:icon="@drawable/ic_icon"
                            android:roundIcon="@drawable/ic_round_icon"
                            android:label="@string/app_name" >
                        </application>
                    </manifest>"""
            ).indented()
        ).run()
            .expectClean()
    }
}
