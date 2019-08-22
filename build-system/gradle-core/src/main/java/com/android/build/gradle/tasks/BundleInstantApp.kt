/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants.DOT_ZIP
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InstantAppOutputScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.ModuleMetadata
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.tools.build.apkzlib.zip.compress.DeflateExecutionCompressor
import com.android.utils.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.Serializable
import java.io.UncheckedIOException
import java.util.ArrayList
import java.util.TreeSet
import java.util.zip.Deflater
import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/** Task to bundle a bundle of feature APKs.  */
abstract class BundleInstantApp : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val bundleDirectory: DirectoryProperty

    @get:Input
    abstract val bundleName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var applicationMetadataFile: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var apkDirectories: FileCollection
        private set

    override fun doTaskAction() {
        // FIXME: Make this task incremental.
        getWorkerFacadeWithWorkers().use { workers ->
            workers.submit(
                BundleInstantAppRunnable::class.java,
                BundleInstantAppParams(
                    projectName,
                    path,
                    bundleDirectory.get().asFile,
                    bundleName.get(),
                    ModuleMetadata.load(applicationMetadataFile.singleFile)
                        .applicationId,
                    TreeSet(apkDirectories.files)
                )
            )
        }
    }

    class CreationAction(private val scope: VariantScope, private val bundleDirectory: File) :
        TaskCreationAction<BundleInstantApp>() {

        override val name: String
            get() = scope.getTaskName("package", "InstantAppBundle")

        override val type: Class<BundleInstantApp>
            get() = BundleInstantApp::class.java

        override fun handleProvider(taskProvider: TaskProvider<out BundleInstantApp>) {
            super.handleProvider(taskProvider)
            scope.artifacts
                .producesDir(
                    InternalArtifactType.INSTANTAPP_BUNDLE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    BundleInstantApp::bundleDirectory,
                    bundleDirectory.absolutePath,
                    ""
                )
        }

        override fun configure(task: BundleInstantApp) {
            task.variantName = scope.fullVariantName

            task.bundleName.set(
                "${scope.globalScope.projectBaseName}-${scope.variantConfiguration.baseName}$DOT_ZIP"
            )

            task.applicationMetadataFile = scope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION
            )

            task.apkDirectories = scope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.APK
            )
        }
    }

    private class BundleInstantAppRunnable @Inject
    constructor(private val params: BundleInstantAppParams) : Runnable {

        override fun run() {
            try {
                FileUtils.mkdirs(params.bundleDirectory)

                val bundleFile = File(params.bundleDirectory, params.bundleName)
                FileUtils.deleteIfExists(bundleFile)

                val zFileOptions = ZFileOptions()

                Workers.withThreads(params.projectName, params.taskOwnerName).use { executor ->
                    zFileOptions.compressor = DeflateExecutionCompressor(
                        { compressJob ->
                            executor.submit(
                                CompressorRunnable::class.java,
                                CompressorParams(compressJob)
                            )
                        },
                        Deflater.DEFAULT_COMPRESSION
                    )
                    ZFile.openReadWrite(bundleFile, zFileOptions).use { file ->
                        for (apkDirectory in params.apkDirectories) {
                            for (buildOutput in ExistingBuildElements.from(
                                InternalArtifactType.APK, apkDirectory
                            )) {
                                val apkFile = buildOutput.outputFile
                                FileInputStream(apkFile).use { fileInputStream ->
                                    file.add(
                                        apkFile.name,
                                        fileInputStream
                                    )
                                }
                            }
                        }
                    }
                }

                // Write the json output.
                val instantAppOutputScope = InstantAppOutputScope(
                    params.applicationId,
                    bundleFile,
                    ArrayList(params.apkDirectories)
                )
                instantAppOutputScope.save(params.bundleDirectory)
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }

        }

        internal class CompressorRunnable @Inject
        constructor(params: CompressorParams) : Runnable {
            private val compressJob: Runnable

            init {
                this.compressJob = params.compressJob
            }

            override fun run() {
                compressJob.run()
            }
        }

        private class CompressorParams(val compressJob: Runnable) : Serializable
    }

    private class BundleInstantAppParams internal constructor(
        val projectName: String,
        val taskOwnerName: String,
        val bundleDirectory: File,
        val bundleName: String,
        val applicationId: String,
        val apkDirectories: Set<File>
    ) : Serializable
}
