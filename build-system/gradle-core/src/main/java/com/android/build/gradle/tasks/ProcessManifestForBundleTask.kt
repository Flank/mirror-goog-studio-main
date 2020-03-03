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
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestModel
import com.android.manifmerger.XmlDocument
import com.android.utils.PositionXmlParser
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.w3c.dom.Document
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.Serializable
import javax.inject.Inject

/**
 * Task that consumes [InternalArtifactType.MERGED_MANIFESTS] to produce a unique Android Manifest
 * file suitable for the bundle tool.
 *
 * The merged manifest will be annotated by added android:splitName to all activities in case the
 * module is a dynamic feature module, otherwise it wil be unchanged.
 */
abstract class ProcessManifestForBundleTask @Inject constructor(
    objects: ObjectFactory,
    workers: WorkerExecutor): NonIncrementalTask() {

    @get:OutputFile
    abstract val bundleManifest: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val applicationMergedManifests: DirectoryProperty

    @get:Input
    @get:org.gradle.api.tasks.Optional
    abstract val featureName: Property<String>

    @get:Nested
    abstract val mainSplit: Property<VariantOutputImpl>

    @get:Internal
    val model = ManifestModel(false)

    // Use a property to hold the [WorkerExecutor] so unit tests can reset it if necessary.
    @get:Internal
    val workersProperty: Property<WorkerExecutor> = objects.property(WorkerExecutor::class.java)

    init {
        workersProperty.set(workers)
    }

    @TaskAction
    override fun doTaskAction() {

        val builtArtifact = BuiltArtifactsLoaderImpl().load(applicationMergedManifests)
            ?.getBuiltArtifact(mainSplit.get())
            ?: throw RuntimeException("Cannot find main split from generated manifest files at" +
                    " ${applicationMergedManifests.asFile.get().absolutePath}")

        workersProperty.get().noIsolation().submit(WorkItem::class.java) {
            it.featureName.set(featureName)
            it.inputXmlFile.set(File(builtArtifact.outputFile))
            it.outputXmlFile.set(bundleManifest)
        }
    }

    interface WorkItemParameters: WorkParameters, Serializable {
        val inputXmlFile: RegularFileProperty
        val outputXmlFile: RegularFileProperty
        val featureName: Property<String>
    }

    abstract class WorkItem@Inject constructor(private val workItemParameters: WorkItemParameters)
        : WorkAction<WorkItemParameters> {
        override fun execute() {
            // if there is no feature name to write, just use the original merged manifest file.
            if (!workItemParameters.featureName.isPresent) {
                workItemParameters.inputXmlFile.get().asFile.copyTo(
                    target = workItemParameters.outputXmlFile.get().asFile, overwrite = true)
                return
            }

            val xmlDocument = BufferedInputStream(FileInputStream(
                workItemParameters.inputXmlFile.get().asFile)).use {
                PositionXmlParser.parse(it)
            }
            adjustInstantAppFeatureSplitInfo(document = xmlDocument,
                featureName = workItemParameters.featureName.get())
            workItemParameters.outputXmlFile.get().asFile.writeText(XmlDocument.prettyPrint(xmlDocument))
        }

        /**
         * Set the "android:splitName" attribute to `featureName` for every `activity`,
         * `service` and `provider` element.
         *
         * @param document the document whose attributes are changed
         * @param featureName the value all of the changed attributes are set to
         */
        private fun adjustInstantAppFeatureSplitInfo(
            document: Document, featureName: String
        ) {
            val manifest = document.documentElement ?: return
            // then update attributes in the application element's child elements
            val applicationElements =
                ManifestMerger2.getChildElementsByName(manifest, SdkConstants.TAG_APPLICATION)
            if (applicationElements.isEmpty()) {
                return
            }
            // assumes just 1 application element among manifest's immediate children.
            val application = applicationElements[0]
            val elementNamesToUpdate =
                listOf(
                    SdkConstants.TAG_ACTIVITY,
                    SdkConstants.TAG_SERVICE,
                    SdkConstants.TAG_PROVIDER
                )
            for (elementName in elementNamesToUpdate) {
                for (elementToUpdate in ManifestMerger2.getChildElementsByName(
                    application,
                    elementName
                )) {
                    ManifestMerger2.setAndroidAttribute(
                        elementToUpdate,
                        SdkConstants.ATTR_SPLIT_NAME,
                        featureName
                    )
                }
            }
        }
    }

    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<ProcessManifestForBundleTask, ApkCreationConfig>(
            creationConfig = creationConfig
        ) {
        override val name: String
            get() = computeTaskName("processApplicationManifest", "ForBundle")
        override val type: Class<ProcessManifestForBundleTask>
            get() = ProcessManifestForBundleTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ProcessManifestForBundleTask>) {
            super.handleProvider(taskProvider)
            creationConfig.operations.setInitialProvider(
                taskProvider,
                ProcessManifestForBundleTask::bundleManifest
            )
                .withName(SdkConstants.ANDROID_MANIFEST_XML)
                .on(InternalArtifactType.BUNDLE_MANIFEST)
        }

        override fun configure(task: ProcessManifestForBundleTask) {
            super.configure(task)
            task.workersProperty.disallowChanges()
            creationConfig.operations.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_MANIFESTS,
                task.applicationMergedManifests
            )
            task.mainSplit.setDisallowChanges(creationConfig.outputs.getMainSplit())
            if (creationConfig.variantType.isDynamicFeature) {
                val dfCreationConfig =
                    creationConfig as DynamicFeatureCreationConfig

                task.featureName.setDisallowChanges(dfCreationConfig.featureName)
            }
        }
    }
}