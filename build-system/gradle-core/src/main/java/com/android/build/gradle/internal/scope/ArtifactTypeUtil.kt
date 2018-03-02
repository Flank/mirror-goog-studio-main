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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import java.io.File

/**
 * Returns a suitable output directory for this receiving artifact type.
 * This folder will be independent of variant or tasks names and should be further qualified
 * if necessary.
 *
 * @param parentFile the parent directory.
 */
fun ArtifactType.getOutputDir(parentFile: File): File {
    return File(
        if (this is InternalArtifactType) {
            kind.getOutputDir(parentFile)
        } else {
            InternalArtifactType.Kind.INTERMEDIATES.getOutputDir(parentFile)
        },
        name().toLowerCase())
}