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

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.PLATFORM_WINDOWS;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.errorln;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.infoln;
import static com.android.build.gradle.internal.cxx.logging.PassThroughRecordingLoggingEnvironmentKt.toJsonString;
import static com.android.build.gradle.internal.cxx.model.CreateCxxAbiModelKt.createCxxAbiModel;
import static com.android.build.gradle.internal.cxx.model.CreateCxxVariantModelKt.createCxxVariantModel;
import static com.android.build.gradle.internal.cxx.model.JsonUtilKt.writeJsonToFile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationInvalidationState;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini;
import com.android.build.gradle.internal.cxx.logging.GradleSyncLoggingEnvironment;
import com.android.build.gradle.internal.cxx.logging.PassThroughRecordingLoggingEnvironment;
import com.android.build.gradle.internal.cxx.model.CxxAbiModel;
import com.android.build.gradle.internal.cxx.model.CxxCmakeModuleModel;
import com.android.build.gradle.internal.cxx.model.CxxModuleModel;
import com.android.build.gradle.internal.cxx.model.CxxVariantModel;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import com.google.wireless.android.sdk.stats.GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;

/**
 * Base class for generation of native JSON.
 */
public abstract class ExternalNativeJsonGenerator {
    @NonNull protected final CxxVariantModel variant;
    @NonNull protected final List<CxxAbiModel> abis;
    @NonNull protected final Set<String> configurationFailures;
    @NonNull protected final AndroidBuilder androidBuilder;
    @NonNull protected final GradleBuildVariant.Builder stats;

    ExternalNativeJsonGenerator(
            @NonNull CxxVariantModel variant,
            @NonNull List<CxxAbiModel> abis,
            @NonNull Set<String> configurationFailures,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull GradleBuildVariant.Builder stats) {
        this.variant = variant;
        this.abis = abis;
        this.configurationFailures = configurationFailures;
        this.androidBuilder = androidBuilder;
        this.stats = stats;

        // Check some basic configuration information at sync time.
        if (!getNdkFolder().isDirectory()) {
            errorln(
                    "NDK not configured (%s).\n"
                            + "Download the NDK from http://developer.android.com/tools/sdk/ndk/."
                            + "Then add ndk.dir=path/to/ndk in local.properties.\n"
                            + "(On Windows, make sure you escape backslashes, "
                            + "e.g. C:\\\\ndk rather than C:\\ndk)",
                    getNdkFolder());
        }
    }

    /**
     * Returns true if platform is windows
     */
    protected static boolean isWindows() {
        return (CURRENT_PLATFORM == PLATFORM_WINDOWS);
    }


    @NonNull
    private List<File> getDependentBuildFiles(@NonNull File json) throws IOException {
        List<File> result = Lists.newArrayList();
        if (!json.exists()) {
            return result;
        }

        // Now check whether the JSON is out-of-date with respect to the build files it declares.
        NativeBuildConfigValueMini config =
                AndroidBuildGradleJsons.getNativeBuildMiniConfig(json, stats);
        return config.buildFiles;
    }

    public void build() throws IOException, ProcessException {
        buildAndPropagateException(false);
    }

    public void build(boolean forceJsonGeneration) {
        try {
            infoln("building json with force flag %s", forceJsonGeneration);
            buildAndPropagateException(forceJsonGeneration);
        } catch (@NonNull IOException | GradleException e) {
            errorln("exception while building Json $%s", e.getMessage());
        } catch (ProcessException e) {
            errorln(
                    "executing external native build for %s %s",
                    getNativeBuildSystem().getTag(), variant.getModule().getMakeFile());
        }
    }

    public List<Callable<Void>> parallelBuild(boolean forceJsonGeneration) {
        List<Callable<Void>> buildSteps = new ArrayList<>(abis.size());
        for (CxxAbiModel abi : abis) {
            buildSteps.add(
                    () -> buildForOneConfigurationConvertExceptions(forceJsonGeneration, abi));
        }
        return buildSteps;
    }

