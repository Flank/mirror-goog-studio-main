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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.AP_GENERATED_SOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_EXPORT_CLASS_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.sdklib.AndroidTargetHash
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
class JavaCompileCreationAction(private val componentProperties: ComponentPropertiesImpl) :
    TaskCreationAction<JavaCompile>() {

    private val globalScope = componentProperties.globalScope

    private val classesOutputDirectory = globalScope.project.objects.directoryProperty()
    private val annotationProcessorOutputDirectory = globalScope.project.objects.directoryProperty()

    // We need to get this path at the task's configuration, so we have to manually construct it
    // here (normally the Operations API doesn't require this manual step).
    private val annotationProcessorOutputLocation =
        componentProperties.artifacts.getOperations()
            .getOutputPath(AP_GENERATED_SOURCES, "out")

    private val dataBindingArtifactDir = globalScope.project.objects.directoryProperty()
    private val dataBindingExportClassListFile = globalScope.project.objects.fileProperty()

    init {
        val compileSdkVersion = globalScope.extension.compileSdkVersion
        if (compileSdkVersion != null && isPostN(compileSdkVersion) && !JavaVersion.current().isJava8Compatible) {
            throw RuntimeException(
                "compileSdkVersion '$compileSdkVersion' requires JDK 1.8 or later to compile."
            )
        }
    }

    override val name: String
        get() = componentProperties.computeTaskName("compile", "JavaWithJavac")

    override val type: Class<JavaCompile>
        get() = JavaCompile::class.java

    override fun handleProvider(taskProvider: TaskProvider<out JavaCompile>) {
        super.handleProvider(taskProvider)

        componentProperties.taskContainer.javacTask = taskProvider

        val artifacts = componentProperties.artifacts

        artifacts.getOperations()
            .setInitialProvider(taskProvider) { classesOutputDirectory }
            .withName("classes")
            .on(JAVAC)

        artifacts.getOperations()
            .setInitialProvider(taskProvider) { annotationProcessorOutputDirectory }
            .atLocation(annotationProcessorOutputLocation.path)
            .on(AP_GENERATED_SOURCES)

        if (componentProperties.buildFeatures.dataBinding) {
            // Data binding artifacts are part of the annotation processing outputs
            registerDataBindingOutputs(
                dataBindingArtifactDir,
                dataBindingExportClassListFile,
                componentProperties.variantType.isExportDataBindingClassList,
                true, /* firstRegistration */
                taskProvider,
                artifacts
            )
        }
    }

    override fun configure(task: JavaCompile) {
        task.dependsOn(componentProperties.taskContainer.preBuildTask)
        task.extensions.add(PROPERTY_VARIANT_NAME_KEY, componentProperties.name)

        task.configureProperties(componentProperties)
        task.configurePropertiesForAnnotationProcessing(
            componentProperties,
            annotationProcessorOutputLocation
        )

        // Wrap sources in Callable to evaluate them just before execution, b/117161463.
        val sourcesToCompile = Callable { listOf(componentProperties.javaSources) }
        // Include only java sources, otherwise we hit b/144249620.
        val javaSourcesFilter = PatternSet().include("**/*.java")
        task.source = task.project.files(sourcesToCompile).asFileTree.matching(javaSourcesFilter)

        task.options.isIncremental = globalScope.extension.compileOptions.incremental
            ?: DEFAULT_INCREMENTAL_COMPILATION

        // Record apList as input. It impacts handleAnnotationProcessors() below.
        val apList = componentProperties.artifacts.getFinalProduct(ANNOTATION_PROCESSOR_LIST)
        task.inputs.files(apList).withPathSensitivity(PathSensitivity.NONE)
            .withPropertyName("annotationProcessorList")

        task.handleAnnotationProcessors(apList, componentProperties.name)

        task.setDestinationDir(classesOutputDirectory.asFile)

        // Manually declare these output providers as the task's outputs as they are not yet
        // annotated as outputs.
        task.outputs.dir(classesOutputDirectory).withPropertyName("classesOutputDirectory")
        task.outputs.dir(annotationProcessorOutputDirectory)
            .withPropertyName("annotationProcessorOutputDirectory").optional()
        // Also do that for data binding artifacts---even though the output providers are present
        // in DataBindingCompilerArguments, it is not enough. (If we don't do this, these tests will
        // fail: DataBindingMultiModuleTest, DataBindingExternalArtifactDependencyTest,
        // DataBindingIncrementalityTest.)
        task.outputs.dir(dataBindingArtifactDir).withPropertyName("dataBindingArtifactDir")
            .optional()
        task.outputs.file(dataBindingExportClassListFile)
            .withPropertyName("dataBindingExportClassListFile").optional()

        task.logger.info(
            "Configuring Java sources compilation with source level " +
                    "${task.sourceCompatibility} and target level ${task.targetCompatibility}."
        )
    }
}

/**
 * Registers data binding outputs as outputs of the JavaCompile task (or the Kapt task if Kapt is
 * used).
 */
fun registerDataBindingOutputs(
    dataBindingArtifactDir: DirectoryProperty,
    dataBindingExportClassListFile: RegularFileProperty,
    isExportDataBindingClassList: Boolean,
    firstRegistration: Boolean,
    taskProvider: TaskProvider<out Task>,
    artifacts: BuildArtifactsHolder
) {
    if (firstRegistration) {
        artifacts.getOperations()
            .setInitialProvider(taskProvider) { dataBindingArtifactDir }
            // a name is required, or DataBindingCachingTest would fail (somehow adding a name
            // solves the issue of overlapping outputs between Kapt and JavaCompile when this
            // artifact is set as the output of both tasks).
            .withName("out")
            .on(DATA_BINDING_ARTIFACT)
    } else {
        artifacts.getOperations()
            .replace(taskProvider) { dataBindingArtifactDir }
            .on(DATA_BINDING_ARTIFACT)
    }
    if (isExportDataBindingClassList) {
        if (firstRegistration) {
            artifacts.getOperations()
                .setInitialProvider(taskProvider) { dataBindingExportClassListFile }
                // a name is required for a regular file, without it
                // ./gradlew :dataBinding:buildDataBindingRuntimeArtifacts would fail
                .withName("class_list")
                .on(DATA_BINDING_EXPORT_CLASS_LIST)
        } else {
            artifacts.getOperations()
                .replace(taskProvider) { dataBindingExportClassListFile }
                .on(DATA_BINDING_EXPORT_CLASS_LIST)
        }
    }
}

private fun JavaCompile.handleAnnotationProcessors(
    processorListFile: Provider<RegularFile>,
    variantName: String
) {
    val hasKapt = this.project.pluginManager.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)
    val projectPath = this.project.path
    doFirst {
        val annotationProcessors =
            readAnnotationProcessorsFromJsonFile(processorListFile.get().asFile)
        val nonIncrementalAPs =
            annotationProcessors.filter { it.value == java.lang.Boolean.FALSE }
        val allAPsAreIncremental = nonIncrementalAPs.isEmpty()

        // Warn users about non-incremental annotation processors
        if (!hasKapt && !allAPsAreIncremental && options.isIncremental) {
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

private fun isPostN(compileSdkVersion: String): Boolean {
    val hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion)
    return hash != null && hash.apiLevel >= 24
}