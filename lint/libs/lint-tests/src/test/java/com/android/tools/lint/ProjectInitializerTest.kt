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

import com.android.testutils.TestUtils
import com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS
import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.ProjectDescription.Type.LIBRARY
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectInitializerTest {
    @Test
    fun testManualProject() {

        val library = project(
                xml("AndroidManifest.xml", """
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

</manifest>"""),
                java("src/test/pkg/Loader.java", """
package test.pkg;

@SuppressWarnings("ClassNameDiffersFromFileName")
public abstract class Loader<P> {
    private P mParam;

    public abstract void loadInBackground(P val);

    public void load() {
        // Invoke a method that takes a generic type.
        loadInBackground(mParam);
    }
}"""),
                java("src/test/pkg/NotInProject.java", """
package test.pkg;

@SuppressWarnings("ClassNameDiffersFromFileName")
public class Foo {
    private String foo = "/sdcard/foo";
}
""")

        ).type(LIBRARY).name("Library")

        val main = project(
                xml("AndroidManifest.xml", """
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
"""),
                xml("res/values/strings.xml", """
<resources>
    <string name="string1">String 1</string>
    <string name="string1">String 2</string>
</resources>
"""),
                xml("res/values/not_in_project.xml", """
<resources>
    <string name="string2">String 1</string>
    <string name="string2">String 2</string>
</resources>
""")
        ).name("App").dependsOn(library)

        val root = temp.newFolder()

        val projects = lint().projects(main, library).createProjects(root)
        val appProjectPath = projects[0].path

        @Language("XML")
        val descriptor = """
            <project>
            <root dir="$root" />
            <classpath jar="test.jar" />
            <module name="$appProjectPath:App" android="true" library="false">
              <manifest file="AndroidManifest.xml" />
              <resource file="res/values/strings.xml" />
              <dep module="Library" />
            </module>
            <module name="Library" android="true" library="true">
              <manifest file="Library/AndroidManifest.xml" />
              <src file="Library/src/test/pkg/Loader.java" />
            </module>
            </project>""".trimIndent()
        Files.asCharSink(File(root, "project.xml"), Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
                "" +
                        "res/values/strings.xml:4: Error: string1 has already been defined in this folder [DuplicateDefinition]\n" +
                        "    <string name=\"string1\">String 2</string>\n" +
                        "            ~~~~~~~~~~~~~~\n" +
                        "    res/values/strings.xml:3: Previously defined here\n" +
                        "../Library/AndroidManifest.xml:9: Error: Permission name SEND_SMS is not unique (appears in both foo.permission.SEND_SMS and bar.permission.SEND_SMS) [UniquePermission]\n" +
                        "    <permission android:name=\"bar.permission.SEND_SMS\"\n" +
                        "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "    AndroidManifest.xml:9: Previous permission here\n" +
                        "2 errors, 0 warnings\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                arrayOf("--quiet",
                        "--check",
                        "UniquePermission,DuplicateDefinition,SdCardPath",
                        "--sdk-home", // since we don't extend AbstractCheckTest
                        TestUtils.getSdk().path,
                        "--project",
                        File(root, "project.xml").path), null)
    }

    @Test
    fun testManualProjectErrorHandling() {

        @Language("XML")
        val descriptor = """
            <project>
            <classpath jar="test.jar" />
            <module name="Foo:App" android="true" library="true">
              <unknown file="foo.Bar" />
              <resource file="res/values/strings.xml" />
              <dep module="NonExistent" />
            </module>
            </project>""".trimIndent()
        val projectXml = File(temp.root, "project.xml")
        Files.asCharSink(projectXml, Charsets.UTF_8).write(descriptor)

        MainTest.checkDriver(
                "" +
                        "project.xml:4: Error: Unexpected tag unknown [LintError]\n" +
                        "  <unknown file=\"foo.Bar\" />\n" +
                        "  ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "1 errors, 0 warnings\n",
                "",

                ERRNO_SUCCESS,

                arrayOf("--quiet",
                        "--project",
                        projectXml.path), null)
    }

    companion object {
        @ClassRule
        @JvmField
        var temp = TemporaryFolder()

        fun project(vararg files: TestFile): ProjectDescription = ProjectDescription(*files)

    }
}