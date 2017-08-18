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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.external.cmake.CmakeUtils;
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
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.external.gson.NativeSourceFileValue;
import com.android.build.gradle.external.gson.NativeToolchainValue;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.google.common.primitives.UnsignedInts;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Contract;

/**
 * This strategy uses the Vanilla-CMake that supports Cmake server version 1.0 to configure the
 * project and generate the android build JSON.
 */
class CmakeServerExternalNativeJsonGenerator extends CmakeExternalNativeJsonGenerator {
    // Constructor
    public CmakeServerExternalNativeJsonGenerator(
            @NonNull NdkHandler ndkHandler,
            int minSdkVersion,
            @NonNull String variantName,
            @NonNull Collection<Abi> abis,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File sdkFolder,
            @NonNull File ndkFolder,
            @NonNull File soFolder,
            @NonNull File objFolder,
            @NonNull File jsonFolder,
            @NonNull File makeFile,
            boolean debuggable,
            @Nullable List<String> buildArguments,
            @Nullable List<String> cFlags,
            @Nullable List<String> cppFlags,
            @NonNull List<File> nativeBuildConfigurationsJsons) {
        super(
                ndkHandler,
                minSdkVersion,
                variantName,
                abis,
                androidBuilder,
                sdkFolder,
                ndkFolder,
                soFolder,
                objFolder,
                jsonFolder,
                makeFile,
                debuggable,
                buildArguments,
                cFlags,
                cppFlags,
                nativeBuildConfigurationsJsons);
    }

    /** @return Returns the default generator supported. */
    @Contract(pure = true)
    @NonNull
    private static String getDefaultGenerator() {
        return "Ninja";
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
        for (String key : toolchains.keySet()) {
            return key;
        }
        return null;
    }

    @NonNull
    private static String getCmakeInfoString(@NonNull Server cmakeServer) {
        return String.format(
                "Cmake path: %s, version: %s",
                cmakeServer.getCmakePath(),
                CmakeUtils.getVersion(cmakeServer.getCmakePath()).toString());
    }

    @NonNull
    @Override
    List<String> getCacheArguments(@NonNull String abi, int abiPlatformVersion) {
        List<String> cacheArguments = getCommonCacheArguments(abi, abiPlatformVersion);
        cacheArguments.add(String.format("-DCMAKE_ANDROID_ARCH_ABI=%s", abi));
        cacheArguments.add(String.format("-DCMAKE_SYSTEM_VERSION=%s", abiPlatformVersion));
        // Generates the compile_commands json file that will help us get the compiler executable
        // and flags.
        cacheArguments.add("-DCMAKE_EXPORT_COMPILE_COMMANDS=ON");
        cacheArguments.add(String.format("-DCMAKE_ANDROID_NDK=%s", getNdkFolder()));

        // Add our toolchain file.
        // Note: When setting this flag, Cmake's android toolchain would end up calling our
        // toolchain via ndk-cmake-hooks, but our toolchains will (ideally) be executed only ones.
        cacheArguments.add(
                String.format("-DCMAKE_TOOLCHAIN_FILE=%s", getToolChainFile().getAbsolutePath()));

        // By default, use the ninja generator.
        cacheArguments.add("-G Ninja");
        return cacheArguments;
    }

    @NonNull
    @Override
    public String executeProcess(
            @NonNull String abi, int abiPlatformVersion, @NonNull File outputJsonDir)
            throws ProcessException, IOException {
        // Once a Cmake server object is created
        // - connect to the server
        // - perform a handshake
        // - configure and compute.
        // Create the NativeBuildConfigValue and write the required JSON file.
        Server cmakeServer = createServerAndConnect();
        doHandshake(outputJsonDir, cmakeServer);
        ConfigureCommandResult configureCommandResult =
                doConfigure(abi, abiPlatformVersion, cmakeServer);
        doCompute(cmakeServer);
        generateAndroidGradleBuild(abi, cmakeServer);
        return configureCommandResult.interactiveMessages;
    }

