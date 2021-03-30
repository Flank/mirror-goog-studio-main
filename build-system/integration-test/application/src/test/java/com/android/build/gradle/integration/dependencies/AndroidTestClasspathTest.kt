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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import org.junit.Rule
import org.junit.Test

class AndroidTestClasspathTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestApp(
        MultiModuleTestProject.builder()
            .subproject("app", MinimalSubProject.app("com.test.app"))
            .subproject("lib", MinimalSubProject.lib("com.test.lib"))
            .build()
    ).withAdditionalMavenRepo(
        MavenRepoGenerator(
            listOf(
                MavenRepoGenerator.Library(
                    "com.test:lib:1.0",
                    TestInputsGenerator.jarWithEmptyClasses(listOf("com/test/MyClass"))
                )
            )
        )
    ).create()

    /** See b/155802460 for more details. */
    @Test
    fun testAndroidTestClasspathContainsProjectDep() {
        project.getSubproject("app").buildFile.appendText(
            """

            dependencies {
                implementation "com.test:lib:1.0"
                implementation project(":lib")
                androidTestImplementation "com.test:lib:1.0"
            }
        """.trimIndent()
        )

        project.getSubproject("lib").buildFile.appendText(
            """
            
            group = "com.test"
            version = "99.0"
        """.trimIndent()
        )

        project.getSubproject("lib").mainSrcDir.resolve("test/Data.java").also {
            it.parentFile.mkdirs()
            it.writeText("package test; public class Data {}")
        }
        with(project.getSubproject("app")) {
            projectDir.resolve("src/androidTest/java/test/DataTest.java").also {
                it.parentFile.mkdirs()
                it.writeText("package test; public class DataTest extends Data {}")
            }

            // Disable failOnWarning temporarily (bug 184038058)
            executor().withFailOnWarning(false).run("assembleDebug", "assembleDebugAndroidTest")
            getApk(GradleTestProject.ApkType.DEBUG).use {
                assertThatApk(it).containsClass("Ltest/Data;")
            }
            getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG).use {
                assertThatApk(it).containsClass("Ltest/DataTest;")
                assertThatApk(it).doesNotContainClass("Ltest/Data;")
                assertThatApk(it).doesNotContainClass("Lcom/test/MyClass;")
            }
        }
    }
}