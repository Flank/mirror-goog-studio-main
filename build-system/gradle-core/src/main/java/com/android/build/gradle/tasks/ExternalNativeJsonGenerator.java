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
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.cxx.configure.AbiConfigurator;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationAbiConfiguration;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationInvalidationState;
import com.android.build.gradle.internal.cxx.configure.NativeBuildSystemVariantConfig;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.model.ApiVersion;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;

/**
 * Base class for generation of native JSON.
 */
public abstract class ExternalNativeJsonGenerator {
    @NonNull protected final String variantName;
    @NonNull protected final AndroidBuilder androidBuilder;
    @NonNull protected final GradleBuildVariant.Builder stats;
    @NonNull private final NdkHandler ndkHandler;
    @NonNull private final File makefile;
    @NonNull private final File sdkFolder;
    @NonNull private final File ndkFolder;
    @NonNull private final File soFolder;
    @NonNull private final File objFolder;
    @NonNull private final File jsonFolder;
    private final boolean debuggable;
    @NonNull private final List<String> buildArguments;
    @NonNull private final List<String> cFlags;
    @NonNull private final List<String> cppFlags;
    @NonNull private final List<File> nativeBuildConfigurationsJsons;
    @NonNull private final List<JsonGenerationAbiConfiguration> abiConfigurations;

