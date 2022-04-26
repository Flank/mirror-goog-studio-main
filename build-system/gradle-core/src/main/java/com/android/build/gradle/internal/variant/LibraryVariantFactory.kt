/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DataBinding
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.impl.GlobalVariantBuilderConfig
import com.android.build.api.variant.impl.LibraryVariantBuilderImpl
import com.android.build.api.variant.impl.LibraryVariantImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.LibraryVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.TestFixturesBuildFeaturesValuesImpl
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.BuilderConstants
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.errors.IssueReporter
import com.google.common.collect.ImmutableList
import org.gradle.api.Project

class LibraryVariantFactory(
    projectServices: ProjectServices,
) : BaseVariantFactory<LibraryVariantBuilderImpl, LibraryVariantDslInfo, LibraryVariantImpl>(
    projectServices,
) {

    override fun createVariantBuilder(
        globalVariantBuilderConfig: GlobalVariantBuilderConfig,
        componentIdentity: ComponentIdentity,
        variantDslInfo: LibraryVariantDslInfo,
        variantBuilderServices: VariantBuilderServices
    ): LibraryVariantBuilderImpl {
        return projectServices
                .objectFactory
                .newInstance(
                        LibraryVariantBuilderImpl::class.java,
                        globalVariantBuilderConfig,
                        variantDslInfo,
                        componentIdentity,
                        variantBuilderServices)
    }

    override fun createVariant(
        variantBuilder: LibraryVariantBuilderImpl,
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        variantDslInfo: LibraryVariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantScope: VariantScope,
        variantData: BaseVariantData,
        transformManager: TransformManager,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig,
        ): LibraryVariantImpl {
        val libVariant = projectServices
                .objectFactory
                .newInstance(
                        LibraryVariantImpl::class.java,
                        variantBuilder,
                        buildFeatures,
                        variantDslInfo,
                        variantDependencies,
                        variantSources,
                        paths,
                        artifacts,
                        variantScope,
                        variantData,
                        transformManager,
                        variantServices,
                        taskCreationServices,
                        globalConfig,
                )

        // create default output
        val name = "${libVariant.services.projectInfo.getProjectBaseName()}-${libVariant.baseName}.${BuilderConstants.EXT_LIB_ARCHIVE}"
        libVariant.addVariantOutput(
                VariantOutputConfigurationImpl(false, ImmutableList.of()), name)
        return libVariant
    }

    override fun createBuildFeatureValues(
            buildFeatures: BuildFeatures, projectOptions: ProjectOptions): BuildFeatureValues {
        return if (buildFeatures is LibraryBuildFeatures) {
            BuildFeatureValuesImpl(
                    buildFeatures,
                    projectOptions,
                    null /*dataBindingOverride*/,
                    null /*mlModelBindingOverride*/)
        } else {
            throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")
        }
    }

    override fun createTestFixturesBuildFeatureValues(
        buildFeatures: BuildFeatures,
        projectOptions: ProjectOptions,
        androidResourcesEnabled: Boolean
    ): BuildFeatureValues {
        return if (buildFeatures is LibraryBuildFeatures) {
            TestFixturesBuildFeaturesValuesImpl(
                buildFeatures,
                projectOptions,
                androidResourcesEnabled,
                dataBindingOverride = null,
                mlModelBindingOverride = null
            )
        } else {
            throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")
        }
    }

    override fun createTestBuildFeatureValues(
            buildFeatures: BuildFeatures,
            dataBinding: DataBinding,
            projectOptions: ProjectOptions): BuildFeatureValues {
        return BuildFeatureValuesImpl(
                buildFeatures,
                projectOptions,
                null,  /* dataBindingOverride */
                false /* mlModelBindingOverride */)
    }

    override fun createVariantData(
        componentIdentity: ComponentIdentity,
        variantDslInfo: LibraryVariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        services: VariantServices,
        taskContainer: MutableTaskContainer
    ): BaseVariantData {
        return LibraryVariantData(
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                services,
                taskContainer)
    }

    override val variantImplementationClass: Class<out BaseVariantImpl?>
        get() {
            return com.android.build.gradle.internal.api.LibraryVariantImpl::class.java
        }

    override val componentType
        get() = ComponentTypeImpl.LIBRARY

    /** * Prevent customization of applicationId or applicationIdSuffix.  */
    override fun preVariantCallback(
        project: Project,
        dslExtension: CommonExtension<*, *, *, *>,
        model: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
    ) {
        super.preVariantCallback(project, dslExtension, model)
        val issueReporter: IssueReporter = projectServices.issueReporter
        val defaultConfig = model.defaultConfigData.defaultConfig
        if (defaultConfig.applicationId != null) {
            val applicationId = defaultConfig.applicationId!!
            issueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    "Library projects cannot set applicationId. applicationId is set to" +
                            " '$applicationId' in default config.",
                    applicationId)
        }
        if (defaultConfig.applicationIdSuffix != null) {
            val applicationIdSuffix = defaultConfig.applicationIdSuffix!!
            issueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    "Library projects cannot set applicationIdSuffix. applicationIdSuffix is " +
                            "set to '$applicationIdSuffix' in default config.",
                    applicationIdSuffix)
        }
        for (buildType in model.buildTypes.values) {
            if (buildType.buildType.applicationIdSuffix != null) {
                val applicationIdSuffix = buildType.buildType.applicationIdSuffix!!
                issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "Library projects cannot set applicationIdSuffix. applicationIdSuffix " +
                                "is set to '$applicationIdSuffix' in build type " +
                                "'${buildType.buildType.name}'.",
                        applicationIdSuffix)
            }
        }
        for (productFlavor in model.productFlavors.values) {
            if (productFlavor.productFlavor.applicationId != null) {
                val applicationId = productFlavor.productFlavor.applicationId!!
                issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "Library projects cannot set applicationId. applicationId is set to " +
                                "'$applicationId' in flavor '${productFlavor.productFlavor.name}'.",
                        applicationId)
            }
            if (productFlavor.productFlavor.applicationIdSuffix != null) {
                val applicationIdSuffix = productFlavor.productFlavor.applicationIdSuffix!!
                issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "Library projects cannot set applicationIdSuffix. applicationIdSuffix " +
                                "is set to '$applicationIdSuffix' in flavor" +
                                " '${productFlavor.productFlavor.name}'.",
                        applicationIdSuffix)
            }
        }
    }

    override fun createDefaultComponents(
            dslContainers: DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        val signingConfig = dslContainers.signingConfigContainer.create(BuilderConstants.DEBUG)
        dslContainers.buildTypeContainer.create(BuilderConstants.DEBUG)
        dslContainers.buildTypeContainer.create(BuilderConstants.RELEASE)
        dslContainers.defaultConfig.signingConfig = signingConfig
    }
}
