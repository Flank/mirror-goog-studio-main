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

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.incremental.InstantRunBuildContext
import com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType.APPEND
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_GENERATED_SOURCES_PRIVATE_USE
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_GENERATED_SOURCES_PUBLIC_USE
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.sdklib.AndroidTargetHash
import com.google.common.base.Joiner
import org.gradle.api.JavaVersion
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
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
 *   1. Kapt is not used
 *   2. Incremental compilation is requested (either by the user through the DSL or by default)
 *   3. Not all of the annotation processors are incremental
 *   4. The [BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING] flag is enabled
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
 *   + Otherwise, [ProcessAnnotationsTask] is either not created or skipped, and
 *     [AndroidJavaCompile] performs both annotation processing and compilation.
 */
@CacheableTask
open class AndroidJavaCompile : JavaCompile(), VariantAwareTask {

    @get:Internal
    override lateinit var variantName: String

    /**
     * Whether incremental compilation is requested (either by the user through the DSL or by
     * default).
     */
    @get:Input
    var incrementalFromDslOrByDefault: Boolean = DEFAULT_INCREMENTAL_COMPILATION
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var processorListFile: BuildableArtifact
        internal set

    @get:Input
    var separateAnnotationProcessingFlag: Boolean =
        BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING.defaultValue
        private set

    @get:Input
    lateinit var compileSdkVersion: String
        private set

    @get:Internal
    lateinit var instantRunBuildContext: InstantRunBuildContext
        private set

    override fun compile(inputs: IncrementalTaskInputs) {
        if (isPostN(compileSdkVersion) && !JavaVersion.current().isJava8Compatible) {
            throw RuntimeException(
                "compileSdkVersion '$compileSdkVersion'" +
                        " requires JDK 1.8 or later to compile."
            )
        }

        val hasKapt = this.project.pluginManager.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)

        val annotationProcessors =
            readAnnotationProcessorsFromJsonFile(processorListFile.singleFile())
        val nonIncrementalAPs =
            annotationProcessors.filter { it -> it.value == java.lang.Boolean.FALSE }
        val allAPsAreIncremental = nonIncrementalAPs.isEmpty()

        // If incremental compilation is requested but not all of the annotation processors are
        // incremental, and annotation processing is performed by this task, then compilation
        // will not be incremental. We warn users about non-incremental annotation processors and
        // tell them to enable the separateAnnotationProcessing flag to make compilation
        // incremental. (Note that Kapt already takes care of annotation processing, hence the check
        // !hasKapt.)
        if (!hasKapt
            && incrementalFromDslOrByDefault
            && !allAPsAreIncremental
            && !separateAnnotationProcessingFlag
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

        // This is the condition that ProcessAnnotationsTask is executed (see the documentation of
        // AndroidJavaCompile)
        val processAnnotationsTaskExecuted = !hasKapt
                && incrementalFromDslOrByDefault
                && !allAPsAreIncremental
                && separateAnnotationProcessingFlag

        // Disable annotation processing if it is done by ProcessAnnotationsTask
        if (processAnnotationsTaskExecuted) {
            this.options.compilerArgs.add(PROC_NONE)
            // We set this directory earlier during the task's configuration as we didn't know
            // whether this task would perform annotation processing or not. Now that we know, we
            // need to unset it, otherwise Gradle may delete this directory in certain cases (e.g.,
            // when moving from a build with separateAnnotationProcessingFlag disabled to a build
            // with separateAnnotationProcessingFlag enabled).
            this.options.annotationProcessorGeneratedSourcesDirectory = null
        }

        this.options.isIncremental = incrementalFromDslOrByDefault

        /*
         * HACK: The following are workarounds for known issues.
         */
        if (!hasKapt
            && incrementalFromDslOrByDefault
            && !allAPsAreIncremental
            && !separateAnnotationProcessingFlag
        ) {
            // If incremental compilation is requested but not all of the annotation processors are
            // incremental, and annotation processing is performed by this task, then compilation
            // should not be incremental. However, there is a Gradle bug that if a non-incremental
            // annotation processor changes a resource, Gradle will mistakenly remain in incremental
            // mode, and may not even perform any recompilation at all even though this task is not
            // UP-TO-DATE. See http://issuetracker.google.com/113054294. Before the fix is available
            // in Gradle 5.0, we need to disable incremental mode explicitly here.
            this.options.isIncremental = false
        }
        if (hasKapt
            || incrementalFromDslOrByDefault
            && !allAPsAreIncremental
            && separateAnnotationProcessingFlag
        ) {
            /*
             * If Kapt or ProcessAnnotationTask has done annotation processing earlier, this task
             * does not need to run annotation processing again.
             *
             * However, if the Lombok annotation processor is used, this task still needs to run
             * annotation processing again as Lombok requires annotation processing and compilation
             * to be done in the same invocation of the Java compiler.
             *
             * Note that in that case, even though this task runs annotation processing again, it
             * should run Lombok only, to avoid running the other annotation processors twice.
             *
             * Also note that the version of Lombok being used may or may not be incremental.
             * Related bugs: https://github.com/rzwitserloot/lombok/pull/1680 and
             * https://github.com/rzwitserloot/lombok/issues/1817.
             */
            val lomboks = annotationProcessors.filter { it -> it.key.contains(LOMBOK) }
            if (lomboks.isNotEmpty()) {
                this.options.compilerArgs.removeIf { it -> it == PROC_NONE }
                this.options.annotationProcessorPath =
                        this.options.annotationProcessorPath!!.filter { it ->
                            it.name.contains(LOMBOK)
                        }

                val nonIncrementalLomboks =
                    lomboks.filter { it -> it.value == java.lang.Boolean.FALSE }
                if (nonIncrementalLomboks.isNotEmpty()) {
                    logger.warn(
                        "Gradle may disable incremental compilation" +
                                " as the following annotation processors are not incremental:" +
                                " ${Joiner.on(", ").join(nonIncrementalLomboks.keys)}."
                    )

                    // Because of the Gradle bug mentioned in the previous hack, we need to disable
                    // incremental mode explicitly here
                    this.options.isIncremental = false
                }
            }
        }

        // Record annotation processors that has been executed by another task or will be executed
        // by this task for analytics purposes. This recording needs to happen here instead of
        // JavaPreCompileTask as it needs to be done even in incremental builds where
        // JavaPreCompileTask may be UP-TO-DATE.
        recordAnnotationProcessorsForAnalytics(
            annotationProcessors.keys, project.path, variantName
        )

        logger.info(
            "Compiling with source level $sourceCompatibility" +
                    " and target level $targetCompatibility."
        )

        instantRunBuildContext.startRecording(InstantRunBuildContext.TaskType.JAVAC)
        super.compile(inputs)
        instantRunBuildContext.stopRecording(InstantRunBuildContext.TaskType.JAVAC)
    }

