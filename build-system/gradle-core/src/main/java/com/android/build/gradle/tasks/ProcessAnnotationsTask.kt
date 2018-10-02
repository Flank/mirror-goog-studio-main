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
import com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType.APPEND
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_GENERATED_SOURCES_PRIVATE_USE
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.tasks.CacheableTask
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

/**
 * Task to perform annotation processing only, without compiling.
 *
 * This task may or may not be created/executed depending on whether it is needed. See the
 * documentation of [AndroidJavaCompile] for more details.
 */
@CacheableTask
open class ProcessAnnotationsTask : JavaCompile(), VariantAwareTask {

    @get:Internal
    override lateinit var variantName: String

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var processorListFile: BuildableArtifact
        private set

    @get:Internal
    lateinit var sourceFileTrees: () -> List<FileTree>
        private set

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    fun getSourceFileTree(): FileTree {
        return this.project.files(this.sourceFileTrees()).asFileTree
    }

    override fun compile(inputs: IncrementalTaskInputs) {
        val annotationProcessors =
            readAnnotationProcessorsFromJsonFile(processorListFile.singleFile())
        val allAPsAreIncremental = !annotationProcessors.containsValue(java.lang.Boolean.FALSE)

        // Skip this task if it is not needed (see the documentation of AndroidJavaCompile and
        // ProcessAnnotationsTask.Companion.taskShouldBeCreated)
        if (!annotationProcessors.isEmpty() && allAPsAreIncremental) {
            logger.info(
                "Skipped annotation-processing-only task because all annotation processors are" +
                        " incremental (annotation processing and compilation can be done" +
                        " incrementally in the same task)."
            )
            return
        }

        // Perform annotation processing only
        this.options.compilerArgs.add(PROC_ONLY)

        // Disable incremental mode as Gradle's JavaCompile currently does not work correctly in
        // incremental mode when -proc:only is used. We will revisit this issue later and
        // investigate what it means for an annotation-processing-only task to be incremental.
        this.options.isIncremental = false

        // Add individual sources instead of adding all at once due to a Gradle bug that happened
        // late 2015 (see commit 830450), not sure if it has been fixed or not
        for (source in sourceFileTrees()) {
            this.source(source)
        }

        // If no annotation processors are present, the Java compiler will proceed to compile the
        // source files even when the -proc:only option is specified (see
        // https://bugs.openjdk.java.net/browse/JDK-6378728). That would mess up the setup of this
        // task which is meant to do annotation processing only. Therefore, we need to return here
        // in that case. (This decision can't be made at the task's configuration as we don't want
        // to resolve annotation processors at configuration time.)
        if (annotationProcessors.isEmpty()) {
            return
        }

        super.compile(inputs)
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ProcessAnnotationsTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("process", "AnnotationsWithJavac")

        override val type: Class<ProcessAnnotationsTask>
            get() = ProcessAnnotationsTask::class.java

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            // Register annotation processing output.
            // Note that the decision to actually execute ProcessAnnotationsTask can't be made at
            // the task's configuration as the full information is available only at execution time
            // (see ProcessAnnotationsTask.compile()). Therefore, the annotation processing output
            // may be not be available when the consuming task (AndroidJavaCompile) requests it.
            // However, that is okay because in that case, AndroidJavaCompile should perform
            // annotation processing itself and does not need to consume the annotation processing
            // output.
            variantScope.artifacts.createBuildableArtifact(
                ANNOTATION_PROCESSOR_GENERATED_SOURCES_PRIVATE_USE,
                APPEND,
                listOf(variantScope.annotationProcessorOutputDir),
                taskName
            )
        }

        override fun configure(task: ProcessAnnotationsTask) {
            super.configure(task)

            // Configure properties that are shared between AndroidJavaCompile and
            // ProcessAnnotationTask
            task.configureProperties(variantScope)

            // Configure properties that are specific to ProcessAnnotationTask
            task.processorListFile =
                    variantScope.artifacts.getFinalArtifactFiles(ANNOTATION_PROCESSOR_LIST)
            task.sourceFileTrees = { variantScope.variantData.javaSources }

            task.configurePropertiesForAnnotationProcessing(variantScope)

            // Since this task does not output compiled classes, destinationDir will not be used.
            // However, Gradle requires this property to be set, so let's just set it to the
            // annotation processor output directory for convenience.
            task.destinationDir = variantScope.annotationProcessorOutputDir

            task.dependsOn(variantScope.taskContainer.sourceGenTask)
        }
    }

    companion object {

        /**
         * Determine whether [ProcessAnnotationsTask] should be created.
         *
         * As documented at [AndroidJavaCompile], the task is needed if all of the following
         * conditions are met:
         *   1. Kapt is not used
         *   2. Incremental compilation is requested (either by the user through the DSL or by
         *      default)
         *   3. Not all of the annotation processors are incremental
         *   4. The [BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING] flag is enabled.
         *
         * However, we don't want to perform the check for non-incremental annotation processors
         * (#3) here as it requires resolving annotation processors at configuration time. Instead,
         * we move that check to execution time (see ProcessAnnotationsTask.compile) and determine
         * whether the task is still needed then.
         */
        @JvmStatic
        fun taskShouldBeCreated(variantScope: VariantScope): Boolean {
            val globalScope = variantScope.globalScope
            val project = globalScope.project
            val compileOptions = globalScope.extension.compileOptions
            val separateAnnotationProcessingFlag = globalScope
                .projectOptions
                .get(BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING)

            return !project.pluginManager.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)
                        && compileOptions.incremental ?: DEFAULT_INCREMENTAL_COMPILATION
                        && separateAnnotationProcessingFlag
        }
    }
}