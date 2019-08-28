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

@file:JvmName("ProcessAnnotationsTask")

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType.AP_GENERATED_SOURCES
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.scope.getOutputDirectory
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile

/**
 * Task to perform annotation processing only, without compiling.
 *
 * This task may or may not be created depending on whether it is needed. See the documentation of
 * [JavaCompileCreationAction] for more details.
 */
class ProcessAnnotationsTaskCreationAction(private val variantScope: VariantScope) :
    TaskCreationAction<JavaCompile>() {

    override val name: String
        get() = variantScope.getTaskName("process", "AnnotationsWithJavac")

    override val type: Class<JavaCompile>
        get() = JavaCompile::class.java

    private val output = variantScope.globalScope.project.objects.directoryProperty()

    override fun handleProvider(taskProvider: TaskProvider<out JavaCompile>) {
        super.handleProvider(taskProvider)

        variantScope.artifacts.producesDir(
            AP_GENERATED_SOURCES,
            BuildArtifactsHolder.OperationType.INITIAL,
            taskProvider,
            { output }
        )
    }

    override fun configure(task: JavaCompile) {
        val taskContainer: MutableTaskContainer = variantScope.taskContainer
        task.dependsOn(taskContainer.preBuildTask)
        task.extensions.add(PROPERTY_VARIANT_NAME_KEY, variantScope.fullVariantName)

        // Configure properties that are shared between JavaCompile and ProcessAnnotationTask
        task.configureProperties(variantScope)

        // Configure properties for annotation processing
        task.configurePropertiesForAnnotationProcessing(variantScope, output)

        // Collect the list of source files to process
        task.source = task.project.files({ variantScope.variantData.javaSources }).asFileTree

        // Since this task does not output compiled classes, destinationDir will not be used.
        // However, Gradle requires this property to be set, so let's just set it to the
        // annotation processor output directory for convenience.
        task.setDestinationDir(output.asFile)

        // Manually declare our output directory as a Task output since it's not annotated as
        // an OutputDirectory on the task implementation.
        task.outputs.dir(output)

        task.options.compilerArgs.add(PROC_ONLY)
        // Disable incremental mode as Gradle's JavaCompile currently does not work correctly in
        // incremental mode when -proc:only is used. We will revisit this issue later and
        // investigate what it means for an annotation-processing-only task to be incremental.
        task.options.isIncremental = false

        // Perform annotation processing only (but only if the user did not request -proc:none)
        task.onlyIf { PROC_NONE !in task.options.compilerArgs }
    }
}

/**
 * Determine whether task for separate annotation processing should be created.
 *
 * As documented at [JavaCompileCreationAction], separate annotation processing is needed if
 * all of the following conditions are met:
 *   1. Incremental compilation is requested (either by the user through the DSL or by
 *      default)
 *   2. Kapt is not used
 *   3. The [BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING] flag is enabled
 */
fun taskShouldBeCreated(variantScope: VariantScope): Boolean {
    val globalScope = variantScope.globalScope
    val project = globalScope.project
    val compileOptions = globalScope.extension.compileOptions
    val separateAnnotationProcessingFlag = globalScope
        .projectOptions
        .get(BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING)

    return compileOptions.incremental ?: DEFAULT_INCREMENTAL_COMPILATION
            && !project.pluginManager.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)
            && separateAnnotationProcessingFlag
}
