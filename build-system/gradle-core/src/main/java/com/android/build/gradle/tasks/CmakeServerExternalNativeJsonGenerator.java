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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.external.cmake.CmakeUtils.getObjectToString;
import static com.android.build.gradle.internal.cxx.json.CompilationDatabaseIndexingVisitorKt.indexCompilationDatabase;
import static com.android.build.gradle.internal.cxx.json.CompilationDatabaseToolchainVisitorKt.populateCompilationDatabaseToolchains;
import static com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils.getOutputFolder;
import static com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils.getOutputJson;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.build.gradle.external.cmake.server.BuildFiles;
import com.android.build.gradle.external.cmake.server.CmakeInputsResult;
import com.android.build.gradle.external.cmake.server.CodeModel;
import com.android.build.gradle.external.cmake.server.CompileCommand;
import com.android.build.gradle.external.cmake.server.ComputeResult;
import com.android.build.gradle.external.cmake.server.Configuration;
import com.android.build.gradle.external.cmake.server.ConfigureCommandResult;
import com.android.build.gradle.external.cmake.server.FileGroup;
import com.android.build.gradle.external.cmake.server.HandshakeRequest;
import com.android.build.gradle.external.cmake.server.HandshakeResult;
import com.android.build.gradle.external.cmake.server.Project;
import com.android.build.gradle.external.cmake.server.ProtocolVersion;
import com.android.build.gradle.external.cmake.server.Server;
import com.android.build.gradle.external.cmake.server.ServerFactory;
import com.android.build.gradle.external.cmake.server.ServerUtils;
import com.android.build.gradle.external.cmake.server.Target;
import com.android.build.gradle.external.cmake.server.receiver.InteractiveMessage;
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationVariantConfiguration;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.CompilationDatabaseToolchain;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue;
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue;
import com.android.build.gradle.internal.cxx.json.StringTable;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.utils.ILogger;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedInts;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;

/**
 * This strategy uses the Vanilla-CMake that supports Cmake server version 1.0 to configure the
 * project and generate the android build JSON.
 */
class CmakeServerExternalNativeJsonGenerator extends CmakeExternalNativeJsonGenerator {

    private static final String CMAKE_SERVER_LOG_PREFIX = "CMAKE SERVER: ";

    public CmakeServerExternalNativeJsonGenerator(
            @NonNull JsonGenerationVariantConfiguration config,
            @NonNull NdkHandler ndkHandler,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File cmakeFolder,
            @NonNull GradleBuildVariant.Builder stats) {
        super(config, ndkHandler, androidBuilder, cmakeFolder, stats);
    }

    /**
     * @param toolchains - toolchains map
     * @return the hash of the only entry in the map, ideally the toolchains map should have only
     *     one entry.
     */
    @Nullable
    private static String getOnlyToolchainName(
            @NonNull Map<String, NativeToolchainValue> toolchains) {
        if (toolchains.size() != 1) {
            throw new RuntimeException(
                    String.format(
                            "Invalid number %d of toolchains. Only one toolchain should be present.",
                            toolchains.size()));
        }
        return toolchains.keySet().iterator().next();
    }

    @NonNull
    private static String getCmakeInfoString(@NonNull Server cmakeServer) throws IOException {
        return String.format(
                "Cmake path: %s, version: %s",
                cmakeServer.getCmakePath(),
                CmakeUtils.getVersion(new File(cmakeServer.getCmakePath())).toString());
    }

