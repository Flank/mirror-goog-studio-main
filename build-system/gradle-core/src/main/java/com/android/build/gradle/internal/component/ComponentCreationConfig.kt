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

package com.android.build.gradle.internal.component

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Instrumentation
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.DirectoryEntry
import com.android.build.api.variant.impl.SourcesImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.AssetsCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.ResValuesCreationConfig
import com.android.build.gradle.internal.component.legacy.ModelV1LegacySupport
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.ProductFlavor
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.ComponentType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.io.File
import java.util.function.Predicate

/**
 * Base of the interfaces used internally to access *PropertiesImpl object.
 *
 * This allows a graph hierarchy rather than a strict tree, in order to have multiple
 * supertype and make some tasks receive a generic type that does not fit the actual
 * implementation hierarchy (see for instance ApkCreationConfig)
 */
interface ComponentCreationConfig : ComponentIdentity {
    // ---------------------------------------------------------------------------------------------
    // BASIC INFO
    // ---------------------------------------------------------------------------------------------
    val dirName: String
    val baseName: String
    val componentType: ComponentType
    val description: String
    val productFlavorList: List<ProductFlavor>

    // ---------------------------------------------------------------------------------------------
    // NEEDED BY ALL COMPONENTS
    // ---------------------------------------------------------------------------------------------

    // needed by resource compilation/link
    val applicationId: Provider<String>
    val namespace: Provider<String>
    val asmApiVersion: Int
    val asmFramesComputationMode: FramesComputationMode
    val registeredProjectClassesVisitors: List<AsmClassVisitorFactory<*>>
    val registeredDependenciesClassesVisitors: List<AsmClassVisitorFactory<*>>
    val allProjectClassesPostAsmInstrumentation: FileCollection
    val projectClassesAreInstrumented: Boolean
    val dependenciesClassesAreInstrumented: Boolean
    val instrumentation: Instrumentation
    val debuggable: Boolean
    val profileable: Boolean
    val manifestPlaceholders: MapProperty<String, String>
    val supportedAbis: Set<String>

    val minSdkVersion: AndroidVersion
    val targetSdkVersion: AndroidVersion
    val targetSdkVersionOverride: AndroidVersion?

    // ---------------------------------------------------------------------------------------------
    // OPTIONAL FEATURES
    // ---------------------------------------------------------------------------------------------

    val assetsCreationConfig: AssetsCreationConfig?
    val androidResourcesCreationConfig: AndroidResourcesCreationConfig?
    val resValuesCreationConfig: ResValuesCreationConfig?
    val buildConfigCreationConfig: BuildConfigCreationConfig?

    // TODO figure out whether these properties are needed by all
    // TODO : remove as it is now in Variant.
    // ---------------------------------------------------------------------------------------------
    val outputs: VariantOutputList
    val manifestArtifactType: InternalArtifactType<Directory>

    // ---------------------------------------------------------------------------------------------
    // INTERNAL DELEGATES
    // ---------------------------------------------------------------------------------------------
    val buildFeatures: BuildFeatureValues
    val variantScope: VariantScope
    val variantDependencies: VariantDependencies
    val artifacts: ArtifactsImpl
    val sources: SourcesImpl
    val taskContainer: MutableTaskContainer
    val transformManager: TransformManager
    val paths: VariantPathHelper
    val services: TaskCreationServices

    /**
     * DO NOT USE, this is still present to support ModelBuilder v1 code that should be deleted
     * soon. Instead, use [sources] API.
     */

    val variantSources: VariantSources


    /**
     * Access to the global task creation configuration
     */
    val global: GlobalTaskCreationConfig

    // ---------------------------------------------------------------------------------------------
    // INTERNAL HELPERS
    // ---------------------------------------------------------------------------------------------

    fun computeTaskName(prefix: String, suffix: String): String
    fun computeTaskName(prefix: String): String

    // TODO : Remove BaseVariantData.
    val variantData: BaseVariantData

    /**
     * Get the compile classpath for compiling sources in this component
     */
    fun getJavaClasspath(
        configType: AndroidArtifacts.ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any? = null
    ): FileCollection

    val compileClasspath: FileCollection

    fun configureAndLockAsmClassesVisitors(objectFactory: ObjectFactory)

    fun getDependenciesClassesJarsPostAsmInstrumentation(scope: AndroidArtifacts.ArtifactScope): FileCollection

    val packageJacocoRuntime: Boolean

    val javaCompilation: JavaCompilation

    fun addVariantOutput(
        variantOutputConfiguration: VariantOutputConfiguration,
        outputFileName: String? = null
    )

    fun computeLocalFileDependencies(filePredicate: Predicate<File>): FileCollection

    fun computeLocalPackagedJars(): FileCollection

    // ---------------------------------------------------------------------------------------------
    // VARIANT DSL INFO REPLACEMENTS
    // ---------------------------------------------------------------------------------------------
    // TODO: Figure out if we should be exposing any of the below

    val isAndroidTestCoverageEnabled: Boolean

    fun handleMissingDimensionStrategy(dimension: String, alternatedValues: List<String>)

    fun addDataBindingSources(sourceSets: MutableList<DirectoryEntry>)

    /** Publish intermediate artifacts in the BuildArtifactsHolder based on PublishingSpecs.  */
    fun publishBuildArtifacts()

    fun <T: Component> createUserVisibleVariantObject(
        projectServices: ProjectServices,
        operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
        stats: GradleBuildVariant.Builder?
    ): T

    // ---------------------------------------------------------------------------------------------
    // LEGACY SUPPORT
    // ---------------------------------------------------------------------------------------------

    @Deprecated("DO NOT USE, this is just for model v1 legacy support")
    val modelV1LegacySupport: ModelV1LegacySupport

    @Deprecated("DO NOT USE, this is just for old variant API legacy support")
    val oldVariantApiLegacySupport: OldVariantApiLegacySupport
}
