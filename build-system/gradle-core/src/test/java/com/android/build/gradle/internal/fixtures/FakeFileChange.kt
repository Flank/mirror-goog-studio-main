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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.file.FileType
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import java.io.File

class FakeFileChange @JvmOverloads constructor(
    private val file: File,
    private val changeType: ChangeType = ChangeType.REMOVED,
    private val fileType: FileType = FileType.FILE,
    private val normalizedPath: String = file.canonicalPath
) : FileChange {
    override fun getChangeType(): ChangeType = changeType

    override fun getFile(): File = file

    override fun getFileType(): FileType = fileType

    override fun getNormalizedPath(): String = normalizedPath
}
