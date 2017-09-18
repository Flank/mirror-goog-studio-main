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

package com.android.build.api.artifact

import org.gradle.api.Incubating
import org.gradle.api.Task
import java.io.File

/**
 * Builder to create a task for a specific variant that consumes or transforms a build artifact.
 *
 * The builder allows the task's input and output to be configured.  This modifies the
 * [InputArtifactProvider], and [OutputFileProvider] that will be constructed of for the [create]
 * method.  The [create] method accept [ConfigurationAction] that will be used to configure the
 * task.
 *
 * For example, to create a task that uses compile classpath and javac output and create additional
 * .class files for both compile classpath and javac output:
 *
 * buildArtifactTransformBuilder
 *     .input(BuildableArtifactType.JAVAC_CLASSES)
 *     .input(BuildableArtifactType.JAVA_COMPILE_CLASSPATH)
 *     .output(BuildableArtifactType.JAVAC_CLASSES, OperationType.APPEND)
 *     .output(BuildableArtifactType.JAVA_COMPILE_CLASSPATH, OperationType.APPEND)
 *     .outputFile("classes",
 *             BuildableArtifactType.JAVAC_CLASSES,
 *             BuildableArtifactType.JAVA_COMPILE_CLASSPATH)
 *     .create() { task, input, output ->
 *         task.classpath = input.getArtifact(BuildableArtifactType.JAVA_COMPILE_CLASSPATH)
 *         task.javaClasses = input.getArtifact(BuildableArtifactType.JAVAC_CLASSES)
 *         task.outputDir = output.getFile()
 *     }
 */
@Incubating
interface BuildArtifactTransformBuilder<out T : Task> {
    /**
     * Defines how the output of the new task interacts with the original [BuildableArtifact].
     */
    @Incubating
    enum class OperationType {
        // Replaces the output of the original task.  Subsequent tasks using the output will see
        // only the output of the new task.
        REPLACE,

        // Supplement the output of the original task.  Subsequent tasks using the output will see
        // *both* the output of the new task and the original task.
        APPEND
    }

    /**
     * Action to configure the task.
     */
    @Incubating
    interface ConfigurationAction<in T : Task> {
        fun accept(task: T, input: InputArtifactProvider, output: OutputFileProvider)
    }

    /**
     * Action to configure the task for simple cases.
     */
    @Incubating
    interface SimpleConfigurationAction<in T : Task> {
        fun accept(task: T, input: BuildableArtifact, output: File)
    }

    /**
     * Specifies a [BuildableArtifact] that will be available to the task as an input
     *
     * @param artifactType the ArtifactType the task will modify.
     */
    fun input(artifactType: ArtifactType) : BuildArtifactTransformBuilder<T>

    /**
     * Specifies the type of [BuildableArtifact] that the task will produce and how the existing
     * [BuildableArtifact] is impacted.
     *
     * @param artifactType the ArtifactType the task will modify.
     * @param operationType determines how the new output interacts with the original outputs.
     */
    fun output(artifactType: ArtifactType, operationType : OperationType)
            : BuildArtifactTransformBuilder<T>

    /**
     * Defines a File that the new Task will produce.
     *
     * The File will be generated with the given filename at an appropriate location.
     *
     * This File will be made available in the [OutputFileProvider] when [create] is called.  It
     * will use the specified filename, and the full path will be computed based on the variant.
     *
     * This method can be called multiple times to create multiple output files.
     *
     * @param filename name of the resulting File.
     * @param consumers the artifact type this File is intended for, if no consumer is provided, the
     *         output file will not be used by any task in the Android Gradle Plugin.
     */
    fun outputFile(filename : String, vararg consumers : ArtifactType) : BuildArtifactTransformBuilder<T>

    /**
     * Creates the task and uses the supplied action to configure it.
     *
     * @param action action that configures the resulting Task.
     */
    fun create(action : ConfigurationAction<T>) : T

    /**
     * Creates the task and uses the supplied function to configure it.
     *
     * Accepts a function instead of a functional interface.
     *
     * @param function function that configures the resulting Task.
     */
    fun create(function : T.(InputArtifactProvider, OutputFileProvider) -> Unit) : T
}
