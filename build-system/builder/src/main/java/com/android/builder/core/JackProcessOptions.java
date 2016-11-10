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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.ApiVersion;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Options for configuring Jack compilation. It contains all necessary information to configure Jack
 * toolchain (both Jack and Jill). See individual options for more details.
 */
public class JackProcessOptions {

    /** Tool used for processing in the Jack toolchain. */
    public enum ProcessingTool {
        JACK,
        JILL
    }

    // Class name of the code coverage plugin.
    public static final String COVERAGE_PLUGIN_NAME = "com.android.jack.coverage.CodeCoverage";

    public static final ImmutableMap<String, String> DEFAULT_CONFIG =
            ImmutableMap.<String, String>builder()
                    .put("jack.import.type.policy", "keep-first")
                    .put("jack.import.resource.policy", "keep-first")
                    .put("jack.dex.optimize", "true") // due to b.android.com/82031
                    .put("sched.runner.thread.kind", "fixed")
                    .put("sched.runner.thread.fixed.count", Integer.toString(jackThreadsToUse()))
                    .build();

    @NonNull private final ImmutableList<File> classPaths;
    @Nullable private final File dexOutputDirectory;
    @Nullable private final File jackOutputFile;
    @NonNull private final ImmutableList<File> importFiles;
    @NonNull private final ImmutableList<File> proguardFiles;
    @Nullable private final String javaMaxHeapSize;
    @Nullable private final File mappingFile;
    @NonNull private final ApiVersion minSdkVersion;
    @NonNull private final ImmutableList<File> resourceDirectories;
    @NonNull private final ImmutableList<File> inputFiles;
    @Nullable private final File ecjOptionFile;
    @NonNull private final ImmutableList<File> jarJarRuleFiles;
    @Nullable private final String sourceCompatibility;
    @Nullable private final File incrementalDir;
    @NonNull private final ImmutableList<String> annotationProcessorNames;
    @NonNull private final ImmutableList<File> annotationProcessorClassPath;
    @Nullable private final File annotationProcessorOutputDirectory;
    @NonNull private final ImmutableMap<String, String> annotationProcessorOptions;
    @Nullable private final File coverageMetadataFile;
    @Nullable private final String encoding;
    @NonNull private final ImmutableMap<String, String> additionalParameters;
    @NonNull private final ImmutableSet<String> jackPluginNames;
    @NonNull private final ImmutableList<File> jackPluginClassPath;
    @NonNull private final ProcessingTool processingToolUsed;

    private final boolean runInProcess;
    private final boolean debugJackInternals;
    private final boolean verboseProcessing;
    private final boolean debuggable;
    private final boolean jumboMode;
    private final boolean dexOptimize;
    private final boolean generateDex;
    private final boolean multiDex;
    private final boolean minified;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(@NonNull JackProcessOptions options) {
        return new Builder(options);
    }

    /** The number of threads Jack will launch internally when processing input. */
    private static int jackThreadsToUse() {
        return (int) Math.min(4, Math.ceil(Runtime.getRuntime().availableProcessors() / 2));
    }

    /** Classpath libraries when invoking Jack/Jill. */
    @NonNull
    public ImmutableList<File> getClassPaths() {
        return classPaths;
    }

    @Nullable
    public File getDexOutputDirectory() {
        return dexOutputDirectory;
    }

    /** The JACK library output file. It contains .jayce and optionally .dex files. */
    @Nullable
    public File getJackOutputFile() {
        return jackOutputFile;
    }

    /** Import libraries that will be part of the output (JACK or DEX). */
    @NonNull
    public ImmutableList<File> getImportFiles() {
        return importFiles;
    }

    @NonNull
    public ImmutableList<File> getProguardFiles() {
        return proguardFiles;
    }

    @Nullable
    public String getJavaMaxHeapSize() {
        return javaMaxHeapSize;
    }

    @Nullable
    public File getMappingFile() {
        return mappingFile;
    }

    /** Minimum SDK version that the generated output should support. */
    @NonNull
    public ApiVersion getMinSdkVersion() {
        return minSdkVersion;
    }

