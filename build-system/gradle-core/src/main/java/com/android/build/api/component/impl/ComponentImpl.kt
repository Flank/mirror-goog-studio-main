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

package com.android.build.api.component.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.component.impl.features.AndroidResourcesCreationConfigImpl
import com.android.build.api.component.impl.features.AssetsCreationConfigImpl
import com.android.build.api.component.impl.features.ResValuesCreationConfigImpl
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.DirectoryEntry
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.SourcesImpl
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.api.variant.impl.baseName
import com.android.build.api.variant.impl.fullName
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.AssetsCreationConfig
import com.android.build.gradle.internal.component.features.ResValuesCreationConfig
import com.android.build.gradle.internal.core.ProductFlavor
import com.android.build.gradle.internal.core.VariantDslInfoImpl
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.PublishableVariantDslInfo
import com.android.build.gradle.internal.dependency.AsmClassesTransform
import com.android.build.gradle.internal.dependency.RecalculateStackFramesTransform
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.InstrumentationImpl
import com.android.build.gradle.internal.instrumentation.ASM_API_VERSION_FOR_INSTRUMENTATION
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.scope.BuildArtifactSpec.Companion.get
import com.android.build.gradle.internal.scope.BuildArtifactSpec.Companion.has
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.*
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.databinding.DataBindingCompilerArguments
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.compiling.BuildConfigType
import com.android.builder.core.ComponentType
import com.android.utils.appendCapitalized
import com.google.common.collect.ImmutableMap
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

