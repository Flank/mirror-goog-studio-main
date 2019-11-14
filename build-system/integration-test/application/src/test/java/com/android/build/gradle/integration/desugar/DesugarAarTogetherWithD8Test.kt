/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.desugar.resources.ImplOfInterfaceWithDefaultMethod
import com.android.build.gradle.integration.desugar.resources.InterfaceWithDefaultMethod
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator.jarWithClasses
import com.android.testutils.generateAarWithContent
import org.junit.Rule
import org.junit.Test

/**
 * Checks that classes within an AAR can be desugared together.
 * Regression test for https://issuetracker.google.com/140508065
 *
 */
class DesugarAarTogetherWithD8Test {

    private val app = MinimalSubProject.app("com.example.desugar.aar.together").apply {
        appendToBuild(
            """
                android.compileOptions.sourceCompatibility 1.8
                android.compileOptions.targetCompatibility 1.8
                dependencies {
                    implementation 'com.example:myaar:1'
                }
                """.trimIndent()
        )
    }

    private val aar = generateAarWithContent(
        packageName = "com.example.myaar",
        mainJar = jarWithClasses(listOf(ImplOfInterfaceWithDefaultMethod::class.java)),
        secondaryJars = mapOf("other" to jarWithClasses(listOf(InterfaceWithDefaultMethod::class.java)))
    )

    private val mavenRepo = MavenRepoGenerator(
        listOf(
            MavenRepoGenerator.Library("com.example:myaar:1", "aar", aar)
        )
    )

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(app)
        .withAdditionalMavenRepo(mavenRepo)
        .create()

    @Test
    fun desugarsLibraryDependency() {
        project.executor().run("assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).hasDexVersion(35)

        assertThat(apk)
            .hasClass("L" + "com/android/build/gradle/integration/desugar/resources/ImplOfInterfaceWithDefaultMethod;")
            .that().hasMethod("myDefaultMethod")
    }
}
