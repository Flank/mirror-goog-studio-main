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

import com.android.tools.lint.detector.api.Detector

class WrongLocationDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return WrongLocationDetector()
    }

    fun testOk() {
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                <resources>
                    <!-- Home -->
                    <string name="home_title">Home Sample</string>
                    <string name="show_all_apps">All</string>

                    <!-- Home Menus -->
                    <string name="menu_wallpaper">Wallpaper</string>
                    <string name="menu_search">Search</string>
                    <string name="menu_settings">Settings</string>
                    <string name="sample" translatable="false">Ignore Me</string>

                    <!-- Wallpaper -->
                    <string name="wallpaper_instructions">Tap picture to set portrait wallpaper</string>
                </resources>

                """
            ).indented()
        ).run().expectClean()
    }

    fun test() {
        lint().files(
            xml(
                "res/layout/alias.xml",
                """
                <resources>
                    <!-- Home -->
                    <string name="home_title">Home Sample</string>
                    <string name="show_all_apps">All</string>

                    <!-- Home Menus -->
                    <string name="menu_wallpaper">Wallpaper</string>
                    <string name="menu_search">Search</string>
                    <string name="menu_settings">Settings</string>
                    <string name="sample" translatable="false">Ignore Me</string>

                    <!-- Wallpaper -->
                    <string name="wallpaper_instructions">Tap picture to set portrait wallpaper</string>
                </resources>

                """
            ).indented()
        ).run().expect(
            """
            res/layout/alias.xml:1: Error: This file should be placed in a values/ folder, not a layout/ folder [WrongFolder]
            <resources>
             ~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }
}
