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

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.testutils.AssumeUtil.assumeWindows
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Tests system dependent behaviors on windows
 */

class WindowsSystemDependencyTest {
    @get:Rule
    val project =
        GradleTestProject.builder().fromTestApp(HelloWorldApp.
            forPlugin("com.android.library")).create()

    // b/132880111
    @Test
    fun noBackslashInBuildElements() {
        assumeWindows()

        project.execute("assembleDebug")

        val buildElementsJson = project.file("build/intermediates/packaged_manifests/debug/${BuiltArtifactsImpl.METADATA_FILE_NAME}")

        assertThat(buildElementsJson).doesNotContain("\\\\")
    }

}
