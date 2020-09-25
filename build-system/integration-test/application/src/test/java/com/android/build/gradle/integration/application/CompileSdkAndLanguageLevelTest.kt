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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.gradle.api.JavaVersion
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Check different Java language levels when compiling against older platform versions. */
@RunWith(FilterableParameterized::class)
class CompileSdkAndLanguageLevelTest(
    private val javaVersion: JavaVersion,
    private val compileSdkVersion: Int
) {
    companion object {
        @Parameterized.Parameters(name = "javaVersion_{0}_compileSdkVersion_{1}")
        @JvmStatic
        fun getParams() =
            arrayOf(
                arrayOf(JavaVersion.VERSION_1_6, 19),
                arrayOf(JavaVersion.VERSION_1_6, 21),
                arrayOf(JavaVersion.VERSION_1_6, 24),
                arrayOf(JavaVersion.VERSION_1_7, 19),
                arrayOf(JavaVersion.VERSION_1_7, 21),
                arrayOf(JavaVersion.VERSION_1_7, 24),
                arrayOf(JavaVersion.VERSION_1_8, 19),
                arrayOf(JavaVersion.VERSION_1_8, 21),
                arrayOf(JavaVersion.VERSION_1_8, 24)
            )
    }

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
        .fromTestApp(MinimalSubProject.app("com.example"))
        .create()

    @Before
    fun setUp() {
        if (javaVersion.isJava6) {
            // With target compatibility Java 6, javac generates the following warning:
            // "warning: [options] target value 1.6 is obsolete and will be removed in a future release"
            // By default, integration tests treat javac warnings as errors, but the above warning
            // is expected so we can ignore it.
            TestFileUtils.searchAndReplace(
                    project.buildFile,
                    "options.compilerArgs << \"-Werror\"",
                    "")
        }
    }

    @Test
    fun `test build successfully`() {
        addJavaSources()
        project.buildFile.appendText(
            """

            android.compileSdkVersion $compileSdkVersion
            android.compileOptions.sourceCompatibility $javaVersion
            android.compileOptions.targetCompatibility $javaVersion
            """.trimIndent()
        )

        project.executor().run("assembleDebug")
    }

    private fun addJavaSources() {
        project.mainSrcDir.resolve("com/example/DataJava6.java").also {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package com.example;
                
                public class DataJava6 {
                }
            """.trimIndent()
            )
        }
        if (javaVersion.isJava7Compatible) {
            project.mainSrcDir.resolve("com/example/DataJava7.java").also {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                package com.example;
                
                import java.io.StringWriter;
                import java.io.IOException;
                
                public class DataJava7 {
                    static {
                        try(StringWriter sw = new StringWriter()) {
                            sw.append('c');
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            """.trimIndent()
                )
            }
        }
        if (javaVersion.isJava8Compatible) {
            project.mainSrcDir.resolve("com/example/DataJava8.java").also {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                package com.example;
                
                public class DataJava8 {
                    static {
                        Runnable r = () -> System.out.println("");
                    }
                }
            """.trimIndent()
                )
            }
        }
    }
}