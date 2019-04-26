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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.cxx.attribution.UtilsKt.collectNinjaLogs;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.infoln;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.warnln;
import static com.android.build.gradle.internal.cxx.model.GetCxxBuildModelKt.getCxxBuildModel;
import static com.android.build.gradle.internal.cxx.process.ProcessOutputJunctionKt.createProcessOutputJunction;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.BuildSessionImpl;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.attribution.UtilsKt;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini;
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment;
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment;
import com.android.build.gradle.internal.cxx.model.CxxAbiModel;
import com.android.build.gradle.internal.cxx.model.CxxBuildModel;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.errors.EvalIssueReporter;
import com.android.ide.common.process.BuildCommandException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * Task that takes set of JSON files of type NativeBuildConfigValueMini and does build steps with
 * them.
 *
 * <p>It declares no inputs or outputs, as it's supposed to always run when invoked. Incrementality
 * is left to the underlying build system.
 */
public class ExternalNativeBuildTask extends NonIncrementalTask {

    private EvalIssueReporter evalIssueReporter;
    private Provider<ExternalNativeJsonGenerator> generator;

    // This placeholder is inserted into the buildTargetsCommand, and then later replaced by the
    // list of libraries that shall be built with a single build tool invocation.
    public static final String BUILD_TARGETS_PLACEHOLDER = "{LIST_OF_TARGETS_TO_BUILD}";

    /** Represents a single build step that, when executed, builds one or more libraries. */
    private static class BuildStep {
        @NonNull private String buildCommand;
        @NonNull private List<NativeLibraryValueMini> libraries;
        @NonNull private File outputFolder;

        // Defines a build step that builds one library with a single command.
        BuildStep(
                @NonNull String buildCommand,
                @NonNull NativeLibraryValueMini library,
                @NonNull File outputFolder) {
            this(buildCommand, Lists.newArrayList(library), outputFolder);
        }

        // Defines a build step that builds one or more libraries with a single command.
        BuildStep(
                @NonNull String buildCommand,
                @NonNull List<NativeLibraryValueMini> libraries,
                @NonNull File outputFolder) {
            this.buildCommand = buildCommand;
            this.libraries = libraries;
            this.outputFolder = outputFolder;
        }
    }

    @Override
    protected void doTaskAction() throws BuildCommandException, IOException {
        try (ThreadLoggingEnvironment ignore =
                new IssueReporterLoggingEnvironment(evalIssueReporter)) {
            buildImpl();
        }
    }

