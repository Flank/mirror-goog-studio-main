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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.AP_GENERATED_SOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_EXPORT_CLASS_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.JavaVersion
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.util.PatternSet
import java.util.concurrent.Callable


/**
 * [TaskCreationAction] for the [JavaCompile] task.
 *
 * Note that when Kapt is not used (e.g., in Java-only projects), [JavaCompile] performs both
 * annotation processing and compilation. When Kapt is used (e.g., in most Kotlin-only or hybrid
 * Kotlin-Java projects), [JavaCompile] performs compilation only, without annotation processing.
 */
class JavaCompileCreationAction(
    private val componentProperties: ComponentPropertiesImpl, private val usingKapt: Boolean
) : TaskCreationAction<JavaCompile>() {

    private val globalScope = componentProperties.globalScope

    private val classesOutputDirectory = globalScope.project.objects.directoryProperty()
    private val annotationProcessorOutputDirectory = globalScope.project.objects.directoryProperty()

    private val dataBindingArtifactDir = globalScope.project.objects.directoryProperty()
    private val dataBindingExportClassListFile = globalScope.project.objects.fileProperty()

    override val name: String
        get() = componentProperties.computeTaskName("compile", "JavaWithJavac")

    override val type: Class<JavaCompile>
        get() = JavaCompile::class.java

    override fun handleProvider(taskProvider: TaskProvider<JavaCompile>) {
        super.handleProvider(taskProvider)

        componentProperties.taskContainer.javacTask = taskProvider

        val artifacts = componentProperties.artifacts

        artifacts
            .setInitialProvider(taskProvider) { classesOutputDirectory }
            .withName("classes")
            .on(JAVAC)

        artifacts
            .setInitialProvider(taskProvider) { annotationProcessorOutputDirectory }
            // Setting a name is not required, but a lot of AGP and IDE tests are assuming this name
            // so we leave it here for now.
            .withName(AP_GENERATED_SOURCES_DIR_NAME)
            .on(AP_GENERATED_SOURCES)

        if (componentProperties.buildFeatures.dataBinding) {
            // Data binding artifacts are part of the annotation processing outputs of JavaCompile
            // if Kapt is not used; otherwise, they are the outputs of Kapt.
            if (!usingKapt) {
                registerDataBindingOutputs(
                    dataBindingArtifactDir,
                    dataBindingExportClassListFile,
                    componentProperties.variantType.isExportDataBindingClassList,
                    taskProvider,
                    artifacts
                )
            }
        }
    }

    override fun configure(task: JavaCompile) {
        task.dependsOn(componentProperties.taskContainer.preBuildTask)
        task.extensions.add(PROPERTY_VARIANT_NAME_KEY, componentProperties.name)

        task.configureProperties(componentProperties)
        // Set up the annotation processor classpath even when Kapt is used, because Java compiler
        // plugins like ErrorProne share their classpath with annotation processors (see
        // https://github.com/gradle/gradle/issues/6573), and special annotation processors like
        // Lombok want to run via JavaCompile (see https://youtrack.jetbrains.com/issue/KT-7112).
        task.configurePropertiesForAnnotationProcessing(componentProperties)

        // Wrap sources in Callable to evaluate them just before execution, b/117161463.
        val sourcesToCompile = Callable { listOf(componentProperties.javaSources) }
        // Include only java sources, otherwise we hit b/144249620.
        val javaSourcesFilter = PatternSet().include("**/*.java")
        task.source = task.project.files(sourcesToCompile).asFileTree.matching(javaSourcesFilter)

        task.options.isIncremental = globalScope.extension.compileOptions.incremental
            ?: DEFAULT_INCREMENTAL_COMPILATION

        // When Kapt is used, it runs annotation processors declared in the `kapt` configurations.
        // However, we currently collect annotation processors for analytics purposes only from the
        // `annotationProcessor` configurations, not `kapt` configurations, so the data is only
        // correct if Kapt is not used.
        if (!usingKapt) {
            // Record apList as input. It impacts recordAnnotationProcessors() below.
            val apList = componentProperties.artifacts.get(ANNOTATION_PROCESSOR_LIST)
            task.inputs.files(apList).withPathSensitivity(PathSensitivity.NONE)
                .withPropertyName("annotationProcessorList")

            task.recordAnnotationProcessors(apList, componentProperties.name)
        }

        // Set up the outputs
        task.setDestinationDir(classesOutputDirectory.asFile)
        // Can't write `task.options.setAnnotationProcessorGeneratedSourcesDirectory(
        // annotationProcessorOutputDirectory.asFile)` due to a new requirement in Gradle 7.0:
        // https://docs.gradle.org/current/userguide/upgrading_version_6.html#querying_a_mapped_output_property_of_a_task_before_the_task_has_completed
        // (currently caught by BasicInstantExecutionTest).
        task.options.annotationProcessorGeneratedSourcesDirectory =
            componentProperties.artifacts
                .getOutputPath(AP_GENERATED_SOURCES, AP_GENERATED_SOURCES_DIR_NAME)

        // The API of JavaCompile to set up outputs only accepts File or Provider<File> (see above).
        // However, for task wiring to work correctly, we will need to register the corresponding
        // DirectoryProperty / RegularFileProperty as outputs too (they are not yet annotated as
        // outputs).
        task.outputs.dir(classesOutputDirectory).withPropertyName("classesOutputDirectory")
        task.outputs.dir(annotationProcessorOutputDirectory)
            .withPropertyName("annotationProcessorOutputDirectory")

        // Also do that for data binding artifacts
        if (componentProperties.buildFeatures.dataBinding) {
            // Data binding artifacts are part of the annotation processing outputs of JavaCompile
            // if Kapt is not used; otherwise, they are the outputs of Kapt.
            if (!usingKapt) {
                task.outputs.dir(dataBindingArtifactDir).withPropertyName("dataBindingArtifactDir")
                if (componentProperties.variantType.isExportDataBindingClassList) {
                    task.outputs.file(dataBindingExportClassListFile)
                        .withPropertyName("dataBindingExportClassListFile")
                }
            }
        }

        task.logger.debug(
            "Configuring Java sources compilation for '${task.name}' with source level " +
                    "${task.sourceCompatibility} and target level ${task.targetCompatibility}."
        )
    }
}

