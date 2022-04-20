/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.packaging

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.testutils.truth.ZipFileSubject
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class BundleMetadataTest {
    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.app("com.example.test")
        ).create()

    @Test
    fun bundleContainsAppMetadataTest() {
        val apksPath = generateApks()
        ZipFileSubject.assertThat(apksPath) { apks ->
            apks.nested("splits/base-master.apk") { baseApk ->
                baseApk.contains("META-INF/com/android/build/gradle/app-metadata.properties")
            }
        }
    }

    private fun generateApks(): Path {
        project.executor().run(":makeApkFromBundleForDebug")
        return project.getIntermediateFile(
            "apks_from_bundle",
            "debug",
            "bundle.apks"
        ).toPath()
    }
}

