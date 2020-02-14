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

@file:JvmName("ProjectBuildOutputUtils")
package com.android.build.gradle.integration.common.utils

import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.builder.core.BuilderConstants
import com.android.builder.model.AndroidProject
import com.android.builder.model.ProjectBuildOutput
import com.android.builder.model.VariantBuildInformation
import com.android.builder.model.VariantBuildOutput
import com.google.common.collect.Iterables
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Assert.fail
import java.io.File
import java.lang.RuntimeException

/**
 * Returns the APK file for a single-output variant.
 *
 * @param variantName the name of the variant to return
 * @return the output file, always, or assert before.
 */
fun AndroidProject.findOutputFileByVariantName(variantName: String): File {

    val variantOutput = getVariantBuildInformationByName(variantName)
    Assert.assertNotNull("variant '$variantName' null-check", variantOutput)

    val variantOutputFiles = variantOutput.getOutputFiles()
    Assert.assertNotNull("variantName '$variantName' outputs null-check", variantOutputFiles)
    // we only support single output artifact in this helper method.
    Assert.assertEquals(
            "variantName '$variantName' outputs size check",
            1,
            variantOutputFiles.size.toLong())

    val output = variantOutput.getSingleOutputFile()
    Assert.assertNotNull(
            "variantName '$variantName' single output null-check",
            output)

    return File(output)
}

fun VariantBuildInformation.getBuiltArtifacts() =
    (BuiltArtifactsLoaderImpl.loadFromFile(File(assembleTaskOutputListingFile!!))
        ?: throw RuntimeException("Cannot load built artifacts from $assembleTaskOutputListingFile"))

fun VariantBuildInformation.getOutputFiles() =
    getBuiltArtifacts()
        .elements
        .map(BuiltArtifactImpl::outputFile)

fun VariantBuildInformation.getSingleOutputFile() =
    getOutputFiles().single()

/**
 * Convenience method to verify that the given ProjectBuildOutput contains exactly two variants,
 * then return the "debug" variant. This is most useful for integration tests building projects
 * with no extra buildTypes and no specified productFlavors.
 *
 * @return the build output for the "debug" variant
 * @throws AssertionError if the model contains more than two variants, or does not have a
 * "debug" variant
 */
fun AndroidProject.getDebugVariantBuildOutput(): VariantBuildInformation {
    TruthHelper.assertThat(variantsBuildInformation).hasSize(2)
    val debugVariantOutput = getVariantBuildInformationByName(BuilderConstants.DEBUG)
    TruthHelper.assertThat(debugVariantOutput).isNotNull()
    return debugVariantOutput
}

/**
 * Gets the VariantBuildOutput with the given name.
 *
 * @param name the name to match, e.g. [com.android.builder.core.BuilderConstants.DEBUG]
 * @return the only item with the given name
 * @throws AssertionError if no items match or if multiple items match
 */
fun AndroidProject.getVariantBuildInformation(name: String): VariantBuildInformation {
    return searchForExistingItem(
            this.variantsBuildInformation, name, VariantBuildInformation::variantName, "VariantBuildInformation")
}
