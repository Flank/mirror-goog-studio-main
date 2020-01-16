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

package com.android.build.api.variant.impl

import com.android.build.api.component.ComponentIdentity
import com.android.build.api.variant.TestVariantProperties
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.utils.init
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.dexing.DexingType
import com.google.common.collect.ImmutableSet
import org.gradle.api.provider.Property
import javax.inject.Inject

internal open class TestVariantPropertiesImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: BuildArtifactsHolder,
    variantScope: VariantScope,
    variantData: BaseVariantData,
    transformManager: TransformManager,
    dslScope: DslScope,
    globalScope: GlobalScope
) : VariantPropertiesImpl(
    componentIdentity,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    transformManager,
    dslScope,
    globalScope
), TestVariantProperties, ApkCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val debuggable: Boolean
        get() = variantDslInfo.isDebuggable

    override val applicationId: Property<String> = dslScope.objectFactory.property(String::class.java)
        .init(dslScope.providerFactory.provider { variantDslInfo.applicationId })

    override val manifestPlaceholders: Map<String, Any>
        get() = variantDslInfo.manifestPlaceholders

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // always false for this type
    override val embedsMicroApp: Boolean
        get() = false

    // always true for this kind
    override val testOnlyApk: Boolean
        get() = true
}