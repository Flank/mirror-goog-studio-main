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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType.APPEND
import com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType.INITIAL
import com.android.build.gradle.internal.scope.InternalArtifactType.AP_GENERATED_SOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.sdklib.AndroidTargetHash
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Joiner
import org.gradle.api.JavaVersion
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File

/**
 * Task to perform compilation for Java source code, without or with annotation processing depending
 * on whether annotation processing is done by a separate task or not.
 *
 * The separate annotation processing task can be either KaptTask or [ProcessAnnotationsTask].
 *
 * [ProcessAnnotationsTask] is needed if all of the following conditions are met:
 *   1. Incremental compilation is requested (either by the user through the DSL or by default)
 *   2. Kapt is not used
 *   3. The [BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING] flag is enabled
 *
 * When Kapt is used (e.g., in most Kotlin-only or hybrid Kotlin-Java projects):
 *   + [ProcessAnnotationsTask] is not created.
 *   + KaptTask performs annotation processing only, without compiling.
 *   + [AndroidJavaCompile] and KotlinCompile perform compilation only, without annotation
 *     processing.

 * When Kapt is not used, (e.g., in Java-only projects):
 *   + If [ProcessAnnotationsTask] is needed (see above), [ProcessAnnotationsTask] first performs
 *     annotation processing only, without compiling, and [AndroidJavaCompile] then performs
 *     compilation only, without annotation processing.
 *   + Otherwise, [ProcessAnnotationsTask] is not created, and [AndroidJavaCompile] performs both
 *     annotation processing and compilation.
 */
@CacheableTask
abstract class AndroidJavaCompile : JavaCompile(), VariantAwareTask {

    @get:Internal
    override lateinit var variantName: String

    /**
     * Whether incremental compilation is requested (either by the user through the DSL or by
     * default).
     */
    @get:Input
    var incrementalFromDslOrByDefault: Boolean = DEFAULT_INCREMENTAL_COMPILATION
        private set

    @get:Input
    var separateAnnotationProcessingFlag: Boolean =
        BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING.defaultValue
        private set

    @get:Input
    var processAnnotationsTaskCreated: Boolean = false
        private set

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val processorListFile: RegularFileProperty

    @get:Internal
    lateinit var sourceFileTrees: () -> List<FileTree>
        private set

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    fun getSources(): FileTree {
        return this.project.files(this.sourceFileTrees()).asFileTree
    }

    @get:OutputDirectory
    abstract val classesOutputDirectory: DirectoryProperty

    // Annotation processors generated sources when separate annotation processing is enabled.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val separateTaskAnnotationProcessorOutputDirectory: DirectoryProperty

    // Annotation processors generated sources when separate annotation processing is disabled.
    @get:OutputDirectory
    @get:Optional
    abstract val annotationProcessorOutputDirectory: DirectoryProperty

    /**
     * Overrides the stock Gradle JavaCompile task output directory as we use instead the
     * above classesOutputDirectory. The [JavaCompile.destinationDir] is not declared as a Task
     * output for this task.
     */
    override fun getDestinationDir(): File {
        return classesOutputDirectory.get().asFile
    }

    @get:Input
    lateinit var compileSdkVersion: String
        private set

    override fun compile(inputs: IncrementalTaskInputs) {
        var procNoneOptionSetByAGP = false
        if (processAnnotationsTaskCreated) {
            // Disable annotation processing as it was already done by a separate task (but only if
            // the user did not request -proc:only)
            if (options.compilerArgs.contains(PROC_ONLY)) {
                return
            } else {
                if (!options.compilerArgs.contains(PROC_NONE)) {
                    options.compilerArgs.add(PROC_NONE)
                    procNoneOptionSetByAGP = true
                }
            }
        }

        if (isPostN(compileSdkVersion) && !JavaVersion.current().isJava8Compatible) {
            throw RuntimeException(
                "compileSdkVersion '$compileSdkVersion'" +
                        " requires JDK 1.8 or later to compile."
            )
        }

        val hasKapt = this.project.pluginManager.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)

        val annotationProcessors =
            readAnnotationProcessorsFromJsonFile(processorListFile.get().asFile)
        val nonIncrementalAPs = annotationProcessors.filter { it.value == java.lang.Boolean.FALSE }
        val allAPsAreIncremental = nonIncrementalAPs.isEmpty()