abstract class ComponentImpl<DslInfoT: ComponentDslInfo>(
    open val componentIdentity: ComponentIdentity,
    final override val buildFeatures: BuildFeatureValues,
    protected val dslInfo: DslInfoT,
    final override val variantDependencies: VariantDependencies,
    override val variantSources: VariantSources,
    override val paths: VariantPathHelper,
    override val artifacts: ArtifactsImpl,
    override val variantScope: VariantScope,
    override val variantData: BaseVariantData,
    override val transformManager: TransformManager,
    protected val internalServices: VariantServices,
    final override val services: TaskCreationServices,
    final override val global: GlobalTaskCreationConfig,
): Component, ComponentCreationConfig, ComponentIdentity by componentIdentity {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------
    override val namespace: Provider<String> =
        internalServices.providerOf(
            type = String::class.java,
            value = dslInfo.namespace
        )

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        instrumentation.transformClassesWith(
            classVisitorFactoryImplClass,
            scope,
            instrumentationParamsConfig
        )
    }

    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
        instrumentation.setAsmFramesComputationMode(mode)
    }

    override val javaCompilation: JavaCompilation =
        JavaCompilationImpl(
            dslInfo.javaCompileOptions,
            buildFeatures.dataBinding,
            internalServices)

    override val sources: SourcesImpl by lazy {
        SourcesImpl(
            DefaultSourcesProviderImpl(this, variantSources),
            internalServices.projectInfo.projectDirectory,
            internalServices,
            variantSources.variantSourceProvider,
        ).also { sourcesImpl ->
            // add all source sets extra directories added by the user
            variantSources.customSourceList.forEach{ (_, srcEntries) ->
                srcEntries.forEach { customSourceDirectory ->
                    sourcesImpl.extras.maybeCreate(customSourceDirectory.sourceTypeName).also {
                        (it as FlatSourceDirectoriesImpl).addSource(
                                FileBasedDirectoryEntryImpl(
                                    customSourceDirectory.sourceTypeName,
                                    customSourceDirectory.directory,
                                )
                            )
                    }
                }
            }
        }
    }

    override val instrumentation = InstrumentationImpl(
        services,
        internalServices,
        isLibraryVariant = false
    )

    override val compileClasspath: FileCollection by lazy {
        getJavaClasspath(
            ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactType.CLASSES_JAR,
            generatedBytecodeKey = null
        )
    }

    override val compileConfiguration = variantDependencies.compileClasspath

    override val runtimeConfiguration = variantDependencies.runtimeClasspath

    override val annotationProcessorConfiguration =
        variantDependencies.annotationProcessorConfiguration

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val asmApiVersion = ASM_API_VERSION_FOR_INSTRUMENTATION

    // this is technically a public API for the Application Variant (only)
    override val outputs: VariantOutputList
        get() = VariantOutputList(variantOutputs.toList())

    // Move as direct delegates
    override val taskContainer = variantData.taskContainer

    override val componentType: ComponentType
        get() = dslInfo.componentType

    override val dirName: String
        get() = paths.dirName

    override val baseName: String
        get() = paths.baseName

    override val description: String
        get() = variantData.description

    override val productFlavorList: List<ProductFlavor> = dslInfo.productFlavorList.map {
        ProductFlavor(it)
    }

    override val registeredProjectClassesVisitors: List<AsmClassVisitorFactory<*>>
        get() = instrumentation.registeredProjectClassesVisitors

    override val registeredDependenciesClassesVisitors: List<AsmClassVisitorFactory<*>>
        get() = instrumentation.registeredDependenciesClassesVisitors

    override val asmFramesComputationMode: FramesComputationMode
        get() = instrumentation.finalAsmFramesComputationMode

    override val allProjectClassesPostAsmInstrumentation: FileCollection
        get() =
            if (projectClassesAreInstrumented) {
                if (asmFramesComputationMode == FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES) {
                    services.fileCollection(
                            artifacts.get(
                                    FIXED_STACK_FRAMES_ASM_INSTRUMENTED_PROJECT_CLASSES
                            ),
                            services.fileCollection(
                                    artifacts.get(
                                            FIXED_STACK_FRAMES_ASM_INSTRUMENTED_PROJECT_JARS
                                    )
                            ).asFileTree
                    )
                } else {
                    services.fileCollection(
                            artifacts.get(ASM_INSTRUMENTED_PROJECT_CLASSES),
                            services.fileCollection(
                                    artifacts.get(ASM_INSTRUMENTED_PROJECT_JARS)
                            ).asFileTree
                    )
                }
            } else {
                artifacts.getAllClasses()
            }

    override val projectClassesAreInstrumented: Boolean
        get() = registeredProjectClassesVisitors.isNotEmpty() ||
                (this is ApkCreationConfig && advancedProfilingTransforms.isNotEmpty())

    override val dependenciesClassesAreInstrumented: Boolean
        get() = registeredDependenciesClassesVisitors.isNotEmpty() ||
                (this is ApkCreationConfig && advancedProfilingTransforms.isNotEmpty())

    // by default, we delegate to the build features flags.
    override val buildConfigEnabled: Boolean
        get() = buildFeatures.buildConfig

    override val manifestPlaceholders: MapProperty<String, String> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            dslInfo.manifestPlaceholders
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    private val variantOutputs = mutableListOf<VariantOutputImpl>()

    override fun addVariantOutput(
        variantOutputConfiguration: VariantOutputConfiguration,
        outputFileName: String?
    ) {
        variantOutputs.add(
            VariantOutputImpl(
                createVersionCodeProperty(),
                createVersionNameProperty(),
                internalServices.newPropertyBackingDeprecatedApi(Boolean::class.java, true),
                variantOutputConfiguration,
                variantOutputConfiguration.baseName(this),
                variantOutputConfiguration.fullName(this),
                internalServices.newPropertyBackingDeprecatedApi(
                    String::class.java,
                    outputFileName
                        ?: paths.getOutputFileName(
                            internalServices.projectInfo.getProjectBaseName(),
                            variantOutputConfiguration.baseName(this)
                        ),
                )
            )
        )
    }

    // default impl for variants that don't actually have versionName
    protected open fun createVersionNameProperty(): Property<String?> {
        val stringValue: String? = null
        return internalServices.nullablePropertyOf(String::class.java, stringValue).also {
            it.disallowChanges()
        }
    }

    // default impl for variants that don't actually have versionCode
    protected open fun createVersionCodeProperty() : Property<Int?> {
        val intValue: Int? = null
        return internalServices.nullablePropertyOf(Int::class.java, intValue).also {
            it.disallowChanges()
        }
    }

    override fun computeTaskName(prefix: String): String =
        prefix.appendCapitalized(name)

    override fun computeTaskName(prefix: String, suffix: String): String =
        prefix.appendCapitalized(name, suffix)

    // -------------------------
    // File location computation. Previously located in VariantScope, these are here
    // temporarily until we fully move away from them.

    // Precomputed file paths.
    final override fun getJavaClasspath(
        configType: ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any?
    ): FileCollection {
        var mainCollection = variantDependencies
            .getArtifactFileCollection(configType, ArtifactScope.ALL, classesType)
        mainCollection = mainCollection.plus(variantData.getGeneratedBytecode(generatedBytecodeKey))
        // Add R class jars to the front of the classpath as libraries might also export
        // compile-only classes. This behavior is verified in CompileRClassFlowTest
        // While relying on this order seems brittle, it avoids doubling the number of
        // files on the compilation classpath by exporting the R class separately or
        // and is much simpler than having two different outputs from each library, with
        // and without the R class, as AGP publishing code assumes there is exactly one
        // artifact for each publication.
        mainCollection =
            internalServices.fileCollection(
                *listOfNotNull(
                    androidResourcesCreationConfig?.getCompiledRClasses(configType),
                    getCompiledBuildConfig(),
                    getCompiledManifest(),
                    mainCollection
                ).toTypedArray()
            )
        return mainCollection
    }

    // TODO Move these outside of Variant specific class (maybe GlobalTaskScope?)

    override val manifestArtifactType: InternalArtifactType<Directory>
        get() = if (internalServices.projectOptions[BooleanOption.IDE_DEPLOY_AS_INSTANT_APP])
            INSTANT_APP_MANIFEST
        else
            PACKAGED_MANIFESTS

    /** Publish intermediate artifacts in the BuildArtifactsHolder based on PublishingSpecs.  */
    override fun publishBuildArtifacts() {
        for (outputSpec in variantScope.publishingSpec.outputs) {
            val buildArtifactType = outputSpec.outputType
            // Gradle only support publishing single file.  Therefore, unless Gradle starts
            // supporting publishing multiple files, PublishingSpecs should not contain any
            // OutputSpec with an appendable ArtifactType.
            if (has(buildArtifactType) && get(buildArtifactType).appendable) {
                throw RuntimeException(
                    "Appendable ArtifactType '${buildArtifactType.name()}' cannot be published."
                )
            }
            val artifactProvider = artifacts.get(buildArtifactType)
            val artifactContainer = artifacts.getArtifactContainer(buildArtifactType)
            if (!artifactContainer.needInitialProducer().get()) {
                val isPublicationConfigs =
                    outputSpec.publishedConfigTypes.any { it.isPublicationConfig }

                if (isPublicationConfigs) {
                    val components = (dslInfo as PublishableVariantDslInfo).publishInfo!!.components
                    for(component in components) {
                        variantScope
                            .publishIntermediateArtifact(
                                artifactProvider,
                                outputSpec.artifactType,
                                outputSpec.publishedConfigTypes.map {
                                    PublishedConfigSpec(it, component) }.toSet(),
                                outputSpec.libraryElements?.let {
                                    internalServices.named(LibraryElements::class.java, it)
                                },
                                componentType.isTestFixturesComponent
                            )
                    }
                } else {
                    variantScope
                        .publishIntermediateArtifact(
                            artifactProvider,
                            outputSpec.artifactType,
                            outputSpec.publishedConfigTypes.map { PublishedConfigSpec(it) }.toSet(),
                            outputSpec.libraryElements?.let {
                                internalServices.named(LibraryElements::class.java, it)
                            },
                            componentType.isTestFixturesComponent
                        )
                }
            }
        }
    }

    /**
     * adds databinding sources to the list of sources.
     */
    override fun addDataBindingSources(
        sourceSets: MutableList<DirectoryEntry>
    ) {
        sourceSets.add(
            TaskProviderBasedDirectoryEntryImpl(
                "databinding_generated",
                artifacts.get(DATA_BINDING_BASE_CLASS_SOURCE_OUT),
            )
        )
    }

    override fun getCompiledBuildConfig(): FileCollection {
        val isBuildConfigJar = getBuildConfigType() == BuildConfigType.JAR
        // BuildConfig JAR is not required to be added as a classpath for ANDROID_TEST and UNIT_TEST
        // variants as the tests will use JAR from GradleTestProject which doesn't use testedConfig.
        return if (isBuildConfigJar && this !is TestComponentCreationConfig) {
            internalServices.fileCollection(
                artifacts.get(
                    COMPILE_BUILD_CONFIG_JAR
                )
            )
        } else {
            internalServices.fileCollection()
        }
    }

    private fun getCompiledManifest(): FileCollection {
        val manifestClassRequired = dslInfo.componentType.requiresManifest &&
                services.projectOptions[BooleanOption.GENERATE_MANIFEST_CLASS]
        val isTest = dslInfo.componentType.isForTesting
        val isAar = dslInfo.componentType.isAar
        return if (manifestClassRequired && !isAar && !isTest) {
            internalServices.fileCollection(artifacts.get(COMPILE_MANIFEST_JAR))
        } else {
            internalServices.fileCollection()
        }
    }

    override fun handleMissingDimensionStrategy(
        dimension: String,
        alternatedValues: List<String>
    ) {

        // First, setup the requested value, which isn't the actual requested value, but
        // the variant name, modified
        val requestedValue = VariantManager.getModifiedName(name)
        val attributeKey = ProductFlavorAttr.of(dimension)
        val attributeValue: ProductFlavorAttr = internalServices.named(
            ProductFlavorAttr::class.java, requestedValue
        )

        variantDependencies.compileClasspath.attributes.attribute(attributeKey, attributeValue)
        variantDependencies.runtimeClasspath.attributes.attribute(attributeKey, attributeValue)
        variantDependencies
            .annotationProcessorConfiguration
            .attributes
            .attribute(attributeKey, attributeValue)

        // then add the fallbacks which contain the actual requested value
        DependencyConfigurator.addFlavorStrategy(
            services.dependencies.attributesSchema,
            dimension,
            ImmutableMap.of(requestedValue, alternatedValues)
        )
    }

    override fun getBuildConfigType() : BuildConfigType {
        return if (!buildConfigEnabled) {
            BuildConfigType.NONE
        } else if (services.projectOptions[BooleanOption.ENABLE_BUILD_CONFIG_AS_BYTECODE]
            // TODO(b/224758957): This is wrong we need to check the final build config fields from
            //  the variant API
            && dslInfo.getBuildConfigFields().none()
        ) {
            BuildConfigType.JAR
        } else {
            BuildConfigType.JAVA_SOURCE
        }
    }

    override fun configureAndLockAsmClassesVisitors(objectFactory: ObjectFactory) {
        instrumentation.configureAndLockAsmClassesVisitors(objectFactory, asmApiVersion)
    }

    override fun getDependenciesClassesJarsPostAsmInstrumentation(scope: ArtifactScope): FileCollection {
        return if (dependenciesClassesAreInstrumented) {
            if (asmFramesComputationMode == FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES) {
                variantDependencies.getArtifactFileCollection(
                        ConsumedConfigType.RUNTIME_CLASSPATH,
                        scope,
                        AndroidArtifacts.ArtifactType.CLASSES_FIXED_FRAMES_JAR,
                        RecalculateStackFramesTransform.getAttributesForConfig(this)
                )
            } else {
                variantDependencies.getArtifactFileCollection(
                        ConsumedConfigType.RUNTIME_CLASSPATH,
                        scope,
                        AndroidArtifacts.ArtifactType.ASM_INSTRUMENTED_JARS,
                        AsmClassesTransform.getAttributesForConfig(this)
                )
            }
        } else {
            variantDependencies.getArtifactFileCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                scope,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            )
        }
    }

    override val packageJacocoRuntime: Boolean
        get() = false

    override val isAndroidTestCoverageEnabled: Boolean
        get() = dslInfo.isAndroidTestCoverageEnabled

    override fun addDataBindingArgsToOldVariantApi(args: DataBindingCompilerArguments) {
        dslInfo.javaCompileOptions.annotationProcessorOptions
            .compilerArgumentProviders.add(args)
    }

    override val modelV1LegacySupport =
        ModelV1LegacySupportImpl(dslInfo as VariantDslInfoImpl)
    override val oldVariantApiLegacySupport by lazy {
        OldVariantApiLegacySupportImpl(
            this,
            dslInfo as VariantDslInfoImpl
        )
    }

    override val assetsCreationConfig: AssetsCreationConfig by lazy {
        AssetsCreationConfigImpl(
            dslInfo,
            internalServices
        ) { androidResourcesCreationConfig }
    }

    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig? by lazy {
        if (buildFeatures.androidResources) {
            AndroidResourcesCreationConfigImpl(
                this,
                dslInfo,
                internalServices,
            )
        } else {
            null
        }
    }

    override val resValuesCreationConfig: ResValuesCreationConfig? by lazy {
        if (buildFeatures.resValues) {
            ResValuesCreationConfigImpl(
                dslInfo,
                internalServices
            )
        } else {
            null
        }
    }
}
