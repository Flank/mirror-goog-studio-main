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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Regression test for http://b/144249620. It checks that we are able to build successfully
 * if there aren't any Java sources and BuildConfig generation is disabled.
 */
class GenFolderKotlinOnlyApiTest {
    @JvmField
    @Rule
    var project =
        GradleTestProject.builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.library"))
            .create()

    @JvmField
    @Rule
    var tmp = TemporaryFolder()

    @Before
    fun setUp() {
        project.file("gen_src").also {
            val sourceFile = it.resolve("test/Generated.kt")
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText(
                """
                package test
                class Generated
            """.trimIndent()
            )
        }
        project.buildFile.appendText(
            """
            def emptyTask = tasks.create("emptyTask")
            android.libraryVariants.all {
              it.getGenerateBuildConfigProvider().configure { it.enabled = false }
              it.registerJavaGeneratingTask(emptyTask, new File("gen_src"))
            }
        """.trimIndent()
        )
    }

    @Test
    fun testBuildSucceeds() {
        project.executor().run("assembleDebug")
        project.assertThatAar("debug") {
            containsClass("Ltest/Generated;")
        }
    }
}
