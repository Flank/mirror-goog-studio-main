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
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
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

                    dependencies {
                        compileOnly 'com.google.guava:guava:19.0'
                        testImplementation 'junit:junit:4.12'
                        // Add gradleApi() dependency as regression test for b/198453608
                        implementation gradleApi()
                        // Add external Android dependency as regression test for b/198449627
                        implementation 'com.android.support:appcompat-v7:${SUPPORT_LIB_VERSION}'
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
    fun testLintModelDependencies() {
        project.executor().run("clean", ":java-lib1:lint")

        val mainArtifactDependenciesFile =
            FileUtils.join(
                project.getSubproject("java-lib1").intermediatesDir,
                "lintReport",
                "android-lint-model",
                "main-mainArtifact-dependencies.xml"
            )
        assertThat(mainArtifactDependenciesFile).exists()
        assertThat(mainArtifactDependenciesFile).containsAllOf("guava", "java-lib2", "java-lib3")
        assertThat(mainArtifactDependenciesFile).doesNotContain("junit")

        val testArtifactDependenciesFile =
            FileUtils.join(
                project.getSubproject("java-lib1").intermediatesDir,
                "lintReport",
                "android-lint-model",
                "main-testArtifact-dependencies.xml"
            )
        assertThat(testArtifactDependenciesFile).exists()
        assertThat(testArtifactDependenciesFile).containsAllOf("junit", "java-lib2")
        assertThat(testArtifactDependenciesFile).doesNotContain("guava")
        assertThat(testArtifactDependenciesFile).doesNotContain("java-lib3")
    }
}
