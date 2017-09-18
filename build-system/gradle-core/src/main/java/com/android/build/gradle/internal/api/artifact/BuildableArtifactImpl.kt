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

package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.BuildableArtifact
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskDependency
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of [BuildableArtifact].
 */
class BuildableArtifactImpl(
        internal var fileCollection: FileCollection?,
        private var issueReporter : EvalIssueReporter)
    : BuildableArtifact {
    companion object {
        private val resolvable = AtomicBoolean(false)

        fun isResolvable() : Boolean {
            return resolvable.get()
        }

        fun enableResolution() {
            resolvable.set(true)
        }

        fun disableResolution() {
            resolvable.set(false)
        }
    }

    private fun checkResolvable() {
        if (!isResolvable()) {
            issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                    "Resolving this BuildableArtifact can only done during task execution.")
        }
        if (fileCollection == null) {
            // If this exception is thrown, this is most likely a bug in the plugin.  It means a
            // BuildableArtifact is created, but the Task for this BuildableArtifact is not.
            // This error is also possible if there is another error that occurred previously, but
            // the build continues because this is we are doing a sync and issueReporter does not
            // throw.
            issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                    "BuildableArtifact has not been initialized.")
        }
    }

    override fun iterator(): Iterator<File> {
        return files.iterator()
    }

    override val files : Set<File>
        get() {
            checkResolvable()
            return Collections.unmodifiableSet(fileCollection!!.files)
        }

    override fun isEmpty() : Boolean {
        checkResolvable()
        return fileCollection!!.isEmpty
    }

    override fun getBuildDependencies(): TaskDependency =
            if (fileCollection != null) {
                fileCollection!!.buildDependencies
            } else {
                issueReporter.reportError(
                        EvalIssueReporter.Type.GENERIC,
                        "Cannot get build dependencies before BuildableArtifact is initialized.")
                TaskDependency { mutableSetOf() }
            }
}