/** Registers data binding artifacts as outputs of JavaCompile (or Kapt if Kapt is used). */
fun registerDataBindingOutputs(
    dataBindingArtifactDir: DirectoryProperty,
    dataBindingExportClassListFile: RegularFileProperty,
    isExportDataBindingClassList: Boolean,
    taskProvider: TaskProvider<out Task>,
    artifacts: ArtifactsImpl
) {
    artifacts
        .setInitialProvider(taskProvider) { dataBindingArtifactDir }
        .on(DATA_BINDING_ARTIFACT)
    if (isExportDataBindingClassList) {
        artifacts
            .setInitialProvider(taskProvider) { dataBindingExportClassListFile }
            .on(DATA_BINDING_EXPORT_CLASS_LIST)
    }
}

private fun JavaCompile.recordAnnotationProcessors(
    processorListFile: Provider<RegularFile>,
    variantName: String
) {
    val projectPath = this.project.path
    doFirst {
        val annotationProcessors =
            readAnnotationProcessorsFromJsonFile(processorListFile.get().asFile)
        val nonIncrementalAPs =
            annotationProcessors.filter { it.value == java.lang.Boolean.FALSE }
        val allAPsAreIncremental = nonIncrementalAPs.isEmpty()

        // Warn users about non-incremental annotation processors
        if (!allAPsAreIncremental && options.isIncremental) {
            logger
                .warn(
                    "The following annotation processors are not incremental:" +
                            " ${nonIncrementalAPs.keys.joinToString(", ")}.\n" +
                            "Make sure all annotation processors are incremental" +
                            " to improve your build speed."
                )
        }

        // Record annotation processors for analytics purposes. This recording needs to happen here
        // instead of JavaPreCompileTask as it needs to be done even in incremental builds where
        // JavaPreCompileTask may be UP-TO-DATE.
        recordAnnotationProcessorsForAnalytics(
            annotationProcessors, projectPath, variantName
        )
    }
}

private const val AP_GENERATED_SOURCES_DIR_NAME = "out"