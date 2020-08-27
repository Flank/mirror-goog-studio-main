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

class NamespaceDetectorTest : AbstractCheckTest() {

    private val mCustomview = xml(
        "res/layout/customview.xml",
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "    xmlns:other=\"http://schemas.foo.bar.com/other\"\n" +
            "    xmlns:foo=\"http://schemas.android.com/apk/res/foo\"\n" +
            "    android:id=\"@+id/newlinear\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    android:orientation=\"vertical\" >\n" +
            "\n" +
            "    <foo.bar.Baz\n" +
            "        android:id=\"@+id/button1\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"Button1\"\n" +
            "        foo:misc=\"Custom attribute\"\n" +
            "        tools:ignore=\"HardcodedText\" >\n" +
            "    </foo.bar.Baz>\n" +
            "\n" +
            "    <!-- Wrong namespace uri prefix: Don't warn -->\n" +
            "    <foo.bar.Baz\n" +
            "        android:id=\"@+id/button1\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"Button1\"\n" +
            "        other:misc=\"Custom attribute\"\n" +
            "        tools:ignore=\"HardcodedText\" >\n" +
            "    </foo.bar.Baz>\n" +
            "\n" +
            "</LinearLayout>\n"
    )

    private val mLibrary = source("build.gradle", "")
    private val mLibraryKts = source("build.gradle.kts", "")

    private val mNamespace3 = xml(
        "res/layout/namespace3.xml",
        "" +
            "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:app=\"http://schemas.android.com/apk/res/com.example.apicalltest\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\" >\n" +
            "\n" +
            "    <com.example.library.MyView\n" +
            "        android:layout_width=\"300dp\"\n" +
            "        android:layout_height=\"300dp\"\n" +
            "        android:background=\"#ccc\"\n" +
            "        android:paddingBottom=\"40dp\"\n" +
            "        android:paddingLeft=\"20dp\"\n" +
            "        app:exampleColor=\"#33b5e5\"\n" +
            "        app:exampleDimension=\"24sp\"\n" +
            "        app:exampleDrawable=\"@android:drawable/ic_menu_add\"\n" +
            "        app:exampleString=\"Hello, MyView\" />\n" +
            "\n" +
            "</FrameLayout>\n"
    )

    private val mNamespace4 = xml(
        "res/layout/namespace4.xml",
        "" +
            "<android.support.v7.widget.GridLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "    xmlns:app=\"http://schemas.android.com/apk/res/com.example.apicalltest\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    app:columnCount=\"1\"\n" +
            "    tools:context=\".MainActivity\" >\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button1\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        app:layout_column=\"0\"\n" +
            "        app:layout_gravity=\"center\"\n" +
            "        app:layout_row=\"0\"\n" +
            "        android:text=\"Button\" />\n" +
            "\n" +
            "</android.support.v7.widget.GridLayout>\n"
    )

    override fun getDetector(): Detector {
        return NamespaceDetector()
    }

