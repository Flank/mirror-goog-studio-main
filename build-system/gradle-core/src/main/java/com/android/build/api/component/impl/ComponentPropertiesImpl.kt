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

import android.databinding.tool.LayoutXmlProcessor
import android.databinding.tool.LayoutXmlProcessor.OriginalFileLookup
import com.android.build.api.artifact.Operations
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.ComponentProperties
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.GradleProperty
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.model.ApiVersion
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.blame.SourceFile
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import java.io.File

abstract class ComponentPropertiesImpl(
    componentIdentity: ComponentIdentity,
    val variantDslInfo: VariantDslInfo,
    val variantDependencies: VariantDependencies,
    val variantSources: VariantSources,
    val paths: VariantPathHelper,
    val artifacts: BuildArtifactsHolder,
    val variantScope: VariantScope,
    val variantData: BaseVariantData,
    val transformManager: TransformManager,
    override val dslScope: DslScope,
    val globalScope: GlobalScope
): ComponentProperties, BaseCreationConfig, ComponentIdentity by componentIdentity {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val outputs: VariantOutputList
        get() = VariantOutputList(variantOutputs.toList())

    override val applicationId: Property<String> = dslScope.objectFactory.property(String::class.java).apply {
        setDisallowChanges(dslScope.providerFactory.provider { variantDslInfo.applicationId })
    }

    override val operations: Operations
        get() = artifacts.getOperations()

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // Move as direct delegates
    override val taskContainer = variantData.taskContainer

    private val variantOutputs= mutableListOf<VariantOutputImpl>()

    val variantType: VariantType
        get() = variantDslInfo.variantType

    val dexingType: DexingType
        get() = variantDslInfo.dexingType

    val needsMainDexList: Boolean
        get() = variantDslInfo.dexingType.needsMainDexList

    val minSdkVersion: AndroidVersion
        get() = variantDslInfo.minSdkVersion

    val dirName: String
        get() = variantDslInfo.dirName

    val baseName: String
        get() = variantDslInfo.baseName

    /**
     * Returns the tested variant. This is null for [VariantPropertiesImpl] instances
     *

     * This declares is again, even though the public interfaces only have it via
     * [TestComponentProperties]. This is to facilitate places where one cannot use
     * [TestComponentPropertiesImpl].
     *
     * see [onTestedVariant] for a utility function helping deal with nullability
     */
    open val testedVariant: VariantPropertiesImpl? = null

    /**
     * Runs an action on the tested variant and return the results of the action.
     *
     * if there is no tested variant this does nothing and returns null.
     */
    fun <T> onTestedVariant(action: (VariantPropertiesImpl) -> T): T? {
        if (variantType.isTestComponent) {
            val tested = testedVariant ?: throw RuntimeException("testedVariant null with type $variantType")
            return action(tested)
        }

        return null
    }

    override val layoutXmlProcessor: LayoutXmlProcessor by lazy {
        val resourceBlameLogDir = paths.resourceBlameLogDir
        val mergingLog = MergingLog(resourceBlameLogDir)
        LayoutXmlProcessor(
            variantDslInfo.originalApplicationId,
            globalScope
                .dataBindingBuilder
                .createJavaFileWriter(paths.classOutputForDataBinding),
            OriginalFileLookup { file: File? ->
                val input =
                    SourceFile(file!!)
                val original = mergingLog.find(input)
                if (original === input) null else original.sourceFile
            },
            globalScope.projectOptions[BooleanOption.USE_ANDROID_X]
        )
    }

    fun addVariantOutput(apkData: ApkData): VariantOutputImpl {
        // the DSL objects are now locked, if the versionCode is provided, use that
        // otherwise use the lazy manifest reader to extract the value from the manifest
        // file.
        val versionCode = variantDslInfo.getVersionCode(true)
        val versionCodeProperty = initializeProperty(Int::class.java, "$name::versionCode")
        if (versionCode <= 0) {
            versionCodeProperty.set(
                dslScope.providerFactory.provider {
                    variantDslInfo.manifestVersionCodeSupplier.asInt
                })
        } else {
            versionCodeProperty.set(versionCode)
        }
        // the DSL objects are now locked, if the versionName is provided, use that; otherwise use
        // the lazy manifest reader to extract the value from the manifest file.
        val versionName = variantDslInfo.getVersionName(true)
        val versionNameProperty = initializeProperty(String::class.java, "$name::versionName")
        versionNameProperty.set(
            dslScope.providerFactory.provider {
                versionName ?: variantDslInfo.manifestVersionNameSupplier.get()
            }
        )
        val variantOutputConfiguration = VariantOutputConfigurationImpl(
            apkData.isUniversal,
            apkData.filters.map { filterData ->
                FilterConfiguration(
                    FilterConfiguration.FilterType.valueOf(filterData.filterType),
                    filterData.identifier)
            }
        )
        return VariantOutputImpl(
            versionCodeProperty,
            versionNameProperty,
            initializeProperty(Boolean::class.java, "$name::isEnabled").value(true),
            variantOutputConfiguration,
            apkData
        ).also {
            apkData.variantOutput = it
            variantOutputs.add(it)
        }
    }

    protected fun <T> initializeProperty(type: Class<T>, id: String): Property<T> {
        return if (dslScope.projectOptions[BooleanOption.USE_SAFE_PROPERTIES]) {
            GradleProperty.safeReadingBeforeExecution(id, dslScope.objectFactory.property(type))
        } else {
            dslScope.objectFactory.property(type)
        }
    }

    fun computeTaskName(prefix: String): String =
        prefix.appendCapitalized(name)

    override fun computeTaskName(prefix: String, suffix: String): String =
        prefix.appendCapitalized(name, suffix)

    // -------------------------
    // File location computation. Previously located in VariantScope, these are here
    // temporarily until we fully move away from them.

    val generatedResOutputDir: File
        get() = getGeneratedResourcesDir("resValues")

    private fun getGeneratedResourcesDir(name: String): File {
        return FileUtils.join(
            paths.generatedDir,
            listOf("res", name) + variantDslInfo.directorySegments)
    }

    // Precomputed file paths.
    @JvmOverloads
    fun getJavaClasspath(
        configType: ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any? = null
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
            globalScope.project.files(variantScope.getCompiledRClasses(configType), mainCollection)
        return mainCollection
    }

    fun getJavaClasspathArtifacts(
        configType: ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any?
    ): ArtifactCollection {
        val mainCollection =
            variantDependencies.getArtifactCollection(configType, ArtifactScope.ALL, classesType)
        return ArtifactCollectionWithExtraArtifact.makeExtraCollection(
            mainCollection,
            variantData.getGeneratedBytecode(generatedBytecodeKey),
            globalScope.project.path
        )
    }

}