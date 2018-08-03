/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.tasks.Sync

/** Configuration action for a package-renderscript task.  */
class PackageRenderscriptCreationAction(private val variantScope: VariantScope) :
    TaskCreationAction<Sync>() {

    override val name: String
        get() = variantScope.getTaskName("package", "Renderscript")
    override val type: Class<Sync>
        get() = Sync::class.java

    override fun execute(task: Sync) {
        // package from 3 sources. the order is important to make sure the override works well.
        task
            .from(variantScope.variantConfiguration.renderscriptSourceList)
            .include("**/*.rsh")
        task.into(variantScope.artifacts.appendArtifact(
            InternalArtifactType.RENDERSCRIPT_HEADERS, task, "out"))
    }
}
