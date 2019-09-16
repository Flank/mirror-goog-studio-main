/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import java.io.File

/** Class that checks the presence of the manifest file, if it is required to exist.  */
abstract class CheckManifest : NonIncrementalTask() {

    /** Whether the manifest file is required to exist. */
    private var manifestRequired: Boolean = false

    /** The path to the manifest file. */
    private lateinit var manifestFile: Provider<File>

    /** A fake output directory, used for task dependencies and UP-TO-DATE purposes. */
    @get:OutputDirectory
    abstract val fakeOutputDir: DirectoryProperty

    @Input
    fun isManifestRequiredButNotPresent() = manifestRequired && !manifestFile.get().isFile

    override fun doTaskAction() {
        if (isManifestRequiredButNotPresent()) {
            error(
                "Main manifest is missing for variant $variantName." +
                        " Expected path: ${manifestFile.get().absolutePath}"
            )
        }
    }

    class CreationAction(scope: VariantScope) : VariantTaskCreationAction<CheckManifest>(scope) {

        override val name: String
            get() = variantScope.getTaskName("check", "Manifest")

        override val type: Class<CheckManifest>
            get() = CheckManifest::class.java

        override fun handleProvider(taskProvider: TaskProvider<out CheckManifest>) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.checkManifestTask = taskProvider

            variantScope.artifacts.producesDir(
                InternalArtifactType.CHECK_MANIFEST_RESULT,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                CheckManifest::fakeOutputDir,
                "out"
            )
        }

        override fun configure(task: CheckManifest) {
            super.configure(task)

            task.manifestRequired = variantScope.variantConfiguration.isManifestFileRequired
            task.manifestFile =
                TaskInputHelper.memoizeToProvider(task.project) {
                    variantScope.variantConfiguration.mainManifestFilePath
                }
        }
    }
}
