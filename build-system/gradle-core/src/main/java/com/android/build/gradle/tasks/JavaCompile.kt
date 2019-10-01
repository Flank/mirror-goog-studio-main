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

import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType.APPEND
import com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType.INITIAL
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.AP_GENERATED_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.AP_GENERATED_SOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.sdklib.AndroidTargetHash
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Joiner
import org.gradle.api.JavaVersion
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import java.util.concurrent.Callable

/**
 * Task to perform compilation for Java source code (JavaCompile), without or with annotation
 * processing depending on whether annotation processing is done by a separate task or not.
 *
 * The separate annotation processing task can be either KaptTask or the process-annotations task
 * (task created in [ProcessAnnotationsTaskCreationAction]).
 *
 * Process-annotations task is needed if all of the following conditions are met:
 *   1. Incremental compilation is requested (either by the user through the DSL or by default)
 *   2. Kapt is not used
 *   3. The [BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING] flag is enabled
 *
 * When Kapt is used (e.g., in most Kotlin-only or hybrid Kotlin-Java projects):
 *   + Process-annotations task is not created.
 *   + KaptTask performs annotation processing only, without compiling.
 *   + JavaCompile and KotlinCompile perform compilation only, without annotation processing.

 * When Kapt is not used, (e.g., in Java-only projects):
 *   + If process-annotations task is needed (see above), process-annotations task first performs
 *     annotation processing only, without compiling, and JavaCompile then performs
 *     compilation only, without annotation processing.
 *   + Otherwise, process-annotations task is not created, and JavaCompile performs both
 *     annotation processing and compilation.
 */

