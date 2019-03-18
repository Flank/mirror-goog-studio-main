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

@file:JvmName("GoldenFileUtils")
package com.android.testutils

import com.google.common.io.Resources
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a Golden file for some test case.
 *
 * The golden file is expected to be a java resource at [resourcePath],
 * in a source root located at [resourceRootWorkspacePath].
 *
 * It is expected to have a test that calls [assertUpToDate], and
 * optionally a convenience class with a main method that calls [update].
 * If such a class is provided, it should be passed to [assertUpToDate]
 * to make the error message as helpful as possible.
 *
 * See `GoldenFileTest` for an example.
 */
class GoldenFile(
    private val resourceRootWorkspacePath: String,
    private val resourcePath: String,
    private val actualCallable: () -> List<String>) {
    private class State (val expected: List<String>, val actual: List<String>) {
        val diff: String by lazy {
            TestUtils.getDiff(expected.toTypedArray(), actual.toTypedArray())
        }
    }

    private fun getState(): State = State(
        expected = Resources.readLines(Resources.getResource(resourcePath), Charsets.UTF_8),
        actual = actualCallable.invoke()
    )

    @JvmOverloads
    fun assertUpToDate(updater: Class<*>? = null) {
        getState().apply {
            if (expected == actual) {
                return
            }
            throw AssertionError(
                "Golden file ${resourcePath.substringAfterLast('/')} is not up to date.\nEither:\n" +
                        "  (a) The change that caused this file to be out of date must be reverted, or\n" +
                        "  (b) The following diff must be applied" +
                        "${if (updater != null) " by running ${updater.canonicalName}.main() from within Idea" else " to '$resourceRootWorkspacePath/$resourcePath'"}:\n" +
                        diff
            )
        }
    }

    @JvmOverloads
    fun update(print: (String) -> Unit = { kotlin.io.print(it) }, getWorkspaceRoot: () -> Path = {TestUtils.getWorkspaceRoot().toPath()}) {
        getState().apply {
            if (expected == actual) {
                print("No diff to apply to $resourceRootWorkspacePath/$resourcePath")
                return
            }

            val actualFile = getWorkspaceRoot().resolve(resourceRootWorkspacePath).resolve(resourcePath)
            if (expected !=  Files.readAllLines(actualFile)) {
                throw IOException("Workspace file $resourceRootWorkspacePath/$resourcePath content different from corresponding resource, aborting update")
            }
            Files.write(actualFile, actual)
            print("Applied diff\n$diff")
        }
    }
}

