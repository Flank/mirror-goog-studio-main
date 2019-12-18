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

package com.android.build.gradle.integration.common.fixture

import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import java.io.File

/**
 * Contains the contents (source code and resources) of a Gradle project ([org.gradle.api.Project]).
 */
abstract class GradleProject(

    /**
     * Logical path to this project (e.g., ":app"). If it is provided and doesn't start with ':', it
     * will be normalized to start with ':'.
     */
    path: String? = null

) : TestProject {

    /**
     * Logical path to this project (e.g., ":app"). If it is provided and doesn't start with ':', it
     * will be normalized to start with ':'.
     */
    val path: String? = path?.let {
        if (path.startsWith(':')) {
            path
        } else {
            ":$path"
        }
    }

    /** Map from a relative path to the corresponding [TestSourceFile] instance.  */
    private val sourceFiles: MutableMap<String, TestSourceFile> = mutableMapOf()

    /** Returns a source file with the specified file path.  */
    fun getFile(relativePath: String): TestSourceFile {
        return sourceFiles[relativePath] ?: throw error("$relativePath does not exist")
    }

    /** Returns a source file with the specified file name.  */
    fun getFileByName(fileName: String): TestSourceFile {
        val matchedFiles = sourceFiles.filter { it.value.name == fileName }
        return when {
            matchedFiles.isEmpty() -> error("File with name '$fileName' not found")
            matchedFiles.size > 1 -> error(
                "Found multiple files named '$fileName`:" +
                        " ${matchedFiles.keys.joinToString(", ")}"
            )
            else -> matchedFiles.values.first()
        }
    }

    /** Returns all source files.  */
    fun getAllSourceFiles(): Collection<TestSourceFile> {
        return sourceFiles.values.toList()
    }

    /** Adds a file at the given file path with the given contents. The file must not yet exist. */
    fun addFile(relativePath: String, content: String) {
        addFile(TestSourceFile(relativePath, content))
    }

    /** Adds a source file. The file must not yet exist. */
    fun addFile(file: TestSourceFile) {
        check(!sourceFiles.containsKey(file.path)) { "${file.path} already exists" }
        sourceFiles[file.path] = file
    }

    /** Adds source files. The files must not yet exist. */
    fun addFiles(vararg files: TestSourceFile) {
        for (file in files) {
            addFile(file)
        }
    }

    /** Removes a source file with the specified file path. The file must already exist.  */
    fun removeFile(filePath: String) {
        check(sourceFiles.containsKey(filePath)) { "$filePath does not exist" }
        sourceFiles.remove(filePath)
    }

    /** Removes a source file with the specified file name. The file must already exist.  */
    fun removeFileByName(fileName: String) {
        removeFile(getFileByName(fileName).path)
    }

    /**
     * Replaces a source file at the corresponding file path, or adds it if the file does not yet
     * exist.
     */
    fun replaceFile(relativePath: String, content: String) {
        replaceFile(TestSourceFile(relativePath, content))
    }

    /**
     * Replaces a source file at the corresponding file path, or adds it if the file does not yet
     * exist.
     */
    fun replaceFile(file: TestSourceFile) {
        sourceFiles[file.path] = file
    }

    override fun write(projectDir: File, buildScriptContent: String?) {
        for (sourceFile in getAllSourceFiles()) {
            sourceFile.writeToDir(projectDir)
        }

        if (buildScriptContent != null) {
            val buildFile = File(projectDir, "build.gradle")
            if (buildFile.exists()) {
                buildFile.writeText("$buildScriptContent\n\n${buildFile.readText()}")
            } else {
                buildFile.writeText(buildScriptContent)
            }
        }
    }
}
