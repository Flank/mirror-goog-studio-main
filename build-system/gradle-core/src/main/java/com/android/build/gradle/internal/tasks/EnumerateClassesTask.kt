/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.dependency.EnumerateClassesDelegate
import com.android.build.gradle.internal.dependency.EnumerateClassesRunnable
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider

/**
 * A task that enumerates the classes in each module, so they can be checked for duplicates in
 * CheckDuplicateClassesTask
 */
@CacheableTask
abstract class EnumerateClassesTask : NonIncrementalTask() {

    @get:SkipWhenEmpty
    @get:Classpath
    abstract val classesJar: ConfigurableFileCollection

    @get:OutputFile
    abstract val enumeratedClasses: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(EnumerateClassesRunnable::class.java) { params ->
            params.classJar.set(classesJar.singleFile)
            params.outputFile.set(enumeratedClasses)
        }
    }

    class CreationAction(componentProperties: ComponentPropertiesImpl, val isLibrary: Boolean)
        : VariantTaskCreationAction<EnumerateClassesTask, ComponentPropertiesImpl>(
        componentProperties
    ) {
        override val type = EnumerateClassesTask::class.java

        override val name = componentProperties.computeTaskName("enumerate", "Classes")

        override fun handleProvider(
            taskProvider: TaskProvider<out EnumerateClassesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.producesFile(
                InternalArtifactType.ENUMERATED_CLASSES,
                taskProvider,
                EnumerateClassesTask::enumeratedClasses,
                "classes"
            )
        }

        override fun configure(
            task: EnumerateClassesTask
        ) {
            super.configure(task)

            task.classesJar.fromDisallowChanges(creationConfig.artifacts
                    .getFinalProductAsFileCollection(InternalArtifactType.APP_CLASSES))
        }
    }
}