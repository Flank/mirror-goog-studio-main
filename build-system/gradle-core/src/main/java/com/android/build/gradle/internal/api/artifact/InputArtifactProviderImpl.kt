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
import com.android.build.api.artifact.BuildArtifactType
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.artifact.InputArtifactProvider
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.scope.BuildArtifactHolder
import com.android.builder.errors.EvalIssueReporter

/**
 * Implementation for InputProvider
 */
class InputArtifactProviderImpl(
        private var artifactHolder: BuildArtifactHolder,
        private var inputTypes : Collection<ArtifactType>,
        private val dslScope: DslScope) : InputArtifactProvider {
    private val collections = inputTypes.map { artifactHolder.getArtifactFiles(it) }

    override val artifact: BuildableArtifact
        get() = when {
            collections.isEmpty() -> {
                dslScope.issueReporter.reportError(
                        EvalIssueReporter.Type.GENERIC,
                        "No artifacts was defined for input.")
                BuildableArtifactImpl(null, dslScope)
            }
            collections.size > 1 -> {
                dslScope.issueReporter.reportError(
                        EvalIssueReporter.Type.GENERIC,
                        "Multiple inputs types was defined.")
                BuildableArtifactImpl(null, dslScope)
            }
            else -> collections.single()
        }

    override fun getArtifact(type : BuildArtifactType): BuildableArtifact {
        val index = inputTypes.indexOf(type)
        if (index == -1) {
            dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                    "Artifact was not defined for input of type: $type.")
            return BuildableArtifactImpl(null, dslScope)
        }
        return collections[inputTypes.indexOf(type)]
    }
}
