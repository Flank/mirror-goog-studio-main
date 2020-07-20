/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.AndroidProjectContainer.ProjectInfo
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor.runBuild
import com.android.build.gradle.integration.common.fixture.model.FileNormalizer
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.Option
import com.android.builder.model.v2.ide.SyncIssue
import com.android.builder.model.v2.models.AndroidProject
import com.google.common.collect.Sets
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.GradleProject
import org.junit.Assert
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.function.Consumer

/**
 * Builder for actions that get the gradle model from a [GradleTestProject].
 *
 * This returns the v2 model as a [FetchResult]
 */
class ModelBuilderV2 : BaseGradleExecutor<ModelBuilderV2> {
    private val explicitlyAllowedOptions = mutableSetOf<String>()
    private var maxSyncIssueSeverityLevel = 0

    internal constructor(project: GradleTestProject, projectConnection: ProjectConnection) : super(
        project,
        projectConnection,
        Consumer<GradleBuildResult> { lastBuildResult: GradleBuildResult? ->
            project.setLastBuildResult(lastBuildResult!!)
        },
        project.testDir.toPath(),
        project.buildFile.toPath(),
        project.getProfileDirectory(),
        project.heapSize,
        ConfigurationCaching.NONE
    ) {
    }

    data class FetchResult(
        val container: AndroidProjectContainer,
        val normalizer: FileNormalizer
    )

    /**
     * Do not fail if there are sync issues.
     *
     * Equivalent to `ignoreSyncIssues(SyncIssue.SEVERITY_ERROR)`.
     */
    @JvmOverloads
    fun ignoreSyncIssues(severity: Int = SyncIssue.SEVERITY_ERROR): ModelBuilderV2 {
        maxSyncIssueSeverityLevel = severity
        return this
    }

    fun allowOptionWarning(option: Option<*>): ModelBuilderV2 {
        explicitlyAllowedOptions.add(option.propertyName)
        return this
    }

    /**
     * Fetches the [AndroidProject] and [ProjectSyncIssues] for each project and return them as a
     * [AndroidProjectContainer]
     */
    fun fetchAndroidProjects(): FetchResult {
        val container = assertNoSyncIssues(buildModelV2(GetAndroidProjectV2Action()))
        return FetchResult(
        container,
        normalizer = FileNormalizerImpl(
            container.rootBuildId,
            GradleTestProject.getGradleUserHome(GradleTestProject.BUILD_DIR).toFile(),
            project?.androidHome,
            androidSdkHome
        ))
    }

    /** Return a list of all task names of the project.  */
    fun fetchTaskList(): List<String> = fetchGradleProject().tasks.map { it.name }

    private fun fetchGradleProject(): GradleProject =
        projectConnection.model(GradleProject::class.java).withArguments(arguments).get()


    /**
     * Returns a project model for each sub-project;
     *
     * @param action the build action to gather the model
     */
    private fun <T> buildModelV2(action: BuildAction<T>): T {
        val executor = projectConnection.action(action)
        return buildModel(executor).first
    }

    /**
     * Returns a project model container and the build result.
     *
     *
     * Can be used both when just fetching models or when also scheduling tasks to be run.
     */
    private fun <T> buildModel(executor: BuildActionExecuter<T>): Pair<T, GradleBuildResult> {
        with(BooleanOption.IDE_BUILD_MODEL_ONLY, true)
        with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
        setJvmArguments(executor)

        val stdErrFile = File.createTempFile("stdOut", "log")
        val stdOutFile = File.createTempFile("stdErr", "log")
        val progressListener = CollectingProgressListener()
        executor.addProgressListener(progressListener, OperationType.TASK)
        return try {
            val model: T =
                BufferedOutputStream(FileOutputStream(stdOutFile)).use { stdout ->
                    BufferedOutputStream(FileOutputStream(stdErrFile)).use { stderr ->
                        setStandardOut(executor, stdout)
                        setStandardError(executor, stderr)
                        executor.withArguments(arguments)
                        runBuild(
                            executor,
                            { executer: BuildActionExecuter<T>, resultHandler: ResultHandler<T>? ->
                                executer.run(resultHandler)
                            }
                        )
                    }
                }

            val buildResult = GradleBuildResult(
                stdOutFile, stdErrFile, progressListener.getEvents(), null
            )

            lastBuildResultConsumer.accept(buildResult)

            model to buildResult
        } catch (e: GradleConnectionException) {
            lastBuildResultConsumer.accept(
                GradleBuildResult(stdOutFile, stdErrFile, progressListener.getEvents(), e)
            )
            maybePrintJvmLogs(e)
            throw e
        }
    }

    private fun assertNoSyncIssues(
        container: AndroidProjectContainer
    ): AndroidProjectContainer {
        val allowedOptions: Set<String> =
            Sets.union(
                explicitlyAllowedOptions,
                optionPropertyNames
            )
        val errors = container.infoMaps
            .entries
            .asSequence()
            .flatMap { buildEntry: Map.Entry<BuildIdentifier, Map<String, ProjectInfo>> ->
                buildEntry
                    .value
                    .entries
                    .asSequence()
                    .map { projectEntry: Map.Entry<String, ProjectInfo> ->
                        "${buildEntry.key.rootDir}@@${projectEntry.key}" to removeAllowedIssues(projectEntry.value.issues.syncIssues, allowedOptions)
                    }
            }
            .filter { it.second.isNotEmpty() }
            .map {
                "project ${it.first} has Sync Issues: ${it.second.joinToString(separator = ", ", prefix = "[", postfix = "]")} "
            }
            .toList()

        if (errors.isNotEmpty()) {
            Assert.fail(errors.joinToString(separator = "\n"))
        }

        return container
    }

    private fun removeAllowedIssues(
        issues: Collection<SyncIssue>,
        allowedOptions: Set<String>
    ): List<SyncIssue> {
        return issues
            .asSequence()
            .filter { syncIssue: SyncIssue -> syncIssue.severity > maxSyncIssueSeverityLevel }
            .filter { syncIssue: SyncIssue -> syncIssue.type != SyncIssue.TYPE_DEPRECATED_DSL }
            .filter { syncIssue: SyncIssue ->
                (syncIssue.type
                        != SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE
                        || !allowedOptions.contains(syncIssue.data))
            }
            .toList()
    }
}

class FileNormalizerImpl(
    private val buildId: BuildIdentifier,
    private val gradleUserHome: File,
    private val androidSdk: File?,
    private val androidHome: File?
): FileNormalizer {
    init {
        println("Path variables:")
        println("GRADLE      : $gradleUserHome")
        println("SDK         : ${androidSdk ?: "(null)"}")
        println("ANDROID_HOME: ${androidHome ?: "(null)"}")
    }

    override fun normalize(file: File): String  {
        val result = file.relativeToOrNull(buildId.rootDir, "PROJECT")
            ?: file.relativeToOrNull(gradleUserHome, "GRADLE")
            ?: androidHome?.let { file.relativeToOrNull(it, "ANDROID_HOME") }
            ?: androidSdk?.let { file.relativeToOrNull(it, "SDK") }
            ?: file

        return result.toString()
    }

    private fun File.relativeToOrNull(root: File, varName: String): String? {
        // check first that the file is inside the root, otherwise relativeToOrNull can still
        // return something that starts with a bunch of ../
        if (startsWith(root)) {
            val relativeFile = relativeToOrNull(root)
            if (relativeFile != null) {
                return if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                    "[$varName]/${relativeFile.toString().replace("\\", "/")}"
                } else {
                    "[$varName]/$relativeFile"
                }
            }
        }

        return null
    }
}
