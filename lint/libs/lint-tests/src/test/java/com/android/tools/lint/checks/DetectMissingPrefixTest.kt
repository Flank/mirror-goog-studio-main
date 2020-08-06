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

import com.android.SdkConstants
import com.android.testutils.TestUtils
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.google.common.collect.Lists

class DetectMissingPrefixTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return DetectMissingPrefix()
    }

    fun testBasic() {
        lint().files(
            xml(
                "res/layout/namespace.xml",
                """

                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:other="http://foo.bar" android:id="@+id/newlinear" android:orientation="vertical" android:layout_width="match_parent" android:layout_height="match_parent" orientation="true">
                    <Button style="@style/setupWizardOuterFrame" android.text="Button" android:id="@+id/button1" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                    <ImageView android:style="@style/bogus" android:id="@+id/android_logo" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                    <LinearLayout other:orientation="horizontal"/>
                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/namespace.xml:2: Error: Attribute is missing the Android namespace prefix [MissingPrefix]
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:other="http://foo.bar" android:id="@+id/newlinear" android:orientation="vertical" android:layout_width="match_parent" android:layout_height="match_parent" orientation="true">
                                                                                                                                                                                                                                                      ~~~~~~~~~~~~~~~~~~
            res/layout/namespace.xml:3: Error: Attribute is missing the Android namespace prefix [MissingPrefix]
                <Button style="@style/setupWizardOuterFrame" android.text="Button" android:id="@+id/button1" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                                                             ~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testCustomNamespace() {
        lint().files(
            xml(
                "res/layout/namespace2.xml",
                """

                <LinearLayout
                    xmlns:customprefix="http://schemas.android.com/apk/res/android"
                    xmlns:bogus="http://foo.com/bar"
                    customprefix:id="@+id/newlinear"
                    customprefix:layout_width="match_parent"
                    customprefix:layout_height="match_parent"
                    customprefix:orientation="vertical"
                    orientation="true">

                    <view class="foo.bar.LinearLayout">
                        bogus:orientation="bogus"
                    </view>

                    <foo.bar.LinearLayout
                        customprefix:id="@+id/newlinear2"
                        customprefix:layout_width="match_parent"
                        customprefix:layout_height="match_parent"
                        customprefix:orientation="vertical"
                        bogus:orientation="bogus"
                        orientation="true">

                        <view class="foo.bar.LinearLayout">
                            bogus:orientation="bogus"
                        </view>

                    </foo.bar.LinearLayout>

                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/namespace2.xml:9: Error: Attribute is missing the Android namespace prefix [MissingPrefix]
                orientation="true">
                ~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testCustomAttributesOnFragment() {
        lint().files(
            xml(
                "res/layout/fragment_custom_attrs.xml",
                """
                <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:app="http://schemas.android.com/apk/res-auto"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:orientation="vertical">

                    <fragment
                            android:name="android.app.ListFragment"
                            android:layout_width="match_parent"
                            app:custom_attribute="some_value"
                            android:layout_height="wrap_content"/>

                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testManifest() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """

                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    versionCode="1"
                    split="mysplit"
                    android:versionName="1.0" >
                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android.label="@string/app_name" >
                        <activity
                            android:label="@string/app_name"
                            android:name=".Foo2Activity" >
                            <intent-filter >
                                <action android:name="android.intent.action.MAIN" />

                                <category name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:4: Error: Attribute is missing the Android namespace prefix [MissingPrefix]
                versionCode="1"
                ~~~~~~~~~~~~~~~
            AndroidManifest.xml:11: Error: Attribute is missing the Android namespace prefix [MissingPrefix]
                    android.label="@string/app_name" >
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:18: Error: Attribute is missing the Android namespace prefix [MissingPrefix]
                            <category name="android.intent.category.LAUNCHER" />
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        )
    }

    fun testLayoutAttributes() {
        lint().files(
            xml(
                "res/layout/namespace3.xml",
                """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res/com.example.apicalltest"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" >

                    <com.example.library.MyView
                        android:layout_width="300dp"
                        android:layout_height="300dp"
                        android:background="#ccc"
                        android:paddingBottom="40dp"
                        android:paddingLeft="20dp"
                        app:exampleColor="#33b5e5"
                        app:exampleDimension="24sp"
                        app:exampleDrawable="@android:drawable/ic_menu_add"
                        app:exampleString="Hello, MyView" />

                </FrameLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testLayoutAttributes2() {
        lint().files(
            xml(
                "res/layout/namespace4.xml",
                """
                <android.support.v7.widget.GridLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    xmlns:app="http://schemas.android.com/apk/res/com.example.apicalltest"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    app:columnCount="1"
                    tools:context=".MainActivity" >
                    <Button
                        android:id="@+id/button1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_column="0"
                        app:layout_gravity="center"
                        app:layout_row="0"
                        android:text="Button" />

                </android.support.v7.widget.GridLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testUnusedNamespace() {
        lint().files(
            xml(
                "res/layout/message_edit_detail.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical"
                    xmlns:app="http://schemas.android.com/apk/res/foo.bar.baz"
                    xmlns:tools="http://schemas.android.com/tools">

                    <android.support.v7.widget.GridLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        app:orientation="horizontal"
                        app:useDefaultMargins="true">

                        <TextView
                            app:layout_rowSpan="1"
                            android:text="@string/birthdays"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content" />
                        <TextView
                            android:text="@string/abs__action_bar_home_description"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content" />
                        <TextView
                            android:text="@string/abs__action_mode_done"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content" />
                    </android.support.v7.widget.GridLayout>

                    <EditText
                        android:id="@+id/editTextView"
                        android:visibility="invisible"
                        android:inputType="textMultiLine|textLongMessage|textAutoCorrect"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" />

                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testMissingLayoutAttribute() {
        lint().files(
            projectProperties().compileSdk(10),
            manifest().minSdk(5).targetSdk(17),
            xml(
                "res/layout/rtl.xml",
                """

                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:ignore="HardcodedText" >

                    <Button
                        layout_gravity="left"
                        layout_alignParentLeft="true"
                        editable="false"
                        android:text="Button" />

                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/rtl.xml:7: Error: Attribute is missing the Android namespace prefix [MissingPrefix]
                    layout_gravity="left"
                    ~~~~~~~~~~~~~~~~~~~~~
            res/layout/rtl.xml:8: Error: Attribute is missing the Android namespace prefix [MissingPrefix]
                    layout_alignParentLeft="true"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/rtl.xml:9: Error: Attribute is missing the Android namespace prefix [MissingPrefix]
                    editable="false"
                    ~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        )
    }

    fun testDataBinding() {
        lint().files(
            xml(
                "res/layout/test.xml",
                """
                <layout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:bind="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools">
                    <data>
                        <variable name="activity" type="com.android.example.bindingdemo.MainActivity"/>
                        <!---->
                        <import
                            type="android.view.View"
                            />
                        <!---->
                        <import type="com.android.example.bindingdemo.R.string" alias="Strings"/>
                        <import type="com.android.example.bindingdemo.vo.User"/>
                    </data>
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:orientation="vertical"
                        android:id="@+id/activityRoot"
                        android:clickable="true"
                        android:onClickListener="@{activity.onUnselect}">
                        <android.support.v7.widget.CardView
                            android:id="@+id/selected_card"
                            bind:contentPadding="@{activity.selected == null ? 5 : activity.selected.name.length()}"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            bind:visibility="@{activity.selected == null ? View.INVISIBLE : View.VISIBLE}">

                            <GridLayout
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:columnCount="2"
                                android:rowCount="4">
                                <Button
                                    android:id="@+id/edit_button"
                                    bind:onClickListener="@{activity.onSave}"
                                    android:text='@{"Save changes to " + activity.selected.name}'
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:layout_column="1"
                                    android:layout_gravity="right"
                                    android:layout_row="2"/>
                            </GridLayout>
                        </android.support.v7.widget.CardView>    </LinearLayout>
                </layout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAppCompat() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=201790
        lint().files(
            xml(
                "res/layout/app_compat.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical">

                    <ImageButton
                        android:id="@+id/vote_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:srcCompat="@mipmap/ic_launcher" />

                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAppCompatOther() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=211348
        lint().files(
            xml(
                "res/layout/app_compat.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical">

                    <ImageButton
                        android:id="@+id/vote_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:buttonTint="#ff00ff" />

                </LinearLayout>
                """
            ).indented(),
            xml(
                "res/values/res.xml",
                """
                <resources>
                    <attr name="buttonTint" />
                </resources>
                """
            ).indented()
        )
            .client(object : com.android.tools.lint.checks.infrastructure.TestLintClient() {
                // Set fake library name on resources in this test to pretend the
                // attr comes from appcompat
                override fun getProjectResourceLibraryName(): String? {
                    return "appcompat-v7"
                }
            })
            .sdkHome(TestUtils.getSdk())
            .incremental("res/layout/app_compat.xml").run().expectClean()
    }

    fun testMaterialDesign2() {
        // Regression test for https://b.corp.google.com/78246338
        lint().files(
            xml(
                "res/layout/app_compat.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical">

                    <Button
                        android:id="@+id/m_button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:cornerRadius="12dp"
                        app:rippleColor="@android:color/holo_blue_bright"
                        app:strokeColor="@android:color/holo_blue_light"
                        app:strokeWidth="2dp" />
                </LinearLayout>
                """
            ).indented(),
            xml(
                "res/values/res.xml",
                """
                <resources>
                    <attr name="cornerRadius" />
                    <attr name="rippleColor" />
                    <attr name="strokeColor" />
                    <attr name="strokeWidth" />
                </resources>
                """
            ).indented()
        )
            .client(object : com.android.tools.lint.checks.infrastructure.TestLintClient() {
                // testMaterialDesign2
                // Set fake library name on resources in this test to pretend the
                // attr comes from appcompat
                override fun getProjectResourceLibraryName(): String? {
                    return SdkConstants.ANDROIDX_MATERIAL_ARTIFACT + ":1.0.0"
                }
            })
            .sdkHome(TestUtils.getSdk())
            .incremental("res/layout/app_compat.xml").run().expectClean()
    }

    fun testAaptBundleFormat() {
        lint().files(
            xml(
                "res/drawable/my_drawable.xml",
                """
                <inset xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:aapt="http://schemas.android.com/aapt"
                    android:inset="100dp">

                    <aapt:attr name="android:drawable">
                        <color android:color="@color/colorAccent" />
                    </aapt:attr>
                </inset>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testXmlns() {
        lint().files(
            xml(
                "res/layout/foo.xml",
                """
                <RelativeLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/playbackReplayOptionsLayout"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="10dp">
                    <RelativeLayout
                        android:id="@+id/continueBlock"
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_toRightOf="@+id/replayBlock">
                    </RelativeLayout>
                </RelativeLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/foo.xml:9: Warning: Unused namespace declaration xmlns:android; already declared on the root element [UnusedNamespace]
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testFontFamilyWithAppCompat() {
        lint().files(
            manifest().minSdk(1),
            xml(
                "res/layout/foo.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:id="@+id/LinearLayout1"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical" >
                    <TextView
                        app:fontFamily="@font/my_font"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"/>
                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAutoSize() {
        // Regression test for 78486045: Handle autoSize attributes
        lint().files(
            manifest().minSdk(1),
            xml(
                "res/layout/foo.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:id="@+id/LinearLayout1"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical" >

                      <TextView
                         android:layout_width="match_parent"
                         android:layout_height="200dp"
                         app:autoSizeTextType="uniform"
                         app:autoSizeMinTextSize="12sp"
                         app:autoSizeMaxTextSize="100sp"
                         app:autoSizeStepGranularity="2sp" />
                      <TextView
                         android:layout_width="match_parent"
                         android:layout_height="200dp"
                         app:autoSizeTextType="uniform"
                         app:autoSizePresetSizes="@array/autosize_text_sizes" />

                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    override fun getIssues(): List<Issue> {
        val combined: MutableList<Issue> =
            Lists.newArrayList(
                super.getIssues()
            )
        combined.add(NamespaceDetector.UNUSED)
        return combined
    }
}
