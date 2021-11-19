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

import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JSON
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.instrumentation.AsmInstrumentationManager
import com.android.build.gradle.internal.instrumentation.ClassesHierarchyResolver
import com.android.build.gradle.internal.instrumentation.loadClassData
import com.android.build.gradle.internal.instrumentation.saveClassData
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.JarsClasspathInputsWithIdentity
import com.android.build.gradle.internal.tasks.JarsIdentityMapping
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.builder.files.SerializableFileChanges
import com.android.builder.utils.isValidZipEntryName
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * A task that instruments the project classes with the asm visitors registered via the DSL.
 */
@CacheableTask
abstract class TransformClassesWithAsmTask : NewIncrementalTask() {

    @get:Input
    abstract val asmApiVersion: Property<Int>

    @get:Input
    abstract val framesComputationMode: Property<FramesComputationMode>

    @get:Input
    abstract val excludes: SetProperty<String>

    @get:Nested
    abstract val visitorsList: ListProperty<AsmClassVisitorFactory<*>>

    @get:Incremental
    @get:Classpath
    abstract val inputClassesDir: ConfigurableFileCollection

    // This is used when jacoco instrumented jars are used as inputs
    @get:Incremental
    @get:Classpath
    @get:Optional
    abstract val inputJarsDir: DirectoryProperty

    @get:Nested
    abstract val inputJarsWithIdentity: JarsClasspathInputsWithIdentity

    @get:CompileClasspath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val classesOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val jarsOutputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val profilingTransforms: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val shouldPackageProfilerDependencies: Property<Boolean>

    @get:Internal
    abstract val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>

    /**
     * A folder to save [com.android.build.api.instrumentation.ClassData] objects for classes that
     * were queried by a visitor using
     * [com.android.build.api.instrumentation.ClassContext.loadClassData] so if any of these classes
     * changed in a way that the ClassData is also changed, we will need to run non incrementally.
     */
    @get:OutputDirectory
    lateinit var incrementalFolder: File
        private set

    override fun doTaskAction(inputChanges: InputChanges) {
        if (inputChanges.isIncremental) {
            doIncrementalTaskAction(inputChanges)
        } else {
            doFullTaskAction(inputChanges)
        }
    }

    private fun configureParams(params: BaseWorkerParams, inputChanges: InputChanges) {
        params.initializeFromAndroidVariantTask(this)

        params.visitorsList.set(visitorsList)
        params.asmApiVersion.set(asmApiVersion)
        params.framesComputationMode.set(framesComputationMode)
        params.excludes.set(excludes)
        params.shouldPackageProfilerDependencies.set(
            shouldPackageProfilerDependencies.getOrElse(false)
        )
        params.profilingTransforms.set(profilingTransforms.getOrElse(emptyList()))
        params.projectSources.from(inputClassesDir).from(inputJarsWithIdentity.inputJars)
        params.dependenciesSources.from(runtimeClasspath).from(bootClasspath)
        params.mappingState.set(inputJarsWithIdentity.getMappingState(inputChanges))
        params.jarsOutputDir.set(jarsOutputDir)
        params.classesHierarchyBuildService.set(classesHierarchyBuildService)
        params.classesOutputDir.set(classesOutputDir)
        params.incrementalFolder.set(incrementalFolder)
    }

    private fun doFullTaskAction(inputChanges: InputChanges) {
        incrementalFolder.mkdirs()
        FileUtils.deleteDirectoryContents(classesOutputDir.get().asFile)
        FileUtils.deleteDirectoryContents(incrementalFolder)

        workerExecutor.noIsolation().submit(TransformClassesFullAction::class.java) {
            configureParams(it, inputChanges)
            it.inputClassesDir.from(inputClassesDir)
            it.inputJarsDir.set(inputJarsDir)
        }
    }

