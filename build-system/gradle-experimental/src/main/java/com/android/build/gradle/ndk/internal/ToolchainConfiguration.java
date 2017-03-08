/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.ndk.internal;

import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.Toolchain;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.managed.NdkAbiOptions;
import com.android.utils.FileUtils;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.model.ModelMap;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.GccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.internal.gcc.DefaultGccPlatformToolChain;
import org.gradle.platform.base.PlatformContainer;

/**
 * Action to configure toolchain for native binaries.
 */
public class ToolchainConfiguration {

    public static void configurePlatforms(PlatformContainer platforms, NdkHandler ndkHandler) {
        for (Abi abi : ndkHandler.getSupportedAbis()) {
            NativePlatform platform = platforms.maybeCreate(abi.getName(), NativePlatform.class);

            // All we care is the name of the platform.  It doesn't matter what the
            // architecture is, but it must be set to non-x86 so that it does not match
            // the default supported platform.
            platform.architecture("ppc");
            platform.operatingSystem("linux");
        }
    }

    /**
     * Configure toolchain for a platform.
     */
    public static void configureToolchain(
            NativeToolChainRegistry toolchainRegistry,
            final String toolchainName,
            final ModelMap<NdkAbiOptions> abiConfigs,
            final NdkHandler ndkHandler) {
        final Toolchain ndkToolchain = Toolchain.getByName(toolchainName);
        toolchainRegistry.create(
                "ndk-" + toolchainName,
                (Class<? extends GccCompatibleToolChain>)
                        (toolchainName.equals("gcc") ? Gcc.class : Clang.class),
                toolchain -> {
                    // Configure each platform.
                    for (final Abi abi : ndkHandler.getSupportedAbis()) {
                        toolchain.target(
                                abi.getName(),
                                targetPlatform -> {
                                    // In NDK r12 or below, disable usage of response file as clang do not
                                    // handle file with \r\n properly.
                                    if ((ndkHandler.getRevision() == null
                                                    || ndkHandler.getRevision().getMajor() <= 12)
                                            && OperatingSystem.current().isWindows()
                                            && toolchainName.equals("clang")) {
                                        ((DefaultGccPlatformToolChain) targetPlatform)
                                                .setCanUseCommandFile(false);
                                    }

                                    if (Toolchain.GCC == ndkToolchain) {
                                        targetPlatform
                                                .getcCompiler()
                                                .setExecutable(
                                                        ndkHandler.getCCompiler(abi).getName());
                                        targetPlatform
                                                .getCppCompiler()
                                                .setExecutable(
                                                        ndkHandler.getCppCompiler(abi).getName());
                                        targetPlatform
                                                .getLinker()
                                                .setExecutable(ndkHandler.getLinker(abi).getName());
                                        targetPlatform
                                                .getAssembler()
                                                .setExecutable(
                                                        ndkHandler.getAssembler(abi).getName());
                                    }
                                    // For clang, we use the ar from the GCC toolchain.
                                    targetPlatform
                                            .getStaticLibArchiver()
                                            .setExecutable(ndkHandler.getAr(abi).getName());

                                    // By default, gradle will use -Xlinker to pass arguments to the linker.
                                    // Removing it as it prevents -sysroot from being properly set.
                                    targetPlatform
                                            .getLinker()
                                            .withArguments(
                                                    args ->
                                                            args.removeAll(
                                                                    Collections.singleton(
                                                                            "-Xlinker")));

                                    final NdkAbiOptions config = abiConfigs.get(abi.getName());
                                    String platformVersion =
                                            (config != null && config.getPlatformVersion() != null)
                                                    ? config.getPlatformVersion()
                                                    : ndkHandler.getPlatformVersion();

                                    String compilerSysroot;

                                    compilerSysroot =
                                            ndkHandler.getCompilerSysroot(abi, platformVersion);
                                    if (ndkHandler.isUseUnifiedHeaders()
                                            && platformVersion != null) {
                                        Pattern pattern = Pattern.compile("android-(\\d+)");
                                        Matcher matcher = pattern.matcher(platformVersion);
                                        if (matcher.matches()) {
                                            String api = matcher.group(1);
                                            targetPlatform
                                                    .getcCompiler()
                                                    .withArguments(
                                                            args ->
                                                                    args.add(
                                                                            "-D__ANDROID_API__="
                                                                                    + api));
                                            targetPlatform
                                                    .getCppCompiler()
                                                    .withArguments(
                                                            args ->
                                                                    args.add(
                                                                            "-D__ANDROID_API__="
                                                                                    + api));
                                        }
                                        targetPlatform
                                                .getcCompiler()
                                                .withArguments(
                                                        args -> {
                                                            args.add("-isystem");
                                                            args.add(
                                                                    FileUtils.join(
                                                                            compilerSysroot,
                                                                            "usr",
                                                                            "include",
                                                                            abi
                                                                                    .getGccExecutablePrefix()));
                                                        });
                                        targetPlatform
                                                .getCppCompiler()
                                                .withArguments(
                                                        args -> {
                                                            args.add("-isystem");
                                                            args.add(
                                                                    FileUtils.join(
                                                                            compilerSysroot,
                                                                            "usr",
                                                                            "include",
                                                                            abi
                                                                                    .getGccExecutablePrefix()));
                                                        });
                                    }

                                    targetPlatform
                                            .getcCompiler()
                                            .withArguments(
                                                    args ->
                                                            args.add(
                                                                    "--sysroot="
                                                                            + compilerSysroot));
                                    targetPlatform
                                            .getCppCompiler()
                                            .withArguments(
                                                    args ->
                                                            args.add(
                                                                    "--sysroot="
                                                                            + compilerSysroot));
                                    targetPlatform
                                            .getLinker()
                                            .withArguments(
                                                    args ->
                                                            args.add(
                                                                    "--sysroot="
                                                                            + ndkHandler
                                                                                    .getLinkerSysroot(
                                                                                            abi,
                                                                                            platformVersion)));

                                    if (config != null) {
                                        // Specify ABI specific flags.
                                        targetPlatform
                                                .getcCompiler()
                                                .withArguments(
                                                        args -> args.addAll(config.getCFlags()));
                                        targetPlatform
                                                .getCppCompiler()
                                                .withArguments(
                                                        args -> args.addAll(config.getCppFlags()));
                                        targetPlatform
                                                .getLinker()
                                                .withArguments(
                                                        args -> {
                                                            args.addAll(config.getLdFlags());

                                                            for (String lib : config.getLdLibs()) {
                                                                args.add("-l" + lib);
                                                            }
                                                        });
                                    }
                                });
                        toolchain.path(
                                ndkHandler.getCCompiler(abi).getParentFile(),
                                ndkHandler.getAr(abi).getParentFile());
                    }
                });
    }
}
