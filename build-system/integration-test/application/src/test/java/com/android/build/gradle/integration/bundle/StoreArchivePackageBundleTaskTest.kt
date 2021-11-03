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
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.testutils.truth.PathSubject
import com.android.tools.build.bundletool.model.AppBundle
import com.google.common.truth.Truth
import java.util.zip.ZipFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(FilterableParameterized::class)
class StoreArchivePackageBundleTaskTest(
    private val storeArchiveEnabled: Boolean?
) {

    private val app = MinimalSubProject.app("com.example.test")

    companion object {
        @Parameterized.Parameters(name = "storeArchiveEnabled_{0}")
        @JvmStatic
        fun params() =
            listOf(
                arrayOf(true),
                arrayOf(false),
                arrayOfNulls<Boolean?>(1)
            )
    }

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(MultiModuleTestProject.builder().subproject(":app", app).build())
            .create()

    @Test()
    fun testStoreArchiveFlag() {
        storeArchiveEnabled?.let {
            project.getSubproject(":app")
                .buildFile.appendText(
                    "\nandroid.bundle.storeArchive.enable = $it\n"
                )
        }
        project.executor().run(":app:bundleDebug")
        val bundleFile =
            project.model()
                .fetchContainer(AppBundleProjectBuildOutput::class.java)
                .rootBuildModelMap[":app"]
                ?.getOutputByName("debug")
                ?.bundleFile
        PathSubject.assertThat(bundleFile).isNotNull()
        PathSubject.assertThat(bundleFile).exists()
        ZipFile(bundleFile!!).use { zip ->
            val appBundle = AppBundle.buildFromZip(zip)
            if (storeArchiveEnabled == null) {
                Truth.assertThat(appBundle.bundleConfig.optimizations.hasStoreArchive())
                    .isFalse()
            } else {
                Truth.assertThat(appBundle.bundleConfig.optimizations.storeArchive.enabled)
                    .isEqualTo(storeArchiveEnabled)
            }
        }
    }
}
