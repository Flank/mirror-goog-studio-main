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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ApplicationIdReset {

    @get:Rule
    var project = GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            |apply plugin: "com.android.application"
            |
            |android {
            |    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
            |    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
            |
            |    defaultConfig {
            |        applicationId "com.flavors.appidtest"
            |    }
            |    flavorDimensions 'build', 'price'
            |    productFlavors {
            |       app1 {
            |          dimension 'build'
            |       }
            |       app2 {
            |            dimension 'build'
            |       }
            |       free {
            |          dimension 'price'
            |       }
            |       paid {
            |          dimension 'price'
            |       }
            |    }
            |
            |    applicationVariants.all { variant ->
            |       def applicationId = "com.flavors"
            |       println(variant.flavorName)
            |       switch (variant.flavorName) {
            |         case "app1Paid":
            |           applicationId += '.app1.paid'
            |           break
            |         case "app1Free":
            |           applicationId += '.app1.free'
            |           break
            |         case "app2Free":
            |           applicationId += '.app2.free'
            |           break 
            |         case "app2Paid":
            |           applicationId += '.app2.paid'
            |           break 
            |       }
            |       variant.mergedFlavor.setApplicationId(applicationId)
            |    }
            |}
            |""".trimMargin("|")
        )
    }

    @Test
    fun checkApplicationIdDebug() {
        val model = project.executeAndReturnModel("assembleApp1FreeDebug")
        val listingFile = model.onlyModel.getVariantByName("app1FreeDebug")
            .mainArtifact.assembleTaskOutputListingFile
        Truth.assertThat(File(listingFile).readText(Charsets.UTF_8)).contains(
            "  \"applicationId\": \"com.flavors.app1.free\""
        )
    }
}