    @NonNull
    public ImmutableList<File> getResourceDirectories() {
        return resourceDirectories;
    }

    /** Source files to compile. */
    @NonNull
    public ImmutableList<File> getInputFiles() {
        return inputFiles;
    }

    @Nullable
    public File getEcjOptionFile() {
        return ecjOptionFile;
    }

    @NonNull
    public ImmutableList<File> getJarJarRuleFiles() {
        return jarJarRuleFiles;
    }

    @Nullable
    public String getSourceCompatibility() {
        return sourceCompatibility;
    }

    @Nullable
    public File getIncrementalDir() {
        return incrementalDir;
    }

    @NonNull
    public ImmutableList<String> getAnnotationProcessorNames() {
        return annotationProcessorNames;
    }

    @NonNull
    public ImmutableList<File> getAnnotationProcessorClassPath() {
        return annotationProcessorClassPath;
    }

    @Nullable
    public File getAnnotationProcessorOutputDirectory() {
        return annotationProcessorOutputDirectory;
    }

    @NonNull
    public ImmutableMap<String, String> getAnnotationProcessorOptions() {
        return annotationProcessorOptions;
    }

    @Nullable
    public File getCoverageMetadataFile() {
        return coverageMetadataFile;
    }

    @Nullable
    public String getEncoding() {
        return encoding;
    }

    @NonNull
    public Map<String, String> getAdditionalParameters() {
        return additionalParameters;
    }

    @NonNull
    public ImmutableSet<String> getJackPluginNames() {
        return jackPluginNames;
    }

    @NonNull
    public ImmutableList<File> getJackPluginClassPath() {
        return jackPluginClassPath;
    }

    @NonNull
    public ProcessingTool getProcessingToolUsed() {
        return processingToolUsed;
    }

    public boolean isRunInProcess() {
        return runInProcess;
    }

    /** This will enable the Jack internal logging, if debugging Jack is necessary. */
    public boolean isDebugJackInternals() {
        return debugJackInternals;
    }

    /** Processing of the inputs will be more verbose. */
    public boolean isVerboseProcessing() {
        return verboseProcessing;
    }

    /** Produced output will contain debuggin info, such as source file, lines, variables etc. */
    public boolean isDebuggable() {
        return debuggable;
    }

    public boolean isJumboMode() {
        return jumboMode;
    }

    public boolean isDexOptimize() {
        return dexOptimize;
    }

    /** If the DEX should be generated. Sometime we might want to generate only JACK file. */
    public boolean isGenerateDex() {
        return generateDex;
    }

    public boolean isMultiDex() {
        return multiDex;
    }

    public boolean isMinified() {
        return minified;
    }

