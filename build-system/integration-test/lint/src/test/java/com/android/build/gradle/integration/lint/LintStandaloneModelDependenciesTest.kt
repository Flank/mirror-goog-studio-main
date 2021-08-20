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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test

class LintStandaloneModelDependenciesTest {

    private val javaLib1 =
        MinimalSubProject.javaLibrary()
            .appendToBuild(
                """
                    apply plugin: 'com.android.lint'

                    // add random implementation and compileOnly dependencies
                    dependencies {
                        implementation 'com.google.guava:guava:19.0'
                        compileOnly 'junit:junit:4.12'
                    }
                """.trimIndent()
            )

    private val javaLib2 = MinimalSubProject.javaLibrary()
    private val javaLib3 = MinimalSubProject.javaLibrary()

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .withName("project")
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":java-lib1", javaLib1)
                    .subproject(":java-lib2", javaLib2)
                    .subproject(":java-lib3", javaLib3)
                    .dependency("implementation", javaLib1, javaLib2)
                    .dependency("compileOnly", javaLib1, javaLib3)
                    .build()
            )
            .create()

    // Regression test for b/197146610
    @Test
    fun testLintWithCompileOnlyDependencies() {
        project.executor().run("clean", ":java-lib1:lint")
        val lintModelFile =
            FileUtils.join(
                project.getSubproject("java-lib1").intermediatesDir,
                "lintReport",
                "android-lint-model",
                "main-mainArtifact-dependencies.xml"
            )
        assertThat(lintModelFile).exists()
        assertThat(lintModelFile).containsAllOf("junit", "guava", "java-lib2", "java-lib3")
    }
}
