/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.detector.api

import com.android.resources.ResourceFolderType

/** Specialized interface for detectors that scan resource folders (the folder directory
 * itself, not the individual files within it  */
interface ResourceFolderScanner {
    /**
     * Called for each resource folder
     *
     * @param context the context for the resource folder
     * @param folderName the resource folder name
     */
    fun checkFolder(context: ResourceContext, folderName: String)

    /**
     * Returns whether this detector applies to the given folder type. This
     * allows the detectors to be pruned from iteration, so for example when we
     * are analyzing a string value file we don't need to look up detectors
     * related to layout.
     *
     * @param folderType the folder type to be visited
     * @return true if this detector can apply to resources in folders of the
     * given type
     */
    fun appliesTo(folderType: ResourceFolderType): Boolean
}
