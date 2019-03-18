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

package com.android.testutils

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import kotlin.test.assertFailsWith

class GoldenFileTest {

    class FakeUpdater

    @Test
    fun assertUpToDatePass() {
        val underTest = GoldenFile(
            resourceRootWorkspacePath = "tools/base/testutils/src/test/resources",
            resourcePath = "com/android/testutils/GoldenFileExample.txt",
            actualCallable = { listOf("Expected golden file content", "on multiple", "lines") })

        // Check is currently correct
        underTest.assertUpToDate(FakeUpdater::class.java)
    }

    @Test
    fun assertUpToDateFailWithAutomaticUpdater() {
        val underTest = GoldenFile(
            resourceRootWorkspacePath = "tools/base/testutils/src/test/resources",
            resourcePath = "com/android/testutils/GoldenFileExample.txt",
            actualCallable = { listOf("Expected golden file content", "lines") })

        val failure =
            assertFailsWith<AssertionError> { underTest.assertUpToDate(FakeUpdater::class.java) }
        assertThat(failure)
            .hasMessageThat()
            .isEqualTo(
                """
                    Golden file GoldenFileExample.txt is not up to date.
                    Either:
                      (a) The change that caused this file to be out of date must be reverted, or
                      (b) The following diff must be applied by running com.android.testutils.GoldenFileTest.FakeUpdater.main() from within Idea:
                    @@ -2 +2
                    - on multiple
        """.trimIndent() + "\n"
            )
    }

    @Test
    fun assertUpToDateFailWithoutHelper() {
        val underTest = GoldenFile(
            resourceRootWorkspacePath = "tools/base/testutils/src/test/resources",
            resourcePath = "com/android/testutils/GoldenFileExample.txt",
            actualCallable = { listOf("Expected golden file content", "lines") })

        val failureWithoutAutomaticUpdater =
            assertFailsWith<AssertionError> { underTest.assertUpToDate() }
        assertThat(failureWithoutAutomaticUpdater)
            .hasMessageThat()
            .isEqualTo(
                """
                    Golden file GoldenFileExample.txt is not up to date.
                    Either:
                      (a) The change that caused this file to be out of date must be reverted, or
                      (b) The following diff must be applied to 'tools/base/testutils/src/test/resources/com/android/testutils/GoldenFileExample.txt':
                    @@ -2 +2
                    - on multiple
                    """.trimIndent() + "\n"
            )
    }

    @Test
    fun testUpdateNoOp() {

        val underTest = GoldenFile(
            resourceRootWorkspacePath = "tools/base/testutils/src/test/resources",
            resourcePath = "com/android/testutils/GoldenFileExample.txt",
            actualCallable = { listOf("Expected golden file content", "on multiple", "lines") }
        )

        val output = StringBuilder()
        // Check is currently correct, this shouldn't use the workspace root
        underTest.update(
            print = { string: String -> output.append(string).append('\n') },
            getWorkspaceRoot = { throw UnsupportedOperationException() })

        assertThat(output.toString()).isEqualTo("No diff to apply to tools/base/testutils/src/test/resources/com/android/testutils/GoldenFileExample.txt\n")
    }

    @Test
    fun testUpdateChanged() {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val workspaceRoot = fs.getPath("/studio-master-dev")
            val underTest = GoldenFile(
                resourceRootWorkspacePath = "tools/base/testutils/src/test/resources",
                resourcePath = "com/android/testutils/GoldenFileExample.txt",
                actualCallable = { listOf("Changed") }
            )
            // When we have a workspace with the appropriate file content
            val exampleFile =
                workspaceRoot.resolve("tools/base/testutils/src/test/resources/com/android/testutils/GoldenFileExample.txt")
            Files.createDirectories(exampleFile.parent)
            Files.write(exampleFile, listOf("Expected golden file content", "on multiple", "lines"))
            val output = StringBuilder()
            underTest.update(
                print = { string: String -> output.append(string).append('\n') },
                getWorkspaceRoot = { workspaceRoot })
            // Then the updater updates the content of the file
            assertThat(Files.readAllLines(exampleFile)).containsExactly("Changed")
            assertThat(output.toString())
                .isEqualTo(
                    """
                        Applied diff
                        @@ -1 +1
                        - Expected golden file content
                        - on multiple
                        - lines
                        @@ -4 +1
                        + Changed""".trimIndent() + "\n\n"
                )
        }
    }

    @Test
    fun testUpdateInconsistent() {
        Jimfs.newFileSystem(Configuration.unix()).use { fs ->
            val workspaceRoot = fs.getPath("/studio-master-dev")
            val underTest = GoldenFile(
                resourceRootWorkspacePath = "tools/base/testutils/src/test/resources",
                resourcePath = "com/android/testutils/GoldenFileExample.txt",
                actualCallable = { listOf("Changed") }
            )
            // When we have a workspace with badfile content
            val exampleFile =
                workspaceRoot.resolve("tools/base/testutils/src/test/resources/com/android/testutils/GoldenFileExample.txt")
            Files.createDirectories(exampleFile.parent)
            Files.write(exampleFile, listOf("Corrupt content"))
            // Then the updater fails
            val e = assertFailsWith<IOException> {
                underTest.update(getWorkspaceRoot = { workspaceRoot })
            }
            assertThat(e)
                .hasMessageThat()
                .isEqualTo("Workspace file tools/base/testutils/src/test/resources/com/android/testutils/GoldenFileExample.txt content different from corresponding resource, aborting update")
        }
    }
}