    private void buildImpl() throws BuildCommandException, IOException {
        infoln("starting build");
        checkNotNull(getVariantName());
        infoln("reading expected JSONs");
        List<NativeBuildConfigValueMini> miniConfigs = getNativeBuildConfigValueMinis();
        infoln("done reading expected JSONs");

        Set<String> targets = generator.get().variant.getBuildTargetSet();

        if (targets.isEmpty()) {
            infoln("executing build commands for targets that produce .so files or executables");
        } else {
            verifyTargetsExist(miniConfigs);
        }

        List<BuildStep> buildSteps = Lists.newArrayList();

        for (int miniConfigIndex = 0; miniConfigIndex < miniConfigs.size(); ++miniConfigIndex) {
            NativeBuildConfigValueMini config = miniConfigs.get(miniConfigIndex);
            infoln("evaluate miniconfig");
            if (config.libraries.isEmpty()) {
                infoln("no libraries");
                continue;
            }

            List<NativeLibraryValueMini> librariesToBuild = findLibrariesToBuild(config);
            if (librariesToBuild.isEmpty()) {
                infoln("no libraries to build");
                continue;
            }

            if (!Strings.isNullOrEmpty(config.buildTargetsCommand)) {
                // Build all libraries together in one step, using the names of the artifacts.
                List<String> artifactNames =
                        librariesToBuild
                                .stream()
                                .filter(library -> library.artifactName != null)
                                .map(library -> library.artifactName)
                                .sorted()
                                .distinct()
                                .collect(Collectors.toList());
                String buildTargetsCommand =
                        substituteBuildTargetsCommand(config.buildTargetsCommand, artifactNames);
                buildSteps.add(
                        new BuildStep(
                                buildTargetsCommand,
                                librariesToBuild,
                                generator
                                        .get()
                                        .getNativeBuildConfigurationsJsons()
                                        .get(miniConfigIndex)
                                        .getParentFile()));
                infoln("about to build targets " + String.join(", ", artifactNames));
            } else {
                // Build each library separately using multiple steps.
                for (NativeLibraryValueMini libraryValue : librariesToBuild) {
                    assert libraryValue.buildCommand != null;
                    buildSteps.add(
                            new BuildStep(
                                    libraryValue.buildCommand,
                                    libraryValue,
                                    generator
                                            .get()
                                            .getNativeBuildConfigurationsJsons()
                                            .get(miniConfigIndex)
                                            .getParentFile()));
                    infoln("about to build %s", libraryValue.buildCommand);
                }
            }
        }

        executeProcessBatch(buildSteps);

        infoln("check expected build outputs");
        for (NativeBuildConfigValueMini config : miniConfigs) {
            for (String library : config.libraries.keySet()) {
                NativeLibraryValueMini libraryValue = config.libraries.get(library);
                checkNotNull(libraryValue);
                checkNotNull(libraryValue.output);
                checkState(!Strings.isNullOrEmpty(libraryValue.artifactName));
                if (!targets.isEmpty() && !targets.contains(libraryValue.artifactName)) {
                    continue;
                }
                if (buildSteps.stream().noneMatch(step -> step.libraries.contains(libraryValue))) {
                    // Only need to check existence of output files we expect to create
                    continue;
                }
                if (!libraryValue.output.exists()) {
                    throw new GradleException(
                            String.format(
                                    "Expected output file at %s for target %s"
                                            + " but there was none",
                                    libraryValue.output, libraryValue.artifactName));
                }
                if (libraryValue.abi == null) {
                    throw new GradleException("Expected NativeLibraryValue to have non-null abi");
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
                Abi abi = Abi.getByName(libraryValue.abi);
                if (abi == null) {
                    throw new RuntimeException(
                            String.format("Unknown ABI seen %s", libraryValue.abi));
                }
                File expectedOutputFile =
                        FileUtils.join(
                                generator.get().variant.getObjFolder(),
                                abi.getTag(),
                                libraryValue.output.getName());
                if (!FileUtils.isSameFile(libraryValue.output, expectedOutputFile)) {
                    infoln(
                            "external build set its own library output location for '%s', "
                                    + "copy to expected location",
                            libraryValue.output.getName());

                    if (expectedOutputFile.getParentFile().mkdirs()) {
                        infoln("created folder %s", expectedOutputFile.getParentFile());
                    }
                    infoln("copy file %s to %s", libraryValue.output, expectedOutputFile);
                    Files.copy(libraryValue.output, expectedOutputFile);
                }
            }
        }

        if (!getStlSharedObjectFiles().isEmpty()) {
            infoln("copy STL shared object files");
            for (Abi abi : getStlSharedObjectFiles().keySet()) {
                File stlSharedObjectFile = checkNotNull(getStlSharedObjectFiles().get(abi));
                File objAbi =
                        FileUtils.join(
                                generator.get().variant.getObjFolder(),
                                abi.getTag(),
                                stlSharedObjectFile.getName());
                if (!objAbi.getParentFile().isDirectory()) {
                    // A build failure can leave the obj/abi folder missing. Just note that case
                    // and continue without copying STL.
                    infoln(
                            "didn't copy STL file to %s because that folder wasn't created "
                                    + "by the build ",
                            objAbi.getParentFile());
                } else {
                    infoln("copy file %s to %s", stlSharedObjectFile, objAbi);
                    Files.copy(stlSharedObjectFile, objAbi);
                }
            }
        }

        infoln("build complete");
    }

    /**
     * @param buildTargetsCommand The build command that can build multiple targets in parallel.
     * @param artifactNames The names of artifacts the build command will build in parallel.
     * @return Replaces the placeholder in the input command with the given artifacts and returns a
     *     command that can be executed directly.
     */
    private static String substituteBuildTargetsCommand(
            @NonNull String buildTargetsCommand, @NonNull List<String> artifactNames) {
        return buildTargetsCommand.replace(
                BUILD_TARGETS_PLACEHOLDER, String.join(" ", artifactNames));
    }

    /**
     * Verifies that all targets provided by the user will be built. Throws GradleException if it
     * detects an unexpected target.
     */
    private void verifyTargetsExist(@NonNull List<NativeBuildConfigValueMini> miniConfigs) {
        // Check the resulting JSON targets against the targets specified in ndkBuild.targets or
        // cmake.targets. If a target name specified by the user isn't present then provide an
        // error to the user that lists the valid target names.
        Set<String> targets = generator.get().variant.getBuildTargetSet();
        infoln("executing build commands for targets: '%s'", Joiner.on(", ").join(targets));

        // Search libraries for matching targets.
        Set<String> matchingTargets = Sets.newHashSet();
        Set<String> unmatchedTargets = Sets.newHashSet();
        for (NativeBuildConfigValueMini config : miniConfigs) {
            for (NativeLibraryValueMini libraryValue : config.libraries.values()) {
                if (targets.contains(libraryValue.artifactName)) {
                    matchingTargets.add(libraryValue.artifactName);
                } else {
                    unmatchedTargets.add(libraryValue.artifactName);
                }
            }
        }

        // All targets must be found or it's a build error
        for (String target : targets) {
            if (!matchingTargets.contains(target)) {
                // TODO(emrekultursay): Convert this into a warning.
                throw new GradleException(
                        String.format(
                                "Unexpected native build target %s. Valid values are: %s",
                                target, Joiner.on(", ").join(unmatchedTargets)));
            }
        }
    }

    /**
     * @return List of libraries defined in the input config file, filtered based on the targets
     *     field optionally provided by the user, and other criteria.
     */
    @NonNull
    private List<NativeLibraryValueMini> findLibrariesToBuild(
            @NonNull NativeBuildConfigValueMini config) {
        List<NativeLibraryValueMini> librariesToBuild = Lists.newArrayList();
        Set<String> targets = generator.get().variant.getBuildTargetSet();
        for (NativeLibraryValueMini libraryValue : config.libraries.values()) {
            infoln("evaluate library %s (%s)", libraryValue.artifactName, libraryValue.abi);
            if (!targets.isEmpty() && !targets.contains(libraryValue.artifactName)) {
                infoln(
                        "not building target %s because it isn't in targets set",
                        libraryValue.artifactName);
                continue;
            }

            if (Strings.isNullOrEmpty(config.buildTargetsCommand)
                    && Strings.isNullOrEmpty(libraryValue.buildCommand)) {
                // This can happen when there's an externally referenced library.
                infoln(
                        "not building target %s because there was no buildCommand for the target, "
                                + "nor a buildTargetsCommand for the config",
                        libraryValue.artifactName);
                continue;
            }

            if (targets.isEmpty()) {
                if (libraryValue.output == null) {
                    infoln(
                            "not building target %s because no targets are specified and "
                                    + "library build output file is null",
                            libraryValue.artifactName);
                    continue;
                }

                String extension = Files.getFileExtension(libraryValue.output.getName());
                switch (extension) {
                    case "so":
                        infoln(
                                "building target library %s because no targets are " + "specified.",
                                libraryValue.artifactName);
                        break;
                    case "":
                        infoln(
                                "building target executable %s because no targets are "
                                        + "specified.",
                                libraryValue.artifactName);
                        break;
                    default:
                        infoln(
                                "not building target %s because the type cannot be "
                                        + "determined.",
                                libraryValue.artifactName);
                        continue;
                }
            }

            librariesToBuild.add(libraryValue);
        }

        return librariesToBuild;
    }

    /**
     * Get native build config minis. Also gather stats if they haven't already been gathered for
     * this variant
     *
     * @return the mini configs
     */
    private List<NativeBuildConfigValueMini> getNativeBuildConfigValueMinis() throws IOException {
        // Gather stats only if they haven't been gathered during model build
        if (getStats().getNativeBuildConfigCount() == 0) {
            return AndroidBuildGradleJsons.getNativeBuildMiniConfigs(
                    generator.get().getNativeBuildConfigurationsJsons(), getStats());
        }
        return AndroidBuildGradleJsons.getNativeBuildMiniConfigs(
                generator.get().getNativeBuildConfigurationsJsons(), null);
    }

    /**
     * Given a list of build steps, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    private void executeProcessBatch(@NonNull List<BuildStep> buildSteps)
            throws BuildCommandException, IOException {
        final Logger logger = getLogger();
        final GradleProcessExecutor processExecutor = new GradleProcessExecutor(getProject());

        for (BuildStep buildStep : buildSteps) {
            List<String> tokens = StringHelper.tokenizeCommandLineToEscaped(buildStep.buildCommand);
            ProcessInfoBuilder processBuilder = new ProcessInfoBuilder();
            processBuilder.setExecutable(tokens.get(0));
            for (int i = 1; i < tokens.size(); ++i) {
                processBuilder.addArgs(tokens.get(i));
            }
            infoln("%s", processBuilder);

            String logFileSuffix;
            String abiName = buildStep.libraries.get(0).abi;
            if (buildStep.libraries.size() > 1) {
                logFileSuffix = "targets";
                List<String> targetNames =
                        buildStep
                                .libraries
                                .stream()
                                .map(library -> library.artifactName + "_" + library.abi)
                                .collect(Collectors.toList());
                logger.lifecycle(
                        String.format("Build multiple targets %s", String.join(" ", targetNames)));
            } else {
                checkElementIndex(0, buildStep.libraries.size());
                logFileSuffix = buildStep.libraries.get(0).artifactName + "_" + abiName;
                getLogger().lifecycle(String.format("Build %s", logFileSuffix));
            }

            if (generator.get().getNativeBuildSystem() == NativeBuildSystem.CMAKE) {
                Optional<CxxAbiModel> cxxAbiModelOptional =
                        generator
                                .get()
                                .abis
                                .stream()
                                .filter(abiModel -> abiModel.getAbi().getTag().equals(abiName))
                                .findFirst();
                if (cxxAbiModelOptional.isPresent()) {
                    this.getVariantName();
                    UtilsKt.appendTimestampAndBuildIdToNinjaLog(cxxAbiModelOptional.get());
                    CxxBuildModel buildModel = getCxxBuildModel();
                    BuildSessionImpl.getSingleton()
                            .executeOnceWhenBuildFinished(
                                    CxxAbiModel.class.getName(),
                                    "CollectNinjaLogs",
                                    () -> collectNinjaLogs(buildModel));
                } else {
                    warnln(
                            "Cannot locate ABI {} for generating build attribution metrics.",
                            abiName);
                }
            }

            createProcessOutputJunction(
                            buildStep.outputFolder,
                            "android_gradle_build_" + logFileSuffix,
                            processBuilder,
                            logger,
                            processExecutor,
                            "")
                    .logStderrToInfo()
                    .logStdoutToInfo()
                    .execute();
        }
    }

    @NonNull
    private Map<Abi, File> getStlSharedObjectFiles() {
        return generator.get().getStlSharedObjectFiles();
    }

    @NonNull
    private GradleBuildVariant.Builder getStats() {
        return generator.get().stats;
    }

    public static class CreationAction extends VariantTaskCreationAction<ExternalNativeBuildTask> {
        @NonNull private final Provider<ExternalNativeJsonGenerator> generator;
        @NonNull private final TaskProvider<? extends Task> generateTask;

        public CreationAction(
                @NonNull Provider<ExternalNativeJsonGenerator> generator,
                @NonNull TaskProvider<? extends Task> generateTask,
                @NonNull VariantScope scope) {
            super(scope);
            this.generator = generator;
            this.generateTask = generateTask;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("externalNativeBuild");
        }

        @NonNull
        @Override
        public Class<ExternalNativeBuildTask> getType() {
            return ExternalNativeBuildTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends ExternalNativeBuildTask> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().getExternalNativeBuildTasks().add(taskProvider);
            getVariantScope().getTaskContainer().setExternalNativeBuildTask(taskProvider);
        }

        @Override
        public void configure(@NonNull ExternalNativeBuildTask task) {
            super.configure(task);

            VariantScope scope = getVariantScope();

            task.dependsOn(
                    generateTask, scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, JNI));

            task.generator = generator;
            task.evalIssueReporter = getVariantScope().getGlobalScope().getErrorHandler();
        }
    }
}
