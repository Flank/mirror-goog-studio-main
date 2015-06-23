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

import static com.android.build.gradle.ndk.internal.BinaryToolHelper.getCCompiler;
import static com.android.build.gradle.ndk.internal.BinaryToolHelper.getCppCompiler;

import com.android.build.gradle.internal.NdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.managed.NdkConfig;
import com.android.build.gradle.model.AndroidComponentModelSourceSet;
import com.android.build.gradle.tasks.GdbSetupTask;
import com.android.build.gradle.tasks.StripDebugSymbolTask;
import com.android.builder.core.BuilderConstants;
import com.android.utils.StringHelper;

import org.gradle.api.Action;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.c.CSourceSet;
import org.gradle.language.c.tasks.CCompile;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.model.ModelMap;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.platform.base.BinarySpec;

import java.io.File;

/**
 * Configure settings used by the native binaries.
 */
public class NdkConfiguration {

    public static void configureProperties(
            NativeLibrarySpec library,
            final AndroidComponentModelSourceSet sources,
            final File buildDir,
            final NdkConfig ndkConfig,
            final NdkHandler ndkHandler) {
        for (Abi abi : ndkHandler.getSupportedAbis()) {
            library.targetPlatform(abi.getName());
        }

        library.getBinaries()
                .withType(SharedLibraryBinarySpec.class, new Action<SharedLibraryBinarySpec>() {
                    @Override
                    public void execute(final SharedLibraryBinarySpec binary) {
                        sourceIfExist(binary, sources, "main");
                        sourceIfExist(binary, sources, binary.getFlavor().getName());
                        sourceIfExist(binary, sources, binary.getBuildType().getName());
                        sourceIfExist(binary, sources,
                                binary.getFlavor().getName()
                                        + StringHelper.capitalize(binary.getBuildType().getName()));

                        getCCompiler(binary).define("ANDROID");
                        getCppCompiler(binary).define("ANDROID");
                        getCCompiler(binary).define("ANDROID_NDK");
                        getCppCompiler(binary).define("ANDROID_NDK");

                        // TODO: Right now, we create the library in the "obj" folder, then strip
                        // the debug symbols and place the final output in the "lib" folder.  For
                        // non-debuggable build, we should just place the output in the lib folder.

                        // Set output library filename.
                        binary.setSharedLibraryFile(
                                new File(
                                        buildDir,
                                        NdkNamingScheme.getDebugLibraryDirectoryName(binary)
                                                + "/"
                                                + NdkNamingScheme.getSharedLibraryFileName(
                                                        ndkConfig.getModuleName())));

                        // Replace output directory of compile tasks.
                        binary.getTasks().withType(CCompile.class, new Action<CCompile>() {
                            @Override
                            public void execute(CCompile task) {
                                String sourceSetName = task.getObjectFileDir().getName();
                                task.setObjectFileDir(
                                        NdkNamingScheme.getObjectFilesOutputDirectory(
                                                binary,
                                                buildDir,
                                                sourceSetName));
                            }
                        });
                        binary.getTasks().withType(CppCompile.class, new Action<CppCompile>() {
                            @Override
                            public void execute(CppCompile task) {
                                String sourceSetName = task.getObjectFileDir().getName();
                                task.setObjectFileDir(
                                        NdkNamingScheme.getObjectFilesOutputDirectory(
                                                binary,
                                                buildDir,
                                                sourceSetName));
                            }
                        });

                        new DefaultNativeToolSpecification().apply(binary);

                        String sysroot = ndkHandler.getSysroot(
                                Abi.getByName(binary.getTargetPlatform().getName()));

                        getCCompiler(binary).args("--sysroot=" + sysroot);
                        getCppCompiler(binary).args("--sysroot=" + sysroot);
                        binary.getLinker().args("--sysroot=" + sysroot);
                        binary.getLinker().args("-Wl,--build-id");

                        if (ndkConfig.getRenderscriptNdkMode()) {
                            getCCompiler(binary).args("-I" + sysroot + "/usr/include/rs");
                            getCCompiler(binary).args("-I" + sysroot + "/usr/include/rs/cpp");
                            getCppCompiler(binary).args("-I" + sysroot + "/usr/include/rs");
                            getCppCompiler(binary).args("-I" + sysroot + "/usr/include/rs/cpp");
                            binary.getLinker().args("-L" + sysroot + "/usr/lib/rs");
                        }

                        NativeToolSpecificationFactory.create(
                                ndkHandler,
                                binary.getBuildType(),
                                binary.getTargetPlatform()).apply(binary);

                        // Add flags defined in NdkConfig
                        for (String flag : ndkConfig.getCFlags()) {
                            getCCompiler(binary).args(flag);
                        }

                        for (String flag : ndkConfig.getCppFlags()) {
                            getCppCompiler(binary).args(flag);
                        }

                        for (String flag : ndkConfig.getLdFlags()) {
                            binary.getLinker().args(flag);
                        }

                        for (String ldLib : ndkConfig.getLdLibs()) {
                            binary.getLinker().args("-l" + ldLib);
                        }

                        StlNativeToolSpecification stlConfig = new StlNativeToolSpecification(
                                ndkHandler,
                                ndkConfig.getStl(),
                                binary.getTargetPlatform());
                        stlConfig.apply(binary);
                    }

                });
    }

