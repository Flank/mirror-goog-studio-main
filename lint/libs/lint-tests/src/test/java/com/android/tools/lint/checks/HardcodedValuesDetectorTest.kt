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

class HardcodedValuesDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return HardcodedValuesDetector()
    }

    fun testStrings() {
        lint().files(
            xml(
                "res/layout/accessibility.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/newlinear" android:orientation="vertical" android:layout_width="match_parent" android:layout_height="match_parent">
                    <Button android:text="Button" android:id="@+id/button1" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                    <ImageView android:id="@+id/android_logo" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                    <ImageButton android:importantForAccessibility="yes" android:id="@+id/android_logo2" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                    <Button android:text="Button" android:id="@+id/button2" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                    <Button android:id="@+android:id/summary" android:contentDescription="@string/label" />
                    <ImageButton android:importantForAccessibility="no" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/accessibility.xml:2: Warning: Hardcoded string "Button", should use @string resource [HardcodedText]
                <Button android:text="Button" android:id="@+id/button1" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                        ~~~~~~~~~~~~~~~~~~~~~
            res/layout/accessibility.xml:5: Warning: Hardcoded string "Button", should use @string resource [HardcodedText]
                <Button android:text="Button" android:id="@+id/button2" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                        ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testMenus() {
        lint().files(
            xml(
                "res/menu/menu.xml",
                """
            <menu xmlns:android="http://schemas.android.com/apk/res/android" >

                <item
                    android:id="@+id/item1"
                    android:icon="@drawable/icon1"
                    android:title="My title 1">
                </item>
                <item
                    android:id="@+id/item2"
                    android:icon="@drawable/icon2"
                    android:showAsAction="ifRoom"
                    android:title="My title 2">
                </item>

            </menu>
            """
            ).indented()
        ).run().expect(
            """
            res/menu/menu.xml:6: Warning: Hardcoded string "My title 1", should use @string resource [HardcodedText]
                    android:title="My title 1">
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/menu/menu.xml:12: Warning: Hardcoded string "My title 2", should use @string resource [HardcodedText]
                    android:title="My title 2">
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testMenusOk() {
        lint().files(
            xml(
                "res/menu/titles.xml",
                """
                <menu xmlns:android="http://schemas.android.com/apk/res/android">
                    <item android:id="@+id/action_bar_progress_spinner"
                        android:showAsAction="always"
                        android:background="@null"
                        android:selectableItemBackground="@null"
                        android:actionLayout="@layout/action_bar_progress_spinner_layout"/>
                    <item android:id="@+id/refresh"
                        android:title="@string/menu_refresh"
                        android:showAsAction="always"
                        android:icon="@drawable/ic_menu_refresh"/>
                    <item android:id="@+id/menu_plus_one"
                        android:showAsAction="always"
                        android:icon="@drawable/ic_menu_plus1"/>
                </menu>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testSuppress() {
        // All but one errors in the file contain ignore attributes - direct, inherited
        // and lists
        lint().files(
            xml(
                "res/layout/ignores.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:id="@+id/newlinear"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical" >

                    <!-- Ignored via attribute, should be hidden -->

                    <Button
                        android:id="@+id/button1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button1"
                        tools:ignore="HardcodedText" >
                    </Button>

                    <!-- Inherited ignore from parent -->

                    <LinearLayout
                        android:id="@+id/parent"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        tools:ignore="HardcodedText" >

                        <Button
                            android:id="@+id/button2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Button2" >
                        </Button>
                    </LinearLayout>

                    <!-- Hardcoded text warning ignored through "all" -->

                    <Button
                        android:id="@+id/button3"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button3"
                        tools:ignore="all" >
                    </Button>

                    <!-- Ignored through item in ignore list -->

                    <Button
                        android:id="@+id/button4"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hardcoded"
                        tools:ignore="NewApi,HardcodedText" >
                    </Button>

                    <!-- Not ignored: should show up as a warning -->

                    <Button
                        android:id="@+id/button5"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hardcoded"
                        tools:ignore="Other" >
                    </Button>

                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/ignores.xml:60: Warning: Hardcoded string "Hardcoded", should use @string resource [HardcodedText]
                    android:text="Hardcoded"
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testSuppressViaComment() {
        lint().files(
            xml(
                "res/layout/ignores2.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/newlinear"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical" >

                    <!-- Ignored via comment, should be hidden -->

                    <!--suppress AndroidLintHardcodedText -->
                    <Button
                        android:id="@+id/button1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button1" >
                    </Button>

                    <!-- Inherited ignore from parent -->

                    <!--suppress AndroidLintHardcodedText-->
                    <LinearLayout
                        android:id="@+id/parent"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" >

                        <Button
                            android:id="@+id/button2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Button2" >
                        </Button>
                    </LinearLayout>

                    <!-- Ignored through item in ignore list -->

                    <!--suppress AndroidLintNewApi,AndroidLintHardcodedText -->

                    <Button
                        android:id="@+id/button4"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hardcoded" >
                    </Button>

                    <!-- Not ignored: should show up as a warning -->
                    <!--suppress AndroidLintNewApi -->
                    <Button
                        android:id="@+id/button5"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hardcoded"
                        >
                    </Button>

                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/ignores2.xml:50: Warning: Hardcoded string "Hardcoded", should use @string resource [HardcodedText]
                    android:text="Hardcoded"
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testSkippingPlaceHolders() {
        lint().files(
            xml(
                "res/layout/test.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <TextView
                        android:id="@+id/textView"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hello World!" />

                    <Button
                        android:id="@+id/button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="New Button" />

                    <TextView
                        android:id="@+id/textView2"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Large Text"
                        android:textAppearance="?android:attr/textAppearanceLarge" />

                    <Button
                        android:id="@+id/button2"
                        style="?android:attr/buttonStyleSmall"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="New Button" />

                    <CheckBox
                        android:id="@+id/checkBox"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="New CheckBox" />

                    <TextView
                        android:id="@+id/textView3"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="New Text" />
                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAppRestrictions() {
        // Sample from https://developer.android.com/samples/AppRestrictionSchema/index.html
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
                        android:defaultValue="Hardcoded default value"
                        android:description="Hardcoded description"
                        android:key="message"
                        android:restrictionType="string"
                        android:title="Hardcoded title"/>

                </restrictions>"""
            ).indented(),
            xml(
                "res/xml/random_file.xml",
                """<myRoot xmlns:android="http://schemas.android.com/apk/res/android">

                    <myElement
                        android:description="Hardcoded description"
                        android:title="Hardcoded title"/>

                </myRoot>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/app_restrictions.xml:12: Warning: Hardcoded string "Hardcoded description", should use @string resource [HardcodedText]
                    android:description="Hardcoded description"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/xml/app_restrictions.xml:15: Warning: Hardcoded string "Hardcoded title", should use @string resource [HardcodedText]
                    android:title="Hardcoded title"/>
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testToggleButtonLabels() {
        // Regression test for
        // https://code.google.com/p/android/issues/detail?id=206106
        // Ensure that the toggle button text label attributes are internationalized
        val expected =
            """
            res/layout/test.xml:4: Warning: Hardcoded string "Hi tools!", should use @string resource [HardcodedText]
                 android:textOn="Hi tools!"
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/test.xml:5: Warning: Hardcoded string "Bye tools!", should use @string resource [HardcodedText]
                 android:textOff="Bye tools!" />
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        lint().files(
            xml(
                "res/layout/test.xml",
                """
                <ToggleButton xmlns:android="http://schemas.android.com/apk/res/android"
                     android:layout_width="wrap_content"
                     android:layout_height="wrap_content"
                     android:textOn="Hi tools!"
                     android:textOff="Bye tools!" />
                 """
            ).indented()
        ).run().expect(expected)
    }
}
