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

package com.android.build.gradle.internal.tasks

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.VariantType
import com.google.common.collect.Sets
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FILTERED_PROGUARD_RULES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_MAPPING
import com.android.build.gradle.internal.scope.InternalArtifactType.GENERATED_PROGUARD_FILE
import com.android.build.gradle.internal.variant.BaseVariantData
import com.google.common.base.Preconditions
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.util.Collections
import java.util.concurrent.Callable

/**
 * Base class for tasks that consume ProGuard configuration files.
 *
 * We use this type to configure ProGuard and the R8 consistently, using the same
 * code.
 */
abstract class ProguardConfigurableTask : NonIncrementalTask() {

    @get:Input
    abstract val variantType: Property<VariantType>

    @get:Input
    abstract val includeFeaturesInScopes: Property<Boolean>

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputMappingFile: ConfigurableFileCollection

    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resources: ConfigurableFileCollection

    @get:Classpath
    abstract val referencedClasses: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referencedResources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configurationFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val mappingFile: RegularFileProperty

    abstract class CreationAction <T : ProguardConfigurableTask>
    @JvmOverloads
    internal constructor(
        variantScope: VariantScope,
        private val isTestApplication: Boolean = false
    ) : VariantTaskCreationAction<T>(variantScope) {


        private val includeFeaturesInScopes: Boolean = variantScope.consumesFeatureJars()
        private val variantType: VariantType = variantScope.variantData.type
        private val testedVariantData: BaseVariantData? = variantScope.testedVariantData


        // Override to make this true in proguard
        protected val defaultObfuscate: Boolean = false

        // These filters assume a file can't be class and resources at the same time.
        private val referencedClasses: FileCollection

        private val referencedResources: FileCollection

        private val classes: FileCollection

        private val resources: FileCollection

        init {
            val inputScopes: MutableSet<in QualifiedContent.ScopeType> =
                when {
                    variantType.isAar -> mutableSetOf(Scope.PROJECT, InternalScope.LOCAL_DEPS)
                    includeFeaturesInScopes -> mutableSetOf(
                        Scope.PROJECT,
                        Scope.SUB_PROJECTS,
                        Scope.EXTERNAL_LIBRARIES,
                        InternalScope.FEATURES
                    )
                    else -> mutableSetOf(
                        Scope.PROJECT,
                        Scope.SUB_PROJECTS,
                        Scope.EXTERNAL_LIBRARIES
                    )
                }

            val referencedScopes: Set<Scope> = run {
                val set = Sets.newHashSetWithExpectedSize<Scope>(5)
                if (variantType.isAar) {
                    set.add(Scope.SUB_PROJECTS)
                    set.add(Scope.EXTERNAL_LIBRARIES)
                }

                if (variantType.isTestComponent) {
                    set.add(Scope.TESTED_CODE)
                }

                set.add(Scope.PROVIDED_ONLY)

                Sets.immutableEnumSet(set)
            }

            val inputContentTypes: Set<QualifiedContent.ContentType> =
                setOf(
                    QualifiedContent.DefaultContentType.CLASSES,
                    QualifiedContent.DefaultContentType.RESOURCES
                )

            val classesFilter = StreamFilter { contentTypes, _ ->
                QualifiedContent.DefaultContentType.CLASSES in contentTypes
            }

            val resourcesFilter = StreamFilter { contentTypes, _ ->
                QualifiedContent.DefaultContentType.RESOURCES in contentTypes
            }

            val referencedFilter = StreamFilter { _, scopes ->
                scopes.intersect(referencedScopes).isNotEmpty()
            }

            val nonReferencedFilter = StreamFilter { _, scopes ->
                scopes.intersect(inputScopes).isNotEmpty()
            }

            // Check for overlap in scopes
            Preconditions.checkState(
                referencedScopes.intersect(inputScopes).isEmpty(),
                """|Referenced and non-referenced inputs must not overlap.
                   |Referenced scope: ${referencedScopes}
                   |Non referenced scopes: ${inputScopes}
                   |Overlap: ${referencedScopes.intersect(inputScopes)}
                """.trimMargin()
            )

            classes = variantScope.transformManager
                .getPipelineOutputAsFileCollection(
                    nonReferencedFilter,
                    classesFilter
                )

            resources = variantScope.transformManager
                .getPipelineOutputAsFileCollection(
                    nonReferencedFilter,
                    resourcesFilter
                )

            // Consume non referenced inputs
            variantScope.transformManager
                .consumeStreams(inputScopes, inputContentTypes)


            referencedClasses = variantScope.transformManager
                .getPipelineOutputAsFileCollection(
                    referencedFilter,
                    classesFilter
                )

            referencedResources = variantScope.transformManager
                .getPipelineOutputAsFileCollection(
                    referencedFilter,
                    resourcesFilter
                )
        }

        override fun handleProvider(taskProvider: TaskProvider<out T>) {
            super.handleProvider(taskProvider)

            variantScope
                .artifacts
                .producesFile(
                    APK_MAPPING,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    ProguardConfigurableTask::mappingFile,
                    "mapping.txt"
                )
        }

        override fun configure(task: T) {
            super.configure(task)

            if (testedVariantData?.scope?.artifacts?.hasFinalProduct(APK_MAPPING) == true) {
                task.inputMappingFile.from(
                    testedVariantData
                        .scope
                        .artifacts
                        .getFinalProduct<FileSystemLocation>(APK_MAPPING)
                )
            } else if (isTestApplication) {
                task.inputMappingFile.from(
                    variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.APK_MAPPING
                    )
                )
            }

            task.includeFeaturesInScopes.set(includeFeaturesInScopes)

            task.classes.from(classes)

            task.resources.from(resources)

            task.referencedClasses.from(referencedClasses)

            task.referencedResources.from(referencedResources)

            applyProguardRules(task, task.inputMappingFile, testedVariantData)
        }