    public static void createTasks(ModelMap<Task> tasks, SharedLibraryBinarySpec binary,
            File buildDir, NdkConfig ndkConfig, NdkHandler ndkHandler) {
        StlConfiguration.createStlCopyTask(ndkHandler, ndkConfig.getStl(), tasks, buildDir, binary);

        if (binary.getBuildType().getName().equals(BuilderConstants.DEBUG)) {
            // TODO: Use AndroidTaskRegistry and scopes to create tasks in experimental plugin.
            setupNdkGdbDebug(tasks, binary, buildDir, ndkConfig, ndkHandler);
        }
        createStripDebugTask(tasks, binary, buildDir, ndkHandler);
    }

    /**
     * Add the sourceSet with the specified name to the binary if such sourceSet is defined.
     */
    private static void sourceIfExist(
            BinarySpec binary,
            AndroidComponentModelSourceSet projectSourceSet,
            final String sourceSetName) {
        FunctionalSourceSet sourceSet = projectSourceSet.findByName(sourceSetName);
        if (sourceSet != null) {
            final LanguageSourceSet jni = sourceSet.getByName("jni");
            binary.sources(new Action<PolymorphicDomainObjectContainer<LanguageSourceSet>>() {
                @Override
                public void execute(
                        PolymorphicDomainObjectContainer<LanguageSourceSet> languageSourceSets) {
                    // Hardcode the acceptable extension until we find a suitable DSL for user to
                    // modify.
                    languageSourceSets.create(
                            sourceSetName + "C",
                            CSourceSet.class,
                            new Action<LanguageSourceSet>() {
                                @Override
                                public void execute(LanguageSourceSet source) {
                                    source.getSource().setSrcDirs(jni.getSource().getSrcDirs());
                                    source.getSource().include("**/*.c");
                                }
                            });
                    languageSourceSets.create(
                            sourceSetName + "Cpp",
                            CppSourceSet.class,
                            new Action<LanguageSourceSet>() {
                                @Override
                                public void execute(LanguageSourceSet source) {
                                    source.getSource().setSrcDirs(jni.getSource().getSrcDirs());
                                    source.getSource().include("**/*.C");
                                    source.getSource().include("**/*.CPP");
                                    source.getSource().include("**/*.c++");
                                    source.getSource().include("**/*.cc");
                                    source.getSource().include("**/*.cp");
                                    source.getSource().include("**/*.cpp");
                                    source.getSource().include("**/*.cxx");
                                }
                            });
                }
            });
        }
    }

    /**
     * Setup tasks to create gdb.setup and copy gdbserver for NDK debugging.
     */
    private static void setupNdkGdbDebug(
            ModelMap<Task> tasks,
            final NativeBinarySpec binary,
            final File buildDir,
            final NdkConfig ndkConfig,
            final NdkHandler handler) {
        String copyGdbServerTaskName = NdkNamingScheme.getTaskName(binary, "copy", "GdbServer");
        tasks.create(copyGdbServerTaskName, Copy.class, new Action<Copy>() {
            @Override
            public void execute(Copy task) {
                task.from(new File(handler.getPrebuiltDirectory(
                        Abi.getByName(binary.getTargetPlatform().getName())),
                        "gdbserver/gdbserver"));
                task.into(new File(buildDir, NdkNamingScheme.getOutputDirectoryName(binary)));
            }
        });
        binary.getBuildTask().dependsOn(copyGdbServerTaskName);

        String createGdbSetupTaskName = NdkNamingScheme.getTaskName(binary, "create", "Gdbsetup");
        tasks.create(createGdbSetupTaskName, GdbSetupTask.class, new Action<GdbSetupTask>() {
            @Override
            public void execute(GdbSetupTask task) {
                task.setNdkHandler(handler);
                task.setExtension(ndkConfig);
                task.setBinary(binary);
                task.setOutputDir(
                        new File(buildDir, NdkNamingScheme.getOutputDirectoryName(binary)));
            }
        });
        binary.getBuildTask().dependsOn(createGdbSetupTaskName);
    }

    private static void createStripDebugTask(
            ModelMap<Task> tasks,
            final SharedLibraryBinarySpec binary,
            final File buildDir,
            final NdkHandler handler) {

        String taskName = NdkNamingScheme.getTaskName(binary, "stripSymbols");
        tasks.create(
                taskName,
                StripDebugSymbolTask.class,
                new StripDebugSymbolTask.ConfigAction(binary, buildDir, handler));
        binary.getBuildTask().dependsOn(taskName);
    }
}
