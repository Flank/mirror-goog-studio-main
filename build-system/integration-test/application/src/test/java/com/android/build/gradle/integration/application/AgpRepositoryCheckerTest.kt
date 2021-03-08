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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

class AgpRepositoryCheckerTest {

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
            .fromTestApp(
                    MultiModuleTestProject(
                            mapOf(
                                    "androidLib1" to MinimalSubProject.lib("com.example.androidLib1"),
                                    "androidLib2" to MinimalSubProject.lib("com.example.androidLib2")
                            )
                    )
            ).create()

    @Test
    fun testRepoDetection() {
        // do not add any buildscript dependencies, those are added per project
        project.buildFile.appendText(
                """

            allprojects {
                repositories {
                    jcenter()
                }
            }
        """.trimIndent()
        )

        val result = project.executor().run("help")
        ScannerSubject.assertThat(result.stdout)
                .contains("Please remove usages of `jcenter()` Maven repository from your build scripts and migrate your build to other Maven repositories.")

        val withIdeOutputFormat = project.executor().with(BooleanOption.IDE_INVOKED_FROM_IDE, true).run("help")
        ScannerSubject.assertThat(withIdeOutputFormat.stdout)
                .contains("AGPBI: {\"kind\":\"warning\",\"text\":\"Please remove usages of `jcenter()` Maven repository")
    }
}
