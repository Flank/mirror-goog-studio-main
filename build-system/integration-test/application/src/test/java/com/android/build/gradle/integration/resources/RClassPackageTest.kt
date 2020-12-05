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
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

class RClassPackageTest {

    private val app = MinimalSubProject.app("com.example.app")
            .withFile(
                    "src/main/res/values/values.xml",
                    """
                <resources>
                    <string name="app_string">hello</string>
                </resources>""".trimIndent()
            )
            .withFile("src/main/java/com/example/app/MyClass.java",
                    """
                package com.example.app;

                public class MyClass {
                    void test() {
                        int r = R.string.app_string;
                    }
                }
            """.trimIndent())
            .withFile(
                    "src/androidTest/res/values/values.xml",
                    """
                <resources>
                    <string name="test_string">hi</string>
                </resources>
            """.trimIndent())
            .withFile(
                    "src/androidTest/java/com/example/app/test/MyTestClass.java",
                    """
                package com.example.app.test;

                public class MyTestClass {
                    void test() {
                        int app_r = com.example.app.R.string.app_string;
                        int test_r = com.example.app.test.R.string.test_string;
                    }
                }
            """.trimMargin())

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(app).create()

    @Test
    fun checkDefault() {
        // Check default packages work.
        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getTestApk()).hasPackageName("com.example.app.test")
    }

    @Test
    fun checkCustomApplicationId() {
        // Add package overrides and make sure that:
        // a) app package for R remains unchanged
        // b) androidTest R package gets changed to applicationId + ".test"
        // TODO(170945282): migrate everything to use the actual package name in AGP 7.0.
        project.buildFile.appendText(
                """
                android.defaultConfig.applicationId "com.hello.world"
             """
        )
        // Updating test R class
        project.file("src/androidTest/java/com/example/app/test/MyTestClass.java").also {
            TestFileUtils.searchAndReplace(it, "com.example.app.test.R", "com.hello.world.test.R")
        }

        // App package should not be changed
        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getTestApk()).hasPackageName("com.hello.world.test")
    }

    @Test
    fun testCustomTestApplicationId() {
        project.buildFile.appendText(
                """
                android.defaultConfig.testApplicationId "com.foo.bar"
            """)

        project.executeExpectingFailure("assembleAndroidTest")

        // Updating the package in the test class to use the new R package should fix the build
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(testClass, "com.example.app.test.R", "com.foo.bar.R")

        project.execute("assembleAndroidTest")
        assertThatApk(project.getTestApk()).hasPackageName("com.foo.bar")
    }

    @Test
    fun testCustomApplicationIdAndTestApplicationId() {
        project.buildFile.appendText(
                """
                android.defaultConfig.applicationId "com.hello.world"
                android.defaultConfig.testApplicationId "com.foo.bar"
            """)

        // Updating the package in the test class to use the new R package should fix the build
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(testClass, "com.example.app.test.R", "com.foo.bar.R")

        project.execute("assembleAndroidTest")
        assertThatApk(project.getTestApk()).hasPackageName("com.foo.bar")

    }

    @Test
    fun testCustomNamespace() {
        project.buildFile.appendText(
                """
                android.namespace "com.example.fromDsl"
            """)

        // Update the R class namespaces in MyClass.java and MyTestClass.java
        val appClass = project.file("src/main/java/com/example/app/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.fromDsl.R")
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(testClass, "com.example.app.R", "com.example.fromDsl.R")
        TestFileUtils.searchAndReplace(
                testClass,
                "com.example.app.test.R",
                "com.example.fromDsl.test.R"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .hasPackageName("com.example.fromDsl")
        assertThatApk(project.getTestApk()).hasPackageName("com.example.fromDsl.test")
    }

    @Test
    fun testCustomNamespaceAndApplicationId() {
        project.buildFile.appendText(
                """
                android.namespace "com.example.namespace"
                android.defaultConfig.applicationId "com.example.applicationId"
            """)

        // Update the R class namespaces in MyClass.java and MyTestClass.java
        val appClass = project.file("src/main/java/com/example/app/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.namespace.R")
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(testClass, "com.example.app.R", "com.example.namespace.R")
        // TODO(170945282): migrate everything to use the actual package name in AGP 7.0, in which
        // case we'll replace this with "com.example.namespace.test.R".
        TestFileUtils.searchAndReplace(
                testClass,
                "com.example.app.test.R",
                "com.example.applicationId.test.R"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .hasPackageName("com.example.applicationId")
        assertThatApk(project.getTestApk()).hasPackageName("com.example.applicationId.test")
    }

    @Test
    fun testCustomNamespaceApplicationIdAndTestApplicationId() {
        project.buildFile.appendText(
                """
                android.namespace "com.example.namespace"
                android.defaultConfig.applicationId "com.example.applicationId"
                android.defaultConfig.testApplicationId "com.example.testApplicationId"
            """)

        // Update the R class namespaces in MyClass.java and MyTestClass.java
        val appClass = project.file("src/main/java/com/example/app/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.namespace.R")
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(testClass, "com.example.app.R", "com.example.namespace.R")
        // TODO(170945282): migrate everything to use the actual package name in AGP 7.0, in which
        // case we'll replace this with "com.example.namespace.test.R".
        TestFileUtils.searchAndReplace(
                testClass,
                "com.example.app.test.R",
                "com.example.testApplicationId.R"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .hasPackageName("com.example.applicationId")
        assertThatApk(project.getTestApk()).hasPackageName("com.example.testApplicationId")
    }
}
