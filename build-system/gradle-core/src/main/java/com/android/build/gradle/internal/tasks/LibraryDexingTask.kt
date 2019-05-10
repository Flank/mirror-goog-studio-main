/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.Workers.preferWorkers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * This is implementation of dexing artifact transform as a task. It is used when building
 * android test variant for library projects. Once http://b/115334911 is fixed, this can be removed.
 */
@CacheableTask
abstract class LibraryDexingTask @Inject constructor(
    objectFactory: ObjectFactory,
    executor: WorkerExecutor) : NonIncrementalTask() {

    private val workers: WorkerExecutorFacade =
        preferWorkers(project.name, path, executor, MoreExecutors.newDirectExecutorService())

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: RegularFileProperty

    @get:OutputDirectory
    var output: Provider<Directory> = objectFactory.directoryProperty()
        private set

    @get:Input
    var minSdkVersion = 1
        private set

    @get:Input
    abstract val enableDesugaring: Property<Boolean>

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:Internal
    lateinit var errorFormatMode: SyncOptions.ErrorFormatMode
        private set

    override fun doTaskAction() {
        workers.use {
            it.submit(
                DexingRunnable::class.java,
                DexParams(
                    minSdkVersion,
                    errorFormatMode,
                    classes.get().asFile,
                    output.get().asFile,
                    enableDesugaring = enableDesugaring.get(),
                    bootClasspath = bootClasspath.files,
                    classpath = classpath.files
                )
            )
        }
    }

    class CreationAction(val scope: VariantScope) :
        VariantTaskCreationAction<LibraryDexingTask>(scope) {
        override val name = scope.getTaskName("dex")
        override val type = LibraryDexingTask::class.java

        private lateinit var output: Provider<Directory>

        override fun preConfigure(taskName: String) {
            output = scope.artifacts.createDirectory(InternalArtifactType.DEX, taskName)
        }

        override fun configure(task: LibraryDexingTask) {
            super.configure(task)
            scope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.RUNTIME_LIBRARY_CLASSES,
                task.classes
            )
            task.minSdkVersion = scope.minSdkVersion.featureLevel
            task.output = output
            task.errorFormatMode =
                SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)
            if (scope.java8LangSupportType == VariantScope.Java8LangSupport.D8) {
                task.enableDesugaring.set(true)
                task.bootClasspath.from(scope.globalScope.bootClasspath)
                task.classpath.from(
                    scope.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.CLASSES
                    )
                )
            } else {
                task.enableDesugaring.set(false)
            }
        }
    }
}

private class DexParams(
    val minSdkVersion: Int,
    val errorFormatMode: SyncOptions.ErrorFormatMode,
    val input: File,
    val output: File,
    val enableDesugaring: Boolean,
    val bootClasspath: Collection<File>,
    val classpath: Collection<File>
) : Serializable

private class DexingRunnable @Inject constructor(val params: DexParams) : Runnable {
    override fun run() {
        ClassFileProviderFactory(params.bootClasspath.map(File::toPath)).use { bootClasspath ->
            ClassFileProviderFactory(params.classpath.map(File::toPath)).use { classpath ->
                val d8DexBuilder = DexArchiveBuilder.createD8DexBuilder(
                    params.minSdkVersion,
                    true,
                    bootClasspath,
                    classpath,
                    params.enableDesugaring,
                    MessageReceiverImpl(
                        params.errorFormatMode,
                        Logging.getLogger(LibraryDexingTask::class.java)
                    )
                )

                ClassFileInputs.fromPath(params.input.toPath()).use { classFileInput ->
                    classFileInput.entries { _ -> true }.use { classesInput ->
                        d8DexBuilder.convert(
                            classesInput,
                            params.output.toPath(),
                            false
                        )
                    }
                }
            }
        }
    }
}