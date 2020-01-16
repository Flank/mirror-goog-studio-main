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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.VariantType
import com.google.common.collect.Sets
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FILTERED_PROGUARD_RULES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_MAPPING
import com.android.build.gradle.internal.scope.InternalArtifactType.GENERATED_PROGUARD_FILE
import com.google.common.base.Preconditions
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
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
    abstract val testedMappingFile: ConfigurableFileCollection

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

    abstract class CreationAction<T : ProguardConfigurableTask>
    @JvmOverloads
    internal constructor(
        componentProperties: ComponentPropertiesImpl,
        private val isTestApplication: Boolean = false
    ) : VariantTaskCreationAction<T, ComponentPropertiesImpl>(
        componentProperties
    ) {

        private val includeFeaturesInScopes: Boolean = componentProperties.variantScope.consumesFeatureJars()
        protected val variantType: VariantType = componentProperties.variantType
        private val testedVariant = componentProperties.testedVariant

        // Override to make this true in proguard
        protected open val defaultObfuscate: Boolean = false

        // These filters assume a file can't be class and resources at the same time.
        private val referencedClasses: FileCollection

        private val referencedResources: FileCollection

        private val classes: FileCollection

        private val resources: FileCollection

        protected val inputScopes: MutableSet<QualifiedContent.ScopeType> =
            when {
                variantType.isAar -> mutableSetOf(
                    Scope.PROJECT,
                    InternalScope.LOCAL_DEPS
                )
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

        init {
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

            // Check for overlap in scopes
            Preconditions.checkState(
                referencedScopes.intersect(inputScopes).isEmpty(),
                """|Referenced and non-referenced inputs must not overlap.
                   |Referenced scope: ${referencedScopes}
                   |Non referenced scopes: ${inputScopes}
                   |Overlap: ${referencedScopes.intersect(inputScopes)}
                """.trimMargin()
            )

            val transformManager = componentProperties.transformManager
            classes = transformManager
                .getPipelineOutputAsFileCollection(createStreamFilter(CLASSES, inputScopes))

            resources = transformManager
                .getPipelineOutputAsFileCollection(createStreamFilter(RESOURCES, inputScopes))

            // Consume non referenced inputs
            transformManager.consumeStreams(inputScopes, setOf(CLASSES, RESOURCES))

            referencedClasses = transformManager
                .getPipelineOutputAsFileCollection(
                    createStreamFilter(CLASSES, referencedScopes.toMutableSet())
                )

            referencedResources = transformManager
                .getPipelineOutputAsFileCollection(
                    createStreamFilter(RESOURCES, referencedScopes.toMutableSet())
                )
        }

        override fun handleProvider(
            taskProvider: TaskProvider<out T>
        ) {
            super.handleProvider(taskProvider)

            component
                .artifacts
                .producesFile(
                    APK_MAPPING,
                    taskProvider,
                    ProguardConfigurableTask::mappingFile,
                    "mapping.txt"
                )
        }

        override fun configure(
            task: T
        ) {
            super.configure(task)

            if (testedVariant?.artifacts?.hasFinalProduct(APK_MAPPING) == true) {
                task.testedMappingFile.from(
                    testedVariant
                        .artifacts
                        .getFinalProduct(APK_MAPPING)
                )
            } else if (isTestApplication) {
                task.testedMappingFile.from(
                    component.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.APK_MAPPING
                    )
                )
            }

            task.variantType.set(variantType)

            task.includeFeaturesInScopes.set(includeFeaturesInScopes)

            task.classes.from(classes)

            task.resources.from(resources)

            task.referencedClasses.from(referencedClasses)

            task.referencedResources.from(referencedResources)

            applyProguardRules(task, component, task.testedMappingFile, testedVariant)
        }

        private fun applyProguardRules(
            task: ProguardConfigurableTask,
            component: ComponentPropertiesImpl,
            inputProguardMapping: FileCollection?,
            testedVariant: VariantPropertiesImpl?
        ) {
            when {
                testedVariant != null -> {
                    // This is an androidTest variant inside an app/library.
                    applyProguardDefaultsForTest(task)

                    // All -dontwarn rules for test dependencies should go in here:
                    val configurationFiles = task.project.files(
                        Callable<Collection<File>> { testedVariant.variantScope.testProguardFiles },
                        component.variantDependencies.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            FILTERED_PROGUARD_RULES,
                            maybeGetCodeShrinkerAttrMap(component)
                        )
                    )
                    task.configurationFiles.from(configurationFiles)
                }
                component.variantType.isForTesting && !component.variantType.isTestComponent -> {
                    // This is a test-only module and the app being tested was obfuscated with ProGuard.
                    applyProguardDefaultsForTest(task)

                    // All -dontwarn rules for test dependencies should go in here:
                    val configurationFiles = task.project.files(
                        Callable<Collection<File>> { component.variantScope.testProguardFiles },
                        component.variantDependencies.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            FILTERED_PROGUARD_RULES,
                            maybeGetCodeShrinkerAttrMap(component)
                        )
                    )
                    task.configurationFiles.from(configurationFiles)
                }
                else -> // This is a "normal" variant in an app/library.
                    applyProguardConfigForNonTest(task, component)
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

        private fun applyProguardConfigForNonTest(
            task: ProguardConfigurableTask,
            component: ComponentPropertiesImpl
        ) {
            val variantDslInfo = component.variantDslInfo

            val postprocessingFeatures = component.variantScope.postprocessingFeatures
            postprocessingFeatures?.let { setActions(postprocessingFeatures) }

            val proguardConfigFiles = Callable<Collection<File>> { component.variantScope.proguardFiles }

            val aaptProguardFile =
                if (task.includeFeaturesInScopes.get()) {
                    component.artifacts.getOperations().get(
                        InternalArtifactType.MERGED_AAPT_PROGUARD_FILE)
                } else {
                    component.artifacts.getOperations().get(
                        InternalArtifactType.AAPT_PROGUARD_FILE
                    )
                }

            val configurationFiles = task.project.files(
                proguardConfigFiles,
                aaptProguardFile,
                component.artifacts.getFinalProduct(GENERATED_PROGUARD_FILE),
                component.variantDependencies.getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    FILTERED_PROGUARD_RULES,
                    maybeGetCodeShrinkerAttrMap(component)
                )
            )

            if (task.includeFeaturesInScopes.get()) {
                addFeatureProguardRules(component, configurationFiles)
            }
            task.configurationFiles.from(configurationFiles)

            if (component.variantType.isAar) {
                keep("class **.R")
                keep("class **.R$*")
            }

            if (variantDslInfo.isTestCoverageEnabled) {
                // when collecting coverage, don't remove the JaCoCo runtime
                keep("class com.vladium.** {*;}")
                keep("class org.jacoco.** {*;}")
                keep("interface org.jacoco.** {*;}")
                dontWarn("org.jacoco.**")
            }
        }

        private fun addFeatureProguardRules(
            component: ComponentPropertiesImpl,
            configurationFiles: ConfigurableFileCollection
        ) {
            configurationFiles.from(
                component.variantDependencies.getArtifactFileCollection(
                    REVERSE_METADATA_VALUES,
                    PROJECT,
                    FILTERED_PROGUARD_RULES,
                    maybeGetCodeShrinkerAttrMap(component)
                )
            )
        }

        private fun maybeGetCodeShrinkerAttrMap(
            componentProperties: ComponentPropertiesImpl
        ): Map<Attribute<String>, String>? {
            return if (componentProperties.variantScope.codeShrinker != null) {
                mapOf(VariantManager.SHRINKER_ATTR to componentProperties.variantScope.codeShrinker.toString())
            } else {
                null
            }
        }

        protected abstract fun keep(keep: String)

        protected abstract fun keepAttributes()

        protected abstract fun dontWarn(dontWarn: String)

        protected abstract fun setActions(actions: PostprocessingFeatures)

        /**
         *  Convenience function. Returns a StreamFilter that checks for the given contentType and a
         *  nonempty intersection with the given set of Scopes .
         */
        private fun createStreamFilter(
            desiredType: QualifiedContent.ContentType,
            desiredScopes: MutableSet<in QualifiedContent.ScopeType>
        ): StreamFilter {
            return StreamFilter { contentTypes, scopes ->
                desiredType in contentTypes && desiredScopes.intersect(scopes).isNotEmpty()
            }
        }
    }

}
