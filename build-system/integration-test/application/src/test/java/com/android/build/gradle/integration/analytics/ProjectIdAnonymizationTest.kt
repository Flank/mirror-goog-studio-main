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

package com.android.build.gradle.integration.analytics

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ProfileCapturer
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Ensure we get salt properly for project id anonymization.
 */
class ProjectIdAnonymizationTest {

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .enableProfileOutput()
        .create()

    @Before
    fun setUp() {
        project.buildFile.appendText(
            """
                import com.android.tools.analytics.AnalyticsSettings
                import com.android.utils.DateProvider

                task dateSetTask {
                    doLast {
                        AnalyticsSettings.dateProvider = new DateProvider() {
                            public Date now() {
                                return new Date(2020, 0, 1)
                            }
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun checkSaltRotation() {
        val capturer = ProfileCapturer(project)
        val firstRun = capturer.capture { project.execute("dateSetTask")}.single()
        val firstProjectId = firstRun.projectId
        // change date in the second build and check if we get different salt for project id
        TestFileUtils.searchAndReplace(project.buildFile, "2020", "2030")
        val secondRun = capturer.capture { project.execute("dateSetTask")}.single()
        Truth.assertThat(secondRun.projectId).isNotEqualTo(firstProjectId)
    }
}
