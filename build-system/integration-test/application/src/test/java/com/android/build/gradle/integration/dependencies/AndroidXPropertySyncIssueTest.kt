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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.ANDROIDX_VERSION
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.SyncIssue.SEVERITY_ERROR
import com.android.builder.model.SyncIssue.TYPE_ANDROID_X_PROPERTY_NOT_ENABLED
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Integration test for the [TYPE_ANDROID_X_PROPERTY_NOT_ENABLED] sync issue. */
class AndroidXPropertySyncIssueTest {

    @get:Rule
    var project = EmptyActivityProjectBuilder().also {
        it.withUnitTest = false
        it.useAndroidX = false
    }.build()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
            dependencies {
                implementation 'androidx.annotation:annotation:$ANDROIDX_VERSION'
            }
            """.trimIndent()
        )
    }

    @Test
    fun `AndroidX dependencies used, AndroidX property disabled, expect failure`() {
        val model = project.model().ignoreSyncIssues().fetchAndroidProjects()
        val syncIssues = model.onlyModelSyncIssues
        assertThat(syncIssues).hasSize(1)
        val syncIssue = syncIssues.single()

        assertThat(syncIssue.type).isEqualTo(TYPE_ANDROID_X_PROPERTY_NOT_ENABLED)
        assertThat(syncIssue.severity).isEqualTo(SEVERITY_ERROR)
        assertThat(syncIssue.message).isEqualTo(
            "This project uses AndroidX dependencies, but the 'android.useAndroidX' property is" +
                    " not enabled. Set this property to true in the gradle.properties file and" +
                    " retry.\n" +
                    "The following AndroidX dependencies are detected:" +
                    " androidx.annotation:annotation:$ANDROIDX_VERSION")
        assertThat(syncIssue.data).isEqualTo("androidx.annotation:annotation:$ANDROIDX_VERSION")
    }

    @Test
    fun `AndroidX dependencies used, AndroidX property enabled, expect success`() {
        TestFileUtils.appendToFile(project.gradlePropertiesFile, "android.useAndroidX=true")

        val model = project.model().ignoreSyncIssues().fetchAndroidProjects()
        assertThat(model.onlyModelSyncIssues).isEmpty()
    }
}
