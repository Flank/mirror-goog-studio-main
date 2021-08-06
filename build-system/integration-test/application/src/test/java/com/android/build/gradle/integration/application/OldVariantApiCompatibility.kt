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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

class OldVariantApiCompatibility {

    @get:Rule
    val project: GradleTestProject = builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
            .create()

    @Test
    fun testApplicationId() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android.libraryVariants.all { variant ->
                  println(variant.applicationId)
                }
            """.trimIndent()
        )
        val result = project.executor()
            .run(
                "clean"
            )

        ScannerSubject.assertThat(result.stdout).contains("com.example.helloworld")
    }

    @Test
    fun testApplicationIdInSafeMode() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android.libraryVariants.all { variant ->
                  println(variant.applicationId)
                }
            """.trimIndent()
        )
        val result = project.executor()
            .with(BooleanOption.ENABLE_LEGACY_API, false)
            .expectFailure()
            .run(
                "clean"
            )

        ScannerSubject.assertThat(result.stderr).contains(
            "Access to applicationId via deprecated Variant API requires compatibility mode"
        )
    }
}
