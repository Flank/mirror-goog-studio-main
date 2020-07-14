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
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor.runBuild
import com.android.build.gradle.integration.common.fixture.ModelContainerV2.ModelInfo
import com.android.build.gradle.integration.common.fixture.model.FileNormalizer
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.Option
import com.android.builder.model.v2.ide.SyncIssue
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.VariantDependencies
import com.android.utils.FileUtils
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

    data class FetchResult<T>(
        val container: T,
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
     * [ModelContainer]
     */
    fun fetchAndroidProjects(): FetchResult<ModelContainerV2<AndroidProject>> {
        val container =
            assertNoSyncIssues(buildModelV2(GetAndroidModelV2Action(AndroidProject::class.java)))

        return FetchResult(
            container,
            normalizer = FileNormalizerImpl(
                container.rootBuildId,
                GradleTestProject.getGradleUserHome(GradleTestProject.BUILD_DIR).toFile(),
                project?.androidHome,
                androidSdkHome
            )
        )
    }

    /**
     * Fetches the [AndroidProject] and [ProjectSyncIssues] for each project and return them as a
     * [ModelContainer]
     */
    fun fetchVariantDependencies(variantName: String): FetchResult<ModelContainerV2<VariantDependencies>> {
        val container = buildModelV2(
            GetAndroidModelV2Action(
                VariantDependencies::class.java,
                variantName
            )
        )

        return FetchResult(
            container,
            normalizer = FileNormalizerImpl(
                container.rootBuildId,
                GradleTestProject.getGradleUserHome(GradleTestProject.BUILD_DIR).toFile(),
                project?.androidHome,
                androidSdkHome
            )
        )
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
        container: ModelContainerV2<AndroidProject>
    ): ModelContainerV2<AndroidProject> {
        val allowedOptions: Set<String> =
            Sets.union(
                explicitlyAllowedOptions,
                optionPropertyNames
            )
        val errors = container.infoMaps
            .entries
            .asSequence()
            .flatMap { buildEntry: Map.Entry<BuildIdentifier, Map<String, ModelInfo<AndroidProject>>> ->
                buildEntry
                    .value
                    .entries
                    .asSequence()
                    .map { projectEntry: Map.Entry<String, ModelInfo<AndroidProject>> ->
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
    buildId: BuildIdentifier,
    gradleUserHome: File,
    androidSdk: File?,
    androidHome: File?
): FileNormalizer {

    private data class RootData(
        val root: File,
        val varName: String,
        val stringModifier: ((String) -> String)? = null
    )

    private val rootDataList: List<RootData>

    init {
        val mutableList = mutableListOf<RootData>()

        mutableList.add(RootData(buildId.rootDir, "PROJECT"))

        // Custom root for Gradle's transform cache. We'll replace not the full path but
        // re-inject the paths into it. The goal is to remove the checksum.
        // Must be in the list before gradleUserHome
        val gradleTransformCache: File = FileUtils.join(
            gradleUserHome,
            "caches", "transforms-2", "files-2.1"
        )
        mutableList.add(RootData(gradleTransformCache, "GRADLE") {
            // re-inject the segments between {GRADLE} and the relative path
            // and remove the actual checksum (size 32)
            // incoming string is "XXXX/..." so removing XXX leaves a leading /
            "caches/transforms-2/files-2.1/{CHECKSUM}${it.substring(32)}"
        })

        mutableList.add(RootData(gradleUserHome, "GRADLE"))
        androidSdk?.let {
            mutableList.add(RootData(it, "SDK"))
        }
        androidHome?.let {
            mutableList.add(RootData(it, "ANDROID_HOME"))
        }

        GradleTestProject.localRepositories.asSequence().map { it.toFile()}.forEach {
            mutableList.add(RootData(it, "LOCAL_REPO"))
        }

        rootDataList = mutableList.toList()
    }

    override fun normalize(file: File): String  {
        val suffix = if (file.isFile) {
            "{F}"
        } else if (file.isDirectory) {
            "{D}"
        } else {
            "{!}"
        }

        for (rootData in rootDataList) {
            val result = file.relativeToOrNull(
                rootData.root,
                rootData.varName,
                rootData.stringModifier)

            if (result != null) {
                return result + suffix
            }
        }

        return file.toString() + suffix
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Path variables:")
        val len = rootDataList.map { it.varName.length }.max()?.dec() ?: 10
        for (rootData in rootDataList) {
            sb.append('\n')
            sb.append(rootData.varName)
            for (i in rootData.varName.length..len) {
                sb.append(' ')
            }

            sb.append(": ${rootData.root}")
        }

        return sb.toString()
    }

    private fun File.relativeToOrNull(
        root: File,
        varName: String,
        action: ((String) -> String)? = null
    ): String? {
        // check first that the file is inside the root, otherwise relativeToOrNull can still
        // return something that starts with a bunch of ../
        if (startsWith(root)) {
            val relativeFile = relativeToOrNull(root)
            if (relativeFile != null) {
                val osNormalizedString = if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                    relativeFile.toString().replace("\\", "/")
                } else {
                    relativeFile.toString()
                }

                val finalString = if (action != null) {
                    action(osNormalizedString)
                } else {
                    osNormalizedString
                }

                return "{$varName}/$finalString"
            }
        }

        return null
    }
}