    @NonNull
    @Override
    List<String> getCacheArguments(@NonNull String abi, int abiPlatformVersion) {
        List<String> cacheArguments = getCommonCacheArguments(abi, abiPlatformVersion);
        cacheArguments.add("-DCMAKE_SYSTEM_NAME=Android");
        cacheArguments.add(String.format("-DCMAKE_ANDROID_ARCH_ABI=%s", abi));
        cacheArguments.add(String.format("-DCMAKE_SYSTEM_VERSION=%s", abiPlatformVersion));
        // Generates the compile_commands json file that will help us get the compiler executable
        // and flags.
        cacheArguments.add("-DCMAKE_EXPORT_COMPILE_COMMANDS=ON");
        cacheArguments.add(String.format("-DCMAKE_ANDROID_NDK=%s", getNdkFolder()));

        cacheArguments.add(
                String.format(
                        "-DCMAKE_TOOLCHAIN_FILE=%s", getToolchainFile(abi).getAbsolutePath()));

        // By default, use the ninja generator.
        cacheArguments.add("-G Ninja");

        // To preserve backward compatibility with fork CMake look for ninja.exe next to cmake.exe
        // and use it. If it's not there then normal CMake search logic will be used.
        File possibleNinja =
                isWindows()
                        ? new File(getCmakeBinFolder(), "ninja.exe")
                        : new File(getCmakeBinFolder(), "ninja");
        if (possibleNinja.isFile()) {
            cacheArguments.add(String.format("-DCMAKE_MAKE_PROGRAM=%s", possibleNinja));
        }
        return cacheArguments;
    }

    @NonNull
    @Override
    public String executeProcessAndGetOutput(
            @NonNull String abi, int abiPlatformVersion, @NonNull File outputJsonDir)
            throws ProcessException, IOException {
        // Once a Cmake server object is created
        // - connect to the server
        // - perform a handshake
        // - configure and compute.
        // Create the NativeBuildConfigValue and write the required JSON file.
        PrintWriter serverLogWriter = null;

        try {
            serverLogWriter = getCmakeServerLogWriter(getOutputFolder(getJsonFolder(), abi));
            ILogger logger = LoggerWrapper.getLogger(CmakeServerExternalNativeJsonGenerator.class);
            Server cmakeServer = createServerAndConnect(serverLogWriter, logger);

            doHandshake(outputJsonDir, cmakeServer);
            ConfigureCommandResult configureCommandResult =
                    doConfigure(abi, abiPlatformVersion, cmakeServer);
            if (!ServerUtils.isConfigureResultValid(configureCommandResult.configureResult)) {
                throw new ProcessException(
                        String.format(
                                "Error configuring CMake server (%s).\r\n%s",
                                cmakeServer.getCmakePath(),
                                configureCommandResult.interactiveMessages));
            }

            ComputeResult computeResult = doCompute(cmakeServer);
            if (!ServerUtils.isComputedResultValid(computeResult)) {
                throw new ProcessException(
                        "Error computing CMake server result.\r\n"
                                + configureCommandResult.interactiveMessages);
            }

            generateAndroidGradleBuild(abi, cmakeServer);
            return configureCommandResult.interactiveMessages;
        } finally {
            if (serverLogWriter != null) {
                serverLogWriter.close();
            }
        }
    }

    /** Returns PrintWriter object to write CMake server logs. */
    @NonNull
    private static PrintWriter getCmakeServerLogWriter(@NonNull File outputFolder)
            throws IOException {
        return new PrintWriter(getCmakeServerLog(outputFolder).getAbsoluteFile(), "UTF-8");
    }

    /** Returns the CMake server log file using the given output folder. */
    @NonNull
    private static File getCmakeServerLog(@NonNull File outputFolder) {
        return new File(outputFolder, "cmake_server_log.txt");
    }

