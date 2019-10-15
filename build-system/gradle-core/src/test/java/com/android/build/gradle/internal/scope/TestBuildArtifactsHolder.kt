/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.scope

import com.android.utils.FileUtils
import org.gradle.api.Project
import java.io.File

internal class TestBuildArtifactsHolder(
    project: Project,
    val variantName: String,
    val rootOutputDir: () -> File
) : BuildArtifactsHolder(project, rootOutputDir, variantName) {

    /** Return the expected location of a generated file given the task name and file name. */
    internal fun file(artifactType: InternalArtifactType<*>, taskName : String, filename : String) =
        FileUtils.join(artifactType.getOutputDir(rootOutputDir()), variantName, taskName, filename)

}