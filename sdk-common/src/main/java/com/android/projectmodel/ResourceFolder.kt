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

package com.android.projectmodel

import com.android.ide.common.util.PathString

/**
 * Represents a location on the filesystem where resources may be found. Some resource folders are
 * also places where the user's newly-created resources may be added. Resource folders don't
 * necessary include all of their contents as resources. Each implementation determines which
 * of its contents - if any - should be included.
 *
 * All implementations of [ResourceFolder] are invariant and use value semantics for hashcode
 * and equals.
 */
sealed class ResourceFolder {
    /**
     * filesystem Location of the Resource Folder
     */
    abstract val root: PathString
}

/**
 * Represents a resource folder that includes all of its contents as resources.
 */
data class RecursiveResourceFolder(override val root: PathString) : ResourceFolder()

/**
 * Represents a resource folder that selectively includes a subset of its contents as resources.
 */
data class SelectiveResourceFolder(
    override val root: PathString,
    /**
     * List of paths to the resources being included by this resource folder. These are all located
     * within the [root] folder.
     */
    val resources: List<PathString>
) : ResourceFolder()
