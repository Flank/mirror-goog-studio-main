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
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

class AssetPackTest {

    private val assetPackTestApp = MultiModuleTestProject.builder().apply {
        val app = MinimalSubProject.app("com.example.assetpacktestapp")
            .appendToBuild(
                """android.assetPacks = [':assetPackOne', ':assetPackTwo']"""
            )

        val assetPackOne = MinimalSubProject.assetPack()
            .appendToBuild(
                """assetPack {
                          |  packName = "assetPackOne"
                          |  dynamicDelivery {
                          |    deliveryType = "fast-follow"
                          |    instantDeliveryType = "on-demand"
                          |  }
                          |}""".trimMargin()
            )
            .withFile("src/main/assets/assetFileOne.txt",
                """This is an asset file from asset pack one.""")

        val assetPackTwo = MinimalSubProject.assetPack()
            .appendToBuild(
                """assetPack {
                          |  packName = "assetPackTwo"
                          |  dynamicDelivery {
                          |    deliveryType = "fast-follow"
                          |  }
                          |}""".trimMargin()
            )
            .withFile("src/main/assets/assetFileTwo.txt",
                """This is an asset file from asset pack two.""")

        subproject(":app", app)
        subproject(":assetPackOne", assetPackOne)
        subproject(":assetPackTwo", assetPackTwo)
    }
        .build()

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(assetPackTestApp)
        .create()

    @Test
    fun buildDebugBundle() {
        project.executor().run(":app:bundleDebug")

        val outputAppModel = project.model().fetchContainer(AppBundleProjectBuildOutput::class.java).rootBuildModelMap[":app"]

        val bundleFile = outputAppModel?.getOutputByName("debug")?.bundleFile
        assertThat(bundleFile).exists()

        if (bundleFile != null) {
            Zip(bundleFile).use { bundle ->
                val bundleContents = bundle.entries
                assert(bundleContents.any {
                    it.toString().endsWith("assetPackOne/assets/assetFileOne.txt")
                })
                assert(bundleContents.any {
                    it.toString().endsWith("assetPackOne/manifest/AndroidManifest.xml")
                })
                assert(bundleContents.any {
                    it.toString().endsWith("assetPackOne/assets.pb")
                })
                assert(bundleContents.any {
                    it.toString().endsWith("assetPackTwo/assets/assetFileTwo.txt")
                })
                assert(bundleContents.any {
                    it.toString().endsWith("assetPackTwo/manifest/AndroidManifest.xml")
                })
                assert(bundleContents.any {
                    it.toString().endsWith("assetPackTwo/assets.pb")
                })
            }
        }
    }
}
