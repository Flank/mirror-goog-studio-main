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
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.FileSubject.assertThat
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
    fun checkCorrectPackages() {
        // Check default packages work.
        project.execute("assembleDebug", "assembleAndroidTest")

        // Now add package overrides and make sure that:
        // a) app package for R remains unchanged
        // b) androidTest R package gets changed
        // TODO(b/162244493): migrate everything to use the actual package name in AGP 5.0.
        project.buildFile.appendText(
            """
                android.defaultConfig.applicationId "com.hello.world"
             """
        )

        // App package should not be changed
        project.execute("assembleDebug", "assembleAndroidTest")

        project.buildFile.appendText(
            """
                android.defaultConfig.testApplicationId "com.foo.bar"
            """.trimIndent())

        project.executeExpectingFailure("assembleAndroidTest")

        // Updating the package in the test class to use the new R package should fix the build
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(testClass, "com.example.app.test.R", "com.foo.bar.R")

        project.execute("assembleAndroidTest")

    }
}
