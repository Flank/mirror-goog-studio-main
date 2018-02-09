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
import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildArtifactTransformBuilder.OperationType
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.artifact.InputArtifactProvider
import com.android.build.api.artifact.OutputFileProvider
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.google.common.collect.HashMultimap
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

/**
 * Implementation of VariantTaskBuilder.
 */
class BuildArtifactTransformBuilderImpl<out T : Task>(
        private val project: Project,
        private val artifactsHolder: BuildArtifactsHolder,
        private val artifactsActionsExecutor: DelayedActionsExecutor,
        private val taskNamePrefix: String,
        private val taskType: Class<T>,
        dslScope: DslScope)
    : SealableObject(dslScope), BuildArtifactTransformBuilder<T> {

    private val inputs = mutableListOf<ArtifactType>()
    private val outputFiles = HashMultimap.create<ArtifactType, String>()
    private val replacedOutput = mutableListOf<ArtifactType>()
    private val appendedOutput = mutableListOf<ArtifactType>()
    private val unassociatedFiles = mutableListOf<String>() // files that do not have an ArtifactType

    override fun output(artifactType: ArtifactType, operationType : OperationType)
            : BuildArtifactTransformBuilderImpl<T> {
        if (!checkSeal()) {
            return this
        }
        if (replacedOutput.contains(artifactType) || appendedOutput.contains(artifactType)) {
            dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                    EvalIssueException("Output type '$artifactType' was already specified as an output."))
            return this
        }
        val spec = BuildArtifactSpec.get(artifactType)
        when (operationType) {
            OperationType.REPLACE -> {
                if (!spec.replaceable) {
                    dslScope.issueReporter.reportError(
                            EvalIssueReporter.Type.GENERIC,
                            EvalIssueException("Replacing ArtifactType '$artifactType' is not allowed."))
                    return this
                }
                replacedOutput.add(artifactType)
            }
            OperationType.APPEND -> {
                if (!spec.appendable) {
                    dslScope.issueReporter.reportError(
                            EvalIssueReporter.Type.GENERIC,
                        EvalIssueException("Append to ArtifactType '$artifactType' is not allowed."))
                    return this
                }
                appendedOutput.add(artifactType)
            }
        }
        return this
    }

    override fun input(artifactType: ArtifactType)  : BuildArtifactTransformBuilderImpl<T> {
        if (!checkSeal()) {
            return this
        }
        if (inputs.contains(artifactType)) {
            dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                EvalIssueException("Output type '$artifactType' was already specified as an input."))
            return this
        }
        inputs.add(artifactType)
        return this
    }

    override fun outputFile(filename : String, vararg consumers : ArtifactType)
            : BuildArtifactTransformBuilderImpl<T> {
        if (!checkSeal()) {
            return this
        }
        if (outputFiles.containsValue(filename)) {
            dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                EvalIssueException("Output file '$filename' was already created."))
            return this
        }
        if (consumers.isEmpty()) {
            unassociatedFiles.add(filename)
        } else {
            for (consumer in consumers) {
                outputFiles.put(consumer, filename)

                val spec = BuildArtifactSpec.get(consumer)
                if (!spec.appendable && outputFiles[consumer].size > 1) {
                    dslScope.issueReporter.reportError(
                            EvalIssueReporter.Type.GENERIC,
                        EvalIssueException("OutputType '$consumer' does not support multiple output files."))
                }
            }
        }
        return this
    }

    override fun create(action : BuildArtifactTransformBuilder.ConfigurationAction<T>) : T {
        return create(action, null)
    }

    override fun create(function: T.(InputArtifactProvider, OutputFileProvider) -> Unit): T {
        return create(null, function)
    }

    private fun create(
            action : BuildArtifactTransformBuilder.ConfigurationAction<T>?,
            function : ((T, InputArtifactProvider, OutputFileProvider) -> Unit)?) : T {
        val taskName = artifactsHolder.getTaskName(taskNamePrefix)
        val task = project.tasks.create(taskName, taskType)
        if (!checkSeal()) {
            return task
        }
        val inputProvider = InputArtifactProviderImpl(artifactsHolder, inputs, dslScope)
        val outputProvider =
                OutputFileProviderImpl(
                        artifactsHolder,
                        replacedOutput,
                        appendedOutput,
                        outputFiles,
                        unassociatedFiles,
                        taskName,
                        dslScope)

        artifactsActionsExecutor.addAction {
            try {
                when {
                    action != null -> action.accept(task, inputProvider, outputProvider)
                    function != null -> function(task, inputProvider, outputProvider)
                }
            } catch (e: Exception) {
                dslScope.issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                    EvalIssueException(
                        """Exception thrown while configuring task '$taskName'.
                            |Type: ${e.javaClass.name}
                            |Message: ${e.message}""".trimMargin(),
                        null,
                        e
                    )
                )
            }
        }
        return task
    }

    /**
     * Wrapper to convert a [BuildArtifactTransformBuilder.SimpleConfigurationAction] to a
     * [BuildArtifactTransformBuilder.ConfigurationAction].
     */
    private class ConfigurationActionWrapper<in T : Task>(
            val action : BuildArtifactTransformBuilder.SimpleConfigurationAction<T>)
        : BuildArtifactTransformBuilder.ConfigurationAction<T> {
        override fun accept(task: T, input: InputArtifactProvider, output: OutputFileProvider) {
            action.accept(task, input.artifact, output.file)
        }
    }
    fun create(action : BuildArtifactTransformBuilder.SimpleConfigurationAction<T>) : T {
        return create(ConfigurationActionWrapper(action))
    }

    /**
     * Convert function that is used in the simple transform case to function that is used in the
     * generic case.
     */
    inline private fun <T>convertFunction(
            crossinline function : T.(input: BuildableArtifact, output: File) -> Unit) :
            T.(InputArtifactProvider, OutputFileProvider) -> Unit {
        return { input, output -> function(this, input.artifact, output.file) }
    }

    @JvmName("simpleCreate")
    fun create(function : T.(BuildableArtifact, File) -> Unit) : T {
        return create(convertFunction(function))
    }
}