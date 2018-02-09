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

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.OutputFileProvider
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.google.common.collect.Multimap
import java.io.File

/**
 * Implementation for [OutputFileProvider]
 *
 * @param artifactsHolder the [BuildArtifactsHolder] for the variant
 * @param replacedArtifacts artifacts which the output of the task will replace
 * @param appendedArtifacts artifacts which the output of the task append to
 * @param filenamesMap map artifact types to the names of the files the task will create
 * @param unassociatedFilenames list of names of files the task will create, but is not associated
 *                              with any artifact.
 * @param taskName name of the task to be created.
 */
class OutputFileProviderImpl(
        artifactsHolder: BuildArtifactsHolder,
        replacedArtifacts: Collection<ArtifactType>,
        appendedArtifacts: Collection<ArtifactType>,
        filenamesMap : Multimap<ArtifactType, String>,
        unassociatedFilenames : Collection<String>,
        taskName : String,
        private val dslScope: DslScope) : OutputFileProvider {

    // map from filename to actual File.
    private val fileMap =
            filenamesMap.values().union(unassociatedFilenames)
                    .associate { it to artifactsHolder.createFile(taskName, it) }

    init{
        for (artifactType in replacedArtifacts) {
            val spec = BuildArtifactSpec.get(artifactType)
            val files = filenamesMap.get(artifactType)
            if (!spec.appendable) {
                if (files.isEmpty()) {
                    dslScope.issueReporter.reportError(
                            EvalIssueReporter.Type.GENERIC,
                            EvalIssueException(
                                "An output file must be created for OutputType '$artifactType'."))
                }
            }
            artifactsHolder.replaceArtifact(artifactType, files, taskName)
        }

        for (artifactType in appendedArtifacts) {
            artifactsHolder.appendArtifact(artifactType, filenamesMap[artifactType], taskName)
        }
    }

    override val file : File
        get() = when {
            fileMap.values.isEmpty() -> {
                dslScope.issueReporter.reportError(
                        EvalIssueReporter.Type.GENERIC,
                    EvalIssueException("No output file was defined."))
                File("")

            }
            fileMap.values.size > 1 -> {
                dslScope.issueReporter.reportError(
                        EvalIssueReporter.Type.GENERIC,
                    EvalIssueException("Multiple output files was defined."))
                File("")
            }
            else -> fileMap.values.single()
        }

    override fun getFile(filename: String): File {
        val file = fileMap[filename]
        if (file == null) {
            dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                EvalIssueException("Multiple output files was defined."))
            return File("")
        }
        return file
    }
}
