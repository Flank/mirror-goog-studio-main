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

package com.android.build.gradle.integration.publishing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for generating Java docs from java & kotlin mixed source.
 */
class JavaDocGenerationMixedSourceTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("kotlinApp")
        .create()

    private lateinit var library: GradleTestProject

    @Before
    fun setUp() {
        project.projectDir.resolve("testrepo").mkdirs()
        library = project.getSubproject("library")

        val javaSource = library.mainSrcDir.resolve("$JAVA_SOURCE_DIR/HelloWorld.java")
        FileUtils.createFile(javaSource, """
            package com.example.android.java;

            /**
            * See {@link android.app.Activity}, {@link com.example.android.kotlin.LibActivity}
            */
            public class HelloWorld {
                public void sayHelloInJava() {}
            }
        """.trimIndent())

        TestFileUtils.addMethod(
            library.getMainSrcDir("kotlin").resolve("$KOTLIN_SOURCE_DIR/LibActivity.kt"),
            """
                /**
                * See [com.example.android.java.HelloWorld.sayHelloInJava]
                */
                fun sayHelloInKotlin() {}
            """.trimIndent()
        )
    }

    @Test
    fun testJavaDocGeneration() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        singleVariant('debug') {
                            withJavadocJar()
                        }
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "javaDocDebugGeneration")
        val docDirectory = InternalArtifactType.JAVA_DOC_DIR.getOutputDir(library.buildDir).resolve("debug")

        val javaSourceDoc = docDirectory.resolve(JAVA_SOURCE_DOC)
        val kotlinSourceDoc = docDirectory.resolve(KOTLIN_SOURCE_DOC)

        PathSubject.assertThat(javaSourceDoc.toPath()).isFile()
        PathSubject.assertThat(kotlinSourceDoc.toPath()).isFile()

        PathSubject.assertThat(javaSourceDoc).contains(
            "<a href=../kotlin/LibActivity.html>com.example.android.kotlin.LibActivity</a>")

        PathSubject.assertThat(kotlinSourceDoc).contains("See <a href=../java/HelloWorld.html#" +
                "sayHelloInJava()>com.example.android.java.HelloWorld.sayHelloInJava</a>")
    }

    companion object {
        private const val JAVA_SOURCE_DIR: String = "com/example/android/java"
        private const val JAVA_SOURCE_DOC: String = "$JAVA_SOURCE_DIR/HelloWorld.html"
        private const val KOTLIN_SOURCE_DIR: String = "com/example/android/kotlin"
        private const val KOTLIN_SOURCE_DOC: String = "$KOTLIN_SOURCE_DIR/LibActivity.html"
    }
}
