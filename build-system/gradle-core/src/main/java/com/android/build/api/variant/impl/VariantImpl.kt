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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.UnitTest
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.Component
import com.android.build.api.variant.ExternalNativeBuild
import com.android.build.api.variant.ExternalNdkBuildImpl
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.HasTestFixtures
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.VariantType
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.Serializable

abstract class VariantImpl(
    open val variantBuilder: VariantBuilderImpl,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantScope: VariantScope,
    variantData: BaseVariantData,
    transformManager: TransformManager,
    variantPropertiesApiServices: VariantPropertiesApiServices,
    taskCreationServices: TaskCreationServices,
    sdkComponents: SdkComponents,
    globalScope: GlobalScope
) : ComponentImpl(
    variantBuilder,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    transformManager,
    variantPropertiesApiServices,
    taskCreationServices,
    sdkComponents,
    globalScope
), Variant, ConsumableCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val minSdkVersion: AndroidVersion by lazy {
        variantBuilder.minSdkVersion
    }

    override val targetSdkVersion: AndroidVersion by lazy {
        variantBuilder.targetSdkVersion
    }

    override val maxSdkVersion: Int?
        get() = variantBuilder.maxSdk

    override val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            BuildConfigField::class.java,
            variantDslInfo.getBuildConfigFields()
        )
    }

    // for compatibility with old variant API.
    fun addBuildConfigField(type: String, key: String, value: Serializable, comment: String?) {
        buildConfigFields.put(key, BuildConfigField(type, value, comment))
    }

    override val manifestPlaceholders: MapProperty<String, String> by lazy {
        @Suppress("UNCHECKED_CAST")
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            variantDslInfo.manifestPlaceholders
        )
    }

    override val packaging: Packaging by lazy {
        PackagingImpl(variantDslInfo.packaging, internalServices)
    }


    override val externalNativeBuild: ExternalNativeBuild? by lazy {
        variantDslInfo.nativeBuildSystem?.let { nativeBuildType ->
            when(nativeBuildType) {
                VariantManager.NativeBuiltType.CMAKE ->
                    variantDslInfo.externalNativeBuildOptions.externalNativeCmakeOptions?.let {
                        ExternalCmakeImpl(
                                it,
                                variantPropertiesApiServices
                        )
                    }
                VariantManager.NativeBuiltType.NDK_BUILD ->
                    variantDslInfo.externalNativeBuildOptions.externalNativeNdkBuildOptions?.let {
                        ExternalNdkBuildImpl(
                                it,
                                variantPropertiesApiServices
                        )
                    }
            }
        }
    }

    override fun <T> getExtension(type: Class<T>): T? =
        type.cast(externalExtensions?.get(type))

    override val proguardFiles: ListProperty<RegularFile> =
        variantPropertiesApiServices.listPropertyOf(RegularFile::class.java) {
            variantDslInfo.getProguardFiles(it)
        }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    val testComponents = mutableMapOf<VariantType, ComponentImpl>()
    var testFixturesComponent: ComponentImpl? = null

    val externalExtensions: Map<Class<*>, Any>? by lazy {
        variantBuilder.getRegisteredExtensions()
    }

    override val targetSdkVersionOverride: AndroidVersion?
        get() = variantBuilder.mutableTargetSdk?.sanitize()

    override val resValues: MapProperty<ResValue.Key, ResValue> by lazy {
        internalServices.mapPropertyOf(
            ResValue.Key::class.java,
            ResValue::class.java,
            variantDslInfo.getResValues()
        )
    }

    override fun makeResValueKey(type: String, name: String): ResValue.Key = ResValueKeyImpl(type, name)

    override val renderscriptTargetApi: Int
        get() =  variantBuilder.renderscriptTargetApi

    private var _isMultiDexEnabled: Boolean? = variantDslInfo.isMultiDexEnabled
    override val isMultiDexEnabled: Boolean
        get() {
            return _isMultiDexEnabled ?: (minSdkVersion.getFeatureLevel() >= 21)
        }

    private val isBaseModule = variantDslInfo.variantType.isBaseModule

    override val needsMainDexListForBundle: Boolean
        get() = isBaseModule
                && globalScope.hasDynamicFeatures()
                && dexingType.needsMainDexList

    // TODO: Move down to lower type and remove from VariantScope.
    override val isCoreLibraryDesugaringEnabled: Boolean
        get() = variantScope.isCoreLibraryDesugaringEnabled(this)

    abstract override fun <T : Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
            stats: GradleBuildVariant.Builder?): T

    override var unitTest: UnitTest? = null

    /**
     * adds renderscript sources if present.
     */
    override fun addRenderscriptSources(
            sourceSets: ImmutableList.Builder<DirectoryEntry>,
    ) {
        renderscript?.let {
            if (!it.ndkModeEnabled.get()
                && taskContainer.renderscriptCompileTask != null
            ) {
                sourceSets.add(
                    TaskProviderBasedDirectoryEntryImpl(
                        name = "generated_renderscript",
                        directoryProvider = artifacts.get(InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR),
                    )
                )
            }
        }
    }

    override val pseudoLocalesEnabled: Property<Boolean> =
        internalServices.newPropertyBackingDeprecatedApi(Boolean::class.java, variantDslInfo.isPseudoLocalesEnabled)

    override val experimentalProperties: MapProperty<String, Any> =
            internalServices.mapPropertyOf(
                    String::class.java,
                    Any::class.java,
                    variantDslInfo.experimentalProperties)

    override val nestedComponents: List<Component>
        get() = listOfNotNull(
            unitTest,
            if (this is HasAndroidTest) {
                androidTest
            } else {
                null
            },
            if (this is HasTestFixtures) {
                testFixtures
            } else {
                null
            }
        )
}