    ExternalNativeJsonGenerator(
            @NonNull NdkHandler ndkHandler,
            @NonNull String variantName,
            @NonNull List<JsonGenerationAbiConfiguration> abiConfigurations,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File sdkFolder,
            @NonNull File ndkFolder,
            @NonNull File soFolder,
            @NonNull File objFolder,
            @NonNull File jsonFolder,
            @NonNull File makefile,
            boolean debuggable,
            @Nullable List<String> buildArguments,
            @Nullable List<String> cFlags,
            @Nullable List<String> cppFlags,
            @NonNull List<File> nativeBuildConfigurationsJsons,
            @NonNull GradleBuildVariant.Builder stats) {
        this.ndkHandler = ndkHandler;
        this.variantName = variantName;
        this.androidBuilder = androidBuilder;
        this.sdkFolder = sdkFolder;
        this.ndkFolder = ndkFolder;
        this.soFolder = soFolder;
        this.objFolder = objFolder;
        this.jsonFolder = jsonFolder;
        this.makefile = makefile;
        this.debuggable = debuggable;
        this.buildArguments = buildArguments == null ? Lists.newArrayList() : buildArguments;
        this.cFlags = cFlags == null ? Lists.newArrayList() : cFlags;
        this.cppFlags = cppFlags == null ? Lists.newArrayList() : cppFlags;
        this.nativeBuildConfigurationsJsons = nativeBuildConfigurationsJsons;
        this.stats = stats;
        this.abiConfigurations = abiConfigurations;
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
            diagnostic("building json with force flag %s", forceJsonGeneration);
            buildAndPropagateException(forceJsonGeneration);
        } catch (@NonNull IOException | GradleException e) {
            androidBuilder
                    .getIssueReporter()
                    .reportError(
                            Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                            new EvalIssueException(e, variantName));
        } catch (ProcessException e) {
            androidBuilder
                    .getIssueReporter()
                    .reportError(
                            Type.EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION,
                            new EvalIssueException(
                                    String.format(
                                            "executing external native build for %s %s",
                                            getNativeBuildSystem().getName(), makefile),
                                    e.getMessage()));
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
        diagnostic("starting JSON generation");
        Exception firstException = null;
        for (JsonGenerationAbiConfiguration configuration : abiConfigurations) {
            GradleBuildVariant.NativeBuildConfigInfo.Builder variantStats =
                    GradleBuildVariant.NativeBuildConfigInfo.newBuilder();
            variantStats.setAbi(AnalyticsUtil.getAbi(configuration.getAbiPresentationName()));
            variantStats.setDebuggable(this.debuggable);
            long startTime = System.currentTimeMillis();
            variantStats.setGenerationStartMs(startTime);
            try {
                diagnostic(
                        "using platform version %s for ABI %s and min SDK version %s",
                        configuration.getAbiPlatformVersion(),
                        configuration.getAbiPresentationName(),
                        configuration.getAbiPlatformVersion());

                ProcessInfoBuilder processBuilder =
                        getProcessBuilder(
                                configuration.getAbiPresentationName(),
                                configuration.getAbiPlatformVersion(),
                                configuration.getExpectedJson());


                // See whether the current build command matches a previously written build command.
                String currentBuildCommand = processBuilder.toString();

                JsonGenerationInvalidationState invalidationState =
                        new JsonGenerationInvalidationState(
                                forceJsonGeneration,
                                configuration.getExpectedJson(),
                                configuration.getCommandFile(),
                                currentBuildCommand,
                                getPreviousBuildCommand(configuration.getCommandFile()),
                                getDependentBuildFiles(configuration.getExpectedJson()));

                if (invalidationState.getRebuild()) {
                    diagnostic("rebuilding JSON %s due to:", configuration.getExpectedJson());
                    for (String reason : invalidationState.getRebuildReasons()) {
                        diagnostic(reason);
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
                    if (jsonFolder.exists()) {
                        if (invalidationState.getSoftRegeneration()) {
                            diagnostic(
                                    "keeping json folder '%s' but regenerating project",
                                    configuration.getExpectedJson().getParentFile());

                        } else {
                            diagnostic(
                                    "removing stale contents from '%s'",
                                    configuration.getExpectedJson().getParentFile());
                            FileUtils.deletePath(configuration.getExpectedJson().getParentFile());
                        }
                    }

                    if (configuration.getExpectedJson().getParentFile().mkdirs()) {
                        diagnostic(
                                "created folder '%s'",
                                configuration.getExpectedJson().getParentFile());
                    }

                    diagnostic("executing %s %s", getNativeBuildSystem().getName(), processBuilder);
                    String buildOutput =
                            executeProcess(
                                    configuration.getAbiPresentationName(),
                                    configuration.getAbiPlatformVersion(),
                                    configuration.getExpectedJson());
                    diagnostic("done executing %s", getNativeBuildSystem().getName());

                    // Write the captured process output to a file for diagnostic purposes.
                    diagnostic(
                            "write build output %s",
                            configuration.getBuildOutputFile().getAbsolutePath());
                    Files.write(
                            configuration.getBuildOutputFile().toPath(),
                            buildOutput.getBytes(Charsets.UTF_8));
                    processBuildOutput(
                            buildOutput,
                            configuration.getAbiPresentationName(),
                            configuration.getAbiPlatformVersion());

                    if (!configuration.getExpectedJson().exists()) {
                        throw new GradleException(
                                String.format(
                                        "Expected json generation to create '%s' but it didn't",
                                        configuration.getExpectedJson()));
                    }

                    // Related to https://issuetracker.google.com/69408798
                    // Targets may have been removed or there could be other orphaned extra .so
                    // files. Remove these and rely on the build step to replace them if they are
                    // legitimate. This is to prevent unexpected .so files from being packaged in
                    // the APK.
                    removeUnexpectedSoFiles(
                            configuration.getExpectedOutputFolder(),
                            AndroidBuildGradleJsons.getNativeBuildMiniConfig(
                                    configuration.getExpectedJson(), stats));

                    // Write the ProcessInfo to a file, this has all the flags used to generate the
                    // JSON. If any of these change later the JSON will be regenerated.
                    diagnostic(
                            "write command file %s",
                            configuration.getCommandFile().getAbsolutePath());
                    Files.write(
                            configuration.getCommandFile().toPath(),
                            currentBuildCommand.getBytes(Charsets.UTF_8));

                    // Record the outcome. JSON was built.
                    variantStats.setOutcome(
                            GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome
                                    .SUCCESS_BUILT);
                } else {
                    diagnostic("JSON '%s' was up-to-date", configuration.getExpectedJson());
                    variantStats.setOutcome(
                            GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome
                                    .SUCCESS_UP_TO_DATE);
                }
            } catch (@NonNull GradleException | IOException | ProcessException e) {
                variantStats.setOutcome(
                        GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome.FAILED);
                // If one ABI fails to build that doesn't mean others will. Continue processing
                // all ABIs so that we can get some JSON so the user can still edit the project
                // in Android Studio.
                if (firstException == null) {
                    firstException = e;
                }
            } finally {
                variantStats.setGenerationDurationMs(System.currentTimeMillis() - startTime);
                stats.addNativeBuildConfig(variantStats.build());
            }
        }

        if (firstException == null) {
            diagnostic("JSON generation completed without problems");
            return;
        }

        diagnostic("JSON generation completed with problems");
        if (firstException instanceof GradleException) {
            throw (GradleException) firstException;
        }

        if (firstException instanceof IOException) {
            throw (IOException) firstException;
        }

        throw (ProcessException) firstException;
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
    private void removeUnexpectedSoFiles(
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
                                    diagnostic(
                                            "deleted unexpected build output %s in incremental "
                                                    + "regenerate",
                                            path);
                                }
                            });
        }
    }

    /**
     * Derived class implements this method to post-process build output. Ndk-build uses this to
     * capture and analyze the compile and link commands that were written to stdout.
     */
    abstract void processBuildOutput(
            @NonNull String buildOutput, @NonNull String abi, int abiPlatformVersion)
            throws IOException;

    @NonNull
    abstract ProcessInfoBuilder getProcessBuilder(
            @NonNull String abi, int abiPlatformVersion, @NonNull File outputJson);

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @param abi - ABI for which JSON generation process needs to be executed
     * @param abiPlatformVersion - ABIs platform version
     * @param outputJsonDir - directory where the JSON file and other information needs to be
     *     created
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    abstract String executeProcess(
            @NonNull String abi, int abiPlatformVersion, @NonNull File outputJsonDir)
            throws ProcessException, IOException;

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

    /**
     * Log low level diagnostic information.
     */
    void diagnostic(String format, Object... args) {
        androidBuilder.getLogger().info(
                "External native generate JSON " + variantName + ": " + format, args);
    }

    /**
     * Warning that is visible to the user
     */
    void warn(String format, Object... args) {
        androidBuilder.getLogger().warning(format, args);
    }

    /**
     * General configuration errors that apply to both CMake and ndk-build.
     */
    @NonNull
    List<String> getBaseConfigurationErrors() {
        List<String> messages = Lists.newArrayList();
        if (!getNdkFolder().isDirectory()) {
            messages.add(String.format(
                    "NDK not configured (%s).\n" +
                            "Download the NDK from http://developer.android.com/tools/sdk/ndk/." +
                            "Then add ndk.dir=path/to/ndk in local.properties.\n" +
                            "(On Windows, make sure you escape backslashes, "
                            + "e.g. C:\\\\ndk rather than C:\\ndk)", getNdkFolder()));
        }
        return messages;
    }

    /** @return the variant name for this generator */
    @NonNull
    public String getVariantName() {
        return variantName;
    }

    @NonNull
    public static ExternalNativeJsonGenerator create(
            @NonNull String projectPath,
            @NonNull File projectDir,
            @NonNull File buildDir,
            @Nullable File externalNativeBuildDir,
            @NonNull NativeBuildSystem buildSystem,
            @NonNull File makefile,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantScope scope) {
        checkNotNull(sdkHandler.getSdkFolder(), "No Android SDK folder found");
        File ndkFolder = sdkHandler.getNdkFolder();
        if (ndkFolder == null || !ndkFolder.isDirectory()) {
            throw new InvalidUserDataException(
                    String.format(
                            "NDK not configured. %s\n" + "Download it with SDK manager.",
                            ndkFolder == null ? "" : ndkFolder));
        }
        BaseVariantData variantData = scope.getVariantData();
        GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        GradleBuildVariant.Builder stats =
                ProcessProfileWriter.getOrCreateVariant(projectPath, scope.getFullVariantName());
        GlobalScope globalScope = scope.getGlobalScope();
        File intermediates =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        buildSystem.getName(),
                        variantData.getVariantConfiguration().getDirName());

        File soFolder = new File(intermediates, "lib");
        File externalNativeBuildFolder =
                findExternalNativeBuildFolder(
                        androidBuilder,
                        projectDir,
                        buildSystem,
                        variantData,
                        buildDir,
                        externalNativeBuildDir);
        File objFolder = new File(intermediates, "obj");

        // Get the highest platform version below compileSdkVersion
        NdkHandler ndkHandler = globalScope.getNdkHandler();

        ApiVersion minSdkVersion =
                variantData.getVariantConfiguration().getMergedFlavor().getMinSdkVersion();
        NativeBuildSystemVariantConfig nativeBuildVariantConfig =
                new NativeBuildSystemVariantConfig(
                        buildSystem, variantData.getVariantConfiguration());

        Splits splits = globalScope.getExtension().getSplits();

        if (globalScope.getExtension().getGeneratePureSplits()
                && splits.getAbi().isUniversalApk()) {
            androidBuilder
                    .getLogger()
                    .warning(
                            "ABI based configuration splits"
                                    + " and universal APK cannot be both set, universal APK will not be build.");
        }

        ProjectOptions projectOptions = globalScope.getProjectOptions();
        AbiConfigurator abiConfigurator =
                new AbiConfigurator(
                        globalScope.getErrorHandler(),
                        variantData.getName(),
                        ndkHandler.getSupportedAbis(),
                        ndkHandler.getDefaultAbis(),
                        nativeBuildVariantConfig.getExternalNativeBuildAbiFilters(),
                        nativeBuildVariantConfig.getNdkAbiFilters(),
                        splits.getAbiFilters(),
                        projectOptions.get(BooleanOption.BUILD_ONLY_TARGET_ABI),
                        projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI));

        // These are ABIs that are available on the current platform
        Collection<Abi> validAbis = abiConfigurator.getValidAbis();

        // Produce the list of expected JSON files. This list includes possibly invalid ABIs
        // so that generator can create fallback JSON for them.
        List<File> expectedJsons =
                ExternalNativeBuildTaskUtils.getOutputJsons(
                        externalNativeBuildFolder, abiConfigurator.getAllAbis());

        // Set up per-abi configuration constants
        if (buildSystem == NativeBuildSystem.NDK_BUILD) {
            // ndk-build create libraries in a "local" subfolder.
            objFolder = new File(objFolder, "local");
        }
        List<JsonGenerationAbiConfiguration> abiConfigurations = Lists.newArrayList();
        for (Abi abi : validAbis) {
            AndroidVersion version =
                    minSdkVersion == null
                            ? null
                            : new AndroidVersion(
                                    minSdkVersion.getApiLevel(), minSdkVersion.getCodename());

            abiConfigurations.add(
                    new JsonGenerationAbiConfiguration(
                            abi,
                            externalNativeBuildFolder,
                            objFolder,
                            buildSystem,
                            ndkHandler.findSuitablePlatformVersion(abi.getName(), version)));
        }

        switch (buildSystem) {
            case NDK_BUILD:
                {
                    CoreExternalNativeNdkBuildOptions options =
                            variantConfig
                                    .getExternalNativeBuildOptions()
                                    .getExternalNativeNdkBuildOptions();
                    checkNotNull(options, "NdkBuild options not found");
                    return new NdkBuildExternalNativeJsonGenerator(
                            ndkHandler,
                            variantData.getName(),
                            abiConfigurations,
                            androidBuilder,
                            projectDir,
                            sdkHandler.getSdkFolder(),
                            sdkHandler.getNdkFolder(),
                            soFolder,
                            objFolder,
                            externalNativeBuildFolder,
                            makefile,
                            variantConfig.getBuildType().isDebuggable(),
                            options.getArguments(),
                            options.getcFlags(),
                            options.getCppFlags(),
                            expectedJsons,
                            stats);
                }
            case CMAKE:
                return createCmakeExternalNativeJsonGenerator(
                        variantData,
                        ndkHandler,
                        sdkHandler,
                        abiConfigurations,
                        androidBuilder,
                        soFolder,
                        objFolder,
                        externalNativeBuildFolder,
                        makefile,
                        expectedJsons,
                        stats);
            default:
                throw new IllegalArgumentException("Unknown ExternalNativeJsonGenerator type");
        }
    }


    /**
     * @return creates an instance of CmakeExternalNativeJsonGenerator (server or android-ninja)
     *     based on the version of the cmake.
     */
    private static ExternalNativeJsonGenerator createCmakeExternalNativeJsonGenerator(
            @NonNull BaseVariantData variantData,
            @NonNull NdkHandler ndkHandler,
            @NonNull SdkHandler sdkHandler,
            @NonNull List<JsonGenerationAbiConfiguration> abis,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File soFolder,
            @NonNull File objFolder,
            @NonNull File externalNativeBuildFolder,
            @NonNull File makefile,
            List<File> expectedJsons,
            @NonNull GradleBuildVariant.Builder stats) {
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

        AndroidConfig extension = variantData.getScope().getGlobalScope().getExtension();
        CoreExternalNativeBuild externalNativeBuild = extension.getExternalNativeBuild();
        File cmakeFolder =
                ExternalNativeBuildTaskUtils.findCmakeExecutableFolder(
                        externalNativeBuild.getCmake().getVersion(), sdkHandler);

        CoreExternalNativeCmakeOptions options =
                variantConfig.getExternalNativeBuildOptions().getExternalNativeCmakeOptions();
        checkNotNull(options, "CMake options not found");

        Revision cmakeVersion;
        try {
            cmakeVersion = CmakeUtils.getVersion(new File(cmakeFolder, "bin"));
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to get the CMake version located at: "
                            + (new File(cmakeFolder, "bin")).getAbsolutePath());
        }
        return CmakeExternalNativeJsonGeneratorFactory.createCmakeStrategy(
                cmakeVersion,
                ndkHandler,
                variantData.getName(),
                abis,
                androidBuilder,
                sdkHandler.getSdkFolder(),
                sdkHandler.getNdkFolder(),
                soFolder,
                objFolder,
                externalNativeBuildFolder,
                makefile,
                cmakeFolder,
                variantConfig.getBuildType().isDebuggable(),
                options.getArguments(),
                options.getcFlags(),
                options.getCppFlags(),
                expectedJsons,
                stats);
    }

    private static File findExternalNativeBuildFolder(
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File projectDir,
            @NonNull NativeBuildSystem buildSystem,
            @NonNull BaseVariantData variantData,
            @NonNull File buildDir,
            @Nullable File externalNativeBuildDir) {
        File externalNativeBuildPath;
        if (externalNativeBuildDir == null) {
            return FileUtils.join(
                    projectDir,
                    ".externalNativeBuild",
                    buildSystem.getName(),
                    variantData.getName());
        }

        externalNativeBuildPath =
                FileUtils.join(
                        externalNativeBuildDir, buildSystem.getName(), variantData.getName());

        if (FileUtils.isFileInDirectory(externalNativeBuildPath, buildDir)) {
            File invalidPath = externalNativeBuildPath;
            externalNativeBuildPath =
                    FileUtils.join(
                            projectDir,
                            ".externalNativeBuild",
                            buildSystem.getName(),
                            variantData.getName());
            androidBuilder
                    .getIssueReporter()
                    .reportError(
                            Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                            new EvalIssueException(
                                    String.format(
                                            Locale.getDefault(),
                                            "The build staging directory you specified ('%s')"
                                                    + " is a subdirectory of your project's temporary build directory ('%s')."
                                                    + "Files in this directory do not persist through clean builds.\n"
                                                    + "Either use the default build staging directory ('%s'),"
                                                    + "or specify a path outside the temporary build directory.",
                                            invalidPath.getAbsolutePath(),
                                            buildDir.getAbsolutePath(),
                                            externalNativeBuildPath.getAbsolutePath())));
        }

        return externalNativeBuildPath;
    }

    @NonNull
    protected static File getSdkCmakeExecutable(@NonNull File sdkFolder) {
        if (isWindows()) {
            return new File(getSdkCmakeBinFolder(sdkFolder), "cmake.exe");
        }
        return new File(getSdkCmakeBinFolder(sdkFolder), "cmake");
    }

    public void forEachNativeBuildConfiguration(@NonNull Consumer<JsonReader> callback)
            throws IOException {
        List<File> files = getNativeBuildConfigurationsJsons();
        diagnostic("streaming %s JSON files", files.size());
        for (File file : getNativeBuildConfigurationsJsons()) {
            if (file.exists()) {
                diagnostic("string JSON file %s", file.getAbsolutePath());
                try (JsonReader reader = new JsonReader(new FileReader(file))) {
                    callback.accept(reader);
                } catch (Throwable e) {
                    diagnostic(
                            "Error parsing: %s",
                            String.join("\r\n", Files.readAllLines(file.toPath())));
                    throw e;
                }
            } else {
                // If the tool didn't create the JSON file then create fallback with the
                // information we have so the user can see partial information in the UI.
                diagnostic("streaming fallback JSON for %s", file.getAbsolutePath());
                NativeBuildConfigValueMini fallback = new NativeBuildConfigValueMini();
                fallback.buildFiles = Lists.newArrayList(makefile);
                try (JsonReader reader =
                        new JsonReader(new StringReader(new Gson().toJson(fallback)))) {
                    callback.accept(reader);
                }
            }
        }
    }

    @NonNull
    @InputFile
    public File getMakefile() {
        return makefile;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getObjFolder() {
        return objFolder;
    }

    @NonNull
    // This should not be annotated with @OutputDirectory because getNativeBuildConfigurationsJsons
    // is already annotated with @OutputFiles
    public File getJsonFolder() {
        return jsonFolder;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getNdkFolder() {
        return ndkFolder;
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    @NonNull
    @Optional
    @Input
    public List<String> getBuildArguments() {
        return buildArguments;
    }

    @NonNull
    @Optional
    @Input
    public List<String> getcFlags() {
        return cFlags;
    }

    @NonNull
    @Optional
    @Input
    public List<String> getCppFlags() {
        return cppFlags;
    }

    @NonNull
    @OutputFiles
    public List<File> getNativeBuildConfigurationsJsons() {
        return nativeBuildConfigurationsJsons;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getSoFolder() {
        return soFolder;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public File getSdkFolder() {
        return sdkFolder;
    }

    @NonNull
    protected NdkHandler getNdkHandler() {
        return ndkHandler;
    }


    @Input
    @NonNull
    Collection<Abi> getAbis() {
        List<Abi> result = Lists.newArrayList();
        for (JsonGenerationAbiConfiguration configuration : abiConfigurations) {
            result.add(configuration.getAbi());
        }
        return result;
    }

    @NonNull
    protected static File getSdkCmakeBinFolder(@NonNull File sdkFolder) {
        return new File(getCmakeFolderFromSdkFolder(sdkFolder), "bin");
    }

    @NonNull
    protected static File getCmakeFolderFromSdkFolder(@NonNull File sdkFolder) {
        ProgressIndicator progress = new ConsoleProgressIndicator();
        AndroidSdkHandler sdk = AndroidSdkHandler.getInstance(sdkFolder);
        LocalPackage cmakePackage =
                sdk.getLatestLocalPackageForPrefix(SdkConstants.FD_CMAKE, null, true, progress);
        if (cmakePackage != null) {
            return cmakePackage.getLocation();
        }
        return new File(sdkFolder, SdkConstants.FD_CMAKE);
    }
}
