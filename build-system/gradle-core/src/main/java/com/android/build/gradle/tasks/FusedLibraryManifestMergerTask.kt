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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputs
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.manifest.ManifestProviderImpl
import com.android.build.gradle.internal.tasks.manifest.mergeManifests
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.tasks.ProcessApplicationManifest.Companion.getArtifactName
import com.android.build.gradle.tasks.ShaderCompile.WorkAction
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestProvider
import com.android.utils.FileUtils
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.utils.ProviderDelegate
import org.jetbrains.kotlin.gradle.utils.property
import org.jetbrains.kotlin.gradle.utils.provider
import java.io.File

/**
 * Merges Manifests from libraries that will be included with in fused library.
 */
@CacheableTask
abstract class FusedLibraryManifestMergerTask : ManifestProcessorTask() {

    @get:OutputFile
    abstract val mergedFusedLibraryManifest: RegularFileProperty

    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Input
    @get:Optional
    abstract val targetSdk: Property<Int>

    @get:Internal
    abstract val tmpDir: DirectoryProperty

    @get:Input
    abstract val identifierToManifestDependencyFile: MapProperty<String, File>

    /* For adding a dependency on the files used in identifierToManifestDependencyFile. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val libraryManifestFiles: ConfigurableFileCollection

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(FusedLibraryManifestMergerWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.tmpAndroidManifest.set(tmpDir)
            it.dependencies.set(identifierToManifestDependencyFile)
            it.namespace.set(namespace)
            it.minSdk.set(minSdk)
            it.targetSdk.set(targetSdk)
            it.outMergedManifestLocation.set(mergedFusedLibraryManifest)
            it.reportFile.set(reportFile)
        }
    }

    abstract class FusedLibraryManifestMergerParams: ProfileAwareWorkAction.Parameters() {
        abstract val tmpAndroidManifest: DirectoryProperty
        abstract val dependencies: MapProperty<String, File>
        abstract val namespace: Property<String>
        abstract val minSdk: Property<Int>
        abstract val targetSdk: Property<Int>
        abstract val outMergedManifestLocation: RegularFileProperty
        abstract val reportFile: RegularFileProperty
    }
    abstract class FusedLibraryManifestMergerWorkAction
        : ProfileAwareWorkAction<FusedLibraryManifestMergerParams>() {

        override fun run() {
            with(parameters!!) {
                FileUtils.cleanOutputDir(tmpAndroidManifest.get().asFile)
                val dependencyManifests =
                        dependencies.get().map { ManifestProviderImpl(it.value, it.key) }
                mergeManifests(
                        mainManifest = createTempLibraryManifest(
                                tmpAndroidManifest.get().asFile,
                                namespace.get()),
                        manifestOverlays = emptyList(),
                        dependencies = dependencyManifests,
                        navigationJsons = emptyList(),
                        featureName = null,
                        packageOverride = namespace.get(),
                        namespace = namespace.get(),
                        profileable = false,
                        versionCode = null,
                        versionName = null,
                        minSdkVersion = minSdk.get().toString(),
                        targetSdkVersion = targetSdk.orNull?.toString() ?: minSdk.get().toString(),
                        maxSdkVersion = null,
                        testOnly = false,
                        outMergedManifestLocation = outMergedManifestLocation.get().asFile.absolutePath,
                        outAaptSafeManifestLocation = null,
                        mergeType = ManifestMerger2.MergeType.FUSED_LIBRARY,
                        placeHolders = emptyMap(),
                        optionalFeatures = listOf(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT),
                        dependencyFeatureNames = emptyList(),
                        reportFile = reportFile.get().asFile,
                        logger = LoggerWrapper.getLogger(FusedLibraryManifestMergerTask::class.java))
            }
        }
    }
    class CreationAction(val creationConfig: FusedLibraryVariantScope) :
            TaskCreationAction<FusedLibraryManifestMergerTask>() {

        override val name: String
            get() = "mergeManifest"
        override val type: Class<FusedLibraryManifestMergerTask>
            get() = FusedLibraryManifestMergerTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryManifestMergerTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryManifestMergerTask::mergedFusedLibraryManifest
            ).withName(FN_ANDROID_MANIFEST_XML)
                    .on(FusedLibraryInternalArtifactType.MERGED_MANIFEST)

            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryManifestMergerTask::reportFile
            ).atLocation(
                    FileUtils.join(
                            creationConfig.layout.projectDirectory.asFile,
                            "build",
                            SdkConstants.FD_OUTPUTS,
                            SdkConstants.FD_LOGS
                    ).absolutePath
            ).withName("manifest-merger-$name-report.txt")
                    .on(FusedLibraryInternalArtifactType.MANIFEST_MERGE_REPORT)
            SdkConstants.FD_OUTPUT
        }

        override fun configure(task: FusedLibraryManifestMergerTask) {
            val libraryManifests = creationConfig.dependencies.getArtifactCollection(
                    Usage.JAVA_RUNTIME,
                    creationConfig.mergeSpec,
                    AndroidArtifacts.ArtifactType.MANIFEST
            )
            task.libraryManifestFiles.setFrom(libraryManifests.artifactFiles)
            task.identifierToManifestDependencyFile.set(
                    libraryManifests.associate { getArtifactName(it) to it.file }
            )
            task.minSdk.setDisallowChanges(creationConfig.extension.minSdk)
            task.targetSdk.setDisallowChanges(creationConfig.extension.targetSdk)
            task.namespace.set(creationConfig.extension.namespace)
            task.tmpDir.setDisallowChanges(
                    creationConfig.layout.buildDirectory.dir("tmp/FusedLibraryManifestMerger")
            )
            task.analyticsService.set(
                    getBuildService(task.project.gradle.sharedServices)
            )
        }
    }
}
