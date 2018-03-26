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
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.tasks.Sync

/** Configuration action for a package-renderscript task.  */
class PackageRenderscriptConfigAction(private val variantScope: VariantScope) :
    TaskConfigAction<Sync> {

    override fun getName(): String = variantScope.getTaskName("package", "Renderscript")
    override fun getType(): Class<Sync> = Sync::class.java

    override fun execute(packageRenderscript: Sync) {
        // package from 3 sources. the order is important to make sure the override works well.
        packageRenderscript
            .from(variantScope.variantConfiguration.renderscriptSourceList)
            .include("**/*.rsh")
        packageRenderscript.into(variantScope.artifacts.appendArtifact(
            InternalArtifactType.RENDERSCRIPT_HEADERS, packageRenderscript, "out"))
    }
}
