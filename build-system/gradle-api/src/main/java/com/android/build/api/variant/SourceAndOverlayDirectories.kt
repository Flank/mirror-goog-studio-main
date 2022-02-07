/*
 * Copyright (C) 2022 The Android Open Source Project
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
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * Represent a collection of directories that have overlay properties to each other. This mean
 * that some directories will carry higher priority when merging content will be carried out.
 *
 * The collection is represented as a [List] where the first elements are the highest priority ones.
 *
 * For a specific priority, there can be more than one directory defined and is represented as a
 * [Collection] of [Directory].
 *
 * Therefore, [SourceAndOverlayDirectories] can be represented as a [List] of [Collection] of
 * [Directory].
 *
 */
@Incubating
interface SourceAndOverlayDirectories: AbstractSourceDirectories {
    /**
     * Get all registered source folders and files as a [List] of [Collection] of [Directory].
     *
     * The outer [List] represents the priority of [Directory] respective to each other, meaning that
     * elements first in the list overrides elements last in the list.
     *
     * The inner [Collection] represents all [Directory] with the same priority  respective to each
     * other.
     *
     * The returned [Provider] can be used directly in a [org.gradle.api.tasks.InputFiles] annotated
     * property of a [Task]
     */
    val all: Provider<List<Collection<Directory>>>
}
