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

package com.android.build.gradle.internal.core

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.TestAndroidConfig
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.ManifestAttributeSupplier
import com.android.builder.core.VariantType
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProvider
import java.util.function.BooleanSupplier

/** Interface for building the [GradleVariantConfiguration] instances.  */
interface VariantBuilder {

    companion object {
        /** Depending on the extension, gets appropriate variant configuration builder  */
        @JvmStatic
        fun getBuilderForExtension(extension: BaseExtension): VariantBuilder {
            return if (extension is TestAndroidConfig) { // if this is the test module
                TestModuleConfigurationBuilder()
            } else { // if this is non-test variant
                VariantConfigurationBuilder()
            }
        }
    }

    /** Creates a variant configuration  */
    fun create(
        projectOptions: ProjectOptions,
        defaultConfig: DefaultConfig,
        defaultSourceProvider: SourceProvider,
        mainManifestAttributeSupplier: ManifestAttributeSupplier?,
        buildType: BuildType,
        buildTypeSourceProvider: SourceProvider?,
        type: VariantType,
        signingConfigOverride: SigningConfig?,
        issueReporter: EvalIssueReporter,
        isInExecutionPhase: BooleanSupplier
    ): GradleVariantConfiguration
}

/** Builder for non-testing variant configurations  */
private class VariantConfigurationBuilder :
    VariantBuilder {
    override fun create(
        projectOptions: ProjectOptions,
        defaultConfig: DefaultConfig,
        defaultSourceProvider: SourceProvider,
        mainManifestAttributeSupplier: ManifestAttributeSupplier?,
        buildType: BuildType,
        buildTypeSourceProvider: SourceProvider?,
        type: VariantType,
        signingConfigOverride: SigningConfig?,
        issueReporter: EvalIssueReporter,
        isInExecutionPhase: BooleanSupplier
    ): GradleVariantConfiguration {
        return GradleVariantConfiguration(
            projectOptions,
            null /*testedConfig*/,
            defaultConfig,
            defaultSourceProvider,
            mainManifestAttributeSupplier,
            buildType,
            buildTypeSourceProvider,
            type,
            signingConfigOverride,
            issueReporter,
            isInExecutionPhase
        )
    }
}

/**
 * Creates a [GradleVariantConfiguration] for a testing module variant.
 *
 *
 * The difference from the regular modules is how the original application id,
 * and application id are resolved. Our build process supports the absence of manifest
 * file for these modules, and that is why the value resolution for these attributes
 * is different.
 */
private class TestModuleConfigurationBuilder :
    VariantBuilder {
    override fun create(
        projectOptions: ProjectOptions,
        defaultConfig: DefaultConfig,
        defaultSourceProvider: SourceProvider,
        mainManifestAttributeSupplier: ManifestAttributeSupplier?,
        buildType: BuildType,
        buildTypeSourceProvider: SourceProvider?,
        type: VariantType,
        signingConfigOverride: SigningConfig?,
        issueReporter: EvalIssueReporter,
        isInExecutionPhase: BooleanSupplier
    ): GradleVariantConfiguration {
        return object : GradleVariantConfiguration(
            projectOptions,
            null /*testedConfig*/,
            defaultConfig,
            defaultSourceProvider,
            mainManifestAttributeSupplier,
            buildType,
            buildTypeSourceProvider,
            type,
            signingConfigOverride,
            issueReporter,
            isInExecutionPhase
        ) {
            override val applicationId: String
                get() {
                    val applicationId = mergedFlavor.testApplicationId
                    if (applicationId != null && applicationId.isNotEmpty()) {
                        return applicationId
                    }

                    return super.applicationId
                }

            override val originalApplicationId: String
                get() = applicationId

            override val testApplicationId: String
                get() = applicationId

            override fun getMyTestConfig(
                defaultSourceProvider: SourceProvider,
                mainManifestAttributeSupplier: ManifestAttributeSupplier?,
                buildTypeSourceProvider: SourceProvider?,
                type: VariantType,
                isInExecutionPhase: BooleanSupplier
            ): GradleVariantConfiguration? {
                throw UnsupportedOperationException("Test modules have no test variants.")
            }
        }
    }
}
