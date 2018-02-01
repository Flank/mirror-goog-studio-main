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

@file:JvmName("BuildableArtifactUtil")
package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.BuildableArtifact
import java.io.File

/**
 * Returns the first file in the [BuildableArtifact] by name if it exists, or null otherwise.
 *
 * @name the file name
 */
fun BuildableArtifact.forName(name: String) : File? {
    return files.find { it.name == name }
}