    private JackProcessOptions(
            @NonNull List<File> classPaths,
            @Nullable File dexOutputDirectory,
            @Nullable File jackOutputFile,
            @NonNull List<File> importFiles,
            @NonNull List<File> proguardFiles,
            @Nullable String javaMaxHeapSize,
            @Nullable File mappingFile,
            @NonNull ApiVersion minSdkVersion,
            @NonNull List<File> resourceDirectories,
            @NonNull List<File> inputFiles,
            @Nullable File ecjOptionFile,
            @NonNull List<File> jarJarRuleFiles,
            @Nullable String sourceCompatibility,
            @Nullable File incrementalDir,
            @NonNull List<String> annotationProcessorNames,
            @NonNull List<File> annotationProcessorClassPath,
            @Nullable File annotationProcessorOutputDirectory,
            @NonNull Map<String, String> annotationProcessorOptions,
            @Nullable File coverageMetadataFile,
            @Nullable String encoding,
            @NonNull Map<String, String> additionalParameters,
            @NonNull Set<String> jackPluginNames,
            @NonNull List<File> jackPluginClassPath,
            @NonNull ProcessingTool processingToolUsed,
            boolean runInProcess,
            boolean debugJackInternals,
            boolean verboseProcessing,
            boolean debuggable,
            boolean jumboMode,
            boolean dexOptimize,
            boolean generateDex,
            boolean multiDex,
            boolean minified) {
        this.classPaths = ImmutableList.copyOf(classPaths);
        this.dexOutputDirectory = dexOutputDirectory;
        this.jackOutputFile = jackOutputFile;
        this.importFiles = ImmutableList.copyOf(importFiles);
        this.proguardFiles = ImmutableList.copyOf(proguardFiles);
        this.javaMaxHeapSize = javaMaxHeapSize;
        this.mappingFile = mappingFile;
        this.minSdkVersion = minSdkVersion;
        this.resourceDirectories = ImmutableList.copyOf(resourceDirectories);
        this.inputFiles = ImmutableList.copyOf(inputFiles);
        this.ecjOptionFile = ecjOptionFile;
        this.jarJarRuleFiles = ImmutableList.copyOf(jarJarRuleFiles);
        this.sourceCompatibility = sourceCompatibility;
        this.incrementalDir = incrementalDir;
        this.annotationProcessorNames = ImmutableList.copyOf(annotationProcessorNames);
        this.annotationProcessorClassPath = ImmutableList.copyOf(annotationProcessorClassPath);
        this.annotationProcessorOutputDirectory = annotationProcessorOutputDirectory;
        this.annotationProcessorOptions = ImmutableMap.copyOf(annotationProcessorOptions);
        this.coverageMetadataFile = coverageMetadataFile;
        this.encoding = encoding;
        this.additionalParameters = ImmutableMap.copyOf(additionalParameters);
        this.jackPluginNames = ImmutableSet.copyOf(jackPluginNames);
        this.jackPluginClassPath = ImmutableList.copyOf(jackPluginClassPath);
        this.processingToolUsed = processingToolUsed;
        this.runInProcess = runInProcess;
        this.debugJackInternals = debugJackInternals;
        this.verboseProcessing = verboseProcessing;
        this.debuggable = debuggable;
        this.jumboMode = jumboMode;
        this.dexOptimize = dexOptimize;
        this.generateDex = generateDex;
        this.multiDex = multiDex;
        this.minified = minified;
    }

    public static class Builder {
        @NonNull private List<File> classPaths = ImmutableList.of();
        @Nullable private File dexOutputDirectory;
        @Nullable private File jackOutputFile;
        @NonNull private List<File> importFiles = ImmutableList.of();
        @NonNull private List<File> proguardFiles = ImmutableList.of();
        @Nullable private String javaMaxHeapSize;
        @Nullable private File mappingFile;
        @Nullable private ApiVersion minSdkVersion;
        @NonNull private List<File> resourceDirectories = ImmutableList.of();
        @NonNull private List<File> inputFiles = ImmutableList.of();
        @Nullable private File ecjOptionFile;
        @NonNull private List<File> jarJarRuleFiles = ImmutableList.of();
        @Nullable private String sourceCompatibility;
        @Nullable private File incrementalDir;
        @NonNull private List<String> annotationProcessorNames = ImmutableList.of();
        @NonNull private List<File> annotationProcessorClassPath = ImmutableList.of();
        @Nullable private File annotationProcessorOutputDirectory;
        @NonNull private Map<String, String> annotationProcessorOptions = ImmutableMap.of();
        @Nullable private File coverageMetadataFile;
        @Nullable private String encoding;
        @NonNull private Map<String, String> additionalParameters = ImmutableMap.of();
        @NonNull private Set<String> jackPluginNames = ImmutableSet.of();
        @NonNull private List<File> jackPluginClassPath = ImmutableList.of();
        @NonNull private ProcessingTool processingToolUsed = ProcessingTool.JACK;
        private boolean runInProcess = true;
        private boolean debugJackInternals = false;
        private boolean verboseProcessing = false;
        private boolean debuggable = false;
        private boolean jumboMode = true;
        private boolean dexOptimize = true;
        private boolean multiDex = false;
        private boolean generateDex = false;
        private boolean minified = false;

        private Builder() {
            // empty constructor
        }

