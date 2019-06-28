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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.cxx.attribution.collectNinjaLogs
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.getCxxBuildModel
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.cxx.services.runWhenBuildFinishes
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.google.common.base.Preconditions.checkElementIndex
import com.google.common.base.Preconditions.checkNotNull
import com.google.common.base.Preconditions.checkState
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.attribution.*
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.model.CxxBuildModel
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.errors.EvalIssueReporter
import com.android.ide.common.process.BuildCommandException
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.FileUtils
import com.android.utils.*
import com.google.common.base.Joiner
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.io.Files
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import java.io.File
import java.io.IOException
import java.util.function.Supplier
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import kotlin.streams.toList

/**
 * Task that takes set of JSON files of type NativeBuildConfigValueMini and does build steps with
 * them.
 *
 *
 * It declares no inputs or outputs, as it's supposed to always run when invoked. Incrementality
 * is left to the underlying build system.
 */
open class ExternalNativeBuildTask : NonIncrementalTask() {

    private lateinit var cxxBuildModel: Supplier<CxxBuildModel>
    private lateinit var evalIssueReporter: EvalIssueReporter
    private lateinit var generator: Provider<ExternalNativeJsonGenerator>

    /**
     * Get native build config minis. Also gather stats if they haven't already been gathered for
     * this variant
     *
     * @return the mini configs
     */
    private// Gather stats only if they haven't been gathered during model build
    val nativeBuildConfigValueMinis: List<NativeBuildConfigValueMini>
        @Throws(IOException::class)
        get() = if (stats.nativeBuildConfigCount == 0) {
            AndroidBuildGradleJsons.getNativeBuildMiniConfigs(
                generator.get().nativeBuildConfigurationsJsons, stats
            )
        } else AndroidBuildGradleJsons.getNativeBuildMiniConfigs(
            generator.get().nativeBuildConfigurationsJsons, null/* kotlin.Unit */
        )

    // Exposed in Variants API
    val objFolder: File
        get() = generator.get().objFolder

    // Exposed in Variants API
    val soFolder: File
        get() = generator.get().soFolder

    private val stlSharedObjectFiles: Map<Abi, File>
        get() = generator.get().stlSharedObjectFiles

    private val stats: GradleBuildVariant.Builder
        get() = generator.get().stats