        private fun applyProguardRules(
            task: ProguardConfigurableTask,
            inputProguardMapping: FileCollection?,
            testedVariantData: BaseVariantData?
        ) {
            when {
                testedVariantData != null -> {
                    val testedScope = testedVariantData.scope
                    // This is an androidTest variant inside an app/library.
                    applyProguardDefaultsForTest(task)

                    // All -dontwarn rules for test dependencies should go in here:
                    val configurationFiles = task.project.files(
                        Callable<Collection<File>> { testedScope.testProguardFiles },
                        variantScope.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            FILTERED_PROGUARD_RULES,
                            maybeGetCodeShrinkerAttrMap(variantScope)
                        ))
                    task.configurationFiles.from(configurationFiles)
                }
                variantScope.type.isForTesting && !variantScope.type.isTestComponent -> {
                    // This is a test-only module and the app being tested was obfuscated with ProGuard.
                    applyProguardDefaultsForTest(task)

                    // All -dontwarn rules for test dependencies should go in here:
                    val configurationFiles = task.project.files(
                        Callable<Collection<File>> { variantScope.testProguardFiles },
                        variantScope.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            FILTERED_PROGUARD_RULES,
                            maybeGetCodeShrinkerAttrMap(variantScope)
                        ))
                    task.configurationFiles.from(configurationFiles)
                }
                else -> // This is a "normal" variant in an app/library.
                    applyProguardConfigForNonTest(task)
            }


            if (inputProguardMapping != null) {
                task.dependsOn(inputProguardMapping)
            }
        }

        private fun applyProguardDefaultsForTest(task: ProguardConfigurableTask) {
            // Don't remove any code in tested app.
            // Obfuscate is disabled by default.
            // It is enabled in Proguard since it would ignore the mapping file otherwise.
            // R8 does not have that issue, so we disable obfuscation when running R8.
            setActions(PostprocessingFeatures(false, defaultObfuscate, false))

            keep("class * {*;}")
            keep("interface * {*;}")
            keep("enum * {*;}")
            keepAttributes()
        }

        private fun applyProguardConfigForNonTest(task: ProguardConfigurableTask) {
            val variantConfig = variantScope.variantConfiguration

            val postprocessingFeatures = variantScope.postprocessingFeatures
            if (postprocessingFeatures != null) {
                setActions(postprocessingFeatures)
            }

            val proguardConfigFiles = Callable<Collection<File>> { variantScope.proguardFiles }

            val aaptProguardFileType =
                if (task.includeFeaturesInScopes.get()) {
                    InternalArtifactType.MERGED_AAPT_PROGUARD_FILE
                } else {
                    InternalArtifactType.AAPT_PROGUARD_FILE
                }

            val configurationFiles = task.project.files(
                proguardConfigFiles,
                variantScope.artifacts.getFinalProduct<FileSystemLocation>(aaptProguardFileType),
                variantScope.artifacts.getFinalProduct<FileSystemLocation>(GENERATED_PROGUARD_FILE),
                variantScope.getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    FILTERED_PROGUARD_RULES,
                    maybeGetCodeShrinkerAttrMap(variantScope)
                )
            )

            if (variantScope.type.isHybrid && variantScope.type.isBaseModule) {
                val consumerProguardFiles = Callable<Collection<File>> {
                    this.variantScope.consumerProguardFiles
                }
                configurationFiles.from(consumerProguardFiles)
            }

            if (task.includeFeaturesInScopes.get()) {
                addFeatureProguardRules(configurationFiles)
            }
            task.configurationFiles.from(configurationFiles)

            if (variantScope.variantData.type.isAar) {
                keep("class **.R")
                keep("class **.R$*")
            }

            if (variantConfig.isTestCoverageEnabled) {
                // when collecting coverage, don't remove the JaCoCo runtime
                keep("class com.vladium.** {*;}")
                keep("class org.jacoco.** {*;}")
                keep("interface org.jacoco.** {*;}")
                dontWarn("org.jacoco.**")
            }
        }

        private fun addFeatureProguardRules(configurationFiles: ConfigurableFileCollection) {
            configurationFiles.from(
                variantScope.getArtifactFileCollection(
                    METADATA_VALUES,
                    PROJECT,
                    FILTERED_PROGUARD_RULES,
                    maybeGetCodeShrinkerAttrMap(variantScope)
                )
            )
        }

        private fun maybeGetCodeShrinkerAttrMap(
            variantScope: VariantScope
        ): Map<Attribute<String>, String>? {
            return if (variantScope.codeShrinker != null) {
                mapOf(VariantManager.SHRINKER_ATTR to variantScope.codeShrinker.toString())
            } else {
                null
            }
        }

        protected abstract fun keep(keep: String)

        protected abstract fun keepAttributes()

        protected abstract fun dontWarn(dontWarn: String)

        protected abstract fun setActions(actions: PostprocessingFeatures)
    }
}
