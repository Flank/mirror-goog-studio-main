/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.BuildConfigField
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.VariantDslInfoImpl
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.PublishingSpecs.Companion.getVariantPublishingSpec
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.databinding.DataBindingCompilerArguments
import com.android.build.gradle.internal.variant.BaseVariantData
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.Serializable

class OldVariantApiLegacySupportImpl(
    private val component: ComponentCreationConfig,
    private val variantDslInfo: VariantDslInfoImpl,
    override val variantData: BaseVariantData
): OldVariantApiLegacySupport {

    override val buildTypeObj: BuildType
        get() = variantDslInfo.buildTypeObj
    override val productFlavorList: List<ProductFlavor>
        get() = variantDslInfo.productFlavorList
    override val mergedFlavor: MergedFlavor
        get() = variantDslInfo.mergedFlavor
    override val javaCompileOptions: JavaCompileOptions
        get() = variantDslInfo.javaCompileOptions

    override fun getJavaClasspathArtifacts(
        configType: AndroidArtifacts.ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any?
    ): ArtifactCollection {
        val mainCollection =
            component.variantDependencies.getArtifactCollection(
                configType,
                AndroidArtifacts.ArtifactScope.ALL,
                classesType
            )
        val extraArtifact = component.services.provider {
            variantData.getGeneratedBytecode(generatedBytecodeKey)
        }
        val combinedCollection = component.services.fileCollection(
            mainCollection.artifactFiles, extraArtifact
        )
        val extraCollection = ArtifactCollectionWithExtraArtifact.makeExtraCollection(
            mainCollection,
            combinedCollection,
            extraArtifact,
            component.services.projectInfo.path
        )

        return (component as? TestComponentCreationConfig)?.onTestedVariant { testedVariant ->
            // This is required because of http://b/150500779. Kotlin Gradle plugin relies on
            // TestedComponentIdentifierImpl being present in the returned artifact collection, as
            // artifacts with that identifier type are added to friend paths to kotlinc invocation.
            // Because jar containing all classes of the main artifact is in the classpath when
            // compiling test, we need to add TestedComponentIdentifierImpl artifact with that file.
            // This is needed when compiling test variants that access internal members.
            val internalArtifactType = getVariantPublishingSpec(testedVariant.componentType)
                .getSpec(classesType, configType.publishedTo)!!.outputType

            @Suppress("USELESS_CAST") // Explicit cast needed here.
            val testedAllClasses: Provider<FileCollection> =
                component.services.provider {
                    component.services.fileCollection(
                        testedVariant.artifacts.get(internalArtifactType)
                    ) as FileCollection
                }
            val combinedCollectionForTest = component.services.fileCollection(
                combinedCollection, testedAllClasses, testedAllClasses
            )

            ArtifactCollectionWithExtraArtifact.makeExtraCollectionForTest(
                extraCollection,
                combinedCollectionForTest,
                testedAllClasses,
                component.services.projectInfo.path,
                null
            )
        } ?: extraCollection
    }

    private var allRawAndroidResources: ConfigurableFileCollection? = null

    override fun getAllRawAndroidResources(component: ComponentCreationConfig): FileCollection {
        if (allRawAndroidResources != null) {
            return allRawAndroidResources!!
        }
        allRawAndroidResources = component.services.fileCollection()

        allRawAndroidResources!!.from(
            component.variantDependencies
                .getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.ANDROID_RES
                )
                .artifactFiles
        )

        allRawAndroidResources!!.from(
            component.services.fileCollection(
                variantData.extraGeneratedResFolders
            ).builtBy(listOfNotNull(variantData.extraGeneratedResFolders.builtBy))
        )

        component.taskContainer.generateApkDataTask?.let {
            allRawAndroidResources!!.from(component.artifacts.get(InternalArtifactType.MICRO_APK_RES))
        }

        allRawAndroidResources!!.from(component.sources.res.getVariantSources().map { allRes ->
            allRes.map { directoryEntries ->
                directoryEntries.directoryEntries
                    .map { it.asFiles(component.services::directoryProperty) }
            }
        })
        return allRawAndroidResources!!
    }

    override fun addBuildConfigField(type: String, key: String, value: Serializable, comment: String?) {
        component.buildConfigCreationConfig?.buildConfigFields?.put(
            key, BuildConfigField(type, value, comment)
        )
    }

    override fun addDataBindingArgsToOldVariantApi(args: DataBindingCompilerArguments) {
        variantDslInfo.javaCompileOptions.annotationProcessorOptions
            .compilerArgumentProviders.add(args)
    }

    override fun handleMissingDimensionStrategy(
        dimension: String,
        alternatedValues: List<String>
    ) {

        // First, setup the requested value, which isn't the actual requested value, but
        // the variant name, modified
        val requestedValue = VariantManager.getModifiedName(component.name)
        val attributeKey = ProductFlavorAttr.of(dimension)
        val attributeValue: ProductFlavorAttr = component.services.named(
            ProductFlavorAttr::class.java, requestedValue
        )

        component.variantDependencies.compileClasspath.attributes.attribute(attributeKey, attributeValue)
        component.variantDependencies.runtimeClasspath.attributes.attribute(attributeKey, attributeValue)
        component.variantDependencies
            .annotationProcessorConfiguration
            .attributes
            .attribute(attributeKey, attributeValue)

        // then add the fallbacks which contain the actual requested value
        DependencyConfigurator.addFlavorStrategy(
            component.services.dependencies.attributesSchema,
            dimension,
            ImmutableMap.of(requestedValue, alternatedValues)
        )
    }
}
