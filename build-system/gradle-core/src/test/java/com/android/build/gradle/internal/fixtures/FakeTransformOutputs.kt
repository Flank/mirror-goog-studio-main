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

import com.android.utils.FileUtils
import org.gradle.api.artifacts.transform.TransformOutputs
import org.junit.rules.TemporaryFolder
import java.io.File

class FakeTransformOutputs(temporaryFolder: TemporaryFolder) : TransformOutputs {
    val rootDir: File = temporaryFolder.newFolder()

    lateinit var outputDirectory: File
        private set
    lateinit var outputFile: File
        private set

    override fun file(path: Any): File {
        outputFile = if (path is File && path.isAbsolute) {
            path
        } else {
            path as String
            rootDir.resolve(path)
        }
        return outputFile
    }

    override fun dir(path: Any): File {
        outputDirectory = if (path is File && path.isAbsolute) {
            path
        } else {
            path as String
            rootDir.resolve(path).also { FileUtils.mkdirs(it) }
        }
        return outputDirectory
    }
}