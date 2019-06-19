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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.testutils.apk.Zip
import com.android.testutils.truth.FileSubject
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppIntegrityConfigTest {

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.app("com.example.test")
                .appendToBuild("android.bundle.integrityConfigDir = file('protection_config')")
                .withFile(
                    "protection_config/IntegrityConfig.xml",
                    "<integrity_config></integrity_config>"
                )
        )
        .withName("integrity-test").create()

    @Test
    fun testBuildBundle() {
        project.execute(":bundleDebug")
        val bundleFile = getBundleFile()
        FileSubject.assertThat(bundleFile).exists()
        ZipFileSubject.assertThatZip(bundleFile)
            .contains("/BUNDLE-METADATA/com.google.play.apps.integrity/AppIntegrityConfig.pb")
    }

    private fun getBundleFile(): File {
        return File(
            project.buildDir,
            FileUtils.join("outputs", "bundle", "debug", "${project.name}-debug.aab")
        )
    }
}