    /**
     * Creates a Cmake server and connects to it.
     *
     * @return a Cmake Server object that's successfully connected to the Cmake server
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we are unable
     *     to create or connect to Cmake server.
     */
    @NonNull
    private Server createServerAndConnect(
            @NonNull PrintWriter serverLogWriter, @NonNull ILogger logger) throws IOException {
        // Create a new cmake server for the given Cmake and configure the given project.
        ServerReceiver serverReceiver =
                new ServerReceiver()
                        .setMessageReceiver(
                                message ->
                                        receiveInteractiveMessage(
                                                serverLogWriter,
                                                logger,
                                                message,
                                                getMakefile().getParentFile()))
                        .setDiagnosticReceiver(
                                message ->
                                        receiveDiagnosticMessage(serverLogWriter, logger, message));
        Server cmakeServer = ServerFactory.create(getCmakeBinFolder(), serverReceiver);
        if (cmakeServer == null) {
            throw new RuntimeException(
                    "Unable to create a Cmake server located at: "
                            + getCmakeBinFolder().getAbsolutePath());
        }

        if (!cmakeServer.connect()) {
            throw new RuntimeException(
                    "Unable to connect to Cmake server located at: "
                            + getCmakeBinFolder().getAbsolutePath());
        }

        return cmakeServer;
    }

    /** Processes an interactive message received from the CMake server. */
    static void receiveInteractiveMessage(
            @NonNull PrintWriter writer,
            @NonNull ILogger logger,
            @NonNull InteractiveMessage message,
            @NonNull File makeFileDirectory) {
        writer.println(CMAKE_SERVER_LOG_PREFIX + message.message);
        logInteractiveMessage(logger, message, makeFileDirectory);
    }

    /**
     * Logs info/warning/error for the given interactive message. Throws a RunTimeException in case
     * of an 'error' message type.
     */
    @VisibleForTesting
    static void logInteractiveMessage(
            @NonNull ILogger logger,
            @NonNull InteractiveMessage message,
            @NonNull File makeFileDirectory) {
        // CMake error/warining prefix strings. The CMake errors and warnings are part of the
        // message type "message" even though CMake is reporting errors/warnings (Note: They could
        // have a title that says if it's an error or warning, we check that first before checking
        // the prefix of the message string). Hence we would need to parse the output message to
        // figure out if we need to log them as error or warning.
        final String CMAKE_ERROR_PREFIX = "CMake Error";
        final String CMAKE_WARNING_PREFIX = "CMake Warning";

        // If the final message received is of type error, log and error and throw an exception.
        // Note: This is not the same as a message with type "message" with error information, that
        // case is handled below.
        if (message.type != null && message.type.equals("error")) {
            logger.error(null, correctMakefilePaths(message.errorMessage, makeFileDirectory));
            return;
        }

        String correctedMessage = correctMakefilePaths(message.message, makeFileDirectory);

        if ((message.title != null && message.title.equals("Error"))
                || message.message.startsWith(CMAKE_ERROR_PREFIX)) {
            logger.error(null, correctedMessage);
            return;
        }

        if ((message.title != null && message.title.equals("Warning"))
                || message.message.startsWith(CMAKE_WARNING_PREFIX)) {
            logger.warning(correctedMessage);
            return;
        }

        logger.info(correctedMessage);
    }

    /** Processes an diagnostic message received by/from the CMake server. */
    static void receiveDiagnosticMessage(
            @NonNull PrintWriter writer, @NonNull ILogger logger, @NonNull String message) {
        writer.println(CMAKE_SERVER_LOG_PREFIX + message);
        logger.info(message);
    }

