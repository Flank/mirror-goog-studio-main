/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.testutils.TestUtils
import com.android.tools.lint.LintCliFlags.ERRNO_INVALID_ARGS
import com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS
import com.android.tools.lint.checks.AbstractCheckTest.base64gzip
import com.android.tools.lint.checks.AbstractCheckTest.jar
import com.android.tools.lint.checks.AnnotationDetectorTest.Companion.SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP
import com.android.tools.lint.checks.infrastructure.LintDetectorTest.bytes
import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.ProjectDescription.Type.LIBRARY
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintListener
import com.android.tools.lint.client.api.LintListener.EventType.REGISTERED_PROJECT
import com.android.tools.lint.client.api.LintListener.EventType.STARTING
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Project
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectInitializerTest {
    @Test
    fun testManualProject() {

        val library = project(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <permission android:name="bar.permission.SEND_SMS"
                        android:label="@string/foo"
                        android:description="@string/foo" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>

                </manifest>"""
            ).indented(),
            java(
                "src/test/pkg/Loader.java",
                """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public abstract class Loader<P> {
                    private P mParam;

                    public abstract void loadInBackground(P val);

                    public void load() {
                        // Invoke a method that takes a generic type.
                        loadInBackground(mParam);
                    }
                }"""
            ).indented(),
            java(
                "src/test/pkg/NotInProject.java",
                """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Foo {
                    private String foo = "/sdcard/foo";
                }
                """
            ).indented()
        ).type(LIBRARY).name("Library")

        val main = project(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <permission android:name="foo.permission.SEND_SMS"
                        android:label="@string/foo"
                        android:description="@string/foo" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>

                </manifest>
                """
            ).indented(),
            xml(
                "res/values/strings.xml",
                """
                <resources>
                    <string name="string1">String 1</string>
                    <string name="string1">String 2</string>
                    <string name="string3">String 3</string>
                    <string name="string3">String 4</string>
                </resources>
                """
            ).indented(),
            xml(
                "res/values/not_in_project.xml",
                """
                <resources>
                    <string name="string2">String 1</string>
                    <string name="string2">String 2</string>
                </resources>
                """
            ).indented(),
            java(
                "test/Test.java",
                """
                @SuppressWarnings({"MethodMayBeStatic", "ClassNameDiffersFromFileName"})
                public class Test {
                  String path = "/sdcard/file";
                }"""
            ).indented(),
            java(
                "generated/Generated.java",
                """
                @SuppressWarnings({"MethodMayBeStatic", "ClassNameDiffersFromFileName"})
                public class Test {
                  String path = "/sdcard/file";
                }"""
            ).indented()
        ).name("App").dependsOn(library)

        val root = temp.newFolder()

        val projects = lint().projects(main, library).createProjects(root)
        val appProjectDir = projects[0]
        val appProjectPath = appProjectDir.path

        val sdk = temp.newFolder("fake-sdk")
        val cacheDir = temp.newFolder("cache")
        @Language("XML")
        val mergedManifestXml =
            """

            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="foo.bar2"
                android:versionCode="1"
                android:versionName="1.0" >

                <uses-sdk android:minSdkVersion="14" />

                <permission
                    android:name="foo.permission.SEND_SMS"
                    android:description="@string/foo"
                    android:label="@string/foo" />
                <permission
                    android:name="bar.permission.SEND_SMS"
                    android:description="@string/foo"
                    android:label="@string/foo" />

                <application
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name" >
                </application>

            </manifest>
            """.trimIndent()

        val mergedManifest = temp.newFile("merged-manifest")
        Files.asCharSink(mergedManifest, Charsets.UTF_8).write(mergedManifestXml)

        @Language("XML")
        val baselineXml =
            """
            <issues format="4" by="lint unknown">
                <issue
                    id="DuplicateDefinition"
                    message="`string3` has already been defined in this folder"
                    errorLine1="    &lt;string name=&quot;string3&quot;>String 4&lt;/string>"
                    errorLine2="            ~~~~~~~~~~~~~~">
                    <location
                        file="res/values/strings.xml"
                        line="8"
                        column="13"/>
                    <location
                        file="res/values/strings.xml"
                        line="5"
                        column="13"/>
                </issue>
            </issues>
            """.trimIndent()
        val baseline = File(appProjectDir, "baseline.xml")
        Files.asCharSink(baseline, Charsets.UTF_8).write(baselineXml)

        @Language("XML")
        val descriptor =
            """
            <project>
            <root dir="$root" />
            <sdk dir='$sdk'/>
            <cache dir='$cacheDir'/>
            <classpath jar="test.jar" />
            <baseline file='$baseline' />
            <module name="$appProjectPath:App" android="true" library="false" compile-sdk-version='18'>
              <manifest file="AndroidManifest.xml" />
              <resource file="res/values/strings.xml" />
              <src file="test/Test.java" test="true" />
              <src file="generated/Generated.java" generated="true" />
              <dep module="Library" />
            </module>
            <module name="Library" android="true" library="true" compile-sdk-version='android-M'>
              <manifest file="Library/AndroidManifest.xml" />
              <merged-manifest file='$mergedManifest'/>
              <src file="Library/src/test/pkg/Loader.java" />
            </module>
            </project>
            """.trimIndent()
        Files.asCharSink(File(root, "project.xml"), Charsets.UTF_8).write(descriptor)

        var assertionsChecked = 0
        val listener: LintListener = object : LintListener {
            override fun update(
                driver: LintDriver,
                type: LintListener.EventType,
                project: Project?,
                context: Context?
            ) {
                val client = driver.client
                when (type) {
                    REGISTERED_PROJECT -> {
                        assertThat(project).isNotNull()
                        project!!
                        assertThat(project.name == "App")
                        assertThat(project.buildSdk).isEqualTo(18)
                        assertionsChecked++

                        // Lib project
                        val libProject = project.directLibraries[0]
                        assertThat(libProject.name == "Library")

                        val manifest = client.getMergedManifest(libProject)
                        assertThat(manifest).isNotNull()
                        manifest!!
                        val permission = getFirstSubTagByName(
                            manifest.documentElement,
                            "permission"
                        )!!
                        assertThat(
                            permission.getAttributeNS(
                                ANDROID_URI,
                                ATTR_NAME
                            )
                        ).isEqualTo("foo.permission.SEND_SMS")
                        assertionsChecked++

                        // compileSdkVersion=android-M -> build API=23
                        assertThat(libProject.buildSdk).isEqualTo(23)
                        assertionsChecked++
                    }
                    STARTING -> {
                        // Check extra metadata is handled right
                        assertThat(client.getSdkHome()).isEqualTo(sdk)
                        assertThat(client.getCacheDir(null, false)).isEqualTo(cacheDir)
                        assertionsChecked += 2
                    }
                    else -> {
                        // Ignored
                    }
                }
            }
        }

        val canonicalRoot = root.canonicalPath

        MainTest.checkDriver(
            """
            baseline.xml: Information: 1 error was filtered out because it is listed in the baseline file, baseline.xml
             [LintBaseline]
            project.xml:5: Error: test.jar (relative to ROOT) does not exist [LintError]
            <classpath jar="test.jar" />
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/values/strings.xml:3: Error: string1 has already been defined in this folder [DuplicateDefinition]
                <string name="string1">String 2</string>
                        ~~~~~~~~~~~~~~
                res/values/strings.xml:2: Previously defined here
            ../Library/AndroidManifest.xml:8: Error: Permission name SEND_SMS is not unique (appears in both foo.permission.SEND_SMS and bar.permission.SEND_SMS) [UniquePermission]
                <permission android:name="bar.permission.SEND_SMS"
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:8: Previous permission here
            3 errors, 0 warnings (1 error filtered by baseline baseline.xml)
            """,
            "w: Classpath entry points to a non-existent location: ROOT/test.jar",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "UniquePermission,DuplicateDefinition,SdCardPath",
                "--text",
                "stdout",
                "--project",
                File(root, "project.xml").path
            ),

            {
                it
                    .replace(canonicalRoot, "ROOT")
                    .replace(root.path, "ROOT")
                    .replace(baseline.parentFile.path, "TESTROOT")
                    .replace('\\', '/')
            },
            listener
        )

        // Make sure we hit all our checks with the listener
        assertThat(assertionsChecked).isEqualTo(5)
    }

    @Test
    fun testManualProjectErrorHandling() {

        @Language("XML")
        val descriptor =
            """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <module name="Foo:App" android="true" library="true" javaLanguage="1000" kotlinLanguage="1.3">
              <unknown file="foo.Bar" />
              <resource file="res/values/strings.xml" />
              <dep module="NonExistent" />
            </module>
            </project>
            """.trimIndent()
        val folder = File(temp.root, "app")
        folder.mkdirs()
        val projectXml = File(folder, "project.xml")
        Files.asCharSink(projectXml, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
            """
            app: Error: No .class files were found in project "Foo:App", so none of the classfile based checks could be run. Does the project need to be built first? [LintError]
            project.xml:3: Error: Invalid Java language level "1000" [LintError]
            <module name="Foo:App" android="true" library="true" javaLanguage="1000" kotlinLanguage="1.3">
            ^
            project.xml:4: Error: Unexpected tag unknown [LintError]
              <unknown file="foo.Bar" />
              ~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """,
            "",

            ERRNO_SUCCESS,

            arrayOf(
                "--quiet",
                "--project",
                projectXml.path
            ),
            null, null
        )
    }

    @Test
    fun testSimpleProject() {
        val root = temp.newFolder()
        val projects = lint().files(
            java(
                "src/test/pkg/InterfaceMethodTest.java",
                """
                    package test.pkg;

                    @SuppressWarnings({"unused", "ClassNameDiffersFromFileName"})
                    public interface InterfaceMethodTest {
                        void someMethod();
                        default void method2() {
                            System.out.println("test");
                        }
                        static void method3() {
                            System.out.println("test");
                        }
                    }
                    """
            ).indented(),
            java(
                "C.java",
                """
                    import android.app.Fragment;

                    @SuppressWarnings({"MethodMayBeStatic", "ClassNameDiffersFromFileName"})
                    public class C {
                      String path = "/sdcard/file";
                      void test(Fragment fragment) {
                        Object host = fragment.getHost(); // Requires API 23
                      }
                    }"""
            ).indented(),
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.tools.lint.test"
                    android:versionCode="1"
                    android:versionName="1.0" >
                    <uses-sdk
                        android:minSdkVersion="15"
                        android:targetSdkVersion="22" />

                </manifest>"""
            ).indented(),
            xml(
                "res/values/not_in_project.xml",
                """
                <resources>
                    <string name="string2">String 1</string>
                    <string name="string2">String 2</string>
                </resources>
                """
            ).indented()
        ).createProjects(root)
        val projectDir = projects[0]

        @Language("XML")
        val descriptor =
            """
            <project incomplete="true">
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir"/>
            <module name="M" android="true" library="true">
                <manifest file="$projectDir/AndroidManifest.xml" />
                <src file="$projectDir/C.java" />
                <src file="$projectDir/src/test/pkg/InterfaceMethodTest.java" />
            </module>
            </project>
            """.trimIndent()
        val descriptorFile = File(root, "out1/out2/out3/project.xml")
        descriptorFile.parentFile?.mkdirs()
        Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
            """
            C.java:7: Error: Call requires API level 23 (current min is 15): android.app.Fragment#getHost [NewApi]
                Object host = fragment.getHost(); // Requires API 23
                                       ~~~~~~~
            AndroidManifest.xml:7: Warning: Not targeting the latest versions of Android; compatibility modes apply. Consider testing and updating this version. Consult the android.os.Build.VERSION_CODES javadoc for details. [OldTargetApi]
                    android:targetSdkVersion="22" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            C.java:5: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
              String path = "/sdcard/file";
                            ~~~~~~~~~~~~~~
            1 errors, 2 warnings
            """,
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testPaths() {
        // Regression test for https://issuetracker.google.com/159169803
        val root = temp.newFolder()
        val projects = lint().files(
            xml(
                "layout/AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.tools.lint.test"
                    android:versionCode="1"
                    android:versionName="1.0" >
                    <uses-sdk
                        android:minSdkVersion="15"
                        android:targetSdkVersion="29" />

                </manifest>"""
            ).indented()
        ).createProjects(root)
        val projectDir = projects[0]

        @Language("XML")
        val descriptor =
            """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
            <module name="M" android="true" library="true">
                <manifest file="layout/AndroidManifest.xml" />
            </module>
            </project>
            """.trimIndent()
        val descriptorFile = File(root, "project.xml")
        Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
            "No issues found.",
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "RequiredSize",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testGradleDetectorsFiring() { // Regression test for b/132992488
        val root = temp.newFolder()
        val projects = lint().files(
            java(
                "src/main/pkg/MainActivity.java",
                """
                    package pkg;

                    import android.app.Activity;
                    import android.os.Bundle;

                    public class MainActivity extends Activity {
                        @Override
                        public void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                        }
                    }
                    """
            ).indented(),
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.tools.lint.test"
                    android:versionCode="1"
                    android:versionName="1.0" >
                    <uses-sdk
                        android:minSdkVersion="15"
                        android:targetSdkVersion="22" />

                </manifest>"""
            ).indented(),
            xml(
                "res/values/strings.xml",
                """
                <resources xmlns:tools="http://schemas.android.com/tools">
                    <string name="nam${'\ufeff'}e">Value</string>
                </resources>"""
            ).indented(),
            bytes(
                "res/raw/sample.txt",
                "a\uFEFFb".toByteArray()
            )
        ).createProjects(root)
        val projectDir = projects[0]

        @Language("XML")
        val descriptor =
            """
            <project incomplete="true">
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
            <module name="M" android="true" library="true">
                <manifest file="AndroidManifest.xml" />
                <resource file="res/raw/sample.txt" />
                <resource file="res/values/strings.xml" />
                <src file="src/main/pkg/MainActivity.java" />
            </module>
            </project>
            """.trimIndent()
        val descriptorFile = File(root, "project.xml")
        Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
            """
            res/values/strings.xml:2: Error: Found byte-order-mark in the middle of a file [ByteOrderMark]
                <string name="nam﻿e">Value</string>
                                 ~
            1 errors, 0 warnings
            """,
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "ByteOrderMark",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testAar() {
        // Check for missing application icon and have that missing icon be supplied by
        // an AAR dependency and make its way into the merged manifest.
        val root = temp.newFolder()
        val projects = lint().files(
            xml(
                "AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.android.tools.lint.test"
                        android:versionCode="1"
                        android:versionName="1.0" >
                        <uses-sdk android:minSdkVersion="14" />
                        <application />

                    </manifest>"""
            ).indented(),
            xml(
                "res/values/not_in_project.xml",
                """
                    <resources>
                        <string name="string2">String 1</string>
                        <string name="string2">String 2</string>
                    </resources>
                    """
            ).indented(),
            java(
                "src/main/java/test/pkg/Private.java",
                """package test.pkg;
                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public class Private {
                        void test() {
                            int x = R.string.my_private_string; // ERROR
                            int y = R.string.my_public_string; // OK
                        }
                    }
                    """
            ).indented()
        ).createProjects(root)
        val projectDir = projects[0]

        val aarFile = temp.newFile("foo-bar.aar")
        aarFile.createNewFile()
        val aar = temp.newFolder("aar-exploded")
        @Language("XML")
        val aarManifest =
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.android.tools.lint.test"
                        android:versionCode="1"
                        android:versionName="1.0" >

                        <uses-sdk android:minSdkVersion="14" />
                        <application android:icon='@mipmap/my_application_icon'/>

                    </manifest>"""
        Files.asCharSink(File(aar, "AndroidManifest.xml"), Charsets.UTF_8).write(aarManifest)

        val allResources = (
            "" +
                "int string my_private_string 0x7f040000\n" +
                "int string my_public_string 0x7f040001\n" +
                "int layout my_private_layout 0x7f040002\n" +
                "int id title 0x7f040003\n" +
                "int style Theme_AppCompat_DayNight 0x7f070004"
            )

        val rFile = File(aar, FN_RESOURCE_TEXT)
        Files.asCharSink(rFile, Charsets.UTF_8).write(allResources)

        val publicResources = (
            "" +
                "" +
                "string my_public_string\n" +
                "style Theme.AppCompat.DayNight\n"
            )

        val publicTxtFile = File(aar, FN_PUBLIC_TXT)
        Files.asCharSink(publicTxtFile, Charsets.UTF_8).write(publicResources)

        @Language("XML")
        val descriptor =
            """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
                <module name="M" android="true" library="false">
                <manifest file="AndroidManifest.xml" />
                <src file="src/main/java/test/pkg/Private.java" />
                <aar file="$aarFile" extracted="$aar" />
            </module>
            </project>
            """.trimIndent()
        val descriptorFile = File(root, "project.xml")
        Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
            "" +
                "src/main/java/test/pkg/Private.java".replace('/', File.separatorChar) +
                ":5: Warning: The resource @string/my_private_string is marked as private in foo-bar.aar [PrivateResource]\n" +
                "                            int x = R.string.my_private_string; // ERROR\n" +
                "                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings\n",
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "MissingApplicationIcon,PrivateResource",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testJar() {
        // Check for missing application icon and have that missing icon be supplied by
        // an AAR dependency and make its way into the merged manifest.
        val root = temp.newFolder()
        val projects = lint().files(
            java(
                "src/test/pkg/Child.java",
                """
                package test.pkg;

                import android.os.Parcel;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class Child extends Parent {
                    @Override
                    public int describeContents() {
                        return 0;
                    }

                    @Override
                    public void writeToParcel(Parcel dest, int flags) {

                    }
                }
                """
            ).indented()
        ).createProjects(root)
        val projectDir = projects[0]

        /*
        Compiled from
            package test.pkg;
            import android.os.Parcelable;
            public abstract class Parent implements Parcelable {
            }
         */
        val jarFile = jar(
            "parent.jar",
            base64gzip(
                "test/pkg/Parent.class",
                "" +
                    "H4sIAAAAAAAAAF1Pu07DQBCcTRw7cQx5SHwAXaDgipQgmkhUFkRKlP5sn8IF" +
                    "cxedL/wXFRJFPoCPQuw5qdBKo53Z2R3tz+/3EcAc0xRdXCYYJRgnmBDiB220" +
                    "fyR0ZzcbQrSwlSKMcm3U8+G9UG4ti5qVaW5LWW+k04Gfxci/6oYwyb1qvNi/" +
                    "bcVSOmX8PSFd2YMr1ZMOvuFJvtvJD5mhh5gT/q0QxmEqamm24qXYqZKlK2kq" +
                    "Z3UlbBNspapDbnSNDn/B8fwScfFBxoSZaDnQu/0CfXLTQZ8xPokYMGbnPsWw" +
                    "Xc9a18UfxkO3QyIBAAA="
            )
        ).createFile(root)

        @Language("XML")
        val descriptor =
            """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
                <module name="M" android="true" library="false">
                <jar file="$jarFile" />
                <src file="src/test/pkg/Child.java" />
            </module>
            </project>
            """.trimIndent()
        val descriptorFile = File(root, "project.xml")
        Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
            "" +
                // We only find this error if we correctly include the jar dependency
                // which provides the parent class which implements Parcelable.
                "src/test/pkg/Child.java:6: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n".replace(
                    '/',
                    File.separatorChar
                ) +
                "public class Child extends Parent {\n" +
                "             ~~~~~\n" +
                "1 errors, 0 warnings\n",
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "ParcelCreator",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testClasspathJar() {
        // Ensure that class path jars are properly included for type resolution
        val root = temp.newFolder()

        val projects = lint().files(

            java(
                """
                    package test.pkg;

                    import android.support.annotation.RequiresApi;
                    import android.util.Log;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class RequiresApiFieldTest {
                        @RequiresApi(24)
                        private int Method24() {
                            return 42;
                        }

                        private void ReferenceMethod24() {
                            Log.d("zzzz", "ReferenceField24: " + Method24());
                        }
                    }
                    """
            ).indented(),
            base64gzip("libs/support-annotations.jar", SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP),
            xml(
                "project.xml",
                """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <module name="M" android="true" library="false">
            <classpath jar="libs/support-annotations.jar" />
            <src file="src/test/pkg/RequiresApiFieldTest.java" />
            </module>
            </project>
            """
            ).indented()
        ).createProjects(root)
        val projectDir = projects[0]
        val descriptorFile = File(projectDir, "project.xml")

        MainTest.checkDriver(
            "" +
                // We only find this error if we correctly include the jar dependency
                // which provides the parent class which implements Parcelable.
                "src/test/pkg/RequiresApiFieldTest.java:14: Error: Call requires API level 24 (current min is 1): Method24 [NewApi]\n" +
                "        Log.d(\"zzzz\", \"ReferenceField24: \" + Method24());\n" +
                "                                             ~~~~~~~~\n" +
                "1 errors, 0 warnings\n".replace('/', File.separatorChar),
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "NewApi",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testSrcJar() {
        // Checks that source files can be read from srcjar files as well
        val root = temp.newFolder()
        val projects = lint().files(
            jar(
                "src/my.srcjar",
                java(
                    "test/pkg/Test.java",
                    """
                    package test.pkg;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class Test {
                        public String path = "/sdcard/my.path";
                    }
                    """
                ).indented()
            ),
            jar(
                "src/my2.srcjar",
                java(
                    "test/pkg/Test2.java",
                    """
                    package test.pkg;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class Test2 {
                        public String path = "/sdcard/my2.path";
                    }
                    """
                ).indented()
            )
        ).createProjects(root)
        val projectDir = projects[0]

        @Language("XML")
        val descriptor =
            """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
                <module name="M" android="true" library="false">
                <src file="src/my.srcjar" />
                <src file="src/my2.srcjar" />
            </module>
            </project>
            """.trimIndent()
        val descriptorFile = File(root, "project.xml")
        Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
            """
                src/my.srcjar!/test/pkg/Test.java:5: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
                    public String path = "/sdcard/my.path";
                                         ~~~~~~~~~~~~~~~~~
                src/my2.srcjar!/test/pkg/Test2.java:5: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
                    public String path = "/sdcard/my2.path";
                                         ~~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                            """,
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "SdCardPath",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testNonAndroidProject() {
        val root = temp.newFolder()
        val projects = lint().files(
            java(
                "C.java",
                """
                    @SuppressWarnings({"MethodMayBeStatic", "ClassNameDiffersFromFileName"})
                    public class C {
                      String path = "/sdcard/file";
                    }"""
            ).indented()
        ).createProjects(root)
        val projectDir = projects[0]

        @Language("XML")
        val descriptor =
            """
            <project incomplete="true">
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
            <module name="M" android="false" library="true">
                <src file="C.java" />
            </module>
            </project>
            """.trimIndent()
        val descriptorFile = File(root, "project.xml")
        Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
            """
            No issues found.
            """,
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testJava8Libraries() {
        val root = temp.newFolder()
        val projects = lint().files(
            java(
                "C.java",
                """
                    import java.util.ArrayList;
                    import java.util.Arrays;
                    import java.util.Iterator;
                    import java.util.stream.Stream;

                    @SuppressWarnings({"unused", "SimplifyStreamApiCallChains",
                        "OptionalGetWithoutIsPresent", "OptionalUsedAsFieldOrParameterType",
                        "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class C {
                        public void utils(java.util.Collection<String> collection) {
                            collection.removeIf(s -> s.length() > 5);
                        }

                        public void streams(ArrayList<String> list, String[] array) {
                            list.stream().forEach(s -> System.out.println(s.length()));
                            Stream<String> stream = Arrays.stream(array);
                        }

                        public void bannedMembers(java.util.Collection collection) {
                            Stream stream = collection.parallelStream(); // ERROR
                        }
                    }
                    """
            ).indented()
        ).createProjects(root)
        val projectDir = projects[0]

        @Language("XML")
        val descriptor =
            """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
            <!-- We could have specified desugar="full" instead of specifying android_java8_libs -->
            <module desugar="default" android_java8_libs="true" name="M" android="true" library="false">
                <src file="C.java" />
            </module>
            </project>
            """.trimIndent()
        val descriptorFile = File(root, "project.xml")
        Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
            """
            C.java:20: Error: Call requires API level 24 (current min is 1): java.util.Collection#parallelStream [NewApi]
                    Stream stream = collection.parallelStream(); // ERROR
                                               ~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """,
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "NewApi",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testExternalAnnotations() {
        // Checks that external annotations support works
        val root = temp.newFolder()

        val projects = lint().projects(
            ProjectDescription(
                java(
                    """
                    package test.pkg;
                    import android.support.annotation.WorkerThread;

                    public class Client {
                        @WorkerThread
                        public static void client() {
                            new test.pkg1.Library1().method1();
                            new test.pkg2.Library2().method2();
                        }
                    }
                    """
                ).indented(),
                java(
                    """
                package test.pkg1;

                public class Library1 {
                    public void method1() { // externally annotated as @UiThread
                    }
                }
                """
                ).indented(),
                java(
                    """
                package test.pkg2;

                public class Library2 {
                    public void method2() { // externally annotated as @UiThread
                    }
                }
                """
                ).indented(),
                base64gzip("libs/support-annotations.jar", SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP),
                // zip annotations file
                jar(
                    "annotations.zip",
                    xml(
                        "test/pkg1/annotations.xml",
                        """
                    <root>
                      <item name="test.pkg1.Library1 void method1()">
                        <annotation name="android.support.annotation.UiThread"/>
                      </item>
                    </root>
                    """
                    ).indented()
                ),
                // dir annotation files
                xml(
                    "external-annotations/test/pkg2/annotations.xml",
                    """
                <root>
                  <item name="test.pkg2.Library2 void method2()">
                    <annotation name="android.support.annotation.UiThread"/>
                  </item>
                </root>
                """
                ).indented(),
                xml(
                    "project.xml",
                    """
                <project>
                <root dir="$root/project" />
                <sdk dir='${TestUtils.getSdk()}'/>
                <annotations file="annotations.zip"/>
                <annotations dir="external-annotations"/>
                <module name="M" android="true" library="false">
                <classpath jar="libs/support-annotations.jar" />
                <src file="src/test/pkg1/Library1.java" />
                <src file="src/test/pkg2/Library2.java" />
                <src file="src/test/pkg/Client.java" />
                </module>
                </project>
            """
                ).indented()
            ).name("project")
        ).createProjects(root)
        val projectDir = projects[0]
        val descriptorFile = File(projectDir, "project.xml")

        MainTest.checkDriver(
            "" +
                "src/test/pkg/Client.java:7: Error: Method method1 must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n" +
                "        new test.pkg1.Library1().method1();\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/Client.java:8: Error: Method method2 must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n" +
                "        new test.pkg2.Library2().method2();\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "2 errors, 0 warnings".replace('/', File.separatorChar),
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "WrongThread",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testJava14() {
        // Tests Java language support for some recent features, such as
        // switch expressions
        val root = temp.newFolder()

        val projects = lint().projects(
            ProjectDescription(
                java(
                    """
                package test.pkg;
                import android.support.annotation.IntDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                public class Java14Test {
                    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface Duration {
                    }
                    public static final int LENGTH_INDEFINITE = -2;
                    public static final int LENGTH_SHORT = -1;
                    public static final int LENGTH_LONG = 0;

                    // Switch expression -- missing one of the cases; should generate warning
                    // when we're correctly handling the AST for this
                    public static boolean test(@Duration int duration) {
                        return switch (duration) {
                            // Missing LENGTH_INDEFINITE handling
                            case LENGTH_SHORT, LENGTH_LONG -> true;
                            default -> false;
                        };
                    }
                }
                """
                ).indented(),
                base64gzip("libs/support-annotations.jar", SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP),
                xml(
                    "project.xml",
                    """
                <project>
                <root dir="$root/project" />
                <sdk dir='${TestUtils.getSdk()}'/>
                <module name="M" android="true" library="false" javaLanguage="14">
                <classpath jar="libs/support-annotations.jar" />
                <src file="src/test/pkg/Java14Test.java" />
                </module>
                </project>
            """
                ).indented()
            ).name("project")
        ).createProjects(root)
        val projectDir = projects[0]
        val descriptorFile = File(projectDir, "project.xml")

        MainTest.checkDriver(
            """
            src/test/pkg/Java14Test.java:17: Warning: Switch statement on an int with known associated constant missing case LENGTH_INDEFINITE [SwitchIntDef]
                    return switch (duration) {
                           ^
            0 errors, 1 warnings
            """.replace('/', File.separatorChar),
            "",

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--check",
                "SwitchIntDef",
                "--project",
                descriptorFile.path
            ),
            null, null
        )
    }

    @Test
    fun testCrLf() {
        // Regression test for bug handling Windows line endings,
        // https://issuetracker.google.com/149490356
        val root = temp.newFolder()
        val crlf = File(root, "app/src/main/java/ClassCRLF.java")
        crlf.parentFile.mkdirs()
        crlf.writeText(
            """
            package com.example.foo.notification;

            public class AppNotifBlockedReceiver extends BroadcastReceiver {
                // content removed
            }
            """.trimIndent().replace("\n", "\r\n")
        )
        val lf = File(root, "app/src/main/java/ClassLF.java")
        lf.writeText(
            """
            package com.example.foo.tester.ui;

            public class DisableActivity extends Activity {
            // Content removed
            }
            """.trimIndent()
        )

        @Language("XML")
        val descriptor =
            """
            <project>
               <module android="true" compile-sdk-version="18" name="app">
                  <src file="app/src/main/java/ClassCRLF.java"/>
                  <src file="app/src/main/java/ClassLF.java"/>
               </module>
            </project>
            """.trimIndent()
        val descriptorFile = File(root, "descriptor.xml")
        descriptorFile.writeText(descriptor.replace("\n", "\r\n"))

        MainTest.checkDriver(
            "No issues found.",
            "The source file ClassCRLF.java does not appear to be in the right project location; its package implies .../com/example/foo/notification/ClassCRLF.java but it was found in ...src/main/java/ClassCRLF.java".replace("\\", "/"),

            // Expected exit code
            ERRNO_SUCCESS,

            // Args
            arrayOf(
                "--quiet",
                "--project",
                descriptorFile.path
            ),

            null, null
        )
    }

    @Test
    fun testInvalidDescriptorFile() {
        // Make sure we give a suitable error message when you pass in a directory instead of
        // an XML file
        val root = temp.newFolder()

        MainTest.checkDriver(
            "",
            "Project descriptor ROOT should be an XML descriptor file, not a directory",

            // Expected exit code
            ERRNO_INVALID_ARGS,

            // Args
            arrayOf(
                "--quiet",
                "--project",
                root.path
            ),

            { it.replace(root.path, "ROOT") }, null
        )
    }

    companion object {
        @ClassRule
        @JvmField
        var temp = TemporaryFolder()

        fun project(vararg files: TestFile): ProjectDescription = ProjectDescription(*files)
    }
}
