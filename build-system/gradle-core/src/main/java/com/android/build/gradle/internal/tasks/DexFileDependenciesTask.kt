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

import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.google.common.io.Closer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

open class DexFileDependenciesTask
@Inject constructor(objectFactory: ObjectFactory, private val workerExecutor: WorkerExecutor) :
    NonIncrementalTask() {

    @get:OutputDirectory
    val outputDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:Classpath
    val classes: ConfigurableFileCollection = objectFactory.fileCollection()

    @get:CompileClasspath
    val classpath: ConfigurableFileCollection = objectFactory.fileCollection()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    val bootClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    @get:Input
    val minSdkVersion: Property<Int> = objectFactory.property(Int::class.java)

    @get:Input
    val debuggable: Property<Boolean> = objectFactory.property(Boolean::class.java)

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    // TODO: make incremental
    override fun doTaskAction() {
       Workers.preferWorkers(project.name, path, workerExecutor).use { workerExecutorFacade->
           val inputs = classes.files.toList()
           val totalClasspath = inputs + classpath.files
           val outDir = outputDirectory.get().asFile
           inputs.forEachIndexed { index, input ->
               // Desugar each jar with reference to all the others
               workerExecutorFacade.submit(
                   DexFileDependenciesWorkerAction::class.java,
                   DexFileDependenciesWorkerActionParams(
                       minSdkVersion = minSdkVersion.get(),
                       debuggable = debuggable.get(),
                       bootClasspath = bootClasspath.files,
                       classpath = totalClasspath,
                       input = input,
                       outputFile = outDir.resolve("${index}_${input.name}"),
                       errorFormatMode = errorFormatMode
                   )
               )
           }
       }
    }

    data class DexFileDependenciesWorkerActionParams(
        val minSdkVersion: Int,
        val debuggable: Boolean,
        val bootClasspath: Collection<File>,
        val classpath: Collection<File>,
        val input: File,
        val outputFile: File,
        val errorFormatMode: SyncOptions.ErrorFormatMode
    ) : Serializable

    class DexFileDependenciesWorkerAction @Inject constructor(private val params: DexFileDependenciesWorkerActionParams) :
        Runnable {

        override fun run() {
            val bootClasspath = params.bootClasspath.map(File::toPath)
            val classpath = params.classpath.map(File::toPath)
            Closer.create().use { closer ->
                val d8DexBuilder = DexArchiveBuilder.createD8DexBuilder(
                    params.minSdkVersion,
                    params.debuggable,
                    ClassFileProviderFactory(bootClasspath).also { closer.register(it) },
                    ClassFileProviderFactory(classpath).also { closer.register(it) },
                    true,
                    MessageReceiverImpl(
                        errorFormatMode = params.errorFormatMode,
                        logger = Logging.getLogger(DexFileDependenciesWorkerAction::class.java)
                    )
                )


                ClassFileInputs.fromPath(params.input.toPath()).use { classFileInput ->
                    classFileInput.entries { true }.use { classesInput ->
                        d8DexBuilder.convert(
                            classesInput,
                            params.outputFile.toPath(),
                            false
                        )
                    }
                }
            }
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<DexFileDependenciesTask>(variantScope) {
        override val name: String = variantScope.getTaskName("desugar", "FileDependencies")
        override val type = DexFileDependenciesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out DexFileDependenciesTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                artifactType = InternalArtifactType.EXTERNAL_FILE_LIB_DEX_ARCHIVES,
                operationType = BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider = taskProvider,
                product = taskProvider.map(DexFileDependenciesTask::outputDirectory),
                fileName = "out"
            )
        }

        override fun configure(task: DexFileDependenciesTask) {
            super.configure(task)
            task.debuggable.set(variantScope.variantConfiguration.buildType.isDebuggable)
            task.minSdkVersion.set(variantScope.minSdkVersion.featureLevel)
            task.classes.from(
                variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.FILE,
                    AndroidArtifacts.ArtifactType.PROCESSED_JAR
                )
            )
            task.classpath.from(
                variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.REPOSITORY_MODULE,
                    AndroidArtifacts.ArtifactType.PROCESSED_JAR
                )
            )
            task.bootClasspath.from(variantScope.globalScope.bootClasspath)
            task.errorFormatMode =
                SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)
        }
    }
}