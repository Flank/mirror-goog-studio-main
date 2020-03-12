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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.api.variant.impl.VariantOperations
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import com.android.repository.Revision
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import java.util.function.Supplier

/** Internal implementation of the 'new' DSL interface */
abstract class CommonExtensionImpl<
        AnnotationProcessorOptionsT : com.android.build.api.dsl.AnnotationProcessorOptions,
        BuildFeaturesT : BuildFeatures,
        BuildTypeT : com.android.build.api.dsl.BuildType<AnnotationProcessorOptionsT, SigningConfigT>,
        DefaultConfigT : DefaultConfig<AnnotationProcessorOptionsT>,
        ProductFlavorT : com.android.build.api.dsl.ProductFlavor<AnnotationProcessorOptionsT>,
        SigningConfigT : com.android.build.api.dsl.SigningConfig,
        VariantT : Variant<VariantPropertiesT>,
        VariantPropertiesT : VariantProperties>(
    protected val dslServices: DslServices,
    dslContainers: DslContainerProvider<DefaultConfigT, BuildTypeT, ProductFlavorT, SigningConfigT>
) : CommonExtension<
        AaptOptions,
        AbiSplitOptions,
        AdbOptions,
        AndroidSourceSet,
        AnnotationProcessorOptionsT,
        BuildFeaturesT,
        BuildTypeT,
        CmakeOptions,
        CompileOptions,
        DataBindingOptions,
        DefaultConfigT,
        DensitySplitOptions,
        ExternalNativeBuild,
        JacocoOptions,
        LintOptions,
        NdkBuildOptions,
        PackagingOptions,
        ProductFlavorT,
        SigningConfigT,
        Splits,
        TestOptions,
        TestOptions.UnitTestOptions,
        VariantT,
        VariantPropertiesT>, ActionableVariantObjectOperationsExecutor<VariantT, VariantPropertiesT> {

    private val sourceSetManager = dslContainers.sourceSetManager

    private var buildToolsRevision: Revision = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION

    // This is exposed only to support AndroidConfig.libraryRequests
    // TODO: Make private when AndroidConfig is removed
    val libraryRequests: MutableList<LibraryRequest> = mutableListOf()

    override val buildTypes: NamedDomainObjectContainer<BuildTypeT> =
        dslContainers.buildTypeContainer

    override val defaultConfig: DefaultConfigT = dslContainers.defaultConfig

    override val productFlavors: NamedDomainObjectContainer<ProductFlavorT> =
        dslContainers.productFlavorContainer

    override val signingConfigs: NamedDomainObjectContainer<SigningConfigT> =
        dslContainers.signingConfigContainer

    override val aaptOptions: AaptOptions =
        dslServices.newInstance(
            AaptOptions::class.java,
            dslServices.projectOptions[BooleanOption.ENABLE_RESOURCE_NAMESPACING_DEFAULT]
        )

    override fun aaptOptions(action: AaptOptions.() -> Unit) {
        action.invoke(aaptOptions)
    }

    override val adbOptions: AdbOptions = dslServices.newInstance(AdbOptions::class.java)

    override fun adbOptions(action: AdbOptions.() -> Unit) {
        action.invoke(adbOptions)
    }

    fun buildFeatures(action: Action<BuildFeaturesT>) {
        action.execute(buildFeatures)
    }

    override fun buildFeatures(action: BuildFeaturesT.() -> Unit) {
        action(buildFeatures)
    }

    protected val variantOperations = VariantOperations<VariantT>()
    protected val variantPropertiesOperations = VariantOperations<VariantPropertiesT>()

    override val compileOptions: CompileOptions = dslServices.newInstance(CompileOptions::class.java)

    override fun compileOptions(action: CompileOptions.() -> Unit) {
        action.invoke(compileOptions)
    }

    override var compileSdkVersion: String? by dslServices.newVar(null)

    override fun compileSdkVersion(version: String) {
        this.compileSdkVersion = version
    }

    override fun compileSdkVersion(apiLevel: Int) {
        compileSdkVersion("android-$apiLevel")
    }

    override val composeOptions: ComposeOptions =
        dslServices.newInstance(ComposeOptionsImpl::class.java)

    override fun composeOptions(action: ComposeOptions.() -> Unit) {
        action.invoke(composeOptions)
    }

    override fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildTypeT>>) {
        action.execute(buildTypes)
    }

    override val dataBinding: DataBindingOptions =
        dslServices.newInstance(
            DataBindingOptions::class.java,
            Supplier { buildFeatures },
            dslServices
        )

    override fun dataBinding(action: DataBindingOptions.() -> Unit) {
        action.invoke(dataBinding)
    }

    override fun defaultConfig(action: Action<DefaultConfigT>) {
        action.execute(defaultConfig)
    }

    override val externalNativeBuild: ExternalNativeBuild =
        dslServices.newInstance(ExternalNativeBuild::class.java, dslServices)

    override fun externalNativeBuild(action: (ExternalNativeBuild) -> Unit) {
        action.invoke(externalNativeBuild)
    }

    override val jacoco: JacocoOptions = dslServices.newInstance(JacocoOptions::class.java)

    override fun jacoco(action: JacocoOptions.() -> Unit) {
        action.invoke(jacoco)
    }

    override val lintOptions: LintOptions = dslServices.newInstance(LintOptions::class.java)

    override fun lintOptions(action: LintOptions.() -> Unit) {
        action.invoke(lintOptions)
    }

    override val packagingOptions: PackagingOptions =
        dslServices.newInstance(PackagingOptions::class.java)

    override fun packagingOptions(action: PackagingOptions.() -> Unit) {
        action.invoke(packagingOptions)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavorT>>) {
        action.execute(productFlavors)
    }

    override fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfigT>>) {
        action.execute(signingConfigs)
    }

    override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>
        get() = sourceSetManager.sourceSetsContainer

    override fun sourceSets(action: NamedDomainObjectContainer<AndroidSourceSet>.() -> Unit) {
        sourceSetManager.executeAction(action)
    }

    override val splits: Splits =
        dslServices.newInstance(Splits::class.java, dslServices)

    override fun splits(action: Splits.() -> Unit) {
        action.invoke(splits)
    }

    override val testOptions: TestOptions =
        dslServices.newInstance(TestOptions::class.java, dslServices)

    override fun testOptions(action: TestOptions.() -> Unit) {
        action.invoke(testOptions)
    }

    override fun onVariants(action: Action<VariantT>) {
        variantOperations.actions.add(action)
    }

    override fun onVariants(action: VariantT.() -> Unit) {
        variantOperations.actions.add(Action { action.invoke(it) } )
    }

    override fun onVariantProperties(action: Action<VariantPropertiesT>) {
        variantPropertiesOperations.actions.add(action)
    }

    override fun onVariantProperties(action: (VariantPropertiesT) -> Unit) {
        variantPropertiesOperations.actions.add(Action { action.invoke(it) })
    }

    override fun executeVariantOperations(variant: VariantT) {
        variantOperations.executeActions(variant)
    }

    override fun executeVariantPropertiesOperations(variant: VariantPropertiesT) {
        variantPropertiesOperations.executeActions(variant)
    }

    override var flavorDimensions: MutableList<String> = mutableListOf()

    override var resourcePrefix: String? = null

    override var ndkVersion: String? = null

    override var buildToolsVersion: String
        get() = buildToolsRevision.toString()
        set(version) {
            //The underlying Revision class has the maven artifact semantic,
            // so 20 is not the same as 20.0. For the build tools revision this
            // is not the desired behavior, so normalize e.g. to 20.0.0.
            buildToolsRevision = Revision.parseRevision(version, Revision.Precision.MICRO)
        }

    override fun useLibrary(name: String) {
        useLibrary(name, true)
    }

    override fun useLibrary(name: String, required: Boolean) {
        libraryRequests.add(LibraryRequest(name, required))
    }
}
