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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AssetPackBundleTest {

    private val packageName = "com.test.assetpack.bundle"
    private val versionTag = "20210319.patch1"
    private val versionCodes = listOf(10, 20, 99034)

    private val assetPackBundleTestApp = MultiModuleTestProject.builder().apply {
        val bundle = MinimalSubProject.assetPackBundle()
            .appendToBuild(
                """
                    bundle {
                      applicationId = '$packageName'
                      compileSdk = ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}

                      versionTag = '$versionTag'
                      versionCodes = $versionCodes
                      assetPacks = [':assetPackOne', ':assetPackTwo']

                      deviceTier {
                        enableSplit = true
                        defaultTier = 'medium'
                      }
                    }
                """.trimIndent()
            )
        subproject(":assetPackBundle", bundle)
    }
        .build()

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(assetPackBundleTestApp)
        .create()

    @get:Rule
    val tmpFile = TemporaryFolder()

    @Test
    fun `should build asset pack bundle successfully`() {
        project.executor().run(":assetPackBundle:bundle")

        assertThat(getResultBundle().toPath()).hasContents(versionTag)
    }

    private fun getResultBundle(): File {
        val buildDir = project.getSubproject("assetPackBundle").buildDir
        return FileUtils.join(buildDir, "outputs", "bundle", "assetPackBundle.aab")
    }
}
