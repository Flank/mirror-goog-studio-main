/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * This class provides test coverage for using custom values for namespace, testNamespace,
 * applicationId, testApplicationId, and various combinations of these.
 */
class NamespaceAndApplicationIdTest {

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

                import com.example.app.BuildConfig;

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

                import com.example.app.BuildConfig;

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
    fun testDefault() {
        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(DEBUG)).hasApplicationId("com.example.app")
        assertThatApk(project.getTestApk()).hasApplicationId("com.example.app.test")
        assertThat(
            JAVAC.getOutputDir(project.buildDir)
                .resolve("debugAndroidTest/classes/com/example/app/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomApplicationId() {
        project.buildFile.appendText(
                """
                android.defaultConfig.applicationId "com.example.applicationId"
             """
        )

        // Update the namespace of the test R class until b/176931684 is fixed.
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.test.R",
            "com.example.applicationId.test.R"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(DEBUG)).hasApplicationId("com.example.applicationId")
        assertThatApk(project.getTestApk()).hasApplicationId("com.example.applicationId.test")
        assertThat(
            JAVAC.getOutputDir(project.buildDir)
                .resolve("debugAndroidTest/classes/com/example/app/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomTestApplicationId() {
        project.buildFile.appendText(
                """
                android.defaultConfig.testApplicationId "com.example.testApplicationId"
            """)

        // Update the namespace of the test R class until b/176931684 is fixed.
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.test.R",
            "com.example.testApplicationId.R"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(DEBUG)).hasApplicationId("com.example.app")
        assertThatApk(project.getTestApk()).hasApplicationId("com.example.testApplicationId")
        assertThat(
            JAVAC.getOutputDir(project.buildDir)
                .resolve("debugAndroidTest/classes/com/example/app/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomApplicationIdAndTestApplicationId() {
        project.buildFile.appendText(
                """
                android.defaultConfig.applicationId "com.example.applicationId"
                android.defaultConfig.testApplicationId "com.example.testApplicationId"
            """)

        // Update the namespace of the test R class until b/176931684 is fixed.
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.test.R",
            "com.example.testApplicationId.R"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(DEBUG)).hasApplicationId("com.example.applicationId")
        assertThatApk(project.getTestApk()).hasApplicationId("com.example.testApplicationId")
        assertThat(
            JAVAC.getOutputDir(project.buildDir)
                .resolve("debugAndroidTest/classes/com/example/app/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomNamespace() {
        project.buildFile.appendText(
                """
                android.namespace "com.example.namespace"
            """)

        // Update the R and BuildConfig class namespaces in MyClass.java and MyTestClass.java
        val appClass = project.file("src/main/java/com/example/app/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.app.BuildConfig",
            "com.example.namespace.BuildConfig"
        )
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(testClass, "com.example.app.R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
                testClass,
                "com.example.app.test.R",
                "com.example.namespace.test.R"
        )
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.BuildConfig",
            "com.example.namespace.BuildConfig"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(DEBUG)).hasApplicationId("com.example.namespace")
        assertThatApk(project.getTestApk()).hasApplicationId("com.example.namespace.test")
        assertThat(
            JAVAC.getOutputDir(project.buildDir)
                .resolve("debugAndroidTest/classes/com/example/namespace/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomTestNamespace() {
        project.buildFile.appendText(
            """
                android.testNamespace "com.example.testNamespace"
            """
        )

        // Update the test R class namespaces in MyTestClass.java
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.test.R",
            "com.example.testNamespace.R"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(DEBUG)).hasApplicationId("com.example.app")
        assertThatApk(project.getTestApk()).hasApplicationId("com.example.app.test")
        assertThat(
            JAVAC.getOutputDir(project.buildDir)
                .resolve("debugAndroidTest/classes/com/example/testNamespace/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomNamespaceAndTestNamespace() {
        project.buildFile.appendText(
            """
                android.namespace "com.example.namespace"
                android.testNamespace "com.example.testNamespace"
            """
        )

        // Update the R and BuildConfig class namespaces in MyClass.java and MyTestClass.java
        val appClass = project.file("src/main/java/com/example/app/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.app.BuildConfig",
            "com.example.namespace.BuildConfig"
        )
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(testClass, "com.example.app.R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.test.R",
            "com.example.testNamespace.R"
        )
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.BuildConfig",
            "com.example.namespace.BuildConfig"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(DEBUG)).hasApplicationId("com.example.namespace")
        assertThatApk(project.getTestApk()).hasApplicationId("com.example.namespace.test")
        assertThat(
            JAVAC.getOutputDir(project.buildDir)
                .resolve("debugAndroidTest/classes/com/example/testNamespace/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomEverything() {
        project.buildFile.appendText(
                """
                android.namespace "com.example.namespace"
                android.testNamespace "com.example.testNamespace"
                android.defaultConfig.applicationId "com.example.applicationId"
                android.defaultConfig.testApplicationId "com.example.testApplicationId"
            """)

        // Update the R and BuildConfig class namespaces in MyClass.java and MyTestClass.java
        val appClass = project.file("src/main/java/com/example/app/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.app.BuildConfig",
            "com.example.namespace.BuildConfig"
        )
        val testClass = project.file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(testClass, "com.example.app.R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
                testClass,
                "com.example.app.test.R",
                "com.example.testNamespace.R"
        )
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.BuildConfig",
            "com.example.namespace.BuildConfig"
        )

        project.execute("assembleDebug", "assembleAndroidTest")
        assertThatApk(project.getApk(DEBUG)).hasApplicationId("com.example.applicationId")
        assertThatApk(project.getTestApk()).hasApplicationId("com.example.testApplicationId")
        assertThat(
            JAVAC.getOutputDir(project.buildDir)
                .resolve("debugAndroidTest/classes/com/example/testNamespace/BuildConfig.class")
        ).isFile()
    }
}