    /** Represents a single build step that, when executed, builds one or more libraries.  */
    private class BuildStep(
        val buildCommand: String,
        val libraries: List<NativeLibraryValueMini>,
        val outputFolder: File
    )

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(evalIssueReporter).use { buildImpl() }
    }

    @Throws(BuildCommandException::class, IOException::class)
    private fun buildImpl() {
        infoln("starting build")
        checkNotNull(variantName)
        infoln("reading expected JSONs")
        val miniConfigs = nativeBuildConfigValueMinis
        infoln("done reading expected JSONs")

        val targets = generator.get().variant.buildTargetSet

        if (targets.isEmpty()) {
            infoln("executing build commands for targets that produce .so files or executables")
        } else {
            verifyTargetsExist(miniConfigs)
        }

        val buildSteps = Lists.newArrayList<BuildStep>()

        for (miniConfigIndex in miniConfigs.indices) {
            val config = miniConfigs[miniConfigIndex]
            infoln("evaluate miniconfig")
            if (config.libraries.isEmpty()) {
                infoln("no libraries")
                continue
            }

            val librariesToBuild = findLibrariesToBuild(config)
            if (librariesToBuild.isEmpty()) {
                infoln("no libraries to build")
                continue
            }

            if (!Strings.isNullOrEmpty(config.buildTargetsCommand)) {
                // Build all libraries together in one step, using the names of the artifacts.
                val artifactNames = librariesToBuild
                    .stream()
                    .filter { library -> library.artifactName != null }
                    .map<String> { library -> library.artifactName }
                    .sorted()
                    .distinct()
                    .toList()
                val buildTargetsCommand =
                    substituteBuildTargetsCommand(config.buildTargetsCommand!!, artifactNames)
                buildSteps.add(
                    BuildStep(
                        buildTargetsCommand,
                        librariesToBuild,
                        generator
                            .get()
                            .nativeBuildConfigurationsJsons[miniConfigIndex]
                            .parentFile
                    )
                )
                infoln("about to build targets " + artifactNames.joinToString(", "))
            } else {
                // Build each library separately using multiple steps.
                for (libraryValue in librariesToBuild) {
                    buildSteps.add(
                        BuildStep(
                            libraryValue.buildCommand!!,
                            listOf(libraryValue),
                            generator
                                .get()
                                .nativeBuildConfigurationsJsons[miniConfigIndex]
                                .parentFile
                        )
                    )
                    infoln("about to build ${libraryValue.buildCommand!!}")
                }
            }
        }

        executeProcessBatch(buildSteps)

        infoln("check expected build outputs")
        for (config in miniConfigs) {
            for (library in config.libraries.keys) {
                val libraryValue = config.libraries[library]!!
                checkState(!Strings.isNullOrEmpty(libraryValue.artifactName))
                if (targets.isNotEmpty() && !targets.contains(libraryValue.artifactName)) {
                    continue
                }
                if (buildSteps.stream().noneMatch { step -> step.libraries.contains(libraryValue) }) {
                    // Only need to check existence of output files we expect to create
                    continue
                }
                if (!libraryValue.output!!.exists()) {
                    throw GradleException(
                            "Expected output file at ${libraryValue.output} for target ${libraryValue.artifactName} but there was none")
                }
                if (libraryValue.abi == null) {
                    throw GradleException("Expected NativeLibraryValue to have non-null abi")
                }

                // If the build chose to write the library output somewhere besides objFolder
                // then copy to objFolder (reference b.android.com/256515)
                //
                // Since there is now a .so file outside of the standard build/ folder we have to
                // consider clean. Here's how the two files are covered.
                // (1) Gradle plugin deletes the build/ folder. This covers the destination of the
                //     copy.
                // (2) ExternalNativeCleanTask calls the individual clean targets for everything
                //     that was built. This should cover the source of the copy but it is up to the
                //     CMakeLists.txt or Android.mk author to ensure this.
                val abi = Abi.getByName(libraryValue.abi!!) ?: throw RuntimeException(
                    "Unknown ABI seen $(ibraryValue.abi}"
                )
                val expectedOutputFile = FileUtils.join(
                    generator.get().variant.objFolder,
                    abi.tag,
                    libraryValue.output!!.name
                )
                if (!FileUtils.isSameFile(libraryValue.output!!, expectedOutputFile)) {
                    infoln("external build set its own library output location for " +
                            "'${libraryValue.output!!.name}', copy to expected location")

                    if (expectedOutputFile.parentFile.mkdirs()) {
                        infoln("created folder ${expectedOutputFile.parentFile}")
                    }
                    infoln("copy file ${libraryValue.output} to $expectedOutputFile")
                    Files.copy(libraryValue.output!!, expectedOutputFile)
                }
            }
        }

        if (stlSharedObjectFiles.isNotEmpty()) {
            infoln("copy STL shared object files")
            for (abi in stlSharedObjectFiles.keys) {
                val stlSharedObjectFile = stlSharedObjectFiles.getValue(abi)
                val objAbi = FileUtils.join(
                    generator.get().variant.objFolder,
                    abi.tag,
                    stlSharedObjectFile.name
                )
                if (!objAbi.parentFile.isDirectory) {
                    // A build failure can leave the obj/abi folder missing. Just note that case
                    // and continue without copying STL.
                    infoln("didn't copy STL file to ${objAbi.parentFile} because that folder wasn't created by the build ")
                } else {
                    infoln("copy file $stlSharedObjectFile to $objAbi")
                    Files.copy(stlSharedObjectFile, objAbi)
                }
            }
        }

        infoln("build complete")
    }

    /**
     * Verifies that all targets provided by the user will be built. Throws GradleException if it
     * detects an unexpected target.
     */
    private fun verifyTargetsExist(miniConfigs: List<NativeBuildConfigValueMini>) {
        // Check the resulting JSON targets against the targets specified in ndkBuild.targets or
        // cmake.targets. If a target name specified by the user isn't present then provide an
        // error to the user that lists the valid target names.
        val targets = generator.get().variant.buildTargetSet
        infoln("executing build commands for targets: '${Joiner.on(", ").join(targets)}'")

        // Search libraries for matching targets.
        val matchingTargets = Sets.newHashSet<String>()
        val unmatchedTargets = Sets.newHashSet<String>()
        for (config in miniConfigs) {
            for (libraryValue in config.libraries.values) {
                if (targets.contains(libraryValue.artifactName)) {
                    matchingTargets.add(libraryValue.artifactName)
                } else {
                    unmatchedTargets.add(libraryValue.artifactName)
                }
            }
        }

        // All targets must be found or it's a build error
        for (target in targets) {
            if (!matchingTargets.contains(target)) {
                throw GradleException("Unexpected native build target $target. " +
                        "Valid values are: ${Joiner.on(", ").join(unmatchedTargets)}")
            }
        }
    }

    /**
     * @return List of libraries defined in the input config file, filtered based on the targets
     * field optionally provided by the user, and other criteria.
     */
    private fun findLibrariesToBuild(
        config: NativeBuildConfigValueMini
    ): List<NativeLibraryValueMini> {
        val librariesToBuild = Lists.newArrayList<NativeLibraryValueMini>()
        val targets = generator.get().variant.buildTargetSet
        for (libraryValue in config.libraries.values) {
            infoln("evaluate library ${libraryValue.artifactName} (${libraryValue.abi})")
            if (targets.isNotEmpty() && !targets.contains(libraryValue.artifactName)) {
                infoln("not building target ${libraryValue.artifactName!!} because it isn't in targets set")
                continue
            }

            if (Strings.isNullOrEmpty(config.buildTargetsCommand) && Strings.isNullOrEmpty(
                    libraryValue.buildCommand
                )
            ) {
                // This can happen when there's an externally referenced library.
                infoln(
                    "not building target ${libraryValue.artifactName!!} because there was no " +
                            "buildCommand for the target, nor a buildTargetsCommand for the config")
                continue
            }

            if (targets.isEmpty()) {
                if (libraryValue.output == null) {
                    infoln(
                        "not building target ${libraryValue.artifactName!!} because no targets " +
                                "are specified and library build output file is null")
                    continue
                }

                when (Files.getFileExtension(libraryValue.output!!.name)) {
                    "so" -> infoln("building target library ${libraryValue.artifactName!!} because no targets are specified.")
                    "" -> infoln("building target executable ${libraryValue.artifactName!!} because no targets are specified.")
                    else -> infoln("not building target ${libraryValue.artifactName!!} because the type cannot be determined.")
                }
            }

            librariesToBuild.add(libraryValue)
        }

        return librariesToBuild
    }

    /**
     * Given a list of build steps, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    @Throws(BuildCommandException::class, IOException::class)
    private fun executeProcessBatch(buildSteps: List<BuildStep>) {
        val logger = logger
        val processExecutor = GradleProcessExecutor(project)

        for (buildStep in buildSteps) {
            val tokens = buildStep.buildCommand.tokenizeCommandLineToEscaped()
            val processBuilder = ProcessInfoBuilder()
            processBuilder.setExecutable(tokens[0])
            for (i in 1 until tokens.size) {
                processBuilder.addArgs(tokens[i])
            }
            infoln("$processBuilder")

            val logFileSuffix: String
            val abiName = buildStep.libraries[0].abi
            if (buildStep.libraries.size > 1) {
                logFileSuffix = "targets"
                val targetNames = buildStep
                    .libraries
                    .stream()
                    .map { library -> library.artifactName + "_" + library.abi }
                    .toList()
                logger.lifecycle(
                    String.format("Build multiple targets ${targetNames.joinToString(" ")}"))
            } else {
                checkElementIndex(0, buildStep.libraries.size)
                logFileSuffix = buildStep.libraries[0].artifactName + "_" + abiName
                logger.lifecycle(
                    String.format("Build $logFileSuffix"))
            }

            if (generator.get().nativeBuildSystem === NativeBuildSystem.CMAKE) {
                val cxxAbiModelOptional = generator
                    .get()
                    .abis
                    .stream()
                    .filter { abiModel -> abiModel.abi.tag == abiName }
                    .findFirst()
                if (cxxAbiModelOptional.isPresent) {
                    val buildModel = cxxBuildModel.get()
                    appendTimestampAndBuildIdToNinjaLog(buildModel, cxxAbiModelOptional.get())
                    buildModel.runWhenBuildFinishes(
                        "CollectNinjaLogs"
                    ) {
                        try {
                            collectNinjaLogs(buildModel)
                        } catch (e: IOException) {
                            warnln("Cannot collect ninja logs for build attribution.")
                        }
                    }
                } else {
                    warnln("Cannot locate ABI ${abiName!!} for generating build attribution metrics.")
                }
            }

            createProcessOutputJunction(
                buildStep.outputFolder,
                "android_gradle_build_$logFileSuffix",
                processBuilder,
                logger,
                processExecutor,
                ""
            )
                .logStderrToInfo()
                .logStdoutToInfo()
                .execute()
        }
    }

    class CreationAction(
        private val generator: Provider<ExternalNativeJsonGenerator>,
        private val generateTask: TaskProvider<out Task>,
        scope: VariantScope
    ) : VariantTaskCreationAction<ExternalNativeBuildTask>(scope) {

        override val name: String
            get() = variantScope.getTaskName("externalNativeBuild")

        override val type: Class<ExternalNativeBuildTask>
            get() = ExternalNativeBuildTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out ExternalNativeBuildTask>
        ) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.externalNativeBuildTasks.add(taskProvider)
            variantScope.taskContainer.externalNativeBuildTask = taskProvider
        }

        override fun configure(task: ExternalNativeBuildTask) {
            super.configure(task)

            val scope = variantScope

            task.cxxBuildModel = Supplier { getCxxBuildModel(scope.globalScope.project.gradle) }
            task.dependsOn(
                generateTask, scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, JNI)
            )

            task.generator = generator
            task.evalIssueReporter = variantScope.globalScope.errorHandler
        }
    }

    companion object {

        // This placeholder is inserted into the buildTargetsCommand, and then later replaced by the
        // list of libraries that shall be built with a single build tool invocation.
        const val BUILD_TARGETS_PLACEHOLDER = "{LIST_OF_TARGETS_TO_BUILD}"

        /**
         * @param buildTargetsCommand The build command that can build multiple targets in parallel.
         * @param artifactNames The names of artifacts the build command will build in parallel.
         * @return Replaces the placeholder in the input command with the given artifacts and returns a
         * command that can be executed directly.
         */
        private fun substituteBuildTargetsCommand(
            buildTargetsCommand: String, artifactNames: List<String>
        ): String {
            return buildTargetsCommand.replace(
                BUILD_TARGETS_PLACEHOLDER, artifactNames.joinToString(" ")
            )
        }
    }
}