    /**
     * Requests a handshake to a connected Cmake server.
     *
     * @param outputDir - output/build directory
     * @param cmakeServer - cmake server object that's connected
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
     *     invalid/erroneous handshake result.
     */
    private void doHandshake(@NonNull File outputDir, @NonNull Server cmakeServer)
            throws IOException {
        List<ProtocolVersion> supportedProtocolVersions = cmakeServer.getSupportedVersion();
        if (supportedProtocolVersions == null || supportedProtocolVersions.isEmpty()) {
            throw new RuntimeException(
                    String.format(
                            "Gradle does not support the Cmake server version. %s",
                            getCmakeInfoString(cmakeServer)));
        }

        HandshakeResult handshakeResult =
                cmakeServer.handshake(
                        getHandshakeRequest(supportedProtocolVersions.get(0), outputDir));
        if (!ServerUtils.isHandshakeResultValid(handshakeResult)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid handshake result from Cmake server: \n%s\n%s",
                            getObjectToString(handshakeResult), getCmakeInfoString(cmakeServer)));
        }
    }

    /**
     * Create a default handshake request for the given Cmake server-protocol version
     *
     * @param cmakeServerProtocolVersion - Cmake server's protocol version requested
     * @param outputDir - output directory
     * @return handshake request
     */
    private HandshakeRequest getHandshakeRequest(
            @NonNull ProtocolVersion cmakeServerProtocolVersion, @NonNull File outputDir) {
        HandshakeRequest handshakeRequest = new HandshakeRequest();
        handshakeRequest.cookie = "gradle-cmake-cookie";
        handshakeRequest.generator = getGenerator(getBuildArguments());
        handshakeRequest.protocolVersion = cmakeServerProtocolVersion;
        handshakeRequest.buildDirectory = normalizeFilePath(outputDir.getParentFile());
        handshakeRequest.sourceDirectory = normalizeFilePath(getMakefile().getParentFile());

        return handshakeRequest;
    }

    /**
     * Configures the given project on the cmake server
     *
     * @param abi - ABI to configure
     * @param abiPlatformVersion - ABI platform version to configure
     * @param cmakeServer - connected cmake server
     * @return Valid ConfigureCommandResult
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
     *     invalid/erroneous ConfigureCommandResult.
     */
    @NonNull
    private ConfigureCommandResult doConfigure(
            @NonNull String abi, int abiPlatformVersion, @NonNull Server cmakeServer)
            throws IOException {
        List<String> cacheArgumentsList = getCacheArguments(abi, abiPlatformVersion);
        cacheArgumentsList.addAll(getBuildArguments());
        String argsArray[] = new String[cacheArgumentsList.size()];
        cacheArgumentsList.toArray(argsArray);
        return cmakeServer.configure(argsArray);
    }

    /**
     * Generate build system files in the build directly, or compute the given project and returns
     * the computed result.
     *
     * @param cmakeServer Connected cmake server.
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
     *     invalid/erroneous ComputeResult.
     */
    private static ComputeResult doCompute(@NonNull Server cmakeServer) throws IOException {
        return cmakeServer.compute();
    }

    /**
     * Gets the generator set explicitly by the user (overriding our default).
     *
     * @param buildArguments - build arguments
     */
    @NonNull
    private static String getGenerator(@NonNull List<String> buildArguments) {
        String generatorArgument = "-G ";
        for (String argument : buildArguments) {
            if (!argument.startsWith(generatorArgument)) {
                continue;
            }

            int startIndex = argument.indexOf(generatorArgument) + generatorArgument.length();
            return argument.substring(startIndex, argument.length());
        }
        // Return the default generator, i.e., "Ninja"
        return "Ninja";
    }

    /**
     * Generates nativeBuildConfigValue by generating the code model from the cmake server and
     * writes the android_gradle_build.json.
     *
     * @param abi - ABI for which NativeBuildConfigValue needs to be created
     * @param cmakeServer - cmake server to get the code model from. Expectations: cmake server
     *     should be successfully computed and configured.
     * @throws IOException I/O failure
     */
    private void generateAndroidGradleBuild(@NonNull String abi, @NonNull Server cmakeServer)
            throws IOException {
        NativeBuildConfigValue nativeBuildConfigValue = getNativeBuildConfigValue(abi, cmakeServer);
        AndroidBuildGradleJsons.writeNativeBuildConfigValueToJsonFile(
                getOutputJson(getJsonFolder(), abi), nativeBuildConfigValue);
    }

    /**
     * Returns NativeBuildConfigValue for the given abi from the given Cmake server.
     *
     * @param abi - ABI for which NativeBuildConfigValue needs to be created
     * @param cmakeServer - cmake server to get the code model from. Expectations: cmake server
     *     should be successfully computed and configured.
     * @return returns NativeBuildConfigValue
     * @throws IOException I/O failure
     */
    @VisibleForTesting
    protected NativeBuildConfigValue getNativeBuildConfigValue(
            @NonNull String abi, @NonNull Server cmakeServer) throws IOException {
        NativeBuildConfigValue nativeBuildConfigValue = createDefaultNativeBuildConfigValue();

        assert nativeBuildConfigValue.stringTable != null;
        StringTable strings = new StringTable(nativeBuildConfigValue.stringTable);

        // Build file
        assert nativeBuildConfigValue.buildFiles != null;
        nativeBuildConfigValue.buildFiles.addAll(getBuildFiles(abi, cmakeServer));

        // Clean commands
        assert nativeBuildConfigValue.cleanCommands != null;
        nativeBuildConfigValue.cleanCommands.add(
                CmakeUtils.getCleanCommand(
                        getCmakeExecutable(), getOutputFolder(getJsonFolder(), abi)));

        CodeModel codeModel = cmakeServer.codemodel();
        if (!ServerUtils.isCodeModelValid(codeModel)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid code model received from Cmake server: \n%s\n%s",
                            getObjectToString(codeModel), getCmakeInfoString(cmakeServer)));
        }

        // C and Cpp extensions
        assert nativeBuildConfigValue.cFileExtensions != null;
        nativeBuildConfigValue.cFileExtensions.addAll(CmakeUtils.getCExtensionSet(codeModel));
        assert nativeBuildConfigValue.cppFileExtensions != null;
        nativeBuildConfigValue.cppFileExtensions.addAll(CmakeUtils.getCppExtensionSet(codeModel));

        // toolchains
        nativeBuildConfigValue.toolchains =
                getNativeToolchains(
                        abi,
                        cmakeServer,
                        nativeBuildConfigValue.cppFileExtensions,
                        nativeBuildConfigValue.cFileExtensions);

        String toolchainHashString = getOnlyToolchainName(nativeBuildConfigValue.toolchains);

        // Fill in the required fields in NativeBuildConfigValue from the code model obtained from
        // Cmake server.
        for (Configuration config : codeModel.configurations) {
            for (Project project : config.projects) {
                for (Target target : project.targets) {
                    // Ignore targets that aren't valid.
                    if (!canAddTargetToNativeLibrary(target)) {
                        continue;
                    }

                    NativeLibraryValue nativeLibraryValue =
                            getNativeLibraryValue(abi, target, strings);
                    nativeLibraryValue.toolchain = toolchainHashString;
                    String libraryName = target.name + "-" + config.name + "-" + abi;
                    assert nativeBuildConfigValue.libraries != null;
                    nativeBuildConfigValue.libraries.put(libraryName, nativeLibraryValue);
                } // target
            } // project
        }
        return nativeBuildConfigValue;
    }

    @VisibleForTesting
    protected NativeLibraryValue getNativeLibraryValue(
            @NonNull String abi, @NonNull Target target, StringTable strings) {
        NativeLibraryValue nativeLibraryValue = new NativeLibraryValue();
        nativeLibraryValue.abi = abi;
        nativeLibraryValue.buildCommand =
                CmakeUtils.getBuildCommand(
                        getCmakeExecutable(), getOutputFolder(getJsonFolder(), abi), target.name);
        nativeLibraryValue.artifactName = target.name;
        nativeLibraryValue.buildType = isDebuggable() ? "debug" : "release";
        // We'll have only one output, so get the first one.
        if (target.artifacts.length > 0) {
            nativeLibraryValue.output = new File(target.artifacts[0]);
        }

        nativeLibraryValue.files = new ArrayList<>();
        Map<String, Integer> compilationDatabaseFlags = Maps.newHashMap();

        for (FileGroup fileGroup : target.fileGroups) {
            for (String source : fileGroup.sources) {
                NativeSourceFileValue nativeSourceFileValue = new NativeSourceFileValue();
                nativeSourceFileValue.workingDirectoryOrdinal =
                        strings.intern(target.buildDirectory);
                File sourceFile = new File(target.sourceDirectory, source);
                nativeSourceFileValue.src = sourceFile;
                if (Strings.isNullOrEmpty(fileGroup.compileFlags)) {
                    // If flags weren't available in the CMake server model then fall back to using
                    // compilation_database.json.
                    // This is related to http://b/72065334 in which the compilation database did
                    // not have flags for a particular file. Unclear why. I think the correct flags
                    // to use is fileGroup.compileFlags and that compilation database should be
                    // a fall-back case.
                    if (compilationDatabaseFlags.isEmpty()) {
                        compilationDatabaseFlags =
                                indexCompilationDatabase(getCompileCommandsJson(abi), strings);
                    }
                    if (compilationDatabaseFlags.containsKey(sourceFile.toString())) {
                        nativeSourceFileValue.flagsOrdinal =
                                compilationDatabaseFlags.get(sourceFile.toString());
                    }
                } else {
                    nativeSourceFileValue.flagsOrdinal = strings.intern(fileGroup.compileFlags);
                }
                nativeLibraryValue.files.add(nativeSourceFileValue);
            }
        }

        return nativeLibraryValue;
    }

    /**
     * Helper function that returns true if the Target object is valid to be added to native
     * library.
     */
    private static boolean canAddTargetToNativeLibrary(@NonNull Target target) {
        // If the target has no artifacts or file groups, the target will be get ignored, so mark
        // it valid.
        return (target.artifacts != null) && (target.fileGroups != null);
    }

    /**
     * Returns the list of build files used by CMake as part of the build system. Temporary files
     * are currently ignored.
     */
    @NonNull
    private List<File> getBuildFiles(@NonNull String abi, @NonNull Server cmakeServer)
            throws IOException {
        CmakeInputsResult cmakeInputsResult = cmakeServer.cmakeInputs();
        if (!ServerUtils.isCmakeInputsResultValid(cmakeInputsResult)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid cmakeInputs result received from Cmake server: \n%s\n%s",
                            getObjectToString(cmakeInputsResult), getCmakeInfoString(cmakeServer)));
        }

        // Ideally we should see the build files within cmakeInputs response, but in the weird case
        // that we don't, return the default make file.
        if (cmakeInputsResult.buildFiles == null) {
            List<File> buildFiles = Lists.newArrayList();
            buildFiles.add(getMakefile());
            return buildFiles;
        }

        // The sources listed might be duplicated, so remove the duplicates.
        Set<String> buildSources = Sets.newHashSet();
        for (BuildFiles buildFile : cmakeInputsResult.buildFiles) {
            if (buildFile.isTemporary || buildFile.isCMake || buildFile.sources == null) {
                continue;
            }
            Collections.addAll(buildSources, buildFile.sources);
        }

        // The path to the build file source might be relative, so use the absolute path using
        // source directory information.
        File sourceDirectory = null;
        if (cmakeInputsResult.sourceDirectory != null) {
            sourceDirectory = new File(cmakeInputsResult.sourceDirectory);
        }

        List<File> buildFiles = Lists.newArrayList();
        File preNDKr15ToolchainFile = getTempToolchainFile(getOutputFolder(getJsonFolder(), abi));

        for (String source : buildSources) {
            // The source file can either be relative or absolute, if it's relative, use the source
            // directory to get the absolute path.
            File sourceFile = new File(source);
            if (!sourceFile.isAbsolute()) {
                if (sourceDirectory != null) {
                    sourceFile = new File(sourceDirectory, source);
                }
            }

            if (!sourceFile.exists()) {
                ILogger logger =
                        LoggerWrapper.getLogger(CmakeServerExternalNativeJsonGenerator.class);
                logger.error(
                        null,
                        "Build file "
                                + sourceFile
                                + " provided by CMake "
                                + "does not exists. This might lead to incorrect Android Studio behavior.");
                continue;
            }
            // Ignore the toolchain file created to support pre NDKr15, because, even if the user
            // updates the file, it'll be overwritten by Gradle during the next sync. So we don't
            // need to watch this file.
            if (preNDKr15ToolchainFile.getName().equals(sourceFile.getName())) {
                continue;
            }

            buildFiles.add(sourceFile);
        }

        return buildFiles;
    }

    /**
     * Creates a default NativeBuildConfigValue.
     *
     * @return a default NativeBuildConfigValue.
     */
    @NonNull
    private static NativeBuildConfigValue createDefaultNativeBuildConfigValue() {
        NativeBuildConfigValue nativeBuildConfigValue = new NativeBuildConfigValue();
        nativeBuildConfigValue.buildFiles = new ArrayList<>();
        nativeBuildConfigValue.cleanCommands = new ArrayList<>();
        nativeBuildConfigValue.libraries = new HashMap<>();
        nativeBuildConfigValue.toolchains = new HashMap<>();
        nativeBuildConfigValue.cFileExtensions = new ArrayList<>();
        nativeBuildConfigValue.cppFileExtensions = new ArrayList<>();
        nativeBuildConfigValue.stringTable = Maps.newHashMap();
        return nativeBuildConfigValue;
    }

    /**
     * Returns the native toolchain for the given abi from the provided Cmake server. We ideally
     * should get the toolchain information compile commands JSON file. If it's unavailable, we
     * fallback to figuring this information out from the messages produced by Cmake server when
     * configuring the project (though hacky, it works!).
     *
     * @param abi - ABI for which NativeToolchainValue needs to be created
     * @param cmakeServer - Cmake server
     * @param cppExtensionSet - CXX extensions
     * @param cExtensionSet - C extensions
     * @return a map of toolchain hash to toolchain value. The map will have only one entry.
     */
    @NonNull
    private Map<String, NativeToolchainValue> getNativeToolchains(
            @NonNull String abi,
            @NonNull Server cmakeServer,
            @NonNull Collection<String> cppExtensionSet,
            @NonNull Collection<String> cExtensionSet) {
        NativeToolchainValue toolchainValue = new NativeToolchainValue();
        File cCompilerExecutable = null;
        File cppCompilerExecutable = null;

        File compilationDatabase = getCompileCommandsJson(abi);
        if (compilationDatabase.exists()) {
            CompilationDatabaseToolchain toolchain =
                    populateCompilationDatabaseToolchains(
                            compilationDatabase, cppExtensionSet, cExtensionSet);
            cppCompilerExecutable = toolchain.getCppCompilerExecutable();
            cCompilerExecutable = toolchain.getCCompilerExecutable();
        } else {
            if (!cmakeServer.getCCompilerExecutable().isEmpty()) {
                cCompilerExecutable = new File(cmakeServer.getCCompilerExecutable());
            }
            if (!cmakeServer.getCppCompilerExecutable().isEmpty()) {
                cppCompilerExecutable = new File(cmakeServer.getCppCompilerExecutable());
            }
        }

        if (cCompilerExecutable != null) {
            toolchainValue.cCompilerExecutable = cCompilerExecutable;
        }
        if (cppCompilerExecutable != null) {
            toolchainValue.cppCompilerExecutable = cppCompilerExecutable;
        }

        int toolchainHash = CmakeUtils.getToolchainHash(toolchainValue);
        String toolchainHashString = UnsignedInts.toString(toolchainHash);

        Map<String, NativeToolchainValue> toolchains = new HashMap<>();
        toolchains.put(toolchainHashString, toolchainValue);

        return toolchains;
    }

    /** Helper function that returns the flags used to compile a given file. */
    @VisibleForTesting
    static String getAndroidGradleFileLibFlags(
            @NonNull String fileName, @NonNull List<CompileCommand> compileCommands) {
        String flags = null;

        // Get the path of the given file name so we can compare it with the file specified within
        // CompileCommand.
        Path fileNamePath = Paths.get(fileName);

        // Search for the CompileCommand for the given file and parse the flags used to compile the
        // file.
        for (CompileCommand compileCommand : compileCommands) {
            if (compileCommand.command == null || compileCommand.file == null) {
                continue;
            }

            if (fileNamePath.compareTo(Paths.get(compileCommand.file)) != 0) {
                continue;
            }

            flags =
                    compileCommand.command.substring(
                            compileCommand.command.indexOf(' ') + 1,
                            compileCommand.command.indexOf(fileName));
            break;
        }
        return flags;
    }

    /** Returns the toolchain file to be used. */
    @NonNull
    private File getToolchainFile(@NonNull String abi) {
        // NDK versions r15 and above have the fix in android.toolchain.cmake to work with CMake
        // version 3.7+, but if the user has NDK r14 or below, we add the (hacky) fix
        // programmatically.
        if (getNdkHandler().getRevision() != null
                && getNdkHandler().getRevision().getMajor() >= 15) {
            // Add our toolchain file.
            // Note: When setting this flag, Cmake's android toolchain would end up calling our
            // toolchain via ndk-cmake-hooks, but our toolchains will (ideally) be executed only
            // once.
            return getToolChainFile();
        }
        return getPreNDKr15WrapperToolchainFile(getOutputFolder(getJsonFolder(), abi));
    }

    /**
     * Returns a pre-ndk-r15-wrapper android toolchain cmake file for NDK r14 and below that has a
     * fix to work with CMake versions 3.7+. Note: This is a hacky solution, ideally, the user
     * should install NDK r15+ so it works with CMake 3.7+.
     */
    @NonNull
    private File getPreNDKr15WrapperToolchainFile(@NonNull File outputFolder) {
        StringBuilder tempAndroidToolchain =
                new StringBuilder(
                        "# This toolchain file was generated by Gradle to support NDK versions r14 and below.\n");

        // Include the original android toolchain
        tempAndroidToolchain
                .append(String.format("include(%s)", normalizeFilePath(getToolChainFile())))
                .append(System.lineSeparator());
        // Overwrite the CMAKE_SYSTEM_VERSION to 1 so we skip CMake's Android toolchain.
        tempAndroidToolchain.append("set(CMAKE_SYSTEM_VERSION 1)").append(System.lineSeparator());

        File toolchainFile = getTempToolchainFile(outputFolder);
        try {
            FileUtils.writeStringToFile(toolchainFile, tempAndroidToolchain.toString());
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format(
                            "Unable to write to file: %s."
                                    + "Please upgrade NDK to version 15 or above.",
                            toolchainFile.getAbsolutePath()));
        }

        return toolchainFile;
    }

    /**
     * Returns a pre-ndk-r15-wrapper cmake toolchain file within the object folder for the project.
     */
    @NonNull
    private static File getTempToolchainFile(@NonNull File outputFolder) {
        String tempAndroidToolchainFile = "pre-ndk-r15-wrapper-android.toolchain.cmake";
        return new File(outputFolder, tempAndroidToolchainFile);
    }

    /**
     * Returns the normalized path for the given file. The normalized path for Unix is the default
     * string returned by getPath. For Microsoft Windows, getPath returns a path with "\\" (example:
     * "C:\\Android\\Sdk") while Vanilla-CMake prefers a forward slash (example "C:/Android/Sdk"),
     * without the forward slash, CMake would mix backward slash and forward slash causing compiler
     * issues. This function replaces the backward slashes with forward slashes for Microsoft
     * Windows.
     */
    @NonNull
    private static String normalizeFilePath(@NonNull File file) {
        if (isWindows()) {
            return (file.getPath().replace("\\", "/"));
        }
        return file.getPath();
    }
}