    @Nullable
    private Void buildForOneConfigurationConvertExceptions(
            boolean forceJsonGeneration, CxxAbiModel abi) {
        try (GradleSyncLoggingEnvironment ignore =
                new GradleSyncLoggingEnvironment(
                        getVariantName(),
                        abi.getAbi().getTag(),
                        configurationFailures,
                        androidBuilder.getIssueReporter(),
                        androidBuilder.getLogger())) {
            try {
                buildForOneConfiguration(forceJsonGeneration, abi);
            } catch (@NonNull IOException | GradleException e) {
                errorln("exception while building Json %s", e.getMessage());
            } catch (ProcessException e) {
                errorln(
                        "executing external native build for %s %s",
                        getNativeBuildSystem().getTag(), variant.getModule().getMakeFile());
            }
            return null;
        }
    }

    @NonNull
    private static String getPreviousBuildCommand(@NonNull File commandFile) throws IOException {
        if (!commandFile.exists()) {
            return "";
        }
        return new String(Files.readAllBytes(commandFile.toPath()), Charsets.UTF_8);
    }

    private void buildAndPropagateException(boolean forceJsonGeneration)
            throws IOException, ProcessException {
        Exception firstException = null;
        for (CxxAbiModel abi : abis) {
            try {
                buildForOneConfiguration(forceJsonGeneration, abi);
            } catch (@NonNull GradleException | IOException | ProcessException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }

        if (firstException != null) {
            if (firstException instanceof GradleException) {
                throw (GradleException) firstException;
            }

            if (firstException instanceof IOException) {
                throw (IOException) firstException;
            }

            throw (ProcessException) firstException;
        }
    }

    public void buildForOneAbiName(boolean forceJsonGeneration, String abiName) {
        int built = 0;
        for (CxxAbiModel abi : abis) {
            if (!abi.getAbi().getTag().equals(abiName)) {
                continue;
            }
            built++;
            buildForOneConfigurationConvertExceptions(forceJsonGeneration, abi);
        }
        assert (built == 1);
    }

    private void checkForConfigurationErrors() {
        if (!configurationFailures.isEmpty()) {
            throw new GradleException(Joiner.on("\r\n").join(configurationFailures));
        }
    }

    private void buildForOneConfiguration(boolean forceJsonGeneration, CxxAbiModel abi)
            throws GradleException, IOException, ProcessException {
        try (PassThroughRecordingLoggingEnvironment recorder =
                new PassThroughRecordingLoggingEnvironment()) {
            checkForConfigurationErrors();

            GradleBuildVariant.NativeBuildConfigInfo.Builder variantStats =
                    GradleBuildVariant.NativeBuildConfigInfo.newBuilder();
            variantStats.setAbi(AnalyticsUtil.getAbi(abi.getAbi().getTag()));
            variantStats.setDebuggable(variant.isDebuggableEnabled());
            long startTime = System.currentTimeMillis();
            variantStats.setGenerationStartMs(startTime);
            try {
                infoln(
                        "Start JSON generation. Platform version: %s min SDK version: %s",
                        abi.getAbiPlatformVersion(),
                        abi.getAbi().getTag(),
                        abi.getAbiPlatformVersion());
                ProcessInfoBuilder processBuilder = getProcessBuilder(abi);

                // See whether the current build command matches a previously written build command.
                String currentBuildCommand = processBuilder.toString();

                JsonGenerationInvalidationState invalidationState =
                        new JsonGenerationInvalidationState(
                                forceJsonGeneration,
                                abi.getJsonFile(),
                                abi.getBuildCommandFile(),
                                currentBuildCommand,
                                getPreviousBuildCommand(abi.getBuildCommandFile()),
                                getDependentBuildFiles(abi.getJsonFile()));

                if (invalidationState.getRebuild()) {
                    infoln("rebuilding JSON %s due to:", abi.getJsonFile());
                    for (String reason : invalidationState.getRebuildReasons()) {
                        infoln(reason);
                    }

                    // Related to https://issuetracker.google.com/69408798
                    // Something has changed so we need to clean up some build intermediates and
                    // outputs.
                    // - If only a build file has changed then we try to keep .o files and,
                    // in the case of CMake, the generated Ninja project. In this case we must
                    // remove .so files because they are automatically packaged in the APK on a
                    // *.so basis.
                    // - If there is some other cause to recreate the JSon, such as command-line
                    // changed then wipe out the whole JSon folder.
                    if (variant.getJsonFolder().exists()) {
                        if (invalidationState.getSoftRegeneration()) {
                            infoln(
                                    "keeping json folder '%s' but regenerating project",
                                    abi.getCxxBuildFolder());

                        } else {
                            infoln("removing stale contents from '%s'", abi.getCxxBuildFolder());
                            FileUtils.deletePath(abi.getCxxBuildFolder());
                        }
                    }

                    if (abi.getCxxBuildFolder().mkdirs()) {
                        infoln("created folder '%s'", abi.getCxxBuildFolder());
                    }

                    infoln("executing %s %s", getNativeBuildSystem().getTag(), processBuilder);
                    String buildOutput = executeProcess(abi);
                    infoln("done executing %s", getNativeBuildSystem().getTag());

                    // Write the captured process output to a file for diagnostic purposes.
                    infoln("write build output %s", abi.getBuildOutputFile().getAbsolutePath());
                    Files.write(
                            abi.getBuildOutputFile().toPath(),
                            buildOutput.getBytes(Charsets.UTF_8));
                    processBuildOutput(buildOutput, abi);

                    if (!abi.getJsonFile().exists()) {
                        throw new GradleException(
                                String.format(
                                        "Expected json generation to create '%s' but it didn't",
                                        abi.getJsonFile()));
                    }

                    synchronized (stats) {
                        // Related to https://issuetracker.google.com/69408798
                        // Targets may have been removed or there could be other orphaned extra .so
                        // files. Remove these and rely on the build step to replace them if they are
                        // legitimate. This is to prevent unexpected .so files from being packaged in
                        // the APK.
                        removeUnexpectedSoFiles(
                                abi.getObjFolder(),
                                AndroidBuildGradleJsons.getNativeBuildMiniConfig(
                                        abi.getJsonFile(), stats));
                    }

                    // Write the ProcessInfo to a file, this has all the flags used to generate the
                    // JSON. If any of these change later the JSON will be regenerated.
                    infoln("write command file %s", abi.getBuildCommandFile().getAbsolutePath());
                    Files.write(
                            abi.getBuildCommandFile().toPath(),
                            currentBuildCommand.getBytes(Charsets.UTF_8));

                    // Record the outcome. JSON was built.
                    variantStats.setOutcome(GenerationOutcome.SUCCESS_BUILT);
                } else {
                    infoln("JSON '%s' was up-to-date", abi.getJsonFile());
                    variantStats.setOutcome(GenerationOutcome.SUCCESS_UP_TO_DATE);
                }
                infoln("JSON generation completed without problems");
            } catch (@NonNull GradleException | IOException | ProcessException e) {
                variantStats.setOutcome(GenerationOutcome.FAILED);
                infoln("JSON generation completed with problem. Exception: " + e.toString());
                throw e;
            } finally {
                variantStats.setGenerationDurationMs(System.currentTimeMillis() - startTime);
                synchronized (stats) {
                    stats.addNativeBuildConfig(variantStats);
                }
                abi.getJsonGenerationLoggingRecordFile().getParentFile().mkdirs();
                Files.write(
                        abi.getJsonGenerationLoggingRecordFile().toPath(),
                        toJsonString(recorder.getRecord()).getBytes(Charsets.UTF_8));
                writeJsonToFile(abi);
            }
        }
    }

    /**
     * This function removes unexpected so files from disk. Unexpected means they exist on disk but
     * are not present as a build output from the json.
     *
     * <p>It is generally valid for there to be extra .so files because the build system may copy
     * libraries to the output folder. This function is meant to be used in cases where we suspect
     * the .so may have been orphaned by the build system due to a change in build files.
     *
     * @param expectedOutputFolder the expected location of output .so files
     * @param config the existing miniconfig
     * @throws IOException in the case that json is missing or can't be read or some other IO
     *     problem.
     */
    private static void removeUnexpectedSoFiles(
            @NonNull File expectedOutputFolder, @NonNull NativeBuildConfigValueMini config)
            throws IOException {
        if (!expectedOutputFolder.isDirectory()) {
            // Nothing to clean
            return;
        }

        // Gather all expected build outputs
        List<Path> expectedSoFiles = Lists.newArrayList();
        for (NativeLibraryValueMini library : config.libraries.values()) {
            assert library.output != null;
            expectedSoFiles.add(library.output.toPath());
        }

        try (Stream<Path> paths = Files.walk(expectedOutputFolder.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".so"))
                    .filter(path -> !expectedSoFiles.contains(path))
                    .forEach(
                            path -> {
                                if (path.toFile().delete()) {
                                    infoln(
                                            "deleted unexpected build output %s in incremental "
                                                    + "regenerate",
                                            path);
                                }
                            });
        }
    }

