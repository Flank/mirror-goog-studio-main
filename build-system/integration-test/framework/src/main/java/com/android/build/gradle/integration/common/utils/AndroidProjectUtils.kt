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

@file:JvmName("AndroidProjectUtils")
package com.android.build.gradle.integration.common.utils

import com.android.builder.model.AndroidProject
import com.android.builder.model.ArtifactMetaData
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SigningConfig
import com.android.builder.model.Variant
import com.android.builder.model.VariantBuildInformation

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

fun AndroidProject.getVariantByName(name: String): Variant {
    return searchForExistingItem(variants, name, Variant::getName, "Variant")
}

fun AndroidProject.getVariantBuildInformationByName(name: String): VariantBuildInformation {
    return variantsBuildInformation.first { it.variantName == name }
}

fun AndroidProject.getDebugVariant() = getVariantByName("debug")

fun AndroidProject.getArtifactMetaData(name: String): ArtifactMetaData {
    return searchForExistingItem(
            extraArtifacts, name, ArtifactMetaData::getName, "ArtifactMetaData")
}

fun AndroidProject.getProductFlavor(name: String): ProductFlavorContainer {
    return searchForExistingItem(
            productFlavors, name, { it.productFlavor.name }, "ProductFlavorContainer")
}

fun AndroidProject.getBuildType(name: String): BuildTypeContainer {
    return searchForExistingItem(
        buildTypes, name, { it.buildType.name }, "BuildTypeContainer")
}

fun AndroidProject.findTestedBuildType(): String? {
    return variants
            .stream()
            .filter { variant ->
                variant.getOptionalAndroidArtifact(AndroidProject.ARTIFACT_ANDROID_TEST) != null
            }
            .map { it.buildType }
            .findAny()
            .orElse(null)
}
