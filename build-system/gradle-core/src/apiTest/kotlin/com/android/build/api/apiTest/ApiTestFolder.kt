/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.apiTest

import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

// specialization of [TemporaryFolder] to keep the folder after test execution and generate
// extra documentation artifacts.
class ApiTestFolder(
    private val parentFolder: File,
    private val folderName: String
): TemporaryFolder(parentFolder) {

    var folder: File? = null

    override fun after() {
        // do not delete !
    }

    override fun getRoot(): File {
        return folder ?: throw RuntimeException("the temporary folder has not yet been created")
    }

    override fun create() {
        folder = createTemporaryFolderIn(parentFolder, folderName)
    }

    @Throws(IOException::class)
    private fun createTemporaryFolderIn(parentFolder: File, folderName: String): File? {
        val createdFolder = File(parentFolder, folderName)
        createdFolder.mkdirs()
        return createdFolder
    }
}