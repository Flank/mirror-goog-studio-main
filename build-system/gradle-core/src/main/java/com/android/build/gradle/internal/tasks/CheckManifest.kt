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
import java.io.File
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

/** Class that checks the presence of the manifest.  */
abstract class CheckManifest : NonIncrementalTask() {

    @get:Optional
    @get:Input // we don't care about the content, just that the file is there.
    var manifest: File? = null

    @get:Input
    var optional: Boolean? = null

    @Input // force rerunning the task if the manifest shows up or disappears.
    fun getManifestPresence() = manifest != null && manifest!!.isFile

    @get:OutputDirectory
    abstract val fakeOutputDir: DirectoryProperty

    override fun doTaskAction() {
        if (!(optional!!) && manifest != null && !(manifest!!).isFile) {
            throw IllegalArgumentException(
                String.format(
                    "Main Manifest missing for variant %1\$s. Expected path: %2\$s",
                    variantName, manifest!!.absolutePath
                )
            )
        }
    }

    class CreationAction(scope: VariantScope, private val isManifestOptional: Boolean) :
        VariantTaskCreationAction<CheckManifest>(scope) {

        override val name: String
            get() = variantScope.getTaskName("check", "Manifest")

        override val type: Class<CheckManifest>
            get() = CheckManifest::class.java

        override fun handleProvider(taskProvider: TaskProvider<out CheckManifest>) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.checkManifestTask = taskProvider

            variantScope
                .artifacts
                .producesDir(
                    InternalArtifactType.CHECK_MANIFEST_RESULT,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    { obj: CheckManifest -> obj.fakeOutputDir },
                    "out"
                )
        }

        override fun configure(task: CheckManifest) {
            super.configure(task)
            val scope = variantScope

            task.optional = isManifestOptional
            task.manifest = scope.variantConfiguration.mainManifest
        }
    }
}