    fun testCustom() {
        val expected =
            """
            res/layout/customview.xml:5: Error: When using a custom namespace attribute in a library project, use the namespace "http://schemas.android.com/apk/res-auto" instead [LibraryCustomView]
                xmlns:foo="http://schemas.android.com/apk/res/foo"
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            manifest().pkg("foo.library").minSdk(14),
            projectProperties().library(true).compileSdk(14),
            mCustomview
        ).run().expect(expected)
    }

    fun testGradle() {
        val expected =
            """
            res/layout/customview.xml:5: Error: In Gradle projects, always use http://schemas.android.com/apk/res-auto for custom attributes [ResAuto]
                xmlns:foo="http://schemas.android.com/apk/res/foo"
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            mLibrary, // placeholder; only name counts
            mCustomview
        ).run().expect(expected)
    }

    fun testGradleKts() {
        val expected =
            """
            res/layout/customview.xml:5: Error: In Gradle projects, always use http://schemas.android.com/apk/res-auto for custom attributes [ResAuto]
                xmlns:foo="http://schemas.android.com/apk/res/foo"
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            mLibraryKts, // placeholder; only name counts
            mCustomview
        ).run().expect(expected)
    }

    fun testGradle_namespaced() {
        // In a namespaced project it's fine (and necessary) to declare custom namespaces.
        lint().files(
            xml("src/main/res/layout/customview.xml", mCustomview.contents),
            gradle("android.aaptOptions.namespaced true")
        )
            .run()
            .expectClean()
    }

    fun testCustomOk() {
        lint().files(
            manifest().pkg("foo.library").minSdk(14),

            // Use a standard project properties instead: no warning since it's
            // not a library project:
            // "multiproject/library.properties⇒project.properties",

            mCustomview
        ).run().expectClean()
    }

    fun testCustomOk2() {
        lint().files(
            manifest().pkg("foo.library").minSdk(14),
            projectProperties().library(true).compileSdk(14),
            // This project already uses the res-auto package
            xml(
                "res/layout/customview2.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                    "    xmlns:other=\"http://schemas.foo.bar.com/other\"\n" +
                    "    xmlns:foo=\"http://schemas.android.com/apk/res-auto\"\n" +
                    "    android:id=\"@+id/newlinear\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\" >\n" +
                    "\n" +
                    "    <foo.bar.Baz\n" +
                    "        android:id=\"@+id/button1\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button1\"\n" +
                    "        foo:misc=\"Custom attribute\"\n" +
                    "        tools:ignore=\"HardcodedText\" >\n" +
                    "    </foo.bar.Baz>\n" +
                    "\n" +
                    "</LinearLayout>\n"
            )
        )
            .run().expectClean()
    }

    fun testTypo() {
        val expected =
            """
            res/layout/wrong_namespace.xml:2: Error: Unexpected namespace URI bound to the "android" prefix, was http://schemas.android.com/apk/res/andriod, expected http://schemas.android.com/apk/res/android [NamespaceTypo]
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/andriod"
                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/layout/wrong_namespace.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/andriod\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\" >\n" +
                    "\n" +
                    "    <include\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        layout=\"@layout/layout2\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        android:id=\"@+id/button1\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        android:id=\"@+id/button2\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "</LinearLayout>\n"
            )
        ).run().expect(expected)
    }

    fun testTypo2() {
        val expected =
            """
            res/layout/wrong_namespace2.xml:2: Error: URI is case sensitive: was "http://schemas.android.com/apk/res/Android", expected "http://schemas.android.com/apk/res/android" [NamespaceTypo]
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/Android"
                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/layout/wrong_namespace2.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/Android\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\" >\n" +
                    "\n" +
                    "    <include\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        layout=\"@layout/layout2\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        android:id=\"@+id/button1\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        android:id=\"@+id/button2\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "</LinearLayout>\n"
            )
        ).run().expect(expected)
    }

    fun testTypo3() {
        val expected =
            """
            res/layout/wrong_namespace3.xml:2: Error: Unexpected namespace URI bound to the "android" prefix, was http://schemas.android.com/apk/res/androi, expected http://schemas.android.com/apk/res/android [NamespaceTypo]
            <LinearLayout xmlns:a="http://schemas.android.com/apk/res/androi"
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/layout/wrong_namespace3.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:a=\"http://schemas.android.com/apk/res/androi\"\n" +
                    "    a:layout_width=\"match_parent\"\n" +
                    "    a:layout_height=\"match_parent\"\n" +
                    "    a:orientation=\"vertical\" >\n" +
                    "\n" +
                    "    <include\n" +
                    "        a:layout_width=\"wrap_content\"\n" +
                    "        a:layout_height=\"wrap_content\"\n" +
                    "        layout=\"@layout/layout2\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        a:id=\"@+id/button1\"\n" +
                    "        a:layout_width=\"wrap_content\"\n" +
                    "        a:layout_height=\"wrap_content\"\n" +
                    "        a:text=\"Button\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        a:id=\"@+id/button2\"\n" +
                    "        a:layout_width=\"wrap_content\"\n" +
                    "        a:layout_height=\"wrap_content\"\n" +
                    "        a:text=\"Button\" />\n" +
                    "\n" +
                    "</LinearLayout>\n"
            )
        ).run().expect(expected)
    }

    fun testTypo4() {
        val expected =
            """
            res/layout/wrong_namespace5.xml:2: Error: Suspicious namespace: should start with http:// [NamespaceTypo]
                xmlns:noturi="tp://schems.android.com/apk/res/com.my.package"
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/wrong_namespace5.xml:3: Error: Possible typo in URL: was "http://schems.android.com/apk/res/com.my.package", should probably be "http://schemas.android.com/apk/res/com.my.package" [NamespaceTypo]
                xmlns:typo1="http://schems.android.com/apk/res/com.my.package"
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/wrong_namespace5.xml:4: Error: Possible typo in URL: was "http://schems.android.comm/apk/res/com.my.package", should probably be "http://schemas.android.com/apk/res/com.my.package" [NamespaceTypo]
                xmlns:typo2="http://schems.android.comm/apk/res/com.my.package"
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/layout/wrong_namespace5.xml",
                "" +
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    xmlns:noturi=\"tp://schems.android.com/apk/res/com.my.package\"\n" +
                    "    xmlns:typo1=\"http://schems.android.com/apk/res/com.my.package\"\n" +
                    "    xmlns:typo2=\"http://schems.android.comm/apk/res/com.my.package\"\n" +
                    "    xmlns:ok=\"http://foo.bar/res/unrelated\"\n" +
                    "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                    "    xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\" >\n" +
                    "\n" +
                    "</RelativeLayout>\n"
            )
        ).run().expect(expected)
    }

    fun testMisleadingPrefix() {
        val expected =
            """
            res/layout/layout.xml:3: Error: Suspicious namespace and prefix combination [NamespaceTypo]
                xmlns:app="http://schemas.android.com/tools"
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout.xml:4: Error: Suspicious namespace and prefix combination [NamespaceTypo]
                xmlns:tools="http://schemas.android.com/apk/res/android"
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/layout/layout.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    xmlns:app=\"http://schemas.android.com/tools\"\n" +
                    "    xmlns:tools=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\"\n" +
                    "    app:foo=\"true\"\n" +
                    "    tools:bar=\"true\" />\n"
            )
        ).run().expect(expected)
    }

    fun testTypoOk() {
        lint().files(
            xml(
                "res/layout/wrong_namespace4.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<!-- This file does *not* have a wrong namespace: it's testdata to make sure we don't complain when \"a\" is defined for something unrelated -->\n" +
                    "<LinearLayout\n" +
                    "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    xmlns:a=\"http://something/very/different\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\" >\n" +
                    "\n" +
                    "    <include\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        layout=\"@layout/layout2\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        android:id=\"@+id/button1\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        android:id=\"@+id/button2\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "</LinearLayout>\n"
            )
        ).run().expectClean()
    }

    fun testUnused() {
        val expected =
            """
            res/layout/unused_namespace.xml:3: Warning: Unused namespace unused1 [UnusedNamespace]
                xmlns:unused1="http://schemas.android.com/apk/res/unused1"
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/unused_namespace.xml:4: Warning: Unused namespace unused2 [UnusedNamespace]
                xmlns:unused2="http://schemas.android.com/apk/res/unused1"
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        lint().files(
            xml(
                "res/layout/unused_namespace.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<foo.bar.LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    xmlns:unused1=\"http://schemas.android.com/apk/res/unused1\"\n" +
                    "    xmlns:unused2=\"http://schemas.android.com/apk/res/unused1\"\n" +
                    "    xmlns:unused3=\"http://foo.bar.com/foo\"\n" +
                    "    xmlns:notunused=\"http://schemas.android.com/apk/res/notunused\"\n" +
                    "    xmlns:tools=\"http://schemas.android.com/tools\" >\n" +
                    "\n" +
                    "    <foo.bar.Button\n" +
                    "        notunused:foo=\"Foo\"\n" +
                    "        tools:ignore=\"HardcodedText\" >\n" +
                    "    </foo.bar.Button>\n" +
                    "\n" +
                    "</foo.bar.LinearLayout>\n"
            )
        ).run().expect(expected)
    }

    fun testUnusedOk() {
        lint().files(
            xml(
                "res/layout/layout1.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\" >\n" +
                    "\n" +
                    "    <include\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        layout=\"@layout/layout2\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        android:id=\"@+id/button1\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "    <Button\n" +
                    "        android:id=\"@+id/button2\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "</LinearLayout>\n"
            )
        ).run().expectClean()
    }

    fun testLayoutAttributesOk() {
        lint().files(mNamespace3).run().expectClean()
    }

    fun testLayoutAttributesOk2() {
        lint().files(mNamespace4).run().expectClean()
    }

    fun testLayoutAttributes() {
        val expected =
            """
            res/layout/namespace3.xml:2: Error: When using a custom namespace attribute in a library project, use the namespace "http://schemas.android.com/apk/res-auto" instead [LibraryCustomView]
                xmlns:app="http://schemas.android.com/apk/res/com.example.apicalltest"
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            mNamespace3,
            manifest().pkg("foo.library").minSdk(14),
            projectProperties().library(true).compileSdk(14)
        ).run().expect(expected)
    }

    fun testLayoutAttributes2() {
        val expected =
            """
            res/layout/namespace4.xml:3: Error: When using a custom namespace attribute in a library project, use the namespace "http://schemas.android.com/apk/res-auto" instead [LibraryCustomView]
                xmlns:app="http://schemas.android.com/apk/res/com.example.apicalltest"
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            mNamespace4,
            manifest().pkg("foo.library").minSdk(14),
            projectProperties().library(true).compileSdk(14)
        ).run().expect(expected)
    }

    fun testWrongResAutoUrl() {
        val expected =
            """
            res/layout/namespace5.xml:3: Error: Suspicious namespace: Did you mean http://schemas.android.com/apk/res-auto? [ResAuto]
                xmlns:app="http://schemas.android.com/apk/auto-res/"
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/layout/namespace5.xml",
                "" +
                    "<android.support.v7.widget.GridLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                    "    xmlns:app=\"http://schemas.android.com/apk/auto-res/\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    app:columnCount=\"1\"\n" +
                    "    tools:context=\".MainActivity\" >\n" +
                    "\n" +
                    "    <Button\n" +
                    "        android:id=\"@+id/button1\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        app:layout_column=\"0\"\n" +
                    "        app:layout_gravity=\"center\"\n" +
                    "        app:layout_row=\"0\"\n" +
                    "        android:text=\"Button\" />\n" +
                    "\n" +
                    "</android.support.v7.widget.GridLayout>\n"
            ),
            manifest().pkg("foo.library").minSdk(14),
            projectProperties().library(true).compileSdk(14)
        ).run().expect(expected)
    }

    fun testWrongResUrl() {
        val expected =
            """
            AndroidManifest.xml:2: Error: Suspicious namespace: should start with http:// [NamespaceTypo]
            <manifest xmlns:android="https://schemas.android.com/apk/res/android"
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            xml(
                "AndroidManifest.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest xmlns:android=\"https://schemas.android.com/apk/res/android\"\n" +
                    "          package=\"com.g.a.g.c\"\n" +
                    "          android:versionCode=\"21\"\n" +
                    "          android:versionName=\"0.0.1\">\n" +
                    "    <uses-sdk android:minSdkVersion=\"4\" />\n" +
                    "    <application />\n" +
                    "</manifest>\n" +
                    "\n"
            )
        ).run().expect(expected)
    }

    fun testHttpsTools() {
        // Regression test for
        // 80346518: tools:ignore="MissingTranslation" doesn't work for <resource element>
        lint().files(
            xml(
                "res/values-nb/strings.xml",
                "" +
                    "<resources " +
                    "    xmlns:android=\"https://schemas.android.com/apk/res/android\"\n" +
                    "    xmlns:tools=\"https://schemas.android.com/tools\"\n" +
                    "    tools:ignore=\"ExtraTranslation\">\n" +
                    "    <string name=\"bar\">Bar</string>\n" +
                    "</resources>"
            ),
            xml(
                "res/xml/random.xml",
                "" + "<foo xmlns:foo=\"https://schemas.android.com/apk/res/android\"/>\n"
            )
        )
            .run()
            .expect(
                "" +
                    "res/values-nb/strings.xml:1: Error: Suspicious namespace: should start with http:// [NamespaceTypo]\n" +
                    "<resources     xmlns:android=\"https://schemas.android.com/apk/res/android\"\n" +
                    "                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "res/values-nb/strings.xml:2: Error: Suspicious namespace: should start with http:// [NamespaceTypo]\n" +
                    "    xmlns:tools=\"https://schemas.android.com/tools\"\n" +
                    "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "2 errors, 0 warnings"
            )
            .expectFixDiffs(
                "" +
                    "Fix for res/values-nb/strings.xml line 1: Replace with http://schemas.android.com/apk/res/android:\n" +
                    "@@ -1 +1\n" +
                    "- <resources     xmlns:android=\"https://schemas.android.com/apk/res/android\"\n" +
                    "+ <resources     xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "Fix for res/values-nb/strings.xml line 2: Replace with http://schemas.android.com/tools:\n" +
                    "@@ -2 +2\n" +
                    "-     xmlns:tools=\"https://schemas.android.com/tools\"\n" +
                    "+     xmlns:tools=\"http://schemas.android.com/tools\""
            )
    }

    fun testRedundantNamespace() {
        // Regression test for https://issuetracker.google.com/80443152
        lint().files(
            xml(
                "res/layout/customview2.xml",
                "" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                    "    android:id=\"@+id/newlinear\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:orientation=\"vertical\" >\n" +
                    "    <foo.bar.Baz\n" +
                    "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "        xmlns:tools=\"http://something/else\"\n" +
                    "        xmlns:unrelated=\"http://un/related\"\n" +
                    "        android:id=\"@+id/button1\"\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n />" +
                    "</LinearLayout>\n"
            )
        ).run().expect(
            "" +
                "res/layout/customview2.xml:8: Warning: This namespace declaration is redundant [RedundantNamespace]\n" +
                "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings"
        ).expectFixDiffs(
            "" +
                "Fix for res/layout/customview2.xml line 8: Delete namespace:\n" +
                "@@ -10 +10\n" +
                "-         xmlns:android=\"http://schemas.android.com/apk/res/android\""
        )
    }
}
