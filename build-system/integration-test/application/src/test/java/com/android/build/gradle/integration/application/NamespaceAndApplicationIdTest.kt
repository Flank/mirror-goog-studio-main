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
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
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

    private val test = MinimalSubProject.test("com.example.test")
        .appendToBuild("\n\nandroid.targetProjectPath ':app'\n\n")
        .withFile(
            "src/main/res/values/values.xml",
            """
                <resources>
                    <string name="app_string">hello</string>
                </resources>""".trimIndent()
        )
        .withFile("src/main/java/com/example/test/MyClass.java",
            """
                package com.example.test;

                import com.example.test.BuildConfig;

                public class MyClass {
                    void test() {
                        int r = R.string.app_string;
                    }
                }
            """.trimIndent())

    private val multiModuleTestProject =
        MultiModuleTestProject.builder().subproject(":app", app).subproject(":test", test).build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(multiModuleTestProject).create()

    @Test
    fun testDefault() {
        project.execute("app:assembleDebug", "app:assembleAndroidTest")
        assertThatApk(project.getSubproject(":app").getApk(DEBUG))
            .hasApplicationId("com.example.app")
        assertThatApk(project.getSubproject(":app").getTestApk())
            .hasApplicationId("com.example.app.test")
        assertThat(
            JAVAC.getOutputDir(project.getSubproject(":app").buildDir)
                .resolve("debugAndroidTest/classes/com/example/app/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testDefaultForTestModule() {
        project.execute(":test:assembleDebug")
        assertThatApk(project.getSubproject(":test").getApk(DEBUG))
            .hasApplicationId("com.example.test")
    }

    @Test
    fun testCustomApplicationId() {
        project.getSubproject(":app").buildFile.appendText(
                """
                android.defaultConfig.applicationId "com.example.applicationId"
             """
        )

        // Update the namespace of the test R class until b/176931684 is fixed.
        val testClass =
            project.getSubproject(":app")
                .file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.test.R",
            "com.example.applicationId.test.R"
        )

        project.execute(":app:assembleDebug", ":app:assembleAndroidTest")
        assertThatApk(project.getSubproject(":app").getApk(DEBUG))
            .hasApplicationId("com.example.applicationId")
        assertThatApk(project.getSubproject(":app").getTestApk())
            .hasApplicationId("com.example.applicationId.test")
        assertThat(
            JAVAC.getOutputDir(project.getSubproject(":app").buildDir)
                .resolve("debugAndroidTest/classes/com/example/app/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomTestApplicationId() {
        project.getSubproject(":app").buildFile.appendText(
                """
                android.defaultConfig.testApplicationId "com.example.testApplicationId"
            """)

        // Update the namespace of the test R class until b/176931684 is fixed.
        val testClass =
            project.getSubproject(":app")
                .file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.test.R",
            "com.example.testApplicationId.R"
        )

        project.execute(":app:assembleDebug", ":app:assembleAndroidTest")
        assertThatApk(project.getSubproject(":app").getApk(DEBUG))
            .hasApplicationId("com.example.app")
        assertThatApk(project.getSubproject(":app").getTestApk())
            .hasApplicationId("com.example.testApplicationId")
        assertThat(
            JAVAC.getOutputDir(project.getSubproject(":app").buildDir)
                .resolve("debugAndroidTest/classes/com/example/app/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomTestApplicationIdForTestModule() {
        // First test that we get a warning if we remove the AndroidManifest.xml and specify a
        // testApplicationId (and no namespace is specified)
        FileUtils.deleteIfExists(
            project.getSubproject(":test").file("src/main/AndroidManifest.xml")
        )
        project.getSubproject(":test").buildFile.appendText(
            """
                android.defaultConfig.testApplicationId "com.example.testApplicationId"
            """)

        // Update the R and BuildConfig class namespaces in MyClass.java
        val appClass =
            project.getSubproject(":test")
                .file("src/main/java/com/example/test/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.testApplicationId.R")
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.test.BuildConfig",
            "com.example.testApplicationId.BuildConfig"
        )

        project.execute(":test:assembleDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).contains(
            "Namespace not specified."
        )
        ScannerSubject.assertThat(project.buildResult.stdout).contains(
            "Currently, this test module uses the testApplicationId (com.example.testApplicationId)"
        )

        // Then test that there is no warning after specifying a namespace and updating MyClass.java
        // accordingly.
        project.getSubproject(":test").buildFile.appendText(
            """
                android.namespace "com.example.namespace"
            """)

        // Update the R and BuildConfig class namespaces in MyClass.java
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.testApplicationId.R",
            "com.example.namespace.R"
        )
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.testApplicationId.BuildConfig",
            "com.example.namespace.BuildConfig"
        )

        project.execute(":test:assembleDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).doesNotContain(
            "Namespace not specified."
        )
        assertThatApk(project.getSubproject(":test").getApk(DEBUG))
            .hasApplicationId("com.example.testApplicationId")
    }

    @Test
    fun testCustomApplicationIdAndTestApplicationId() {
        project.getSubproject(":app").buildFile.appendText(
                """
                android.defaultConfig.applicationId "com.example.applicationId"
                android.defaultConfig.testApplicationId "com.example.testApplicationId"
            """)

        // Update the namespace of the test R class until b/176931684 is fixed.
        val testClass =
            project.getSubproject(":app")
                .file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.test.R",
            "com.example.testApplicationId.R"
        )

        project.execute(":app:assembleDebug", "app:assembleAndroidTest")
        assertThatApk(project.getSubproject(":app").getApk(DEBUG))
            .hasApplicationId("com.example.applicationId")
        assertThatApk(project.getSubproject(":app").getTestApk())
            .hasApplicationId("com.example.testApplicationId")
        assertThat(
            JAVAC.getOutputDir(project.getSubproject(":app").buildDir)
                .resolve("debugAndroidTest/classes/com/example/app/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomNamespace() {
        project.getSubproject(":app").buildFile.appendText(
                """
                android.namespace "com.example.namespace"
            """)

        // Update the R and BuildConfig class namespaces in MyClass.java and MyTestClass.java
        val appClass =
            project.getSubproject(":app").file("src/main/java/com/example/app/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.app.BuildConfig",
            "com.example.namespace.BuildConfig"
        )
        val testClass =
            project.getSubproject(":app")
                .file("src/androidTest/java/com/example/app/test/MyTestClass.java")
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

        project.execute(":app:assembleDebug", ":app:assembleAndroidTest")
        assertThatApk(project.getSubproject(":app").getApk(DEBUG))
            .hasApplicationId("com.example.namespace")
        assertThatApk(project.getSubproject(":app").getTestApk())
            .hasApplicationId("com.example.namespace.test")
        assertThat(
            JAVAC.getOutputDir(project.getSubproject(":app").buildDir)
                .resolve("debugAndroidTest/classes/com/example/namespace/test/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomNamespaceForTestModule() {
        project.getSubproject(":test").buildFile.appendText(
            """
                android.namespace "com.example.namespace"
            """)

        // Update the R and BuildConfig class namespaces in MyClass.java
        val appClass =
            project.getSubproject(":test")
                .file("src/main/java/com/example/test/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.test.BuildConfig",
            "com.example.namespace.BuildConfig"
        )

        project.execute(":test:assembleDebug")
        assertThatApk(project.getSubproject(":test").getApk(DEBUG))
            .hasApplicationId("com.example.namespace")
    }

    @Test
    fun testCustomTestNamespace() {
        project.getSubproject(":app").buildFile.appendText(
            """
                android.testNamespace "com.example.testNamespace"
            """
        )

        // Update the test R class namespaces in MyTestClass.java
        val testClass =
            project.getSubproject(":app")
                .file("src/androidTest/java/com/example/app/test/MyTestClass.java")
        assertThat(testClass).exists()
        TestFileUtils.searchAndReplace(
            testClass,
            "com.example.app.test.R",
            "com.example.testNamespace.R"
        )

        project.execute(":app:assembleDebug", ":app:assembleAndroidTest")
        assertThatApk(project.getSubproject(":app").getApk(DEBUG))
            .hasApplicationId("com.example.app")
        assertThatApk(project.getSubproject(":app").getTestApk())
            .hasApplicationId("com.example.app.test")
        assertThat(
            JAVAC.getOutputDir(project.getSubproject(":app").buildDir)
                .resolve("debugAndroidTest/classes/com/example/testNamespace/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomNamespaceAndTestNamespace() {
        project.getSubproject(":app").buildFile.appendText(
            """
                android.namespace "com.example.namespace"
                android.testNamespace "com.example.testNamespace"
            """
        )

        // Update the R and BuildConfig class namespaces in MyClass.java and MyTestClass.java
        val appClass =
            project.getSubproject(":app").file("src/main/java/com/example/app/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.app.BuildConfig",
            "com.example.namespace.BuildConfig"
        )
        val testClass = project.getSubproject(":app")
            .file("src/androidTest/java/com/example/app/test/MyTestClass.java")
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

        project.execute(":app:assembleDebug", ":app:assembleAndroidTest")
        assertThatApk(project.getSubproject(":app").getApk(DEBUG))
            .hasApplicationId("com.example.namespace")
        assertThatApk(project.getSubproject(":app").getTestApk())
            .hasApplicationId("com.example.namespace.test")
        assertThat(
            JAVAC.getOutputDir(project.getSubproject(":app").buildDir)
                .resolve("debugAndroidTest/classes/com/example/testNamespace/BuildConfig.class")
        ).isFile()
    }

    @Test
    fun testCustomEverything() {
        project.getSubproject(":app").buildFile.appendText(
                """
                android.namespace "com.example.namespace"
                android.testNamespace "com.example.testNamespace"
                android.defaultConfig.applicationId "com.example.applicationId"
                android.defaultConfig.testApplicationId "com.example.testApplicationId"
            """)

        // Update the R and BuildConfig class namespaces in MyClass.java and MyTestClass.java
        val appClass = project.getSubproject(":app")
            .file("src/main/java/com/example/app/MyClass.java")
        assertThat(appClass).exists()
        TestFileUtils.searchAndReplace(appClass, "R", "com.example.namespace.R")
        TestFileUtils.searchAndReplace(
            appClass,
            "com.example.app.BuildConfig",
            "com.example.namespace.BuildConfig"
        )
        val testClass = project.getSubproject(":app")
            .file("src/androidTest/java/com/example/app/test/MyTestClass.java")
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

        project.execute(":app:assembleDebug", "app:assembleAndroidTest")
        assertThatApk(project.getSubproject(":app").getApk(DEBUG))
            .hasApplicationId("com.example.applicationId")
        assertThatApk(project.getSubproject(":app").getTestApk())
            .hasApplicationId("com.example.testApplicationId")
        assertThat(
            JAVAC.getOutputDir(project.getSubproject(":app").buildDir)
                .resolve("debugAndroidTest/classes/com/example/testNamespace/BuildConfig.class")
        ).isFile()
    }
}
