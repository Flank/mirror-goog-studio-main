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

package com.android.build.gradle.internal.transforms;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.JackProcessOptions;
import com.android.builder.core.JackToolchain;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;

/**
 * This is an optional transform in the Jack compilation pipeline. If run, this transform will
 * consume all JACK files available in the pipeline, and produce DEX.
 *
 * <p>This transform will run if at least one of the conditions below is satisfied:
 *
 * <ul>
 *   <li>the variant is minified
 *   <li>the variant is non-multidex
 *   <li>the variant is legacy multidex
 * </ul>
 *
 * <p>In case it does not run, {@link JackPreDexTransform} and {@link JackCompileTransform} will
 * produce DEX files that are available to the packaging task.
 *
 * <p>Inputs for this transform are:
 *
 * <ul>
 *   <li>JACK files from the {@link TransformManager#SCOPE_FULL_PROJECT} scopes - it consumes these
 *   <li>from the provided scope and tested code scope it will reference (not consume) JACK files
 *       that will be added to the classpath when outputting the DEX files
 *   <li>Source JACK file which is either an incremental dir or JACK library file - secondary input
 *   <li>Proguard rules - this is secondary input
 *   <li>JarJar rules - this is secondary input
 * </ul>
 *
 * <p>Outputs are:
 *
 * <ul>
 *   <li>DEX file(s) - these are the final dex files
 *   <li>Mapping file if the code is minified - this is a secondary output
 * </ul>
 */
public class JackGenerateDexTransform extends Transform {

    private static final ILogger logger = LoggerWrapper.getLogger(JackGenerateDexTransform.class);

    @NonNull private final Supplier<BuildToolInfo> buildToolInfo;
    @NonNull private final ErrorReporter errorReporter;
    @NonNull private final JavaProcessExecutor javaProcessExecutor;

    @NonNull private final JackProcessOptions options;

    @NonNull private final FileCollection jackCompilationOutput;

    @NonNull private final FileCollection jackPluginsClassPath;

    public JackGenerateDexTransform(
            @NonNull JackProcessOptions jackProcessOptions,
            @NonNull FileCollection jackCompilationOutput,
            @NonNull Supplier<BuildToolInfo> buildToolInfo,
            @NonNull ErrorReporter errorReporter,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @NonNull FileCollection jackPluginsClassPath) {
        this.options = jackProcessOptions;
        this.buildToolInfo = buildToolInfo;
        this.errorReporter = errorReporter;
        this.javaProcessExecutor = javaProcessExecutor;
        this.jackCompilationOutput = jackCompilationOutput;
        this.jackPluginsClassPath = jackPluginsClassPath;
    }

    @NonNull
    @Override
    public String getName() {
        return "jackDexer";
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> builder = ImmutableList.builder();
        builder.addAll(
                options.getProguardFiles()
                        .stream()
                        .map(SecondaryFile::nonIncremental)
                        .collect(Collectors.toList()));
        builder.addAll(
                options.getJarJarRuleFiles()
                        .stream()
                        .map(SecondaryFile::nonIncremental)
                        .collect(Collectors.toList()));

        builder.add(
                SecondaryFile.nonIncremental(
                        new File(buildToolInfo.get().getPath(BuildToolInfo.PathId.JACK))));

        builder.addAll(
                jackCompilationOutput
                        .getFiles()
                        .stream()
                        .map(SecondaryFile::nonIncremental)
                        .collect(Collectors.toList()));

        builder.addAll(
                jackPluginsClassPath
                        .getFiles()
                        .stream()
                        .map(SecondaryFile::nonIncremental)
                        .collect(Collectors.toList()));

        return builder.build();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        if (options.getMappingFile() != null) {
            builder.add(options.getMappingFile());
        }
        return builder.build();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = Maps.newHashMap();
        params.put("javaResourcesFolder", options.getResourceDirectories());
        params.put("isDebuggable", options.isDebuggable());
        params.put("multiDexEnabled", options.isMultiDex());
        params.put("minSdkVersion", options.getMinSdkVersion().getApiString());
        params.put("javaMaxHeapSize", options.getJavaMaxHeapSize());
        params.put("sourceCompatibility", options.getSourceCompatibility());
        params.put("buildToolsRev", buildToolInfo.get().getRevision().toString());
        return params;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_JACK;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_DEX;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(
                QualifiedContent.Scope.PROVIDED_ONLY, QualifiedContent.Scope.TESTED_CODE);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(@NonNull final TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        try {
            runJack(transformInvocation);
        } catch (ProcessException | ClassNotFoundException | JackToolchain.ToolchainException e) {
            throw new TransformException(e);
        }
    }

    private void runJack(@NonNull TransformInvocation transformInvocation)
            throws ProcessException, IOException, JackToolchain.ToolchainException,
                    ClassNotFoundException {

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        checkNotNull(outputProvider);

        // use all import .jack files, create .dex
        final File outDirectory =
                outputProvider.getContentLocation(
                        "main", getOutputTypes(), getScopes(), Format.DIRECTORY);
        List<File> importFiles =
                Lists.newArrayList(TransformInputUtil.getAllFiles(transformInvocation.getInputs()));
        importFiles.addAll(jackCompilationOutput.getFiles());
        List<File> classPath =
                Lists.newArrayList(
                        TransformInputUtil.getAllFiles(transformInvocation.getReferencedInputs()));

        JackProcessOptions finalOptions =
                JackProcessOptions.builder(options)
                        .setDexOutputDirectory(outDirectory)
                        .setImportFiles(importFiles)
                        .setClassPaths(classPath)
                        .setJackPluginClassPath(Lists.newArrayList(jackPluginsClassPath.getFiles()))
                        .build();

        JackToolchain toolchain = new JackToolchain(buildToolInfo.get(), logger, errorReporter);
        toolchain.convert(finalOptions, javaProcessExecutor, finalOptions.isRunInProcess());
    }

    @Nullable
    public File getMappingFile() {
        return options.getMappingFile();
    }
}
