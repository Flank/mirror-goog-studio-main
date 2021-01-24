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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests on building a project with Java 9+ source code and kotlin source code
 */
class Java11WithKotlinCompileTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestProject("kotlinApp")
            .create()

    @Before
    fun setUp() {
        Assume.assumeTrue(TestUtils.runningWithJdk11Plus(System.getProperty("java.version")))

        val app = project.getSubproject("app")

        TestFileUtils.appendToFile(
            app.buildFile,
            """

                android {
                    compileSdkVersion 30

                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_11
                        targetCompatibility JavaVersion.VERSION_11
                    }
                }
            """.trimIndent()
        )

        val javaSourceFile = FileUtils.join(app.mainSrcDir, "com/example/android/Foo.java")
        javaSourceFile.parentFile.mkdirs()
        TestFileUtils.appendToFile(
            javaSourceFile,
            """
                package com.example.android;

                public class Foo {
                    public void java11Feature() {
                        //Local-variable syntax for lambda parameters is the language feature available from Java 11
                        java.util.function.Function<Integer, String> foo = (var input) -> input.toString();
                    }

                    public void invokeKotlinFunction() {
                        new com.example.android.kotlin.MainActivity().kotlinFunction();
                    }
                }
            """.trimIndent()
        )

        TestFileUtils.addMethod(
                FileUtils.join(app.getMainSrcDir("kotlin"), "com/example/android/kotlin/MainActivity.kt"),
                """
                    fun invokeJavaFunction() {
                        com.example.android.Foo().java11Feature()
                    }
                """.trimIndent()
        )

        TestFileUtils.addMethod(
                FileUtils.join(app.getMainSrcDir("kotlin"), "com/example/android/kotlin/MainActivity.kt"),
                """
                    fun kotlinFunction() {
                        val foo = "bar"
                    }
                """.trimIndent()
        )
    }

    @Test
    fun testCompilation() {
        project.executor().run("app:assembleDebug")
    }
}
