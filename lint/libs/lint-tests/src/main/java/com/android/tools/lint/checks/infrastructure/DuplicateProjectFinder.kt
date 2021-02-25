/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import java.io.File

/**
 * Finds cases where the same lint project is seen by more than one
 * test. This catches cases where I've accidentally duplicated tests;
 * this happened in cases where I for example had ".incremental()"
 * enabled to test scenarios for the IDE -- now that resource
 * repositories are always enabled, I want to remove the .incremental()
 * call instead of the whole test, since it's possible that the
 * incremental tests is the only one covering various corner cases
 * around using the resource repository (since previously that was
 * directly linked to resource repository access). Instead, I can now
 * just delete the incremental declarations and then run the tests
 * through this finder to see if they're unique or just duplicating
 * something already being tested. (Note that this detector does not
 * encode all lint() task attributes, so take care to look at the full
 * project config to verify that two potentially duplicate tests are
 * really duplicates.
 *
 * When I first ran this on the built-in tests (before auditing for
 * subtle differences in the tests) it flagged 62 tests as duplicates!
 */
class DuplicateProjectFinder {
    /** Map from checksum to corresponding test. */
    private val checksums = HashMap<TestMode, HashMap<Long, String>>()

    fun recordTestProject(name: String, task: TestLintTask, mode: TestMode, projects: List<File>) {
        // We don't know the version for files coming out of a platform
        // build or a custom file pointed to by an environment variable;
        // in that case just use a hash of the path and the timestamp to
        // make sure the database is updated if the file changes (and the
        // path has to prevent the unlikely scenario of two different
        // databases having the same timestamp
        val hashFunction = Hashing.farmHashFingerprint64()
        @Suppress("SpellCheckingInspection")
        val hasher = hashFunction.newHasher()

        for (root in projects.sortedBy { it.name }) {
            hashFiles(hasher, root, root.name)
        }

        hasher.putString(mode.description, Charsets.UTF_8)

        // Hash issue ids as well  (in case the same file is reused across unrelated tests)
        for (issue in task.checkedIssues.sortedBy { it.id }) {
            hasher.putString(issue.id, Charsets.UTF_8)
        }

        val hashCode = hasher.hash()
        val checksum = hashCode.asLong()
        val modeChecksums = checksums[mode] ?: HashMap<Long, String>().also { checksums[mode] = it }

        if (modeChecksums[checksum] != null) {
            println("** Duplicate projects: $name and ${modeChecksums[checksum]} in mode $mode")
        }
        modeChecksums[checksum] = name
    }

    private fun hashFiles(hasher: Hasher, root: File, relative: String) {
        val listFiles = root.listFiles() ?: return
        for (file in listFiles.sortedBy { it.name }) {
            if (file.isDirectory) {
                hashFiles(hasher, file, relative + "/" + file.name)
            } else if (file.isFile) {
                val newHasher = hasher.putString(file.name, Charsets.UTF_8)
                // Make sure it's not creating new instances; if so we'll
                // need to update this to mutate and return the hashers
                assert(hasher === newHasher)
                val contents = file.readText()
                hasher.putString(contents, Charsets.UTF_8)
            }
        }
    }
}