        private Builder(@NonNull JackProcessOptions options) {
            this.debugJackInternals = options.debugJackInternals;
            this.verboseProcessing = options.verboseProcessing;
            this.debuggable = options.debuggable;
            this.classPaths = options.classPaths;
            this.dexOutputDirectory = options.dexOutputDirectory;
            this.jackOutputFile = options.jackOutputFile;
            this.importFiles = options.importFiles;
            this.proguardFiles = options.proguardFiles;
            this.minified = options.minified;
            this.javaMaxHeapSize = options.javaMaxHeapSize;
            this.jumboMode = options.jumboMode;
            this.dexOptimize = options.dexOptimize;
            this.mappingFile = options.mappingFile;
            this.multiDex = options.multiDex;
            this.minSdkVersion = options.minSdkVersion;
            this.resourceDirectories = options.resourceDirectories;
            this.inputFiles = options.inputFiles;
            this.ecjOptionFile = options.ecjOptionFile;
            this.jarJarRuleFiles = options.jarJarRuleFiles;
            this.sourceCompatibility = options.sourceCompatibility;
            this.incrementalDir = options.incrementalDir;
            this.annotationProcessorNames = options.annotationProcessorNames;
            this.annotationProcessorClassPath = options.annotationProcessorClassPath;
            this.annotationProcessorOutputDirectory = options.annotationProcessorOutputDirectory;
            this.annotationProcessorOptions = options.annotationProcessorOptions;
            this.coverageMetadataFile = options.coverageMetadataFile;
            this.encoding = options.encoding;
            this.additionalParameters = options.additionalParameters;
            this.jackPluginNames = options.jackPluginNames;
            this.jackPluginClassPath = options.jackPluginClassPath;
            this.processingToolUsed = options.processingToolUsed;
            this.runInProcess = options.runInProcess;
            this.generateDex = options.generateDex;
        }

        public Builder setClassPaths(@NonNull List<File> classPaths) {
            this.classPaths = classPaths;
            return this;
        }

        public Builder setDexOutputDirectory(@Nullable File dexOutputDirectory) {
            this.dexOutputDirectory = dexOutputDirectory;
            return this;
        }

        public Builder setJackOutputFile(@Nullable File jackOutputFile) {
            this.jackOutputFile = jackOutputFile;
            return this;
        }

        public Builder setImportFiles(@NonNull List<File> importFiles) {
            this.importFiles = importFiles;
            return this;
        }

        public Builder setProguardFiles(@NonNull List<File> proguardFiles) {
            this.proguardFiles = proguardFiles;
            return this;
        }

        public Builder setJavaMaxHeapSize(@Nullable String javaMaxHeapSize) {
            this.javaMaxHeapSize = javaMaxHeapSize;
            return this;
        }

        public Builder setMappingFile(@NonNull File mappingFile) {
            this.mappingFile = mappingFile;
            return this;
        }

        public Builder setMinSdkVersion(@NonNull ApiVersion minSdkVersion) {
            this.minSdkVersion = minSdkVersion;
            return this;
        }

        public Builder setResourceDirectories(@NonNull List<File> resourceDirectories) {
            this.resourceDirectories = resourceDirectories;
            return this;
        }

        public Builder setInputFiles(@NonNull List<File> inputFiles) {
            this.inputFiles = inputFiles;
            return this;
        }

        public Builder setEcjOptionFile(@NonNull File ecjOptionFile) {
            this.ecjOptionFile = ecjOptionFile;
            return this;
        }

        public Builder setJarJarRuleFiles(@NonNull List<File> jarJarRuleFiles) {
            this.jarJarRuleFiles = jarJarRuleFiles;
            return this;
        }

        public Builder setSourceCompatibility(@NonNull String sourceCompatibility) {
            this.sourceCompatibility = sourceCompatibility;
            return this;
        }

        public Builder setIncrementalDir(@Nullable File incrementalDir) {
            this.incrementalDir = incrementalDir;
            return this;
        }

        public Builder setAnnotationProcessorNames(@NonNull List<String> annotationProcessorNames) {
            this.annotationProcessorNames = annotationProcessorNames;
            return this;
        }

        public Builder setAnnotationProcessorClassPath(
                @NonNull List<File> annotationProcessorClassPath) {
            this.annotationProcessorClassPath = annotationProcessorClassPath;
            return this;
        }

        public Builder setAnnotationProcessorOutputDirectory(
                @NonNull File annotationProcessorOutputDirectory) {
            this.annotationProcessorOutputDirectory = annotationProcessorOutputDirectory;
            return this;
        }

