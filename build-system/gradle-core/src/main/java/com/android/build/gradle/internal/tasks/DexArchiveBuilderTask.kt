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

import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.DexOptions
import com.android.builder.dexing.DexerTool
import com.android.builder.utils.FileCache
import com.android.ide.common.blame.MessageReceiver
import com.android.sdklib.AndroidVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import javax.inject.Inject

/**
 * Task that converts CLASS files to dex archives, [com.android.builder.dexing.DexArchive].
 * This will process class files, and for each of the input scopes (project, subprojects, external
 * Maven libraries, mixed-scope classses), corresponding dex archive will be produced.
 *
 * This task is incremental, only changed classes will be converted again. If only a single class
 * file is changed, only that file will be dex'ed. Additionally, if a jar changes, only classes in
 * that jar will be dex'ed.
 */
@CacheableTask
abstract class DexArchiveBuilderTask @Inject constructor(objectFactory: ObjectFactory) :
    NewIncrementalTask() {

    @get:CompileClasspath
    abstract val androidJarClasspath: ConfigurableFileCollection

    @get:Incremental
    @get:Classpath
    abstract val projectClasses: ConfigurableFileCollection
    @get:Incremental
    @get:Classpath
    abstract val subProjectClasses: ConfigurableFileCollection
    @get:Incremental
    @get:Classpath
    abstract val externalLibClasses: ConfigurableFileCollection
    /**
     * These are classes that contain multiple transform API scopes. E.g. if there is a transform
     * running before this task that outputs classes with both project and subProject scopes, this
     * input will contain them.
     */
    @get:Incremental
    @get:Classpath
    abstract val mixedScopeClasses: ConfigurableFileCollection

    @get:Incremental
    @get:CompileClasspath
    abstract val desugaringClasspathClasses: ConfigurableFileCollection

    @get:Input
    val errorFormatMode: Property<SyncOptions.ErrorFormatMode> =
        objectFactory.property(SyncOptions.ErrorFormatMode::class.java)
    @get:Input
    val minSdkVersion: Property<Int> = objectFactory.property(Int::class.java)
    @get:Input
    val dexer: Property<DexerTool> = objectFactory.property(DexerTool::class.java)
    @get:Input
    val useGradleWorkers: Property<Boolean> = objectFactory.property(Boolean::class.java)
    @get:Input
    val inBufferSize: Property<Int> = objectFactory.property(Int::class.java)
    @get:Input
    val outBufferSize: Property<Int> = objectFactory.property(Int::class.java)
    @get:Input
    val debuggable: Property<Boolean> = objectFactory.property(Boolean::class.java)
    @get:Input
    val java8LangSupportType: Property<VariantScope.Java8LangSupport> =
        objectFactory.property(VariantScope.Java8LangSupport::class.java)
    @get:Input
    val projectVariant: Property<String> = objectFactory.property(String::class.java)
    @get:Input
    val numberOfBuckets: Property<Int> = objectFactory.property(Int::class.java)
    @get:Input
    val dxNoOptimizeFlagPresent: Property<Boolean> = objectFactory.property(Boolean::class.java)
    @get:Optional
    @get:Input
    val libConfiguration: Property<String> = objectFactory.property(String::class.java)

    @get:OutputDirectory
    abstract val projectOutputDex: DirectoryProperty
    @get:OutputDirectory
    abstract val subProjectOutputDex: DirectoryProperty
    @get:OutputDirectory
    abstract val externalLibsOutputDex: DirectoryProperty
    @get:OutputDirectory
    abstract val mixedScopeOutputDex: DirectoryProperty
    @get:LocalState
    abstract val inputJarHashesFile: RegularFileProperty

    private lateinit var messageReceiver: MessageReceiver
    private var userLevelCache: FileCache? = null

    override fun doTaskAction(inputChanges: InputChanges) {

        DexArchiveBuilderTaskDelegate(
            isIncremental = inputChanges.isIncremental,
            androidJarClasspath = androidJarClasspath.files,
            projectClasses = projectClasses.files,
            projectChangedClasses = getChanged(inputChanges, projectClasses),
            subProjectClasses = subProjectClasses.files,
            subProjectChangedClasses = getChanged(inputChanges, subProjectClasses),
            externalLibClasses = externalLibClasses.files,
            externalLibChangedClasses = getChanged(inputChanges, externalLibClasses),
            mixedScopeClasses = mixedScopeClasses.files,
            mixedScopeChangedClasses = getChanged(inputChanges, mixedScopeClasses),

            projectOutputDex = projectOutputDex.asFile.get(),
            subProjectOutputDex = subProjectOutputDex.asFile.get(),
            externalLibsOutputDex = externalLibsOutputDex.asFile.get(),
            mixedScopeOutputDex = mixedScopeOutputDex.asFile.get(),
            inputJarHashesFile = inputJarHashesFile.get().asFile,

            desugaringClasspathClasses = desugaringClasspathClasses.files,
            desugaringClasspathChangedClasses = getChanged(
                inputChanges,
                desugaringClasspathClasses
            ),

            errorFormatMode = errorFormatMode.get(),
            minSdkVersion = minSdkVersion.get(),
            dexer = dexer.get(),
            useGradleWorkers = useGradleWorkers.get(),
            inBufferSize = inBufferSize.get(),
            outBufferSize = outBufferSize.get(),
            isDebuggable = debuggable.get(),
            java8LangSupportType = java8LangSupportType.get(),
            projectVariant = projectVariant.get(),
            numberOfBuckets = numberOfBuckets.get(),

            messageReceiver = messageReceiver,
            isDxNoOptimizeFlagPresent = dxNoOptimizeFlagPresent.get(),
            libConfiguration = libConfiguration.orNull,
            workerExecutor = workerExecutor,
            userLevelCache = userLevelCache
        ).doProcess()
    }

    /**
     * Some files will be reported as both added and removed, as order of inputs may shift and we
     * are using @Classpath on inputs. For those, ignore the removed change,
     * and just handle them as added. For non-incremental builds return an empty set as dexing
     * pipeline traverses directories and we'd like to avoid serializing this information to the
     * worker action.
     */
    private fun getChanged(inputChanges: InputChanges, input: FileCollection): Set<FileChange> {
        if (!inputChanges.isIncremental) {
            return emptySet()
        }
        val fileChanges = mutableMapOf<File, FileChange>()

        inputChanges.getFileChanges(input).forEach { change ->
            val currentValue = fileChanges[change.file]
            if (currentValue == null || (currentValue.changeType == ChangeType.REMOVED && change.changeType == ChangeType.ADDED)) {
                fileChanges[change.file] = change
            }
        }
        return fileChanges.values.toSet()
    }

    class CreationAction(
        private val dexOptions: DexOptions,
        enableDexingArtifactTransform: Boolean,
        private val userLevelCache: FileCache?,
        variantScope: VariantScope
    ) : VariantTaskCreationAction<DexArchiveBuilderTask>(variantScope) {

        override val name = variantScope.getTaskName("dexBuilder")

        private val projectClasses: FileCollection
        private val subProjectsClasses: FileCollection
        private val externalLibraryClasses: FileCollection
        private val mixedScopeClasses: FileCollection
        private val desugaringClasspathClasses: FileCollection

        init {
            val classesFilter =
                StreamFilter { types, _ -> DefaultContentType.CLASSES in types }

            projectClasses = variantScope.transformManager.getPipelineOutputAsFileCollection(
                StreamFilter { _, scopes -> scopes == setOf(Scope.PROJECT) },
                classesFilter
            )

            val desugaringClasspathScopes: MutableSet<ScopeType> =
                mutableSetOf(Scope.TESTED_CODE, Scope.PROVIDED_ONLY)
            if (enableDexingArtifactTransform) {
                subProjectsClasses = variantScope.globalScope.project.files()
                externalLibraryClasses = variantScope.globalScope.project.files()
                mixedScopeClasses = variantScope.globalScope.project.files()

                desugaringClasspathScopes.add(Scope.EXTERNAL_LIBRARIES)
                desugaringClasspathScopes.add(Scope.SUB_PROJECTS)
            } else if (variantScope.consumesFeatureJars()) {
                subProjectsClasses = variantScope.globalScope.project.files()
                externalLibraryClasses = variantScope.globalScope.project.files()

                // Get all classes from the scopes we are interested in.
                mixedScopeClasses = variantScope.transformManager.getPipelineOutputAsFileCollection(
                    StreamFilter { _, scopes ->
                        scopes.isNotEmpty() && scopes.subtract(
                            TransformManager.SCOPE_FULL_WITH_FEATURES
                        ).isEmpty()
                    },
                    classesFilter
                )
                desugaringClasspathScopes.add(Scope.EXTERNAL_LIBRARIES)
                desugaringClasspathScopes.add(Scope.SUB_PROJECTS)
                desugaringClasspathScopes.add(InternalScope.FEATURES)
            } else {
                subProjectsClasses =
                    variantScope.transformManager.getPipelineOutputAsFileCollection(
                        StreamFilter { _, scopes -> scopes == setOf(Scope.SUB_PROJECTS) },
                        classesFilter
                    )
                externalLibraryClasses =
                    variantScope.transformManager.getPipelineOutputAsFileCollection(
                        StreamFilter { _, scopes -> scopes == setOf(Scope.EXTERNAL_LIBRARIES) },
                        classesFilter
                    )
                // Get all classes that have more than 1 scope. E.g. project & subproject, or
                // project & subproject & external libs.
                mixedScopeClasses = variantScope.transformManager.getPipelineOutputAsFileCollection(
                    StreamFilter { _, scopes -> scopes.size > 1 && scopes.subtract(TransformManager.SCOPE_FULL_PROJECT).isEmpty() },
                    classesFilter
                )
            }

            desugaringClasspathClasses =
                variantScope.transformManager.getPipelineOutputAsFileCollection(
                    StreamFilter { _, scopes ->
                        scopes.subtract(desugaringClasspathScopes).isEmpty()
                    },
                    classesFilter
                )

            @Suppress("DEPRECATION") // remove all class files from the transform streams
            variantScope.transformManager.consumeStreams(
                TransformManager.SCOPE_FULL_WITH_FEATURES,
                TransformManager.CONTENT_CLASS
            )
        }

        override val type: Class<DexArchiveBuilderTask> = DexArchiveBuilderTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out DexArchiveBuilderTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                InternalArtifactType.PROJECT_DEX_ARCHIVE,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                DexArchiveBuilderTask::projectOutputDex
            )
            variantScope.artifacts.producesDir(
                InternalArtifactType.SUB_PROJECT_DEX_ARCHIVE,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                DexArchiveBuilderTask::subProjectOutputDex
            )
            variantScope.artifacts.producesDir(
                InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                DexArchiveBuilderTask::externalLibsOutputDex
            )
            variantScope.artifacts.producesDir(
                InternalArtifactType.MIXED_SCOPE_DEX_ARCHIVE,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                DexArchiveBuilderTask::mixedScopeOutputDex
            )
            variantScope.artifacts.producesFile(
                InternalArtifactType.DEX_ARCHIVE_INPUT_JAR_HASHES,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                DexArchiveBuilderTask::inputJarHashesFile
            )
        }

        override fun configure(task: DexArchiveBuilderTask) {
            super.configure(task)

            val projectOptions = variantScope.globalScope.projectOptions

            task.projectClasses.from(projectClasses)
            task.subProjectClasses.from(subProjectsClasses)
            task.externalLibClasses.from(externalLibraryClasses)
            task.mixedScopeClasses.from(mixedScopeClasses)

            val minSdkVersion = variantScope
                .variantConfiguration
                .minSdkVersionWithTargetDeviceApi
                .featureLevel
            task.minSdkVersion.set(minSdkVersion)
            if (variantScope.java8LangSupportType == VariantScope.Java8LangSupport.D8
                && minSdkVersion < AndroidVersion.VersionCodes.N
            ) {
                // Set bootclasspath and classpath only if desugaring with D8 and minSdkVersion < 24
                task.desugaringClasspathClasses.from(desugaringClasspathClasses)
                task.androidJarClasspath.from(variantScope.globalScope.filteredBootClasspath)
            }

            task.errorFormatMode.set(SyncOptions.getErrorFormatMode(projectOptions))
            task.dexer.set(variantScope.dexer)
            task.useGradleWorkers.set(projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS))
            task.inBufferSize.set(
                (projectOptions.get(IntegerOption.DEXING_READ_BUFFER_SIZE)
                    ?: DEFAULT_BUFFER_SIZE_IN_KB) * 1024
            )
            task.outBufferSize.set(
                (projectOptions.get(IntegerOption.DEXING_WRITE_BUFFER_SIZE)
                    ?: DEFAULT_BUFFER_SIZE_IN_KB) * 1024
            )
            task.debuggable.set(variantScope.variantConfiguration.buildType.isDebuggable)
            task.java8LangSupportType.set(variantScope.java8LangSupportType)
            task.projectVariant.set(
                "${variantScope.globalScope.project.name}:${variantScope.fullVariantName}"
            )
            task.numberOfBuckets.set(
                projectOptions.get(IntegerOption.DEXING_NUMBER_OF_BUCKETS) ?: DEFAULT_NUM_BUCKETS
            )
            task.dxNoOptimizeFlagPresent.set(
                dexOptions.additionalParameters.contains("--no-optimize")
            )
            task.messageReceiver = variantScope.globalScope.messageReceiver
            task.userLevelCache = userLevelCache
            val javaApiDesugaringEnabled
                    = variantScope.globalScope.extension.compileOptions.javaApiDesugaringEnabled
            if (javaApiDesugaringEnabled != null && javaApiDesugaringEnabled) {
                task.libConfiguration.set(getDesugarLibConfig(variantScope.globalScope.project))
            }
        }
    }
}

private const val DEFAULT_BUFFER_SIZE_IN_KB = 100
private val DEFAULT_NUM_BUCKETS = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1)