    /**
     * Derived class implements this method to post-process build output. NdkPlatform-build uses
     * this to capture and analyze the compile and link commands that were written to stdout.
     */
    abstract void processBuildOutput(@NonNull String buildOutput, @NonNull CxxAbiModel abiConfig)
            throws IOException;

    @NonNull
    abstract ProcessInfoBuilder getProcessBuilder(@NonNull CxxAbiModel abi);

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    abstract String executeProcess(@NonNull CxxAbiModel abi) throws ProcessException, IOException;

    /**
     * @return the native build system that is used to generate the JSON.
     */
    @NonNull
    public abstract NativeBuildSystem getNativeBuildSystem();

    /**
     * @return a map of Abi to STL shared object (.so files) that should be copied.
     */
    @NonNull
    abstract Map<Abi, File> getStlSharedObjectFiles();

    /** @return the variant name for this generator */
    @NonNull
    public String getVariantName() {
        return variant.getVariantName();
    }

    @NonNull
    public static ExternalNativeJsonGenerator create(
            @NonNull CxxModuleModel module,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull VariantScope scope) {
        // TODO: Shouldn't send mutable state like configurationFailures through construction
        Set<String> configurationFailures = new HashSet<>();
        try (GradleSyncLoggingEnvironment ignore =
                new GradleSyncLoggingEnvironment(
                        scope.getFullVariantName(),
                        "native",
                        configurationFailures,
                        androidBuilder.getIssueReporter(),
                        androidBuilder.getLogger())) {
            return createImpl(configurationFailures, module, androidBuilder, scope);
        }
    }

