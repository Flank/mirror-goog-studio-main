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

package com.android.build.api.variant.impl

import com.android.build.api.component.ComponentIdentity
import com.android.build.api.variant.DynamicFeatureVariantProperties
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.ModuleMetadata
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.utils.init
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.dexing.DexingType
import com.google.common.collect.ImmutableSet
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class DynamicFeatureVariantPropertiesImpl @Inject constructor(
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
), DynamicFeatureVariantProperties, DynamicFeatureCreationConfig {

    /*
     * Providers of data coming from the base modules. These are loaded just once and finalized.
     */
    private val baseModuleMetadata: Provider<ModuleMetadata> = instantiateBaseModuleMetadata(dslScope, variantDependencies)
    private val featureSetMetadata: Provider<FeatureSetMetadata>  = instantiateFeatureSetMetadata(dslScope, variantDependencies)

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val debuggable: Boolean
        get() = variantDslInfo.isDebuggable

    override val applicationId: Property<String> = dslScope.objectFactory.property(String::class.java)
        .init(baseModuleMetadata.map { it.applicationId })

    override val manifestPlaceholders: Map<String, Any>
        get() = variantDslInfo.manifestPlaceholders

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // always false for this type
    override val embedsMicroApp: Boolean
        get() = false

    override val testOnlyApk: Boolean
        get() = variantScope.isTestOnly

    override val baseModuleDebuggable: Provider<Boolean> = dslScope.objectFactory.property(Boolean::class.java)
        .init(baseModuleMetadata.map { it.debuggable })

    override val baseModuleVersionCode: Provider<Int> = dslScope.objectFactory.property(Int::class.java)
        .init(baseModuleMetadata.map { Integer.parseInt(it.versionCode) })

    override val baseModuleVersionName: Provider<String> = dslScope.objectFactory.property(String::class.java)
        .init(baseModuleMetadata.map { it.versionName ?: "" })

    override val featureName: Provider<String> = dslScope.objectFactory.property(String::class.java)
        .init(featureSetMetadata.map {
            val path = globalScope.project.path
            it.getFeatureNameFor(path)
                ?: throw RuntimeException("Failed to find feature name for $path in ${it.sourceFile}")
        })

    /**
     * resource offset for resource compilation of a feature.
     * This is computed by the base module and consumed by the features. */
    override val resOffset: Provider<Int> = dslScope.objectFactory.property(Int::class.java)
        .init(featureSetMetadata.map {
            val path = globalScope.project.path
            it.getResOffsetFor(path)
                ?: throw RuntimeException("Failed to find resource offset for $path in ${it.sourceFile}")
        })


    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    private fun instantiateBaseModuleMetadata(
        dslScope: DslScope,
        variantDependencies: VariantDependencies
    ): Provider<ModuleMetadata> {
        // Create a property instead of just returning the result of artifact.elements.map
        // because we cannot yet call finalizeValueOnRead on providers
        val property = dslScope.objectFactory.property(ModuleMetadata::class.java)

        val artifact = variantDependencies
            .getArtifactFileCollection(
                ConsumedConfigType.COMPILE_CLASSPATH,
                ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.BASE_MODULE_METADATA
            )

        return property.init(artifact.elements.map {
            ModuleMetadata.load(it.single().asFile)
        })
    }


    private fun instantiateFeatureSetMetadata(
        dslScope: DslScope,
        variantDependencies: VariantDependencies
    ): Provider<FeatureSetMetadata> {
        // Create a property instead of just returning the result of artifact.elements.map
        // because we cannot yet call finalizeValueOnRead on providers
        val property = dslScope.objectFactory.property(FeatureSetMetadata::class.java)

        val artifact = variantDependencies.getArtifactFileCollection(
            ConsumedConfigType.COMPILE_CLASSPATH,
            ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.FEATURE_SET_METADATA
        )

        return property.init(artifact.elements.map {
            FeatureSetMetadata.load(it.single().asFile)
        })
    }
}
