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

import static com.android.build.gradle.internal.cxx.cmake.MakeCmakeMessagePathsAbsoluteKt.makeCmakeMessagePathsAbsolute;
import static com.android.build.gradle.internal.cxx.configure.CmakeAndroidGradleBuildExtensionsKt.wrapCmakeListsForCompilerSettingsCaching;
import static com.android.build.gradle.internal.cxx.configure.CmakeAndroidGradleBuildExtensionsKt.writeCompilerSettingsToCache;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.errorln;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.model.CxxAbiModel;
import com.android.build.gradle.internal.cxx.model.CxxCmakeModuleModel;
import com.android.build.gradle.internal.cxx.model.CxxVariantModel;
import com.android.build.gradle.internal.ndk.Stl;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.gradle.api.InvalidUserDataException;

/**
 * CMake JSON generation logic. This is separated from the corresponding CMake task so that JSON can
 * be generated during configuration.
 */
abstract class CmakeExternalNativeJsonGenerator extends ExternalNativeJsonGenerator {
    @NonNull protected final CxxCmakeModuleModel cmake;

    CmakeExternalNativeJsonGenerator(
            @NonNull CxxVariantModel variant,
            @NonNull List<CxxAbiModel> abis,
            @NonNull GradleBuildVariant.Builder stats) {
        super(variant, abis, stats);
        this.stats.setNativeBuildSystemType(GradleNativeAndroidModule.NativeBuildSystemType.CMAKE);
        this.cmake = Objects.requireNonNull(variant.getModule().getCmake());

        // Check some basic requirements. This code executes at sync time but any call to
        // recordConfigurationError will later cause the generation of json to fail.
        File cmakelists = getMakefile();
        if (cmakelists.isDirectory()) {
            errorln(
                    "Gradle project cmake.path %s is a folder. It must be CMakeLists.txt",
                    cmakelists);
        } else if (cmakelists.isFile()) {
            String filename = cmakelists.getName();
            if (!filename.equals("CMakeLists.txt")) {
                errorln(
                        "Gradle project cmake.path specifies %s but it must be CMakeLists.txt",
                        filename);
            }
        } else {
            errorln("Gradle project cmake.path is %s but that file doesn't exist", cmakelists);
        }
    }

    /**
     * Returns the cache arguments for implemented strategy.
     *
     * @return Returns the cache arguments
     */
    @NonNull
    abstract List<String> getCacheArguments(@NonNull CxxAbiModel abi);

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    @NonNull
    public abstract String executeProcessAndGetOutput(@NonNull CxxAbiModel abi)
            throws ProcessException, IOException;

    @NonNull
    @Override
    public String executeProcess(@NonNull CxxAbiModel abi) throws ProcessException, IOException {
        String output = executeProcessAndGetOutput(abi);
        return makeCmakeMessagePathsAbsolute(output, getMakefile().getParentFile());
    }

    @Override
    void processBuildOutput(@NonNull String buildOutput, @NonNull CxxAbiModel abi) {
        if (variant.getModule().isNativeCompilerSettingsCacheEnabled()) {
            writeCompilerSettingsToCache(abi);
        }
    }

    @NonNull
    @Override
    ProcessInfoBuilder getProcessBuilder(@NonNull CxxAbiModel abi) {
        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        builder.setExecutable(cmake.getCmakeExe());
        builder.addArgs(getProcessBuilderArgs(abi));

        return builder;
    }

    /** Returns the list of arguments to be passed to process builder. */
    @VisibleForTesting
    @NonNull
    List<String> getProcessBuilderArgs(@NonNull CxxAbiModel abi) {
        List<String> processBuilderArgs = Lists.newArrayList();
        // CMake requires a folder. Trim the filename off.
        File cmakeListsFolder = getMakefile().getParentFile();
        processBuilderArgs.add(String.format("-H%s", cmakeListsFolder));
        processBuilderArgs.add(String.format("-B%s", abi.getCxxBuildFolder()));
        processBuilderArgs.addAll(getCacheArguments(abi));

        // Add user provided build arguments
        processBuilderArgs.addAll(getBuildArguments());
        if (variant.getModule().isNativeCompilerSettingsCacheEnabled()) {
            return wrapCmakeListsForCompilerSettingsCaching(abi, processBuilderArgs).getArgs();
        }
        return processBuilderArgs;
    }

    /**
     * Returns a list of default cache arguments that the implementations may use.
     *
     * @return list of default cache arguments
     */
    protected List<String> getCommonCacheArguments(@NonNull CxxAbiModel abi) {
        List<String> cacheArguments = Lists.newArrayList();
        cacheArguments.add(String.format("-DANDROID_ABI=%s", abi.getAbi().getTag()));
        cacheArguments.add(
                String.format("-DANDROID_PLATFORM=android-%s", abi.getAbiPlatformVersion()));
        cacheArguments.add(
                String.format(
                        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=%s",
                        new File(getObjFolder(), abi.getAbi().getTag())));
        cacheArguments.add(
                String.format("-DCMAKE_BUILD_TYPE=%s", isDebuggable() ? "Debug" : "Release"));
        cacheArguments.add(String.format("-DANDROID_NDK=%s", getNdkFolder()));
        if (!getcFlags().isEmpty()) {
            cacheArguments.add(
                    String.format("-DCMAKE_C_FLAGS=%s", Joiner.on(" ").join(getcFlags())));
        }

        if (!getCppFlags().isEmpty()) {
            cacheArguments.add(
                    String.format("-DCMAKE_CXX_FLAGS=%s", Joiner.on(" ").join(getCppFlags())));
        }

        return cacheArguments;
    }

    @NonNull
    @Override
    public NativeBuildSystem getNativeBuildSystem() {
        return NativeBuildSystem.CMAKE;
    }

    @NonNull
    @Override
    Map<Abi, File>
            getStlSharedObjectFiles() { // Search for ANDROID_STL build argument. Process in order / later flags take precedent.
        Stl stl = null;
        for (String argument : getBuildArguments()) {
            argument = argument.replace(" ", "");
            if (argument.startsWith("-DANDROID_STL=")) {
                String stlName = argument.split("=", 2)[1];
                stl = Stl.fromArgumentName(stlName);
                if (stl == null) {
                    errorln("Unrecognized STL in arguments: %s", stlName);
                }
            }
        }

        // TODO: Query the default from the NDK.
        // We currently assume the default to not require packaging for the default STL. This is
        // currently safe because the default for ndk-build has always been system (which doesn't
        // require packaging because it's a system library) and gnustl_static or c++_static for
        // CMake (which also doesn't require packaging).
        //
        // https://github.com/android-ndk/ndk/issues/744 wants to change the default for both to
        // c++_shared, but that can't happen until we stop assuming the default does not need to be
        // packaged.
        if (stl == null) {
            return Maps.newHashMap();
        }

        return variant.getModule().getStlSharedObjectMap().get(stl).entrySet().stream()
                .filter(e -> getAbis().contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