    /**
     * Creates a Cmake server and connects to it.
     *
     * @return a Cmake Server object that's successfully connected to the Cmake server
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we are unable
     *     to create or connect to Cmake server.
     */
    @NonNull
    private Server createServerAndConnect() throws IOException {
        // Create a new cmake server for the given Cmake and configure the given project.
        ServerReceiver serverReceiver =
                new ServerReceiver()
                        .setMessageReceiver(
                                message ->
                                        System.err.print("CMAKE SERVER: " + message.message + "\n"))
                        .setDiagnosticReceiver(
                                message -> System.err.print("CMAKE SERVER: " + message + "\n"));
        Server cmakeServer = ServerFactory.create(getSdkCmakeBinFolder(), serverReceiver);
        if (cmakeServer == null) {
            throw new RuntimeException("Unable to create a Cmake server");
        }

        if (!cmakeServer.connect()) {
            throw new RuntimeException("Unable to connect to Cmake server");
        }

        return cmakeServer;
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
        if (supportedProtocolVersions == null || supportedProtocolVersions.size() <= 0) {
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
                            CmakeUtils.getObjectToString(handshakeResult),
                            getCmakeInfoString(cmakeServer)));
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
        handshakeRequest.buildDirectory = outputDir.getParentFile().getPath();
        handshakeRequest.sourceDirectory = getMakefile().getParentFile().getPath();

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
        ConfigureCommandResult configureCommandResult =
                cmakeServer.configure(
                        cacheArgumentsList.toArray(new String[cacheArgumentsList.size()]));
        if (!ServerUtils.isConfigureResultValid(configureCommandResult.configureResult)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid config result from Cmake server: \n%s\n%s",
                            CmakeUtils.getObjectToString(configureCommandResult),
                            getCmakeInfoString(cmakeServer)));
        }

        return configureCommandResult;
    }

    /**
     * Generate build system files in the build directly, or compute the given project.
     *
     * @param cmakeServer Connected cmake server.
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
     *     invalid/erroneous ComputeResult.
     */
    private static void doCompute(@NonNull Server cmakeServer) throws IOException {
        ComputeResult computeResult = cmakeServer.compute();
        if (!ServerUtils.isComputedResultValid(computeResult)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid compute result from Cmake server: \n%s\n%s",
                            CmakeUtils.getObjectToString(computeResult),
                            getCmakeInfoString(cmakeServer)));
        }
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
        return getDefaultGenerator();
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
        ExternalNativeBuildTaskUtils.writeNativeBuildConfigValueToJsonFile(
                ExternalNativeBuildTaskUtils.getOutputJson(getJsonFolder(), abi),
                nativeBuildConfigValue);
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

        // Build file
        nativeBuildConfigValue.buildFiles.add(getMakefile());

        // Clean commands
        nativeBuildConfigValue.cleanCommands.add(
                CmakeUtils.getCleanCommand(getSdkCmakeExecutable(), getJsonFolder()));

        CodeModel codeModel = cmakeServer.codemodel();
        if (!ServerUtils.isCodeModelValid(codeModel)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid code model received from Cmake server: \n%s\n%s",
                            CmakeUtils.getObjectToString(codeModel),
                            getCmakeInfoString(cmakeServer)));
        }
        // C and Cpp extensions
        nativeBuildConfigValue.cFileExtensions.addAll(CmakeUtils.getCExtensionSet(codeModel));
        nativeBuildConfigValue.cppFileExtensions.addAll(CmakeUtils.getCppExtensionSet(codeModel));

        // toolchains
        nativeBuildConfigValue.toolchains =
                getNativeToolchains(
                        abi,
                        cmakeServer,
                        nativeBuildConfigValue.cFileExtensions,
                        nativeBuildConfigValue.cppFileExtensions);

        String toolchainHashString = getOnlyToolchainName(nativeBuildConfigValue.toolchains);

        // Fill in the required fields in NativeBuildConfigValue from the code model obtained from
        // Cmake server.
        for (Configuration config : codeModel.configurations) {
            for (Project project : config.projects) {
                for (Target target : project.targets) {
                    NativeLibraryValue nativeLibraryValue = new NativeLibraryValue();
                    nativeLibraryValue.abi = abi;
                    nativeLibraryValue.buildCommand =
                            CmakeUtils.getBuildCommand(
                                    getSdkCmakeExecutable(), getJsonFolder(), target.name);
                    nativeLibraryValue.artifactName = target.name;
                    nativeLibraryValue.buildType = isDebuggable() ? "debug" : "release";
                    // We'll have only one output, so get the first one.
                    if (target.artifacts.length > 0) {
                        nativeLibraryValue.output = new File(target.artifacts[0]);
                    }

                    nativeLibraryValue.files = new ArrayList<>();

                    for (FileGroup fileGroup : target.fileGroups) {
                        for (String source : fileGroup.sources) {
                            NativeSourceFileValue nativeSourceFileValue =
                                    new NativeSourceFileValue();
                            nativeSourceFileValue.workingDirectory =
                                    new File(target.buildDirectory);
                            nativeSourceFileValue.src = new File(source);
                            File sourceFile = new File(project.sourceDirectory, source);
                            nativeSourceFileValue.flags =
                                    getAndroidGradleFileLibFlags(abi, sourceFile.getAbsolutePath());
                            nativeLibraryValue.files.add(nativeSourceFileValue);
                        }
                    }

                    nativeLibraryValue.toolchain = toolchainHashString;
                    String libraryName = target.name + "-" + config.name + "-" + abi;
                    nativeBuildConfigValue.libraries.put(libraryName, nativeLibraryValue);
                } // target
            } // project
        }
        return nativeBuildConfigValue;
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

        try {
            List<CompileCommand> compileCommands = getCompileCommands(abi);
            if (!compileCommands.isEmpty()) {
                for (CompileCommand compileCommand : compileCommands) {
                    if (compileCommand.file == null || compileCommand.command == null) {
                        continue;
                    }
                    String extension =
                            compileCommand
                                    .file
                                    .substring(compileCommand.file.lastIndexOf('.') + 1)
                                    .trim();
                    String executable =
                            compileCommand.command.substring(
                                    0, compileCommand.command.indexOf(' '));
                    if (toolchainValue.cppCompilerExecutable == null
                            && cppExtensionSet.contains(extension)) {
                        toolchainValue.cppCompilerExecutable = new File(executable);
                        continue;
                    }
                    if (toolchainValue.cCompilerExecutable == null
                            && cExtensionSet.contains(extension)) {
                        toolchainValue.cCompilerExecutable = new File(executable);
                    }
                }
            } else {
                if (cmakeServer.getCCompilerExecutable() != null) {
                    cCompilerExecutable = new File(cmakeServer.getCCompilerExecutable());
                }
                if (cmakeServer.getCppCompilerExecutable() != null) {
                    cppCompilerExecutable = new File(cmakeServer.getCppCompilerExecutable());
                }
            }
        } catch (IOException e) {
            if (cmakeServer.getCCompilerExecutable() != null) {
                cCompilerExecutable = new File(cmakeServer.getCCompilerExecutable());
            }
            if (cmakeServer.getCppCompilerExecutable() != null) {
                cppCompilerExecutable = new File(cmakeServer.getCppCompilerExecutable());
            }
        }

        toolchainValue.cCompilerExecutable = cCompilerExecutable;
        toolchainValue.cppCompilerExecutable = cppCompilerExecutable;

        int toolchainHash = CmakeUtils.getToolchainHash(toolchainValue);
        String toolchainHashString = UnsignedInts.toString(toolchainHash);

        Map<String, NativeToolchainValue> toolchains = new HashMap<>();
        toolchains.put(toolchainHashString, toolchainValue);

        return toolchains;
    }

    /**
     * Returns a list of all CompileCommand from compile-commands json file if one is available.
     *
     * @param abi - ABI for which compile commands json file needs to be read
     * @return list of all CompileCommand
     * @throws IOException I/O failure
     */
    @NonNull
    private List<CompileCommand> getCompileCommands(@NonNull String abi) throws IOException {
        return ServerUtils.getCompilationDatabase(getCompileCommandsJson(abi));
    }

    /**
     * Returns the flags used to compile a given file
     *
     * @param abi - ABI for which compiler flags for the given json file needs to be returned
     * @param fileName - file name
     * @return flags used to compile a give CXX/C file
     */
    @Nullable
    private String getAndroidGradleFileLibFlags(@NonNull String abi, @NonNull String fileName)
            throws IOException {
        List<CompileCommand> compileCommands = getCompileCommands(abi);
        String flags = null;
        for (CompileCommand compileCommand : compileCommands) {
            if (compileCommand.command == null
                    || compileCommand.file == null
                    || !compileCommand.file.equals(fileName)) {
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
}
