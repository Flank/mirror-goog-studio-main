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
import com.google.common.truth.Truth.assertThat
import com.android.testutils.truth.PathSubject
import org.junit.Rule
import org.junit.Test

class AssetPackTest {

    private val assetPackTestApp = MultiModuleTestProject.builder().apply {
        val app = MinimalSubProject.app("com.example.assetpacktestapp")
            .appendToBuild(
                """android.assetPacks = [
                    |':assetPackOne',
                    |':assetPackTwo',
                    |':assetPackA',
                    |':assetPackAA',
                    |':assetPackA:assetPackAB']""".trimMargin()
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

        val assetPackA = MinimalSubProject.assetPack()
            .appendToBuild(
                """assetPack {
                          |  packName = "assetPackA"
                          |  dynamicDelivery {
                          |    deliveryType = "fast-follow"
                          |  }
                          |}""".trimMargin()
            )
            .withFile("src/main/assets/assetFileA.txt",
                """This is an asset file from asset pack A.""")

        val assetPackAA = MinimalSubProject.assetPack()
            .appendToBuild(
                """assetPack {
                          |  packName = "assetPackAA"
                          |  dynamicDelivery {
                          |    deliveryType = "fast-follow"
                          |  }
                          |}""".trimMargin()
            )
            .withFile("src/main/assets/assetFileAA.txt",
                """This is an asset file from asset pack AA.""")

        val assetPackAB = MinimalSubProject.assetPack()
            .appendToBuild(
                """assetPack {
                          |  packName = "assetPackAB"
                          |  dynamicDelivery {
                          |    deliveryType = "fast-follow"
                          |  }
                          |}""".trimMargin()
            )
            .withFile("src/main/assets/assetFileAB.txt",
                """This is an asset file from asset pack AB.""")

        subproject(":app", app)
        subproject(":assetPackOne", assetPackOne)
        subproject(":assetPackTwo", assetPackTwo)
        subproject(":assetPackA", assetPackA)
        subproject(":assetPackAA", assetPackAA)
        subproject(":assetPackA:assetPackAB", assetPackAB)
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
        PathSubject.assertThat(bundleFile).exists()

        if (bundleFile != null) {
            Zip(bundleFile).use { bundle ->
                val bundleContents = bundle.entries

                assertThat(bundleContents.map {it.toString()}).containsAtLeast(
                    "/assetPackOne/assets/assetFileOne.txt",
                    "/assetPackOne/manifest/AndroidManifest.xml",
                    "/assetPackOne/assets.pb",
                    "/assetPackTwo/assets/assetFileTwo.txt",
                    "/assetPackTwo/manifest/AndroidManifest.xml",
                    "/assetPackTwo/assets.pb",
                    "/assetPackA/assets/assetFileA.txt",
                    "/assetPackA/manifest/AndroidManifest.xml",
                    "/assetPackA/assets.pb",
                    "/assetPackAA/assets/assetFileAA.txt",
                    "/assetPackAA/manifest/AndroidManifest.xml",
                    "/assetPackAA/assets.pb",
                    "/assetPackAB/assets/assetFileAB.txt",
                    "/assetPackAB/manifest/AndroidManifest.xml",
                    "/assetPackAB/assets.pb"
                )
            }
        }
    }
}
