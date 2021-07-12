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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for generating Java docs from pure java library projects.
 */
class JavaDocGenerationJavaSourceTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(MinimalSubProject.lib("com.example.lib"))
        .create()

    @Before
    fun setUp() {

        val javaSource = project.mainSrcDir.resolve("com/example/HelloWorld.java")
        FileUtils.createFile(javaSource, """
            package com.example;

            /**
            * See {@link android.app.Activity}
            */
            public class HelloWorld {
                public void sayHelloInJava() {}

                public Greeting getGreeting() {
                    return new Greeting();
                }
            }
        """.trimIndent())

        val javaSource2 = project.mainSrcDir.resolve("com/example/Greeting.java")
        FileUtils.createFile(javaSource2, """
            package com.example;

            public class Greeting {}
        """.trimIndent())
    }

    @Test
    fun testJavaDocGeneration() {
        TestFileUtils.appendToFile(
            project.buildFile,
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
        project.execute("clean", "javaDocDebugGeneration")

        val docDirectory = InternalArtifactType.JAVA_DOC_DIR.getOutputDir(project.buildDir).resolve("debug")
        val javaSourceDoc = docDirectory.resolve("com/example/HelloWorld.html")
        val javaSourceDoc2 = docDirectory.resolve("com/example/Greeting.html")

        PathSubject.assertThat(javaSourceDoc.toPath()).isFile()
        PathSubject.assertThat(javaSourceDoc2.toPath()).isFile()
        PathSubject.assertThat(javaSourceDoc).contains(
            "<a href=Greeting.html>Greeting</a>"
        )
    }
}
