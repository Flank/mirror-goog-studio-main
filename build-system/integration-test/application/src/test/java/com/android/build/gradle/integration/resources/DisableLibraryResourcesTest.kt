/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Tests the behaviour of disabling resource processing in a library module by using BuildFeatures,
 * i.e. "android.buildFeatures.androidResources = false".
 *
 * This feature can be used by developers to mark a library module which does not contain resources
 * and therefore we can skip configuring all of the resource processing tasks in that module.
 * Instead we will use the GenerateEmptyResourcesFilesTask to create empty required artifacts.
 */
class DisableLibraryResourcesTest {
    private val leafLib = MinimalSubProject.lib("com.example.leafLib")
        .withFile(
            "src/main/res/values/values.xml",
            """
                <resources>
                    <string name="leaf_lib_string">hello</string>
                </resources>""".trimIndent()
        )
        .withFile("src/main/res/raw/raw_file", "leafLib")
        .withFile("src/test/java/com/example/MyTest.java",
            //language=java
            """
                package com.example;

                import org.junit.Test;

                public class MyTest {
                    @Test
                    public void check() {
                        System.out.println("ExampleTest has some output");
                    }
                }
                """.trimIndent())
        .appendToBuild("""

            dependencies {
                testImplementation("junit:junit:4.12")
            }

        """.trimIndent())

    private val localLib = MinimalSubProject.lib("com.example.localLib")
        .withFile(
            "src/main/res/values/values.xml",
            """
                <resources>
                    <string name="local_lib_string">hello</string>
                </resources>""".trimIndent()
        )
        .withFile("src/main/res/raw/raw_file", "localLib")
        .appendToBuild("dependencies { implementation project(':leafLib') }")

