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

package com.android.build.api.component.analytics

import com.android.build.api.artifact.Artifacts
import com.android.build.api.component.ComponentProperties
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant

abstract class AnalyticsEnabledComponentProperties(
    open val delegate: ComponentProperties,
    val stats: GradleBuildVariant.Builder
) : ComponentProperties {
    override val artifacts: Artifacts
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ARTIFACTS_VALUE
            return delegate.artifacts
        }

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ASM_TRANSFORM_CLASSES_VALUE
        delegate.transformClassesWith(
            classVisitorFactoryImplClass,
            scope,
            instrumentationParamsConfig)
    }

    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ASM_FRAMES_COMPUTATION_NODE_VALUE
        delegate.setAsmFramesComputationMode(mode)
    }

    override val buildType: String?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.BUILD_TYPE_VALUE
            return delegate.buildType
        }

    override val productFlavors: List<Pair<String, String>>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PRODUCT_FLAVORS_VALUE
            return delegate.productFlavors
        }

    override val flavorName: String
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.FLAVOR_NAME_VALUE
            return delegate.flavorName
        }

    override fun getName(): String = delegate.name
}
