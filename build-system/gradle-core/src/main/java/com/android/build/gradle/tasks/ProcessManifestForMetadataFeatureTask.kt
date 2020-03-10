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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.XmlDocument
import com.android.utils.PositionXmlParser
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.Serializable
import java.util.function.Consumer
import javax.inject.Inject

abstract class ProcessManifestForMetadataFeatureTask @Inject constructor(
    objects: ObjectFactory,
    workers: WorkerExecutor): NonIncrementalTask() {

    @get:OutputFile
    abstract val metadataFeatureManifest: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val bundleManifest: RegularFileProperty

    @get:Input
    abstract val dynamicFeature: Property<Boolean>

    // Use a property to hold the [WorkerExecutor] so unit tests can reset it if necessary.
    @get:Internal
    val workersProperty: Property<WorkerExecutor> = objects.property(WorkerExecutor::class.java)

    init {
        workersProperty.set(workers)
    }

    @TaskAction
    override fun doTaskAction() {

        val inputFile = bundleManifest.get().asFile
        val metadataFeatureManifestFile = metadataFeatureManifest.get().asFile
        // if there is no feature name to write, just use the original merged manifest file.
        if (!dynamicFeature.get()) {
            inputFile.copyTo(target = metadataFeatureManifestFile, overwrite = true)
            return
        }

        workersProperty.get().noIsolation().submit(WorkItem::class.java) {
            it.inputXmlFile.set(bundleManifest)
            it.outputXmlFile.set(metadataFeatureManifestFile)
        }
    }

    interface WorkItemParameters: WorkParameters, Serializable {
        val inputXmlFile: RegularFileProperty
        val outputXmlFile: RegularFileProperty
    }

    abstract class WorkItem@Inject constructor(private val workItemParameters: WorkItemParameters)
        : WorkAction<WorkItemParameters> {
        override fun execute() {
            val xmlDocument = BufferedInputStream(
                FileInputStream(
                    workItemParameters.inputXmlFile.get().asFile
                )
            ).use {
                PositionXmlParser.parse(it)
            }
            stripMinSdkFromFeatureManifest(xmlDocument)
            stripUsesSplitFromFeatureManifest(xmlDocument)
            workItemParameters.outputXmlFile.get().asFile.writeText(
                XmlDocument.prettyPrint(xmlDocument))
        }

        /**
         * This will strip the min sdk from the feature manifest, used to merge it back into the base
         * module. This is used in dynamic-features, as dynamic-features can have different min sdk than
         * the base module. It doesn't need to be strictly <= the base module like libraries.
         *
         * @param document the resulting document to use for stripping the min sdk from.
         */
        private fun stripMinSdkFromFeatureManifest(document: Document) {
            // make changes necessary for metadata feature manifest
            val manifest = document.documentElement
            val usesSdkList =
                ManifestMerger2.getChildElementsByName(manifest, SdkConstants.TAG_USES_SDK)
            val usesSdk: Element
            if (!usesSdkList.isEmpty()) {
                usesSdk = usesSdkList[0]
                usesSdk.removeAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_MIN_SDK_VERSION
                )
            }
        }

        /**
         * This will strip uses-split from the feature manifest used to merge it back into the base
         * module and features that require it. If featureB depends on featureA, we don't want the
         * `<uses split android:name="featureA"/>` from featureB's manifest to appear in
         * featureA's manifest after merging.
         *
         * @param document the resulting document to use for stripping the min sdk from.
         */
        private fun stripUsesSplitFromFeatureManifest(document: Document) {
            // make changes necessary for metadata feature manifest
            val manifest = document.documentElement
            val usesSplitList =
                ManifestMerger2.getChildElementsByName(
                    manifest,
                    SdkConstants.TAG_USES_SPLIT
                )
            usesSplitList.forEach(Consumer { node: Element? ->
                manifest.removeChild(node)
            })
        }
    }

    class CreationAction(creationConfig: BaseCreationConfig) :
        VariantTaskCreationAction<ProcessManifestForMetadataFeatureTask, BaseCreationConfig>(
            creationConfig = creationConfig
        ) {
        override val name: String
            get() = computeTaskName("processManifest", "ForFeature")
        override val type: Class<ProcessManifestForMetadataFeatureTask>
            get() = ProcessManifestForMetadataFeatureTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ProcessManifestForMetadataFeatureTask>) {
            super.handleProvider(taskProvider)
            creationConfig.operations.setInitialProvider(
                taskProvider,
                    ProcessManifestForMetadataFeatureTask::metadataFeatureManifest
            )
                .withName(SdkConstants.ANDROID_MANIFEST_XML)
                .on(InternalArtifactType.METADATA_FEATURE_MANIFEST)
        }

        override fun configure(task: ProcessManifestForMetadataFeatureTask) {
            super.configure(task)
            task.workersProperty.disallowChanges()
            creationConfig.operations.setTaskInputToFinalProduct(
                InternalArtifactType.BUNDLE_MANIFEST,
                task.bundleManifest
            )
            task.dynamicFeature.set(creationConfig.variantType.isDynamicFeature)
        }
    }
}