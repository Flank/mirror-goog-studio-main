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
import android.databinding.tool.store.LayoutInfoInput
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File
import java.io.Serializable
import java.util.ArrayList
import javax.inject.Inject

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
open class DataBindingGenBaseClassesTask : DefaultTask() {
    // where xml info files are
    @get:InputDirectory lateinit var xmlInfoFolder: File
        private set
    // the package name for the module / app
    @get:Input lateinit var packageName: String
        private set
    // list of artifacts from dependencies
    @get:InputFiles lateinit var mergedArtifactsFromDependencies: FileCollection
        private set
    // where to keep the log of the task
    @get:OutputDirectory lateinit var logOutFolder: File
        private set
    // should we generate sources? true if v2 is enabled. it is still a task input because if
    // it changes, we need to clear the source gen folder
    @get:Input
    var generateSources: Boolean = false
        private set
    // where to write the new files
    @get:OutputDirectory lateinit var sourceOutFolder: File
        private set
    @get:OutputDirectory lateinit var classInfoBundleDir: File
        private set

    @TaskAction
    fun writeBaseClasses(inputs: IncrementalTaskInputs) {
        if (generateSources) {
            // TODO figure out why worker execution makes the task flake.
            // Some files cannot be accessed even though they show up when directory listing is
            // invoked.
            // b/69652332
            val args = buildInputArgs(inputs)
            CodeGenerator(args, sourceOutFolder).run()
        } else {
            FileUtils.cleanOutputDir(sourceOutFolder)
            FileUtils.cleanOutputDir(logOutFolder)
        }
    }

    private fun buildInputArgs(inputs: IncrementalTaskInputs): LayoutInfoInput.Args {
        val outOfDate = ArrayList<File>()
        val removed = ArrayList<File>()

        // if dependency added/removed a file, it is handled by the LayoutInfoInput class
        if (inputs.isIncremental) {
            inputs.outOfDate { inputFileDetails ->
                if (FileUtils.isFileInDirectory(inputFileDetails.file,
                        xmlInfoFolder) && inputFileDetails.file.name.endsWith(".xml")) {
                    outOfDate.add(inputFileDetails.file)
                }
            }
            inputs.removed { inputFileDetails ->
                if (FileUtils.isFileInDirectory(inputFileDetails.file,
                        xmlInfoFolder) && inputFileDetails.file.name.endsWith(".xml")) {
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
                infoFolder = xmlInfoFolder,
                dependencyClassesFolder = mergedArtifactsFromDependencies.singleFile,
                logFolder = logOutFolder,
                incremental = inputs.isIncremental,
                packageName = packageName,
                artifactFolder = classInfoBundleDir
        )
    }

    class ConfigAction(val variantScope: VariantScope,
            private val sourceOutFolder: File,
            private var logArtifactFolder: File)
        : TaskConfigAction<DataBindingGenBaseClassesTask> {

        override fun getName(): String = variantScope.getTaskName("dataBindingGenBaseClasses")

        override fun getType(): Class<DataBindingGenBaseClassesTask> = DataBindingGenBaseClassesTask::class.java

        override fun execute(task: DataBindingGenBaseClassesTask) {
            task.xmlInfoFolder = variantScope.layoutInfoOutputForDataBinding
            val variantData = variantScope.variantData
            task.packageName = variantData.variantConfiguration.originalApplicationId
            task.mergedArtifactsFromDependencies = variantScope.getOutput(
                    DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS)
            task.logOutFolder = variantScope.getIncrementalDir(task.name)
            task.generateSources = variantScope.globalScope.projectOptions.get(
                    BooleanOption.ENABLE_DATA_BINDING_V2)
            task.sourceOutFolder = sourceOutFolder
            task.classInfoBundleDir = logArtifactFolder
        }
    }

    class CodeGenerator @Inject constructor(val args: LayoutInfoInput.Args,
            private val sourceOutFolder: File) : Runnable, Serializable {
        override fun run() {
            BaseDataBinder(LayoutInfoInput(args))
                    .generateAll(DataBindingBuilder.GradleFileWriter(sourceOutFolder.absolutePath))
        }

    }
}