        public Builder setAnnotationProcessorOptions(
                @NonNull Map<String, String> annotationProcessorOptions) {
            this.annotationProcessorOptions = annotationProcessorOptions;
            return this;
        }

        public Builder setCoverageMetadataFile(@NonNull File coverageMetadataFile) {
            this.coverageMetadataFile = coverageMetadataFile;
            return this;
        }

        public Builder setEncoding(@NonNull String encoding) {
            this.encoding = encoding;
            return this;
        }

        public Builder setAdditionalParameters(@NonNull Map<String, String> additionalParameters) {
            this.additionalParameters = additionalParameters;
            return this;
        }

        public Builder setJackPluginNames(@NonNull Set<String> jackPluginNames) {
            this.jackPluginNames = jackPluginNames;
            return this;
        }

        public Builder setJackPluginClassPath(@NonNull List<File> jackPluginClassPath) {
            this.jackPluginClassPath = jackPluginClassPath;
            return this;
        }

        public Builder setProcessingToolUsed(@NonNull ProcessingTool processingToolUsed) {
            this.processingToolUsed = processingToolUsed;
            return this;
        }

        public Builder setRunInProcess(boolean runInProcess) {
            this.runInProcess = runInProcess;
            return this;
        }

        public Builder setDebugJackInternals(boolean debugJackInternals) {
            this.debugJackInternals = debugJackInternals;
            return this;
        }

        public Builder setVerboseProcessing(boolean verboseProcessing) {
            this.verboseProcessing = verboseProcessing;
            return this;
        }

        public Builder setDebuggable(boolean debuggable) {
            this.debuggable = debuggable;
            return this;
        }

        public Builder setJumboMode(boolean jumboMode) {
            this.jumboMode = jumboMode;
            return this;
        }

        public Builder setDexOptimize(boolean dexOptimize) {
            this.dexOptimize = dexOptimize;
            return this;
        }

        public Builder setMultiDex(boolean multiDex) {
            this.multiDex = multiDex;
            return this;
        }

        public Builder setGenerateDex(boolean generateDex) {
            this.generateDex = generateDex;
            return this;
        }

        public Builder setMinified(boolean minified) {
            this.minified = minified;
            return this;
        }

        public JackProcessOptions build() {
            Preconditions.checkNotNull(classPaths);
            Preconditions.checkNotNull(importFiles);
            Preconditions.checkNotNull(proguardFiles);
            Preconditions.checkNotNull(minSdkVersion);
            Preconditions.checkNotNull(resourceDirectories);
            Preconditions.checkNotNull(inputFiles);
            Preconditions.checkNotNull(jarJarRuleFiles);
            Preconditions.checkNotNull(annotationProcessorNames);
            Preconditions.checkNotNull(annotationProcessorClassPath);
            Preconditions.checkNotNull(annotationProcessorOptions);
            Preconditions.checkNotNull(additionalParameters);
            Preconditions.checkNotNull(jackPluginNames);
            Preconditions.checkNotNull(jackPluginClassPath);
            Preconditions.checkNotNull(processingToolUsed);
            return new JackProcessOptions(
                    classPaths,
                    dexOutputDirectory,
                    jackOutputFile,
                    importFiles,
                    proguardFiles,
                    javaMaxHeapSize,
                    mappingFile,
                    minSdkVersion,
                    resourceDirectories,
                    inputFiles,
                    ecjOptionFile,
                    jarJarRuleFiles,
                    sourceCompatibility,
                    incrementalDir,
                    annotationProcessorNames,
                    annotationProcessorClassPath,
                    annotationProcessorOutputDirectory,
                    annotationProcessorOptions,
                    coverageMetadataFile,
                    encoding,
                    additionalParameters,
                    jackPluginNames,
                    jackPluginClassPath,
                    processingToolUsed,
                    runInProcess,
                    debugJackInternals,
                    verboseProcessing,
                    debuggable,
                    jumboMode,
                    dexOptimize,
                    generateDex,
                    multiDex,
                    minified);
        }
    }
}