    private fun isPostN(compileSdkVersion: String): Boolean {
        val hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion)
        return hash != null && hash.apiLevel >= 24
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<AndroidJavaCompile>(variantScope) {

        private lateinit var destinationDir: File

        override val name: String
            get() = variantScope.getTaskName("compile", "JavaWithJavac")

        override val type: Class<AndroidJavaCompile>
            get() = AndroidJavaCompile::class.java

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            // Register annotation processing output.
            // Note that the decision to perform annotation processing in AndroidJavaCompile can't
            // be made at the task's configuration as the full information is available only at
            // execution time (see ProcessAnnotationsTask.compile()). Therefore, the annotation
            // processing output may not be directly produced by AndroidJavaCompile. However, that
            // is okay because in that case, AndroidJavaCompile should depend on the
            // annotation-processing-only task (ProcessAnnotationsTask or KaptTask), and the
            // annotation processing output will be available when a consuming task requests it.
            variantScope.artifacts.createBuildableArtifact(
                ANNOTATION_PROCESSOR_GENERATED_SOURCES_PUBLIC_USE,
                APPEND,
                listOf(variantScope.annotationProcessorOutputDir),
                taskName
            )

            // Data binding artifact is one of the annotation processing outputs
            if (variantScope.globalScope.extension.dataBinding.isEnabled) {
                variantScope.artifacts.createBuildableArtifact(
                    DATA_BINDING_ARTIFACT,
                    APPEND,
                    listOf(variantScope.bundleArtifactFolderForDataBinding),
                    taskName
                )
            }

            // Register compiled Java classes output
            destinationDir =
                    variantScope.artifacts.appendArtifact(JAVAC, taskName, "classes")
        }

        override fun handleProvider(taskProvider: TaskProvider<out AndroidJavaCompile>) {
            super.handleProvider(taskProvider)

            variantScope.taskContainer.javacTask = taskProvider
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
            task.incrementalFromDslOrByDefault = compileOptions.incremental ?:
                    DEFAULT_INCREMENTAL_COMPILATION
            task.processorListFile =
                    variantScope.artifacts.getFinalArtifactFiles(ANNOTATION_PROCESSOR_LIST)
            task.separateAnnotationProcessingFlag = separateAnnotationProcessingFlag
            task.compileSdkVersion = globalScope.extension.compileSdkVersion
            task.instantRunBuildContext = variantScope.instantRunBuildContext

            // Annotation processing may or may not be done by AndroidJavaCompile, but because we
            // don't know at this point, we have to prepare this task for annotation processing
            // always.
            task.configurePropertiesForAnnotationProcessing(variantScope)

            // Compile the source files generated by ProcessAnnotationsTask.
            // Note that the annotation processing output may be not be available if
            // ProcessAnnotationsTask is skipped (see
            // ProcessAnnotationsTask.CreationAction.preConfigure). However, that is okay because in
            // that case, AndroidJavaCompile should perform annotation processing itself and does
            // not need to consume the annotation processing output.
            val processAnnotationsTaskCreated =
                ProcessAnnotationsTask.taskShouldBeCreated(variantScope)
            if (processAnnotationsTaskCreated) {
                val generatedSourcesArtifact = variantScope.artifacts
                    .getFinalArtifactFiles(ANNOTATION_PROCESSOR_GENERATED_SOURCES_PRIVATE_USE)
                task.source(
                    project.fileTree(generatedSourcesArtifact.get().singleFile).builtBy(
                        generatedSourcesArtifact
                    )
                )
            }

            task.destinationDir = destinationDir

            // When ProcessAnnotationsTask is created, it also depends on the following task
            // dependencies, and because AndroidJavaCompile already depends on
            // ProcessAnnotationsTask, we don't need to set the dependencies again here (this makes
            // the task dependencies simpler).
            if (!processAnnotationsTaskCreated) {
                task.dependsOn(variantScope.taskContainer.sourceGenTask)
            }
        }
    }
}