class JavaCompileCreationAction(
    private val variantScope: VariantScope,
    private val processAnnotationsTaskCreated: Boolean
) : TaskCreationAction<JavaCompile>() {

    private val classesOutputDirectory =
        variantScope.globalScope.project.objects.directoryProperty()
    private val annotationProcessorOutputDirectory =
        variantScope.globalScope.project.objects.directoryProperty()
    private val bundleArtifactFolderForDataBinding =
        variantScope.globalScope.project.objects.directoryProperty()

    init {
        val compileSdkVersion = variantScope.globalScope.extension.compileSdkVersion
        if (isPostN(compileSdkVersion) && !JavaVersion.current().isJava8Compatible) {
            throw RuntimeException(
                "compileSdkVersion '$compileSdkVersion' requires JDK 1.8 or later to compile."
            )
        }
    }

    override val name: String
        get() = variantScope.getTaskName("compile", "JavaWithJavac")

    override val type: Class<JavaCompile>
        get() = JavaCompile::class.java

    override fun handleProvider(taskProvider: TaskProvider<out JavaCompile>) {
        super.handleProvider(taskProvider)

        variantScope.taskContainer.javacTask = taskProvider

        classesOutputDirectory.set(variantScope.artifacts.getOperations()
            .getOutputDirectory(JAVAC, "classes"))

        variantScope.artifacts.producesDir(
            JAVAC,
            APPEND,
            taskProvider,
            { classesOutputDirectory },
            fileName = "classes"
        )

        annotationProcessorOutputDirectory.set(variantScope.artifacts.getOperations()
            .getOutputDirectory(AP_GENERATED_SOURCES))

        // When doing annotation processing, register its output
        if (!processAnnotationsTaskCreated) {
            variantScope.artifacts.producesDir(
                AP_GENERATED_SOURCES,
                INITIAL,
                taskProvider,
                { annotationProcessorOutputDirectory }
            )
        }

        // Data binding artifact is one of the annotation processing outputs, only if kapt is not
        // configured.
        if (variantScope.globalScope.extension.dataBinding.isEnabled) {
            bundleArtifactFolderForDataBinding.set(variantScope.artifacts.getOperations()
                .getOutputDirectory(DATA_BINDING_ARTIFACT))

            variantScope.artifacts.producesDir(
                DATA_BINDING_ARTIFACT,
                APPEND,
                taskProvider,
                { bundleArtifactFolderForDataBinding }
            )
        }
    }

    override fun configure(task: JavaCompile) {
        task.dependsOn(variantScope.taskContainer.preBuildTask)
        task.extensions.add(PROPERTY_VARIANT_NAME_KEY, variantScope.fullVariantName)

        val globalScope = variantScope.globalScope
        val compileOptions = globalScope.extension.compileOptions
        val separateAnnotationProcessingFlag = globalScope
            .projectOptions
            .get(BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING)

        // Configure properties that are shared between JavaCompile and process-annotations task.
        task.configureProperties(variantScope)

        val sourcesToCompile = if (processAnnotationsTaskCreated) {
            // Don't run if only annotation processing is requested, and separate task runs.
            task.onlyIf { PROC_ONLY !in task.options.compilerArgs}

            // Although we will disable annotation processing (see handleAnnotationProcessors()),
            // we still need to set the annotation processor path because:
            //   1. Java compiler plugins like Error Prone share their classpath with annotation
            //      processors.
            //   2. We may enable annotation processing at execution time to support the Lombok
            //      annotation processor.
            // See https://issuetracker.google.com/130531986.
            task.configureAnnotationProcessorPath(variantScope)

            val apSourcesOutputDir: Provider<Directory> =
                variantScope.artifacts.getFinalProduct(AP_GENERATED_SOURCES)

            val apSourcesFileCollection =
                globalScope.project.fileTree(apSourcesOutputDir).builtBy(apSourcesOutputDir)

            // Wrap sources in Callable to evaluate them just before execution, b/117161463.
            Callable { listOf(variantScope.variantData.javaSources, apSourcesFileCollection) }
        } else {
            // Configure properties for annotation processing, because this task is actually
            // going to perform annotation processing (i.e., annotation processing is not done
            // by process-annotations task).
            task.configurePropertiesForAnnotationProcessing(
                variantScope,
                annotationProcessorOutputDirectory
            )
            // Wrap sources in Callable to evaluate them just before execution, b/117161463.
            Callable { listOf(variantScope.variantData.javaSources) }
        }
        task.source = task.project.files(sourcesToCompile).asFileTree

        // Some annotation processors generate files in the class output directory too, so we need
        // to copy those classes over from the process-annotations task if that task is used.
        if (processAnnotationsTaskCreated) {
            val apClassesOutputDir: Provider<Directory> =
                variantScope.artifacts.getFinalProduct(AP_GENERATED_CLASSES)

            val apClassesFileCollection =
                globalScope.project.fileTree(apClassesOutputDir).builtBy(apClassesOutputDir)

            task.inputs.files(apClassesFileCollection).withPathSensitivity(PathSensitivity.RELATIVE)
                .withPropertyName("apGeneratedClasses")

            task.doLast {
                val apClassesOutDir = apClassesOutputDir.get().asFile
                if (apClassesOutDir.exists()) { // The task could be skipped
                    FileUtils.copyDirectory(apClassesOutDir, classesOutputDirectory.get().asFile)
                }
            }
        }

        task.options.isIncremental =
            compileOptions.incremental ?: DEFAULT_INCREMENTAL_COMPILATION

        val apList =
            variantScope.artifacts.getFinalProduct(ANNOTATION_PROCESSOR_LIST)
        // Record these as inputs. They impact doFirst() below.
        task.inputs.property("__separateAnnotationProcessingFlag", separateAnnotationProcessingFlag)
        task.inputs.property("__processAnnotationsTaskCreated", processAnnotationsTaskCreated)
        task.inputs.files(apList).withPathSensitivity(PathSensitivity.NONE)
            .withPropertyName("annotationProcessorList")

        task.handleAnnotationProcessors(
            apList,
            separateAnnotationProcessingFlag,
            processAnnotationsTaskCreated,
            variantScope.fullVariantName
        )

        task.setDestinationDir(classesOutputDirectory.asFile)
        // Manually declare our output directory as a Task output since it's not annotated as
        // an OutputDirectory on the task implementation.
        task.outputs.dir(classesOutputDirectory)
        task.outputs.dir(annotationProcessorOutputDirectory).optional()
        task.outputs.dir(bundleArtifactFolderForDataBinding).optional()

        task.logger.info(
            "Configuring Java sources compilation with source level " +
                    "${task.sourceCompatibility} and target level ${task.targetCompatibility}."
        )
    }
}

private fun JavaCompile.handleAnnotationProcessors(
    processorListFile: Provider<RegularFile>,
    separateAnnotationProcessingFlag: Boolean,
    processAnnotationsTaskCreated: Boolean,
    variantName: String
) {
    doFirst {
        var procNoneOptionSetByAGP = false
        if (processAnnotationsTaskCreated) {
            if (!options.compilerArgs.contains(PROC_NONE)) {
                options.compilerArgs.add(PROC_NONE)
                procNoneOptionSetByAGP = true
            }
        }

        val hasKapt = this.project.pluginManager.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)

        val annotationProcessors =
            readAnnotationProcessorsFromJsonFile(processorListFile.get().asFile)
        val nonIncrementalAPs =
            annotationProcessors.filter { it.value == java.lang.Boolean.FALSE }
        val allAPsAreIncremental = nonIncrementalAPs.isEmpty()

        // If incremental compilation is requested and annotation processing is performed by this
        // task, but not all of the annotation processors are incremental, then compilation will not
        // be incremental. We warn users about non-incremental annotation processors and tell them
        // to enable the separateAnnotationProcessing flag to make compilation incremental.
        if (options.isIncremental
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
                val nonIncrementalLomboks =
                    lomboks.filter { it.value == java.lang.Boolean.FALSE }
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
    }
}

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
