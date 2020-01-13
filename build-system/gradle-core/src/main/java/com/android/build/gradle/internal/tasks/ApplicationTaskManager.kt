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

package com.android.build.gradle.internal.tasks

import android.databinding.tool.DataBindingBuilder
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.variant.impl.ApplicationVariantPropertiesImpl
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.AbstractAppTaskManager
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureApplicationIdsTask
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadataWriterTask
import com.android.build.gradle.options.BooleanOption
import com.android.builder.profile.Recorder
import com.google.common.collect.ImmutableMap
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.io.File
import java.lang.Boolean
import java.util.ArrayList
import java.util.stream.Collectors

class ApplicationTaskManager(
    globalScope: GlobalScope,
    databindingBuilder: DataBindingBuilder,
    extension: BaseExtension,
    toolingRegistry: ToolingModelBuilderRegistry,
    recorder: Recorder
) : AbstractAppTaskManager<ApplicationVariantPropertiesImpl>(
    globalScope,
    databindingBuilder,
    extension,
    toolingRegistry,
    recorder
) {

    override fun createTasksForVariant(
        variantProperties: ApplicationVariantPropertiesImpl,
        allComponentsWithLint: MutableList<ApplicationVariantPropertiesImpl>
    ) {
        createCommonTasks(variantProperties, allComponentsWithLint)

        // Base feature specific tasks.
        taskFactory.register(FeatureSetMetadataWriterTask.CreationAction(variantProperties))

        createValidateSigningTask(variantProperties)
        // Add a task to produce the signing config file.
        // Add a task to produce the signing config file.
        taskFactory.register(SigningConfigWriterTask.CreationAction(variantProperties))

        if ((extension as BaseAppModuleExtension).assetPacks.isNotEmpty()) {
            createAssetPackTasks(variantProperties)
        }

        if (globalScope.buildFeatures.dataBinding) {
            // Create a task that will package the manifest ids(the R file packages) of all
            // features into a file. This file's path is passed into the Data Binding annotation
            // processor which uses it to known about all available features.
            //
            // <p>see: {@link TaskManager#setDataBindingAnnotationProcessorParams(VariantScope)}
            taskFactory.register(
                DataBindingExportFeatureApplicationIdsTask.CreationAction(variantProperties)
            )
        }

        createDynamicBundleTask(variantProperties)

        handleMicroApp(variantProperties)

        // do not publish the APK(s) if there are dynamic feature.
        // do not publish the APK(s) if there are dynamic feature.
        if (!variantProperties.globalScope.hasDynamicFeatures()) {
            createSoftwareComponent(
                variantProperties,
                "_apk",
                PublishedConfigType.APK_PUBLICATION
            )
        }
        createSoftwareComponent(variantProperties, "_aab", PublishedConfigType.AAB_PUBLICATION)
    }

    /** Configure variantData to generate embedded wear application.  */
    private fun handleMicroApp(variantProperties: ApplicationVariantPropertiesImpl) {
        val variantDslInfo = variantProperties.variantDslInfo
        val variantType = variantProperties.variantType
        if (variantType.isBaseModule) {
            val unbundledWearApp = variantDslInfo.isWearAppUnbundled
            if (Boolean.TRUE != unbundledWearApp && variantDslInfo.isEmbedMicroApp) {
                val wearApp =
                    variantProperties.variantDependencies.wearAppConfiguration
                        ?: error("Wear app with no wearApp configuration")
                if (!wearApp.allDependencies.isEmpty()) {
                    val setApkArtifact =
                        Action { container: AttributeContainer ->
                            container.attribute(
                                AndroidArtifacts.ARTIFACT_TYPE,
                                AndroidArtifacts.ArtifactType.APK.type
                            )
                        }
                    val files = wearApp.incoming
                        .artifactView { config: ArtifactView.ViewConfiguration ->
                            config.attributes(
                                setApkArtifact
                            )
                        }
                        .files
                    createGenerateMicroApkDataTask(variantProperties, files)
                }
            } else {
                if (Boolean.TRUE == unbundledWearApp) {
                    createGenerateMicroApkDataTask(variantProperties)
                }
            }
        }
    }

    /**
     * Creates the task that will handle micro apk.
     *
     *
     * New in 2.2, it now supports the unbundled mode, in which the apk is not bundled anymore,
     * but we still have an XML resource packaged, and a custom entry in the manifest. This is
     * triggered by passing a null [Configuration] object.
     *
     * @param variantProperties the variant scope
     * @param config an optional Configuration object. if non null, this will embed the micro apk,
     * if null this will trigger the unbundled mode.
     */
    private fun createGenerateMicroApkDataTask(
        variantProperties: ApplicationVariantPropertiesImpl,
        config: FileCollection? = null
    ) {
        val generateMicroApkTask =
            taskFactory.register(
                GenerateApkDataTask.CreationAction(
                    variantProperties,
                    config
                )
            )
        // the merge res task will need to run after this one.
        variantProperties.taskContainer.resourceGenTask.dependsOn(
            generateMicroApkTask
        )
    }

    private fun createAssetPackTasks(variantProperties: VariantPropertiesImpl) {
        val depHandler = project.dependencies
        val notFound: MutableList<String> =
            ArrayList()
        val assetPackFilesConfiguration =
            project.configurations.maybeCreate("assetPackFiles")
        val assetPackManifestConfiguration =
            project.configurations.maybeCreate("assetPackManifest")
        var needToRegisterAssetPackTasks = false
        val assetPacks: Set<String> =
            (extension as BaseAppModuleExtension).assetPacks
        for (assetPack in assetPacks) {
            if (project.findProject(assetPack) != null) {
                val filesDependency: Map<String, String?> =
                    ImmutableMap.of<String, String?>(
                        "path",
                        assetPack,
                        "configuration",
                        "packElements"
                    )
                depHandler.add("assetPackFiles", depHandler.project(filesDependency))
                val manifestDependency: Map<String, String?> =
                    ImmutableMap.of<String, String?>(
                        "path",
                        assetPack,
                        "configuration",
                        "manifestElements"
                    )
                depHandler.add("assetPackManifest", depHandler.project(manifestDependency))
                needToRegisterAssetPackTasks = true
            } else {
                notFound.add(assetPack)
            }
        }
        if (needToRegisterAssetPackTasks) {
            val assetPackManifest =
                assetPackManifestConfiguration.incoming.files
            val assetFiles = assetPackFilesConfiguration.incoming.files
            taskFactory.register(
                ProcessAssetPackManifestTask.CreationAction(
                    variantProperties,
                    assetPackManifest,
                    assetPacks
                        .stream()
                        .map { assetPackName: String ->
                            assetPackName.replace(
                                ":",
                                File.separator
                            )
                        }
                        .collect(Collectors.toSet())
                )
            )
            taskFactory.register(
                LinkManifestForAssetPackTask.CreationAction(
                    variantProperties
                )
            )
            taskFactory.register(
                AssetPackPreBundleTask.CreationAction(
                    variantProperties,
                    assetFiles
                )
            )
        }
        if (!notFound.isEmpty()) {
            logger.error("Unable to find matching projects for Asset Packs: $notFound")
        }
    }

    private fun createDynamicBundleTask(variantProperties: VariantPropertiesImpl) {

        // If namespaced resources are enabled, LINKED_RES_FOR_BUNDLE is not generated,
        // and the bundle can't be created. For now, just don't add the bundle task.
        // TODO(b/111168382): Remove this
        if (variantProperties.globalScope.extension.aaptOptions.namespaced) {
            return
        }

        taskFactory.register(
            PerModuleBundleTask.CreationAction(
                variantProperties,
                TaskManager.packagesCustomClassDependencies(variantProperties, projectOptions)
            )
        )

        val addDependenciesTask = addDependenciesTask(variantProperties)

        if (addDependenciesTask) {
            taskFactory.register(PerModuleReportDependenciesTask.CreationAction(variantProperties))
        }
        if (variantProperties.variantType.isBaseModule) {
            taskFactory.register(ParseIntegrityConfigTask.CreationAction(variantProperties))
            taskFactory.register(PackageBundleTask.CreationAction(variantProperties))
            taskFactory.register(FinalizeBundleTask.CreationAction(variantProperties))
            if (addDependenciesTask) {
                taskFactory.register(BundleReportDependenciesTask.CreationAction(variantProperties))
                if (variantProperties.globalScope
                        .projectOptions[BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS]
                ) {
                    taskFactory.register(SdkDependencyDataGeneratorTask.CreationAction(variantProperties))
                }
            }
            taskFactory.register(BundleToApkTask.CreationAction(variantProperties))
            taskFactory.register(BundleToStandaloneApkTask.CreationAction(variantProperties))
            taskFactory.register(ExtractApksTask.CreationAction(variantProperties))
        }
    }

    private fun createSoftwareComponent(
        variantProperties: VariantPropertiesImpl,
        suffix: String,
        publication: PublishedConfigType
    ) {
        val component = globalScope.componentFactory.adhoc(variantProperties.name + suffix)
        val config = variantProperties.variantDependencies.getElements(publication)!!
        component.addVariantsFromConfiguration(config) { }
        project.components.add(component)
    }

    override fun createInstallTask(componentProperties: ComponentPropertiesImpl) {
        if (extension is BaseAppModuleExtension && extension.dynamicFeatures.isEmpty()) {
            // no dynamic features means we can just use the standard install task
            super.createInstallTask(componentProperties)
        } else {
            // need to install via bundle
            taskFactory.register(InstallVariantViaBundleTask.CreationAction(componentProperties))
        }
    }

    private fun addDependenciesTask(variantProperties: VariantPropertiesImpl): kotlin.Boolean {
        return !variantProperties.variantDslInfo.isDebuggable && extension is BaseAppModuleExtension
          && (extension as BaseAppModuleExtension).dependenciesInfo.include
    }
}
