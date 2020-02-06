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

package com.android.build.gradle.internal.variant

import com.android.build.api.component.ComponentIdentity
import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.variant.impl.DynamicFeatureVariantImpl
import com.android.build.api.variant.impl.DynamicFeatureVariantPropertiesImpl
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.DataBindingOptions
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantApiScope
import com.android.build.gradle.internal.scope.VariantPropertiesApiScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl

internal class DynamicFeatureVariantFactory(
    variantApiScope: VariantApiScope,
    variantPropertiesApiScope: VariantPropertiesApiScope,
    globalScope: GlobalScope
) : AbstractAppVariantFactory<DynamicFeatureVariantImpl, DynamicFeatureVariantPropertiesImpl>(
    variantApiScope,
    variantPropertiesApiScope,
    globalScope
) {

    override fun createVariantObject(
        componentIdentity: ComponentIdentity,
        variantDslInfo: VariantDslInfo
    ): DynamicFeatureVariantImpl {
        return globalScope
            .dslScope
            .objectFactory
            .newInstance(
                DynamicFeatureVariantImpl::class.java,
                variantDslInfo,
                componentIdentity,
                variantApiScope
            )
    }

    override fun createVariantPropertiesObject(
        variant: DynamicFeatureVariantImpl,
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        variantDslInfo: VariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: BuildArtifactsHolder,
        variantScope: VariantScope,
        variantData: BaseVariantData,
        transformManager: TransformManager
    ): DynamicFeatureVariantPropertiesImpl {
        val variantProperties = globalScope
            .dslScope
            .objectFactory
            .newInstance(
                DynamicFeatureVariantPropertiesImpl::class.java,
                componentIdentity,
                buildFeatures,
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                variantScope,
                variantData,
                transformManager,
                variantPropertiesApiScope,
                globalScope
            )

        // create default output
        variantProperties.addVariantOutput(variantData.outputFactory.addMainApk())

        return variantProperties
    }

    override fun createBuildFeatureValues(
        buildFeatures: BuildFeatures,
        projectOptions: ProjectOptions
    ): BuildFeatureValues {
        val features = buildFeatures as? DynamicFeatureBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")

        return BuildFeatureValuesImpl(
            buildFeatures,
            dataBinding = features.dataBinding ?: projectOptions[BooleanOption.BUILD_FEATURE_DATABINDING],
            projectOptions = projectOptions)
    }

    override fun createTestBuildFeatureValues(
        buildFeatures: BuildFeatures,
        dataBindingOptions: DataBindingOptions,
        projectOptions: ProjectOptions
    ): BuildFeatureValues {
        val features = buildFeatures as? DynamicFeatureBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")

        val dataBinding =
            features.dataBinding ?: projectOptions[BooleanOption.BUILD_FEATURE_DATABINDING]

        return BuildFeatureValuesImpl(
            buildFeatures,
            dataBinding = dataBinding && dataBindingOptions.isEnabledForTests,
            projectOptions = projectOptions)
    }

    override fun getVariantType(): VariantType {
        return VariantTypeImpl.OPTIONAL_APK
    }
}