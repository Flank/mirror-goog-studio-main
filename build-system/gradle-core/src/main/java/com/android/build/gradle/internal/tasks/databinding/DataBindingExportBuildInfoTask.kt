/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.databinding.tool.LayoutXmlProcessor
import android.databinding.tool.processing.Scope
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

/**
 * Task to create an empty class annotated with @BindingBuildInfo, so that the Java compiler invokes
 * data binding even when the rest of the source code does not have data binding annotations.
 *
 * Note: The task name might be misleading: Historically, this task was used to generate a class
 * that contained the build environment information needed for data binding, but it is now no longer
 * the case. We'll rename it later.
 */
@CacheableTask
abstract class DataBindingExportBuildInfoTask : NonIncrementalTask() {

    @get:Input
    abstract val generatedClassFileName: Property<String>

    @get:Input
    abstract val useAndroidX: Property<Boolean>

    @get:Internal
    abstract val xmlProcessor: Property<LayoutXmlProcessor>

    @get:OutputDirectory
    abstract val triggerDir: DirectoryProperty

    override fun doTaskAction() {
        xmlProcessor.get().writeEmptyInfoClass(useAndroidX.get())
        Scope.assertNoError()
    }

    class CreationAction(componentProperties: ComponentPropertiesImpl) :
        VariantTaskCreationAction<DataBindingExportBuildInfoTask, ComponentPropertiesImpl>(
            componentProperties
        ) {

        override val name: String = computeTaskName("dataBindingExportBuildInfo")

        override val type: Class<DataBindingExportBuildInfoTask> =
            DataBindingExportBuildInfoTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out DataBindingExportBuildInfoTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.dataBindingExportBuildInfoTask = taskProvider
        }

        override fun configure(
            task: DataBindingExportBuildInfoTask
        ) {
            super.configure(task)
            task.generatedClassFileName.setDisallowChanges(
                creationConfig
                    .globalScope
                    .project.provider<String> {
                    task.xmlProcessor.get().infoClassFullName
                }
            )
            task.useAndroidX.setDisallowChanges(
                creationConfig
                    .services
                    .projectOptions[BooleanOption.USE_ANDROID_X]
            )
            task.xmlProcessor.setDisallowChanges(
                creationConfig
                    .globalScope
                    .project
                    .provider<LayoutXmlProcessor>(creationConfig::layoutXmlProcessor)
            )
            task.triggerDir.setDisallowChanges(
                creationConfig
                    .globalScope
                    .project.layout.projectDirectory.dir(
                    creationConfig.paths.classOutputForDataBinding.path
                )
            )
            task.dependsOn(creationConfig.taskContainer.sourceGenTask)
        }
    }
}