    private val app = MinimalSubProject.app("com.example.app")
        .withFile(
            "src/main/res/values/values.xml",
            """
                <resources>
                    <string name="app_string">hello</string>
                </resources>""".trimIndent()
        )
        .appendToBuild("dependencies { implementation project(':localLib') }")
        .withFile("src/main/java/com/example/app/MyClass.java",
            """
                package com.example.app;

                public class MyClass {
                    void test() {
                        int r = com.example.app.R.string.leaf_lib_string;
                        int r2 = com.example.localLib.R.string.leaf_lib_string;
                        int r3 = com.example.leafLib.R.string.leaf_lib_string;
                        int r4 = com.example.app.R.raw.raw_file;
                        int r5 = com.example.app.R.string.local_lib_string;
                    }
                }
            """.trimIndent())

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":leafLib", leafLib)
            .subproject(":localLib", localLib)
            .subproject(":app", app)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testEnabledResourcesBuild() {
        // Tests a normal build with the resources pipeline enabled. Note the references to library
        // resources in the app's java class.
        val result = project.executor().run(":app:assembleDebug")
        assertThat(result.didWorkTasks).contains(":localLib:parseDebugLocalResources")

        // Sanity check that the overlay worked - local lib overrides the resource from leaf lib.
        TruthHelper.assertThatApk(project.getSubproject("app")
            .getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent(
                "res/raw/raw_file",
                "localLib"
            )
    }

    @Test
    fun testDisabledResourcesBuildFailsRemovedReferences() {
        // Tests the expected behaviour of code breaking if the flag is triggered (turned off, it is
        // turned on by default). Since the app was referencing the resources, java compilation
        // should fail.
        project.getSubproject("localLib").buildFile
            .appendText("android.buildFeatures.androidResources = false")
        val result = project.executor().expectFailure().run(":app:assembleDebug")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains("package com.example.localLib.R does not exist")
            ScannerSubject.assertThat(it).contains("error: cannot find symbol")
            ScannerSubject.assertThat(it).contains("com.example.app.R.string.local_lib_string")
        }
    }

    @Test
    fun testDisablingTasks() {
        // Tests a full build with the resource processing disabled in the middle library (the leaf
        // lib still has resource processing enabled). The app's code is modified to remove the
        // references to resources from the middle lib.
        project.getSubproject("localLib").buildFile
            .appendText("android.buildFeatures.androidResources = false")
        val appClass =
            project.getSubproject("app").file("src/main/java/com/example/app/MyClass.java")
        // Remove the reference in the app's class.
        TestFileUtils.searchAndReplace(appClass, "int r2 ","//int r2 ")
        TestFileUtils.searchAndReplace(appClass, "int r5 ","//int r5 ")

        val result = project.executor().run(":app:assembleDebug")
        // Make sure that in the local lib we called the task to generate the empty resources, and
        // not the standard res processing tasks. For leaf lib, resource processing should be still
        // enabled.
        assertThat(result.didWorkTasks).contains(":localLib:generateDebugEmptyResourceFiles")
        assertThat(result.didWorkTasks).doesNotContain(":localLib:parseDebugLocalResources")
        assertThat(result.didWorkTasks).contains(":leafLib:parseDebugLocalResources")

        // Since we've disabled resources in the local lib, but not in the leaf lib, then the raw
        // file defined in both should have the value of the one from the leaf lib.
        TruthHelper.assertThatApk(project.getSubproject("app")
            .getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent(
                "res/raw/raw_file",
                "leafLib"
            )
    }

    @Test
    fun testBooleanOptionDisablesAllLibraryResources() {
        // We should be able to use the boolean option to turn off res processing in all library
        // modules in the current project.
        val result = project.executor()
            .with(BooleanOption.BUILD_FEATURE_ANDROID_RESOURCES, false)
            .expectFailure()
            .run(":app:assembleDebug")

        result.stderr.use {
            ScannerSubject.assertThat(it).contains("error: cannot find symbol")
            ScannerSubject.assertThat(it).contains("com.example.app.R.string.leaf_lib_string")
            ScannerSubject.assertThat(it).contains("com.example.localLib.R.string.leaf_lib_string")
            ScannerSubject.assertThat(it).contains("com.example.leafLib.R.string.leaf_lib_string")
            ScannerSubject.assertThat(it).contains("com.example.app.R.raw.raw_file")
            ScannerSubject.assertThat(it).contains("com.example.app.R.string.local_lib_string")
        }

        assertThat(result.didWorkTasks).contains(":localLib:generateDebugEmptyResourceFiles")
        assertThat(result.didWorkTasks).contains(":leafLib:generateDebugEmptyResourceFiles")
        assertThat(result.didWorkTasks).doesNotContain(":localLib:parseDebugLocalResources")
        assertThat(result.didWorkTasks).doesNotContain(":leafLib:parseDebugLocalResources")
    }

    @Test
    fun testDslOverridesBooleanOption() {
        // Even when the global flag is on, we should be able to use the DSL option to turn the res
        // processing back on in library modules.
        project.getSubproject("localLib").buildFile
            .appendText("android.buildFeatures.androidResources = true")
        project.getSubproject("leafLib").buildFile
            .appendText("${System.lineSeparator()}android.buildFeatures.androidResources = true")

        val result = project.executor()
            .with(BooleanOption.BUILD_FEATURE_ANDROID_RESOURCES, false)
            .run(":app:assembleDebug")

        assertThat(result.didWorkTasks).doesNotContain(":localLib:generateDebugEmptyResourceFiles")
        assertThat(result.didWorkTasks).doesNotContain(":leafLib:generateDebugEmptyResourceFiles")
        assertThat(result.didWorkTasks).contains(":localLib:parseDebugLocalResources")
        assertThat(result.didWorkTasks).contains(":leafLib:parseDebugLocalResources")
    }

    @Test
    fun testAndroidAndUnitTests() {
        project.executor().with(BooleanOption.BUILD_FEATURE_ANDROID_RESOURCES, false)
            .run(":leaflib:assembleDebugAndroidTest", ":leaflib:test")
        assertThat(project.file("leafLib/build/reports/tests/testReleaseUnitTest/classes/com.example.MyTest.html").readText())
            .contains("ExampleTest has some output")
        assertThat(project.file("leafLib/build/reports/tests/testDebugUnitTest/classes/com.example.MyTest.html").readText())
            .contains("ExampleTest has some output")
    }

    /** Regression test for b/173134919. */
    @Test
    fun testAndroidAndUnitTestsSync() {
        project.model()
                .with(BooleanOption.BUILD_FEATURE_ANDROID_RESOURCES, false)
                .fetchAndroidProjects()
    }
}
