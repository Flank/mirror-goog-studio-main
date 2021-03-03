/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.integration.common.utils

import com.android.builder.model.ProductFlavor
import com.google.common.truth.Truth

class ProductFlavorHelper(private val productFlavor: ProductFlavor, private val name: String) {
    private var applicationId: String? = null
    private var versionCode: Int? = null
    private var versionName: String? = null
    private var minSdkVersion: FakeApiVersion? = null
    private var targetSdkVersion: FakeApiVersion? = null
    private var renderscriptTargetApi: Int? = null
    private var testApplicationId: String? = null
    private var testInstrumentationRunner: String? = null
    private var testHandleProfiling: Boolean? = null
    private var testFunctionalTest: Boolean? = null
    fun setApplicationId(applicationId: String?): ProductFlavorHelper {
        this.applicationId = applicationId
        return this
    }

    fun setVersionCode(versionCode: Int?): ProductFlavorHelper {
        this.versionCode = versionCode
        return this
    }

    fun setVersionName(versionName: String?): ProductFlavorHelper {
        this.versionName = versionName
        return this
    }

    fun setMinSdkVersion(minSdkVersion: Int): ProductFlavorHelper {
        this.minSdkVersion = FakeApiVersion(minSdkVersion)
        return this
    }

    fun setTargetSdkVersion(targetSdkVersion: Int): ProductFlavorHelper {
        this.targetSdkVersion = FakeApiVersion(targetSdkVersion)
        return this
    }

    fun setRenderscriptTargetApi(renderscriptTargetApi: Int?): ProductFlavorHelper {
        this.renderscriptTargetApi = renderscriptTargetApi
        return this
    }

    fun setTestApplicationId(testApplicationId: String?): ProductFlavorHelper {
        this.testApplicationId = testApplicationId
        return this
    }

    fun setTestInstrumentationRunner(testInstrumentationRunner: String?): ProductFlavorHelper {
        this.testInstrumentationRunner = testInstrumentationRunner
        return this
    }

    fun setTestHandleProfiling(testHandleProfiling: Boolean?): ProductFlavorHelper {
        this.testHandleProfiling = testHandleProfiling
        return this
    }

    fun setTestFunctionalTest(testFunctionalTest: Boolean?): ProductFlavorHelper {
        this.testFunctionalTest = testFunctionalTest
        return this
    }

    fun test() {
        Truth.assertWithMessage("$name:VersionCode")
            .that(versionCode)
            .isEqualTo(productFlavor.versionCode)
        Truth.assertWithMessage("$name:VersionName")
            .that(versionName)
            .isEqualTo(productFlavor.versionName)
        Truth.assertWithMessage("$name:minSdkVersion")
            .that(minSdkVersion)
            .isEqualTo(productFlavor.minSdkVersion)
        Truth.assertWithMessage("$name:targetSdkVersion")
            .that(targetSdkVersion)
            .isEqualTo(productFlavor.targetSdkVersion)
        Truth.assertWithMessage("$name:renderscriptTargetApi")
            .that(renderscriptTargetApi)
            .isEqualTo(productFlavor.renderscriptTargetApi)
        Truth.assertWithMessage("$name:testApplicationId")
            .that(testApplicationId)
            .isEqualTo(productFlavor.testApplicationId)
        Truth.assertWithMessage("$name:testInstrumentationRunner")
            .that(testInstrumentationRunner)
            .isEqualTo(productFlavor.testInstrumentationRunner)
        Truth.assertWithMessage("$name:testHandleProfiling")
            .that(testHandleProfiling)
            .isEqualTo(productFlavor.testHandleProfiling)
        Truth.assertWithMessage("$name:testFunctionalTest")
            .that(testFunctionalTest)
            .isEqualTo(productFlavor.testFunctionalTest)
    }
}
