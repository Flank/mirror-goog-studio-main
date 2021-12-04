/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("AndroidProjectUtilsV2")
package com.android.build.gradle.integration.common.utils

import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.dsl.SigningConfig
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import java.io.File

/**
 * Returns a Variant object from a given name
 * @param name the name of the variant to return
 * @return the matching variant or null if not found
 */
fun AndroidProject.findVariantByName(name: String): Variant? {
    for (item in variants) {
        if (name == item.name) {
            return item
        }
    }

    return null
}

fun BasicAndroidProject.getVariantByName(name: String): BasicVariant {
    return searchForExistingItem(variants, name, BasicVariant::name, "Variant")
}

fun AndroidProject.getVariantByName(name: String): Variant {
    return searchForExistingItem(variants, name, Variant::name, "Variant")
}

fun AndroidProject.getDebugVariant() = getVariantByName("debug")

fun AndroidDsl.getSigningConfig(name: String): SigningConfig =
    searchForExistingItem(signingConfigs, name, SigningConfig::name, "SigningConfig")

fun AndroidDsl.getProductFlavor(name: String): ProductFlavor =
    searchForExistingItem(productFlavors, name, { it.name }, "ProductFlavor")

fun AndroidDsl.getBuildType(name: String): BuildType =
    searchForExistingItem(buildTypes, name, { it.name }, "BuildType")

fun ModelContainerV2.ModelInfo.findTestedBuildType(): String? {
    if (androidProject == null || basicAndroidProject == null) return null

    val variantWithTest =
        androidProject.variants.singleOrNull { it.testedTargetVariant != null } ?: return null

    return basicAndroidProject.getVariantByName(variantWithTest.name).buildType
}

fun Variant.getBundleLocation(): File {
    val bundleInfo =
        this.mainArtifact.bundleInfo
            ?: throw RuntimeException("Variant $name does not have BundleInfo in its main artifact model")
    val output =
        BuiltArtifactsLoaderImpl.loadFromFile(bundleInfo.bundleTaskOutputListingFile)
            ?: throw RuntimeException("Failed to load bundleTaskOutputListingFile at ${bundleInfo.bundleTaskOutputListingFile}")
    // for bundle there should be a single output
    val element = output.elements.single()
    return File(element.outputFile)
}
