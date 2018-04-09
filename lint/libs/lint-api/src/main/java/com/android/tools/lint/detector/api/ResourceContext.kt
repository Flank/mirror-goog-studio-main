/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.tools.lint.client.api.LintDriver
import com.google.common.annotations.Beta
import java.io.File

/**
 * A [com.android.tools.lint.detector.api.Context] used when checking resource files
 * (both bitmaps and XML files; for XML files a subclass of this context is used:
 * [com.android.tools.lint.detector.api.XmlContext].)
 *
 * @param driver the driver running through the checks
 * @param project the project containing the file being checked
 * @param main the main project if this project is a library project, or
 *   null if this is not a library project. The main project is
 *   the root project of all library projects, not necessarily the
 *   directly including project.
 * @param file the file being checked
 * @property resourceFolderType the [com.android.resources.ResourceFolderType] of this file, if any
 * @constructor Constructs a new [ResourceContext]
 * <p>
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
open class ResourceContext(
    driver: LintDriver,
    project: Project,
    main: Project?,
    file: File,
    val resourceFolderType: ResourceFolderType?,
    contents: String?
) : Context(driver, project, main, file, contents) {
    private var folderConfiguration: FolderConfiguration? = null

    /**
     * Returns the folder version. For example, for the file values-v14/foo.xml,
     * it returns 14.
     *
     * @return the folder version, or -1 if no specific version was specified
     */
    val folderVersion: Int
        get() {
            val config = getFolderConfiguration()
            if (config != null) {
                val versionQualifier = config.versionQualifier
                if (versionQualifier != null) {
                    return versionQualifier.version
                }
            }

            return -1
        }

    /**
     * Returns the resource folder that this resource context corresponds to, if applicable
     *
     * @return the resource folder if this resource context corresponds to a resource file
     */
    protected open val resourceFolder: File?
        get() = if (resourceFolderType != null) file else null

    /**
     * Returns the [FolderConfiguration] that this resource context belongs to
     *
     * @return the folder configuration, or null for non-resource XML files such as manifest files
     */
    fun getFolderConfiguration(): FolderConfiguration? {
        if (folderConfiguration == null && resourceFolderType != null) {
            val folder = resourceFolder
            if (folder != null) {
                folderConfiguration = FolderConfiguration.getConfigForFolder(folder.name)
            }
        }
        return folderConfiguration
    }
}
