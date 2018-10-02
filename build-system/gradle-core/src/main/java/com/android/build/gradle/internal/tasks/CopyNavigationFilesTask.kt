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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Sync
import java.io.File

/** task copying the navigation files */
open class CopyNavigationFilesTask : Sync(), VariantAwareTask {

    @Internal
    override lateinit var variantName: String

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<CopyNavigationFilesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("copy", "NavigationFiles")
        override val type: Class<CopyNavigationFilesTask>
            get() = CopyNavigationFilesTask::class.java

        private lateinit var destinationDir: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            destinationDir = variantScope.artifacts.appendArtifact(
                InternalArtifactType.NAVIGATION_FILES, taskName, "out")
        }

        override fun configure(task: CopyNavigationFilesTask) {
            super.configure(task)
            task.from(variantScope.variantConfiguration.navigationFiles)
            task.destinationDir = destinationDir
        }
    }
}