        // If incremental compilation is requested and annotation processing is performed by this
        // task, but not all of the annotation processors are incremental, then compilation will not
        // be incremental. We warn users about non-incremental annotation processors and tell them
        // to enable the separateAnnotationProcessing flag to make compilation incremental.
        if (incrementalFromDslOrByDefault
            && !hasKapt
            && !separateAnnotationProcessingFlag
            && !allAPsAreIncremental
        ) {
            logger
                .warn(
                    "Gradle may disable incremental compilation" +
                            " as the following annotation processors are not incremental:" +
                            " ${Joiner.on(", ").join(nonIncrementalAPs.keys)}.\n" +
                            "Consider setting the experimental feature flag" +
                            " ${BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING.propertyName}" +
                            "=true in the gradle.properties file to run annotation processing" +
                            " in a separate task and make compilation incremental."
                )
        }

        this.options.isIncremental = incrementalFromDslOrByDefault

        // For incremental compile to work, Gradle requires individual sources to be added one by
        // one, rather than adding one single composite FileTree aggregated from the individual
        // sources (method getSources() above).
        for (source in sourceFileTrees()) {
            this.source(source)
        }

        /*
         * Support Lombok (https://issuetracker.google.com/130531986). Note that we can't support
         * Lombok with Kapt (https://youtrack.jetbrains.com/issue/KT-7112) correctly yet, as we
         * can't tell if -proc:none is requested by Kapt or by the user.
         */
        if (procNoneOptionSetByAGP) {
            val lomboks = annotationProcessors.filter { it.key.contains(LOMBOK) }
            if (lomboks.isNotEmpty()) {
                configureCompilerArgumentsForLombok(this.options.compilerArgs)

                // In case the version of Lombok being used is not incremental, print out a warning.
                val nonIncrementalLomboks = lomboks.filter { it.value == java.lang.Boolean.FALSE }
                if (nonIncrementalLomboks.isNotEmpty()) {
                    logger.warn(
                        "Gradle may disable incremental compilation" +
                                " as the following annotation processors are not incremental:" +
                                " ${Joiner.on(", ").join(nonIncrementalLomboks.keys)}."
                    )
                }
            }
        }

        // Record annotation processors that has been executed by another task or will be executed
        // by this task for analytics purposes. This recording needs to happen here instead of
        // JavaPreCompileTask as it needs to be done even in incremental builds where
        // JavaPreCompileTask may be UP-TO-DATE.
        recordAnnotationProcessorsForAnalytics(
            annotationProcessors, project.path, variantName
        )

        logger.info(
            "Compiling with source level $sourceCompatibility" +
                    " and target level $targetCompatibility."
        )

