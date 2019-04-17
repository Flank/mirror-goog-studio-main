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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.BaseDataBinder
import android.databinding.tool.DataBindingBuilder
import android.databinding.tool.processing.ScopedException
import android.databinding.tool.store.LayoutInfoInput
import android.databinding.tool.util.L
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_MERGE
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File
import java.io.Serializable
import java.util.ArrayList
import javax.inject.Inject
import javax.tools.Diagnostic
import kotlin.reflect.KFunction

/**
 * Generates base classes from data binding info files.
 *
 * This class takes the output of XML processor which generates binding info files (binding
 * information in layout files). Then it generates base classes which are the classes accessed
 * by the user code.
 *
 * Generating these classes in gradle instead of annotation processor avoids showing too many
 * errors to the user if the compilation fails before annotation processor output classes are
 * compiled.
 */
@CacheableTask
open class DataBindingGenBaseClassesTask : AndroidVariantTask() {
    // where xml info files are
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var layoutInfoDirectory: BuildableArtifact
        private set
    // the package name for the module / app
    private lateinit var packageNameSupplier: KFunction<String>
    @get:Input val packageName: String
        get() = packageNameSupplier.call()
    // list of artifacts from dependencies
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var mergedArtifactsFromDependencies: BuildableArtifact
        private set
    // list of v1 artifacts from dependencies
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var v1Artifacts: BuildableArtifact
        private set
    // where to keep the log of the task
    @get:OutputDirectory lateinit var logOutFolder: File
        private set
    // where to write the new files
    @get:OutputDirectory lateinit var sourceOutFolder: File
        private set
    @get:OutputDirectory lateinit var classInfoBundleDir: File
        private set
    @get:Input
    var useAndroidX: Boolean = false
        private set
    @get:Internal
    var encodeErrors: Boolean = false
        private set

    @TaskAction
    fun writeBaseClasses(inputs: IncrementalTaskInputs) {
        // TODO extend NewIncrementalTask when moved to new API so that we can remove the manual call to recordTaskAction

        recordTaskAction {
            // TODO figure out why worker execution makes the task flake.
            // Some files cannot be accessed even though they show up when directory listing is
            // invoked.
            // b/69652332
            val args = buildInputArgs(inputs)
            CodeGenerator(args, sourceOutFolder, project.logger, encodeErrors).run()
        }
    }

    private fun buildInputArgs(inputs: IncrementalTaskInputs): LayoutInfoInput.Args {
        val outOfDate = ArrayList<File>()
        val removed = ArrayList<File>()
        val layoutInfoDir = layoutInfoDirectory.get().singleFile

        // if dependency added/removed a file, it is handled by the LayoutInfoInput class
        if (inputs.isIncremental) {
            inputs.outOfDate { inputFileDetails ->
                if (FileUtils.isFileInDirectory(
                        inputFileDetails.file,
                        layoutInfoDir
                    ) && inputFileDetails.file.name.endsWith(".xml")
                ) {
                    outOfDate.add(inputFileDetails.file)
                }
            }
            inputs.removed { inputFileDetails ->
                if (FileUtils.isFileInDirectory(
                        inputFileDetails.file,
                        layoutInfoDir
                    ) && inputFileDetails.file.name.endsWith(".xml")
                ) {
                    removed.add(inputFileDetails.file)
                }
            }
        } else {
            FileUtils.cleanOutputDir(logOutFolder)
            FileUtils.cleanOutputDir(sourceOutFolder)
        }
        return LayoutInfoInput.Args(
                outOfDate = outOfDate,
                removed = removed,
                infoFolder = layoutInfoDir,
                dependencyClassesFolder = mergedArtifactsFromDependencies.single(),
                logFolder = logOutFolder,
                incremental = inputs.isIncremental,
                packageName = packageName,
                artifactFolder = classInfoBundleDir,
                v1ArtifactsFolder = if (v1Artifacts.isEmpty()) null else v1Artifacts.single(),
                useAndroidX = useAndroidX
        )
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<DataBindingGenBaseClassesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("dataBindingGenBaseClasses")
        override val type: Class<DataBindingGenBaseClassesTask>
            get() = DataBindingGenBaseClassesTask::class.java

        private lateinit var sourceOutFolder: File
        private lateinit var classInfoBundleDir: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            val artifacts = variantScope.artifacts
            sourceOutFolder = artifacts.appendArtifact(
                InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT,
                taskName)
            classInfoBundleDir = artifacts.appendArtifact(
                InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                taskName)
        }

        override fun configure(task: DataBindingGenBaseClassesTask) {
            super.configure(task)

            task.layoutInfoDirectory =
                    variantScope.artifacts.getFinalArtifactFiles(
                            DATA_BINDING_LAYOUT_INFO_TYPE_MERGE)
            val variantData = variantScope.variantData
            val artifacts = variantScope.artifacts
            task.packageNameSupplier = variantData.variantConfiguration::getOriginalApplicationId
            task.mergedArtifactsFromDependencies = artifacts.getFinalArtifactFiles(
                    DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS)
            task.v1Artifacts = artifacts.getFinalArtifactFiles(
                    InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS
            )
            task.logOutFolder = variantScope.getIncrementalDir(task.name)
            task.sourceOutFolder = sourceOutFolder
            task.classInfoBundleDir = classInfoBundleDir
            task.useAndroidX = variantScope.globalScope.projectOptions[BooleanOption.USE_ANDROID_X]
            // needed to decide whether data binding should encode errors or not
            task.encodeErrors = variantScope.globalScope
                .projectOptions[BooleanOption.IDE_INVOKED_FROM_IDE]
        }
    }

    class CodeGenerator @Inject constructor(
        val args: LayoutInfoInput.Args,
        private val sourceOutFolder: File,
        private val logger: Logger,
        private val encodeErrors: Boolean
    ) : Runnable, Serializable {
        override fun run() {
            try {
                initLogger()
                BaseDataBinder(LayoutInfoInput(args))
                    .generateAll(DataBindingBuilder.GradleFileWriter(sourceOutFolder.absolutePath))
            } finally {
                clearLogger()
            }
        }

        private fun initLogger() {
            ScopedException.encodeOutput(encodeErrors)
            L.setClient { kind, message, _ ->
                logger.log(
                    kind.toLevel(),
                    message
                )
            }
        }

        private fun Diagnostic.Kind.toLevel() = when (this) {
            Diagnostic.Kind.ERROR -> LogLevel.ERROR
            Diagnostic.Kind.WARNING -> LogLevel.WARN
            else -> LogLevel.INFO
        }

        private fun clearLogger() {
            L.setClient(null)
        }
    }
}
