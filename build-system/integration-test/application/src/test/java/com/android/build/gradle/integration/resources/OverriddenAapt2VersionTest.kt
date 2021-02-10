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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.StringOption
import org.junit.Rule
import org.junit.Test

class OverriddenAapt2VersionTest {

    @get:Rule
    val project =
            GradleTestProject
                    .builder()
                    .fromTestApp(MinimalSubProject.app("com.example.app"))
                    .create()

    @Test
    fun testOptions() {
        project.executor().run("assembleDebug")

        var error = project
                .executor()
                .with(StringOption.AAPT2_FROM_MAVEN_OVERRIDE, "/incorrect/path")
                .expectFailure()
                .run("assembleDebug")
        error.stderr.use {
            ScannerSubject.assertThat(it)
                    .contains("Custom AAPT2 location does not point to an AAPT2 executable: /incorrect/path")
        }

        error = project
                .executor()
                .with(StringOption.AAPT2_FROM_MAVEN_VERSION_OVERRIDE, "0.0.0-123456")
                .expectFailure()
                .run("assembleDebug")
        error.stderr.use {
            ScannerSubject.assertThat(it)
                    .contains("Could not find com.android.tools.build:aapt2:0.0.0-123456")
        }

        error = project
                .executor()
                .with(StringOption.AAPT2_FROM_MAVEN_OVERRIDE, "/incorrect/path")
                .with(StringOption.AAPT2_FROM_MAVEN_VERSION_OVERRIDE, "0.0.0-123456")
                .expectFailure()
                .run("assembleDebug")
        error.stderr.use {
            ScannerSubject.assertThat(it)
                    .contains("You cannot specify both local and remote custom versions of AAPT2")
        }

        // Flag with no value should result in default behaviour (no overrides specified).
        project.executor().with(StringOption.AAPT2_FROM_MAVEN_OVERRIDE, "").run("assembleDebug")
    }
}