    @NonNull
    public static ExternalNativeJsonGenerator createImpl(
            @NonNull Set<String> configurationFailures,
            @NonNull CxxModuleModel module,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull VariantScope scope) {
        CxxVariantModel variant = createCxxVariantModel(module, scope.getVariantData());
        List<CxxAbiModel> abis = Lists.newArrayList();
        for (Abi abi : variant.getValidAbiList()) {
            abis.add(
                    createCxxAbiModel(
                            variant, abi, scope.getGlobalScope(), scope.getVariantData()));
        }

        GradleBuildVariant.Builder stats =
                ProcessProfileWriter.getOrCreateVariant(
                        module.getGradleModulePathName(), scope.getFullVariantName());


        switch (module.getBuildSystem()) {
            case NDK_BUILD:
                return new NdkBuildExternalNativeJsonGenerator(
                        variant, abis, configurationFailures, androidBuilder, stats);
            case CMAKE:
                CxxCmakeModuleModel cmake = Objects.requireNonNull(variant.getModule().getCmake());

                // parent of 'bin'
                Revision cmakeRevision = cmake.getFoundCmakeVersion();

                stats.setNativeCmakeVersion(cmakeRevision.toShortString());

                // Custom Cmake shipped with Android studio has a fixed version, we'll just use that exact
                // version to check.
                if (cmakeRevision.equals(
                        Revision.parseRevision(
                                ExternalNativeBuildTaskUtils.CUSTOM_FORK_CMAKE_VERSION,
                                Revision.Precision.MICRO))) {
                    return new CmakeAndroidNinjaExternalNativeJsonGenerator(
                            variant, abis, configurationFailures, androidBuilder, stats);
                }

                if (cmakeRevision.getMajor() < 3
                        || (cmakeRevision.getMajor() == 3 && cmakeRevision.getMinor() <= 6)) {
                    throw new RuntimeException(
                            "Unexpected/unsupported CMake version "
                                    + cmakeRevision.toString()
                                    + ". Try 3.7.0 or later.");
                }

                return new CmakeServerExternalNativeJsonGenerator(
                        variant, abis, configurationFailures, androidBuilder, stats);
            default:
                throw new IllegalArgumentException("Unknown ExternalNativeJsonGenerator type");
        }
    }


