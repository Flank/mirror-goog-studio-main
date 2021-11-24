/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * Represents all the source folders for a source type in the variant.
 *
 * since 7.2
 */
@Incubating
interface SourceDirectories: AbstractSourceDirectories {

    /**
     * Get all registered source folders and files as a [List] of [Directory].
     *
     * Some source types do not have the concept of overriding, while others require a merging step to
     * ensure only one source file is used when processing begins.
     *
     * The returned [Provider] can be used directly in a [org.gradle.api.tasks.InputFiles] annotated
     * property of a [Task]
     */
    val all: Provider<List<Directory>>
}
