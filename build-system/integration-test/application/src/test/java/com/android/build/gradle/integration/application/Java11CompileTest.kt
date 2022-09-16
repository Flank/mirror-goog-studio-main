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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.OsType
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests on building a project with Java 9+ source code, annotation processing and unit tests
 */
class Java11CompileTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestProject("daggerTwo")
            .create()

    @Before
    fun setUp() {
        Assume.assumeTrue(TestUtils.runningWithJdk11Plus(System.getProperty("java.version")))

        TestFileUtils.appendToFile(
            project.buildFile,
            """
                dependencies {
                    testImplementation 'junit:junit:4.+'
                }

                android {
                    compileSdkVersion $DEFAULT_COMPILE_SDK_VERSION

                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_11
                        targetCompatibility JavaVersion.VERSION_11
                    }
                    lintOptions {
                        checkReleaseBuilds false
                    }
                }
            """.trimIndent()
        )

        val sourceFile = FileUtils.join(project.mainSrcDir, "com/android/tests/Foo.java")
        sourceFile.parentFile.mkdirs()
        TestFileUtils.appendToFile(
            sourceFile,
            """
                package com.android.tests;

                public class Foo {
                    public void java11Feature() {
                        //Local-variable syntax for lambda parameters is the language feature available from Java 11
                        java.util.function.Function<Integer, String> foo = (var input) -> input.toString();
                    }

                    public void stringConcat() {
                        String hello = "hello";
                        String combine = hello + "world";
                    }
                }
            """.trimIndent()
        )

        val unitTestFile = FileUtils.join(project.projectDir, "src/test/java/com/android/tests/UnitTest.java")
        unitTestFile.parentFile.mkdirs()
        TestFileUtils.appendToFile(
            unitTestFile,
            """
                package com.android.tests;

                import org.junit.Test;

                public class UnitTest {

                    @Test
                    public void testCodeWithJava11Feature() {
                        //Local-variable syntax for lambda parameters is the language feature available from Java 11
                        java.util.function.Function<Integer, String> foo = (var input) -> input.toString();
                    }

                    @Test
                    public void testInvokingAppCodeWithJava11Feature() {
                        new Foo().java11Feature();
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testJava11CompileAndAnnotationProcessing() {
        executor().run("assemble")
    }

    @Test
    fun testUnitTestWithJava11Feature() {
        executor().run("test")
    }

    @Test
    fun testSyncErrorWhenSdkIsNotCompatible() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """

                android.compileSdkVersion 29
            """.trimIndent()
        )
        val result = executor().expectFailure().run("assembleDebug")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "In order to compile Java 9+ source, please set compileSdkVersion to 30 or above")
        }
    }

    @Test
    fun testCompatibilityWithJacocoPlugin() {
        TemporaryProjectModification.doTest(project) { it: TemporaryProjectModification ->
            it.appendToFile(
                project.buildFile.path,
                """
                        android {
                          buildTypes {
                            debug {
                                testCoverageEnabled true
                            }
                          }
                          compileOptions {
                            sourceCompatibility JavaVersion.VERSION_11
                            targetCompatibility JavaVersion.VERSION_11
                          }
                        }"""
            )
            executor().run("assembleDebug")
        }
    }

    @Test
    fun testCompatibilityWithJavaToolChain() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "org.gradle.java.installations.paths=${customJdkLocation(JdkVersion.JDK8)}"
        )
        // jdk 8 is going to be used to create the jdk image(configured through java toolChain)
        // we expect it to fail because jdk 8 doesn't have the jlink tool to create jdk image
        TestFileUtils.appendToFile(
            project.buildFile,
            """

                tasks.withType(JavaCompile).configureEach {
                    javaCompiler = javaToolchains.compilerFor {
                        languageVersion = JavaLanguageVersion.of(8)
                    }
                }
            """.trimIndent()
        )
        val result = executor().expectFailure().run("assembleDebug")
        val gLink = if (OsType.getHostOs() == OsType.WINDOWS) "jlink.exe" else "jlink"
        result.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "$gLink does not exist"
            )
        }
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "JavaLanguageVersion.of(8)",
            "JavaLanguageVersion.of(11)"
        )

        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "org.gradle.java.installations.paths=${customJdkLocation(JdkVersion.JDK11)}"
        )
        executor().run("assembleDebug")
    }

    private fun customJdkLocation(jdkVersion: JdkVersion): String {
        return when(jdkVersion) {
            JdkVersion.JDK8 -> TestUtils.getJava8Jdk()
            JdkVersion.JDK11 -> TestUtils.getJava11Jdk()
        }.toString().replace("\\", "/")
    }

    private fun executor() =
        project.executor().with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)

    companion object {
        enum class JdkVersion {
            JDK11,
            JDK8
        }
    }
}