    public void forEachNativeBuildConfiguration(@NonNull Consumer<JsonReader> callback)
            throws IOException {
        try (GradleSyncLoggingEnvironment ignore =
                new GradleSyncLoggingEnvironment(
                        getVariantName(),
                        "native",
                        configurationFailures,
                        androidBuilder.getIssueReporter(),
                        androidBuilder.getLogger())) {
            List<File> files = getNativeBuildConfigurationsJsons();
            infoln("streaming %s JSON files", files.size());
            for (File file : getNativeBuildConfigurationsJsons()) {
                if (file.exists()) {
                    infoln("string JSON file %s", file.getAbsolutePath());
                    try (JsonReader reader = new JsonReader(new FileReader(file))) {
                        callback.accept(reader);
                    } catch (Throwable e) {
                        infoln(
                                "Error parsing: %s",
                                String.join("\r\n", Files.readAllLines(file.toPath())));
                        throw e;
                    }
                } else {
                    // If the tool didn't create the JSON file then create fallback with the
                    // information we have so the user can see partial information in the UI.
                    infoln("streaming fallback JSON for %s", file.getAbsolutePath());
                    NativeBuildConfigValueMini fallback = new NativeBuildConfigValueMini();
                    fallback.buildFiles = Lists.newArrayList(variant.getModule().getMakeFile());
                    try (JsonReader reader =
                            new JsonReader(new StringReader(new Gson().toJson(fallback)))) {
                        callback.accept(reader);
                    }
                }
            }
        }
    }

    @NonNull
    public CxxVariantModel getVariant() {
        return this.variant;
    }

    @NonNull
    @InputFile
    public File getMakefile() {
        return variant.getModule().getMakeFile();
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getObjFolder() {
        return variant.getObjFolder();
    }

    @NonNull
    // This should not be annotated with @OutputDirectory because getNativeBuildConfigurationsJsons
    // is already annotated with @OutputFiles
    public File getJsonFolder() {
        return variant.getJsonFolder();
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getNdkFolder() {
        return variant.getModule().getNdkFolder();
    }

    @Input
    public boolean isDebuggable() {
        return variant.isDebuggableEnabled();
    }

    @NonNull
    @Optional
    @Input
    public List<String> getBuildArguments() {
        return variant.getBuildSystemArgumentList();
    }

    @NonNull
    @Optional
    @Input
    public List<String> getcFlags() {
        return variant.getCFlagList();
    }

    @NonNull
    @Optional
    @Input
    public List<String> getCppFlags() {
        return variant.getCppFlagsList();
    }

    @NonNull
    @OutputFiles
    public List<File> getNativeBuildConfigurationsJsons() {
        List<File> generatedJsonFiles = new ArrayList<>();
        for (CxxAbiModel abi : abis) {
            generatedJsonFiles.add(abi.getJsonFile());
        }
        return generatedJsonFiles;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getSoFolder() {
        return variant.getSoFolder();
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getSdkFolder() {
        return variant.getModule().getSdkFolder();
    }

    @Input
    @NonNull
    public Collection<Abi> getAbis() {
        List<Abi> result = Lists.newArrayList();
        for (CxxAbiModel abi : abis) {
            result.add(abi.getAbi());
        }
        return result;
    }

}
