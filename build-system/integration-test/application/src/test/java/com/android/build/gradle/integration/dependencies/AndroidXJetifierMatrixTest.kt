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

import com.android.build.gradle.integration.common.fixture.ANDROIDX_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelContainer
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter
import com.android.builder.model.AndroidProject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for detection of different issues relating to different combinations of the
 * [BooleanOption.USE_ANDROID_X] and [BooleanOption.ENABLE_JETIFIER] properties.
 */
class AndroidXJetifierMatrixTest {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(MinimalSubProject.app("com.example.application"))
            .create()

    private fun addAndroidXDependencies() {
        TestFileUtils.appendToFile(
                project.buildFile,
                """
                dependencies {
                    implementation 'androidx.annotation:annotation:$ANDROIDX_VERSION'
                }
                """.trimIndent()
        )
    }

    private fun expectSyncIssue(
            model: ModelContainer<AndroidProject>,
            type: IssueReporter.Type,
            severity: IssueReporter.Severity,
            message: String,
            data: String? = null) {
        val syncIssues = model.onlyModelSyncIssues
        assertThat(syncIssues).hasSize(1)
        val syncIssue = syncIssues.single()

        assertThat(syncIssue.type).isEqualTo(type.type)
        assertThat(syncIssue.severity).isEqualTo(severity.severity)
        assertThat(syncIssue.message).isEqualTo(message)
        data?.let { assertThat(syncIssue.data).isEqualTo(it) }
    }

    @Test
    fun `AndroidX=false, Jetifier=false, AndroidX dependencies present, expect sync issue`() {
        addAndroidXDependencies()

        val model = project.model()
                .with(BooleanOption.USE_ANDROID_X, false)
                .with(BooleanOption.ENABLE_JETIFIER, false)
                .ignoreSyncIssues().fetchAndroidProjects()
        expectSyncIssue(
                model,
                IssueReporter.Type.ANDROID_X_PROPERTY_NOT_ENABLED,
                IssueReporter.Severity.ERROR,
                "This project uses AndroidX dependencies, but the 'android.useAndroidX' property is" +
                        " not enabled. Set this property to true in the gradle.properties file and retry.\n" +
                        "The following AndroidX dependencies are detected:" +
                        " androidx.annotation:annotation:$ANDROIDX_VERSION",
                "androidx.annotation:annotation:$ANDROIDX_VERSION"
        )
    }

    @Test
    fun `AndroidX=false, Jetifier=false, AndroidX dependencies not present, expect no issues`() {
        val model = project.model()
                .with(BooleanOption.USE_ANDROID_X, false)
                .with(BooleanOption.ENABLE_JETIFIER, false)
                .ignoreSyncIssues().fetchAndroidProjects()
        assertThat(model.onlyModelSyncIssues).isEmpty()
    }

    @Test
    fun `AndroidX=false, Jetifier=true, expect sync issue`() {
        val model = project.model()
                .with(BooleanOption.USE_ANDROID_X, false)
                .with(BooleanOption.ENABLE_JETIFIER, true)
                .ignoreSyncIssues().fetchAndroidProjects()
        expectSyncIssue(
                model,
                type = IssueReporter.Type.ANDROID_X_PROPERTY_NOT_ENABLED,
                severity = IssueReporter.Severity.ERROR,
                message = "AndroidX must be enabled when Jetifier is enabled. To resolve, set" +
                        " ${BooleanOption.USE_ANDROID_X.propertyName}=true" +
                        " in your gradle.properties file."
        )
    }
}