    private fun doIncrementalTaskAction(inputChanges: InputChanges) {
        val previouslyQueriedClasses = incrementalFolder.listFiles()!!.map {
            it.name.removeSuffix(DOT_JSON)
        }.toSet()

        inputChanges.getFileChanges(inputClassesDir).filter { it.changeType == ChangeType.MODIFIED }
            .filter {
                val className = it.normalizedPath.removeSuffix(DOT_CLASS)
                    .replace('/', '.')
                // check if this class that changed was queried in a previous build
                previouslyQueriedClasses.contains(className)
            }
            .let { changes ->
                if (changes.isNotEmpty()) {
                    val classesHierarchyResolver = classesHierarchyBuildService.get()
                        .getClassesHierarchyResolverBuilder()
                        .addProjectSources(inputClassesDir.files)
                        .addProjectSources(inputJarsWithIdentity.inputJars.files)
                        .addDependenciesSources(runtimeClasspath.files)
                        .addDependenciesSources(bootClasspath.files)
                        .build()

                    changes.forEach {
                        val className = it.normalizedPath.removeSuffix(DOT_CLASS)
                            .replace('/', '.')
                        val classDataFromLastBuild =
                            loadClassData(File(incrementalFolder, className + DOT_JSON))!!
                        val currentClassData =
                            classesHierarchyResolver.maybeLoadClassDataForClass(className)

                        // the class data changed so we need to run the full task action
                        if (classDataFromLastBuild != currentClassData) {
                            doFullTaskAction(inputChanges)
                            return
                        }
                    }
                }
            }

        workerExecutor.noIsolation().submit(TransformClassesIncrementalAction::class.java) {
            configureParams(it, inputChanges)
            it.inputClassesDirChanges.set(
                inputChanges.getFileChanges(inputClassesDir).toSerializable()
            )
            if (inputJarsDir.isPresent) {
                it.inputJarsChanges.set(inputChanges.getFileChanges(inputJarsDir).toSerializable())
            }
        }
    }

    abstract class BaseWorkerParams: ProfileAwareWorkAction.Parameters() {
        @get:Nested
        abstract val visitorsList: ListProperty<AsmClassVisitorFactory<*>>
        abstract val asmApiVersion: Property<Int>
        abstract val framesComputationMode: Property<FramesComputationMode>
        abstract val excludes: SetProperty<String>
        abstract val shouldPackageProfilerDependencies: Property<Boolean>
        abstract val profilingTransforms: ListProperty<String>
        abstract val projectSources: ConfigurableFileCollection
        abstract val dependenciesSources: ConfigurableFileCollection
        abstract val mappingState: Property<JarsIdentityMapping>
        abstract val jarsOutputDir: DirectoryProperty
        @get:Internal
        abstract val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>
        abstract val classesOutputDir: DirectoryProperty
        abstract val incrementalFolder: RegularFileProperty
    }

    abstract class TransformClassesWorkerAction<T: BaseWorkerParams>: ProfileAwareWorkAction<T>() {

        protected fun updateIncrementalState(
            previouslyQueriedClasses: Set<String>,
            classesHierarchyResolver: ClassesHierarchyResolver
        ) {
            classesHierarchyResolver.queriedProjectClasses.forEach { classData ->
                // we know that the data of the classes in previouslyQueriedClasses didn't change so
                // just save the classes that weren't queried before
                if (!previouslyQueriedClasses.contains(classData.className)) {
                    saveClassData(
                        File(
                            parameters.incrementalFolder.get().asFile,
                            classData.className + DOT_JSON
                        ),
                        classData
                    )
                }
            }
        }

        /**
         * Extract profiler dependency jars and add them to the project jars as they need to be
         * packaged with the rest of the classes.
         */
        protected fun extractProfilerDependencyJars() {
            if (parameters.shouldPackageProfilerDependencies.getOrElse(false)) {
                parameters.profilingTransforms.get().forEach { path ->
                    val profilingTransformFile = File(path)
                    extractDependencyJars(profilingTransformFile) { name: String ->
                        FileUtils.join(
                            parameters.jarsOutputDir.get().asFile,
                            "profiler-deps",
                            profilingTransformFile.nameWithoutExtension,
                            name + DOT_JAR
                        )
                    }
                }
            }
        }