        super.compile(inputs)
    }

    companion object {

        private fun isPostN(compileSdkVersion: String): Boolean {
            val hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion)
            return hash != null && hash.apiLevel >= 24
        }

        /**
         * Configures compiler arguments to run Lombok (and only Lombok) when no annotation
         * processing (-proc:none) was requested earlier.
         */
        @VisibleForTesting
        fun configureCompilerArgumentsForLombok(compilerArgs: MutableList<String>) {
            check(compilerArgs.contains(PROC_NONE))
                    { "compilerArgs $compilerArgs does not contain $PROC_NONE" }

            compilerArgs.removeAll { it == PROC_NONE }

            // Remove -processor and the class names that follow it
            val filteredArgs =
                compilerArgs.filterIndexed { index, _ ->
                    (compilerArgs[index] != PROCESSOR)
                            && (index == 0 || compilerArgs[index - 1] != PROCESSOR)
                }
            compilerArgs.clear()
            compilerArgs.addAll(filteredArgs)

            // Run Lombok only by setting the -processor argument (doing this is better than
            // filtering the annotation processor path to include only Lombok, since we don't
            // want to remove dependencies used by Java compiler plugins like Error Prone which
            // are also put on the annotation processor path).
            compilerArgs.add(PROCESSOR)
            compilerArgs.add(LOMBOK_ANNOTATION_PROCESSOR)
        }
    }

    class CreationAction(
        variantScope: VariantScope,
        private val processAnnotationsTaskCreated: Boolean
    ) : VariantTaskCreationAction<AndroidJavaCompile>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("compile", "JavaWithJavac")

        override val type: Class<AndroidJavaCompile>
            get() = AndroidJavaCompile::class.java

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            // Data binding artifact is one of the annotation processing outputs
            if (variantScope.globalScope.extension.dataBinding.isEnabled) {
                variantScope.artifacts.createBuildableArtifact(
                    DATA_BINDING_ARTIFACT,
                    APPEND,
                    listOf(variantScope.bundleArtifactFolderForDataBinding),
                    taskName
                )
            }
        }

        override fun handleProvider(taskProvider: TaskProvider<out AndroidJavaCompile>) {
            super.handleProvider(taskProvider)

            variantScope.taskContainer.javacTask = taskProvider

            variantScope.artifacts.producesDir(
                JAVAC,
                APPEND,
                taskProvider,
                AndroidJavaCompile::classesOutputDirectory,
                fileName = "classes"
            )

            // When doing annotation processing, register its output
            if (!processAnnotationsTaskCreated) {
                variantScope.artifacts.producesDir(
                    AP_GENERATED_SOURCES,
                    INITIAL,
                    taskProvider,
                    AndroidJavaCompile::annotationProcessorOutputDirectory
                )
            }
        }

        override fun configure(task: AndroidJavaCompile) {
            super.configure(task)

            val globalScope = variantScope.globalScope
            val project = globalScope.project
            val compileOptions = globalScope.extension.compileOptions
            val separateAnnotationProcessingFlag = globalScope
                .projectOptions
                .get(BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING)

            // Configure properties that are shared between AndroidJavaCompile and
            // ProcessAnnotationTask.
            task.configureProperties(variantScope)

            // Configure properties that are specific to AndroidJavaCompile
            task.incrementalFromDslOrByDefault =
                compileOptions.incremental ?: DEFAULT_INCREMENTAL_COMPILATION
            task.separateAnnotationProcessingFlag = separateAnnotationProcessingFlag
            task.processAnnotationsTaskCreated = processAnnotationsTaskCreated
            variantScope.artifacts.setTaskInputToFinalProduct(
                ANNOTATION_PROCESSOR_LIST,
                task.processorListFile
            )
            task.compileSdkVersion = globalScope.extension.compileSdkVersion

            // Configure properties for annotation processing, but only if this task is actually
            // going to perform annotation processing (i.e., annotation processing is not done by
            // ProcessAnnotationsTask).
            if (!processAnnotationsTaskCreated) {
                task.configurePropertiesForAnnotationProcessing(
                    variantScope,
                    task.annotationProcessorOutputDirectory
                )
            } else {
                // Otherwise, we will disable annotation processing (see the task action).
                //
                // We still need to set the annotation processor path because:
                //   1. Java compiler plugins like Error Prone share their classpath with annotation
                //      processors.
                //   2. We may enable annotation processing at execution time to support the Lombok
                //      annotation processor.
                // See https://issuetracker.google.com/130531986.
                task.configureAnnotationProcessorPath(variantScope)
            }

            // Collect the list of source files to process/compile, which includes the annotation
            // processing output if annotation processing is done by ProcessAnnotationsTask
            if (processAnnotationsTaskCreated) {
                task.separateTaskAnnotationProcessorOutputDirectory.set(
                    variantScope.artifacts.getFinalProduct(AP_GENERATED_SOURCES)
                )
                val generatedSources =
                    project.fileTree(task.separateTaskAnnotationProcessorOutputDirectory)
                        .builtBy(task.separateTaskAnnotationProcessorOutputDirectory)
                task.sourceFileTrees = {
                    val sources = mutableListOf<FileTree>()
                    sources.addAll(variantScope.variantData.javaSources)
                    sources.add(generatedSources)
                    sources.toList()
                }
            } else {
                task.sourceFileTrees = { variantScope.variantData.javaSources }
            }

            // The annotation processor output is either an input or an output of this task, so one
            // of the following fields will not be used.
            if (processAnnotationsTaskCreated) {
                task.annotationProcessorOutputDirectory.set(null as Directory?)
            } else {
                task.separateTaskAnnotationProcessorOutputDirectory.set(null as Directory?)
            }
        }
    }
}