        private fun extractDependencyJars(inputJar: File, outputLocation: (String) -> File) {
            // To avoid https://bugs.openjdk.java.net/browse/JDK-7183373
            // we extract the resources directly as a zip file.
            ZipInputStream(FileInputStream(inputJar)).use { zis ->
                val pattern = Pattern.compile("dependencies/(.*)\\.jar")
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null && isValidZipEntryName(entry)) {
                    val matcher = pattern.matcher(entry.name)
                    if (matcher.matches()) {
                        val name = matcher.group(1)
                        val outputJar: File = outputLocation.invoke(name)
                        Files.createParentDirs(outputJar)
                        FileOutputStream(outputJar).use { fos -> ByteStreams.copy(zis, fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        abstract fun maybeProcessJacocoInstrumentedJars(
            instrumentationManager: AsmInstrumentationManager
        ): Boolean

        fun processJars(instrumentationManager: AsmInstrumentationManager) {
             if (!maybeProcessJacocoInstrumentedJars(instrumentationManager)) {
                val mappingState = parameters.mappingState.get()
                if (mappingState.reprocessAll) {
                    FileUtils.deleteDirectoryContents(parameters.jarsOutputDir.get().asFile)
                    extractProfilerDependencyJars()
                }
                mappingState.jarsInfo.forEach { (file, info) ->
                    if (info.hasChanged) {
                        val instrumentedJar =
                            File(parameters.jarsOutputDir.get().asFile, info.identity + DOT_JAR)
                        FileUtils.deleteIfExists(instrumentedJar)
                        instrumentationManager.instrumentClassesFromJarToJar(file, instrumentedJar)
                    }
                }
            }
        }

        protected fun getInstrumentationManager(
            classesHierarchyResolver: ClassesHierarchyResolver
        ): AsmInstrumentationManager {
            return AsmInstrumentationManager(
                visitors = parameters.visitorsList.get(),
                apiVersion = parameters.asmApiVersion.get(),
                classesHierarchyResolver = classesHierarchyResolver,
                framesComputationMode = parameters.framesComputationMode.get(),
                excludes = parameters.excludes.get(),
                profilingTransforms = parameters.profilingTransforms.getOrElse(emptyList())
            )
        }

        protected fun getClassesHierarchyResolver(): ClassesHierarchyResolver {
            return parameters.classesHierarchyBuildService.get()
                .getClassesHierarchyResolverBuilder()
                .addProjectSources(parameters.projectSources.files)
                .addDependenciesSources(parameters.dependenciesSources.files)
                .build()
        }
    }

    abstract class IncrementalWorkerParams: BaseWorkerParams() {
        abstract val inputClassesDirChanges: Property<SerializableFileChanges>
        abstract val inputJarsChanges: Property<SerializableFileChanges>
    }

    abstract class TransformClassesIncrementalAction:
        TransformClassesWorkerAction<IncrementalWorkerParams>() {

        override fun run() {
            val classesHierarchyResolver = getClassesHierarchyResolver()

            getInstrumentationManager(classesHierarchyResolver).use { instrumentationManager ->
                val classesChanges = parameters.inputClassesDirChanges.get()

                classesChanges.removedFiles.plus(classesChanges.modifiedFiles).forEach {
                    val outputFile =
                        parameters.classesOutputDir.get().asFile.resolve(it.normalizedPath)
                    FileUtils.deleteIfExists(outputFile)
                }

                classesChanges.addedFiles.plus(classesChanges.modifiedFiles).forEach {
                    val outputFile =
                        parameters.classesOutputDir.get().asFile.resolve(it.normalizedPath)
                    instrumentationManager.instrumentModifiedFile(
                        inputFile = it.file,
                        outputFile = outputFile,
                        relativePath = it.normalizedPath
                    )
                }

                processJars(instrumentationManager)
            }

            updateIncrementalState(
                previouslyQueriedClasses = parameters.incrementalFolder.get().asFile
                    .listFiles()!!.map {
                    it.name.removeSuffix(DOT_JSON)
                }.toSet(),
                classesHierarchyResolver = classesHierarchyResolver
            )
        }

        override fun maybeProcessJacocoInstrumentedJars(
            instrumentationManager: AsmInstrumentationManager
        ): Boolean {
            if (!parameters.inputJarsChanges.isPresent) {
                return false
            }
            parameters.inputJarsChanges.get().fileChanges.forEach { inputJar ->
                val instrumentedJar =
                    File(parameters.jarsOutputDir.get().asFile, inputJar.file.name)
                FileUtils.deleteIfExists(instrumentedJar)
                if (inputJar.fileStatus == FileStatus.NEW ||
                    inputJar.fileStatus == FileStatus.CHANGED
                ) {
                    instrumentationManager.instrumentClassesFromJarToJar(
                        inputJar.file,
                        instrumentedJar
                    )
                }
            }
            return true
        }
    }

    abstract class FullActionWorkerParams: BaseWorkerParams() {
        abstract val inputClassesDir: ConfigurableFileCollection
        abstract val inputJarsDir: DirectoryProperty
    }

    abstract class TransformClassesFullAction:
        TransformClassesWorkerAction<FullActionWorkerParams>() {

        override fun run() {
            val classesHierarchyResolver = getClassesHierarchyResolver()
            getInstrumentationManager(classesHierarchyResolver).use { instrumentationManager ->
                parameters.inputClassesDir.files.filter(File::exists).forEach {
                    instrumentationManager.instrumentClassesFromDirectoryToDirectory(
                        it,
                        parameters.classesOutputDir.get().asFile
                    )
                }

                processJars(instrumentationManager)
            }

            updateIncrementalState(emptySet(), classesHierarchyResolver)
        }

        override fun maybeProcessJacocoInstrumentedJars(
            instrumentationManager: AsmInstrumentationManager
        ): Boolean {
            if (!parameters.inputJarsDir.isPresent) {
                return false
            }
            FileUtils.deleteDirectoryContents(parameters.jarsOutputDir.get().asFile)
            extractProfilerDependencyJars()
            parameters.inputJarsDir.get().asFile.listFiles()?.forEach { inputJar ->
                val instrumentedJar = File(parameters.jarsOutputDir.get().asFile, inputJar.name)
                instrumentationManager.instrumentClassesFromJarToJar(inputJar, instrumentedJar)
            }
            return true
        }
    }

    class CreationAction(
        component: ComponentImpl,
        val isTestCoverageEnabled: Boolean
    ) : VariantTaskCreationAction<TransformClassesWithAsmTask, ComponentImpl>(
        component
    ) {

        override val name: String = computeTaskName("transform", "ClassesWithAsm")
        override val type: Class<TransformClassesWithAsmTask> =
                TransformClassesWithAsmTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<TransformClassesWithAsmTask>) {
            super.handleProvider(taskProvider)
            creationConfig
                    .artifacts
                    .setInitialProvider(taskProvider) { it.classesOutputDir }
                    .on(InternalArtifactType.ASM_INSTRUMENTED_PROJECT_CLASSES)

            creationConfig
                    .artifacts
                    .setInitialProvider(taskProvider) { it.jarsOutputDir }
                    .on(InternalArtifactType.ASM_INSTRUMENTED_PROJECT_JARS)
        }

        override fun configure(task: TransformClassesWithAsmTask) {
            super.configure(task)
            task.incrementalFolder = creationConfig.paths.getIncrementalDir(task.name)

            task.visitorsList.setDisallowChanges(creationConfig.registeredProjectClassesVisitors)

            task.framesComputationMode.setDisallowChanges(creationConfig.asmFramesComputationMode)

            task.asmApiVersion.setDisallowChanges(creationConfig.asmApiVersion)

            task.excludes.setDisallowChanges(creationConfig.instrumentation.excludes)

            task.inputClassesDir.from(
                creationConfig.artifacts.getAll(MultipleArtifact.ALL_CLASSES_DIRS)
            )

            task.inputJarsWithIdentity.inputJars.from(
                creationConfig.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS)
            )

            task.bootClasspath.from(creationConfig.sdkComponents.bootClasspath)

            task.runtimeClasspath.from(creationConfig.variantScope.providedOnlyClasspath)


            task.runtimeClasspath.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                        if (creationConfig.services.projectOptions[BooleanOption.ENABLE_JACOCO_TRANSFORM_INSTRUMENTATION]
                            && creationConfig.variantDslInfo.isTestCoverageEnabled
                        ) {
                            AndroidArtifacts.ArtifactType.JACOCO_CLASSES_JAR
                        } else {
                            AndroidArtifacts.ArtifactType.CLASSES_JAR
                        }
                    )
            )

            task.classesHierarchyBuildService.setDisallowChanges(
                    getBuildService(creationConfig.services.buildServiceRegistry)
            )

            if (creationConfig is ApkCreationConfig) {
                task.profilingTransforms.setDisallowChanges(
                        creationConfig.advancedProfilingTransforms
                )
                task.shouldPackageProfilerDependencies.setDisallowChanges(
                        creationConfig.shouldPackageProfilerDependencies
                )
            }
        }
    }
}
