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
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.tasks.factory.AbstractCompilesUtil;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.JackProcessOptions;
import com.android.builder.utils.PerformanceUtils;
import com.android.utils.ILogger;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.LogLevel;

/**
 * Utility methods for the Jack toolchain. Main purpose is to analyze the variant data, and to
 * create {@link JackProcessOptions} that can be used to setup the Jack-related transforms.
 */
public class JackOptionsUtils {
    private static final ILogger logger = LoggerWrapper.getLogger(JackOptionsUtils.class);

    /**
     * This is the configuration data necessary when pre-dexing the libraries using Jack. It is
     * extracted from scope data for the processes variant.
     *
     * @param scope data about the specific variant that we would like to process
     * @return options that can be used when pre-dexing using Jack
     */
    @NonNull
    public static JackProcessOptions forPreDexing(@NonNull VariantScope scope) {
        GlobalScope globalScope = scope.getGlobalScope();

        Project project = globalScope.getProject();
        JackProcessOptions.Builder builder =
                JackProcessOptions.builder()
                        .setDebugJackInternals(project.getLogger().isEnabled(LogLevel.DEBUG))
                        .setVerboseProcessing(project.getLogger().isEnabled(LogLevel.INFO));

        AndroidConfig androidConfig = globalScope.getExtension();
        builder.setJavaMaxHeapSize(androidConfig.getDexOptions().getJavaMaxHeapSize())
                .setJumboMode(androidConfig.getDexOptions().getJumboMode())
                .setDebuggable(scope.getVariantConfiguration().getBuildType().isDebuggable())
                .setDexOptimize(true);

        final GradleVariantConfiguration config = scope.getVariantData().getVariantConfiguration();
        builder.setMinified(scope.useJavaCodeShrinker())
                .setMultiDex(config.isMultiDexEnabled())
                .setMinSdkVersion(config.getMinSdkVersion());

        /* Dex generation only for non-minified, native multidex, w/o JarJar. */
        builder.setGenerateDex(
                !scope.useJavaCodeShrinker()
                        && config.isMultiDexEnabled()
                        && !DefaultApiVersion.isLegacyMultidex(config.getMinSdkVersion())
                        && config.getJarJarRuleFiles().isEmpty());

        //noinspection ConstantConditions - there is a default value for jackInProcess
        builder.setRunInProcess(
                isInProcess(
                        scope.getVariantConfiguration().getJackOptions().isJackInProcess(),
                        false));

        Map<String, String> additionalParameters =
                Maps.newHashMap(JackProcessOptions.DEFAULT_CONFIG);
        additionalParameters.putAll(config.getJackOptions().getAdditionalParameters());
        builder.setAdditionalParameters(additionalParameters);

        configureJackPlugins(builder, scope);

        return builder.build();
    }

    /**
     * Processes the specified {@link VariantScope} object, and creates {@link JackProcessOptions}
     * corresponding to it. It will set all necessary options by analyzing the DSL options,
     * conditions for incremental compilation, running the annotation processor, running Jack
     * plugins, setting JarJar rules etc.
     *
     * @param scope scope containing data for a specific variant that we would like to compile
     *     sources for using Jack
     * @return options that can be used when running Jack
     */
    public static JackProcessOptions forSourceCompilation(@NonNull VariantScope scope) {
        JackProcessOptions.Builder builder = JackProcessOptions.builder(forPreDexing(scope));

        final GradleVariantConfiguration config = scope.getVariantData().getVariantConfiguration();

        /* Annotation processor setup */
        CoreAnnotationProcessorOptions annotationProcessorOptions =
                config.getJavaCompileOptions().getAnnotationProcessorOptions();
        builder.setAnnotationProcessorNames(annotationProcessorOptions.getClassNames())
                .setAnnotationProcessorOptions(annotationProcessorOptions.getArguments())
                .setAnnotationProcessorOutputDirectory(scope.getAnnotationProcessorOutputDir())
                .setEcjOptionFile(scope.getJackEcjOptionsFile());

        /* Resources dir setup. */
        builder.setResourceDirectories(ImmutableList.of(scope.getJavaResourcesDestinationDir()));

        /* Set compile jackOptions. */
        AndroidConfig androidConfig = scope.getGlobalScope().getExtension();
        CompileOptions compileOptions = androidConfig.getCompileOptions();
        AbstractCompilesUtil.setDefaultJavaVersion(
                compileOptions,
                androidConfig.getCompileSdkVersion(),
                VariantScope.Java8LangSupport.JACK);
        builder.setSourceCompatibility(compileOptions.getSourceCompatibility().toString())
                .setEncoding(compileOptions.getEncoding());

        /* Incremental setup - disabled for: test coverage. */
        Project project = scope.getGlobalScope().getProject();
        Configuration annotationConfig =
                scope.getVariantDependencies().getAnnotationProcessorConfiguration();
        boolean incremental =
                AbstractCompilesUtil.isIncremental(
                                project, scope, compileOptions, annotationConfig, logger);
        if (incremental) {
            builder.setIncrementalDir(getJackIncrementalDir(scope));
        }

        /* Test coverage setup, don't run it for the test variants */
        if (config.isTestCoverageEnabled() && !config.getType().isForTesting()) {
            builder.setCoverageMetadataFile(scope.getJackCoverageMetadataFile());
        }

        builder.setJackOutputFile(scope.getJackClassesZip());

        return builder.build();
    }

    /**
     * Configuration that can be used to set up the dex generation with Jack. It contains all
     * necessary configuration data for the final (optional step) in the Jack pipeline.
     *
     * @param scope scope associated with the variant we are currently processing
     * @return options for configuring Jack
     */
    public static JackProcessOptions forDexGeneration(@NonNull VariantScope scope) {
        JackProcessOptions preDexOptions = forPreDexing(scope);

        JackProcessOptions.Builder builder = JackProcessOptions.builder(preDexOptions);

        final GradleVariantConfiguration config = scope.getVariantData().getVariantConfiguration();
        Project project = scope.getGlobalScope().getProject();
        /* Minification setup. */
        if (scope.useJavaCodeShrinker()) {
            // since all the output use the same resources, we can use the first output
            // to query for a proguard file.
            File sdkDir = scope.getGlobalScope().getSdkHandler().getAndCheckSdkFolder();
            checkNotNull(sdkDir);
            File defaultProguardFile =
                    ProguardFiles.getDefaultProguardFile(
                            TaskManager.DEFAULT_PROGUARD_CONFIG_FILE, project);

            Set<File> proguardFiles =
                    config.getProguardFiles(ImmutableList.of(defaultProguardFile));
            File proguardResFile = scope.getProcessAndroidResourcesProguardOutputFile();
            proguardFiles.add(proguardResFile);
            // for tested app, we only care about their aapt config since the base
            // configs are the same files anyway.
            if (scope.getTestedVariantData() != null) {
                proguardResFile =
                        scope.getTestedVariantData()
                                .getScope()
                                .getProcessAndroidResourcesProguardOutputFile();
                proguardFiles.add(proguardResFile);
            }
            builder.setProguardFiles(Lists.newArrayList(proguardFiles))
                    .setMappingFile(new File(scope.getProguardOutputFolder(), "mapping.txt"));
        }

        /* Set JarJar rules */
        List<File> jarJarRuleFiles = new ArrayList<>(config.getJarJarRuleFiles().size());
        for (File file : config.getJarJarRuleFiles()) {
            jarJarRuleFiles.add(project.file(file));
        }
        builder.setJarJarRuleFiles(jarJarRuleFiles);

        Map<String, String> additionalParameters = Maps.newHashMap();
        additionalParameters.put("jack.android.api-level.check", "false");
        additionalParameters.putAll(preDexOptions.getAdditionalParameters());
        builder.setAdditionalParameters(additionalParameters);

        builder.setJackOutputFile(null).setIncrementalDir(null);

        return builder.build();
    }

    /** Warns user if non-optimized dex is specified in the jackOptions, and checks heap size. */
    public static void executeJackChecks(@NonNull CoreJackOptions jackOptions) {
        if (ImmutableSet.of("false", "no", "off", "0")
                .contains(jackOptions.getAdditionalParameters().get("jack.dex.optimize"))) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
        }

        isInProcess(Boolean.TRUE.equals(jackOptions.isJackInProcess()), true);
    }

    private static void configureJackPlugins(
            @NonNull JackProcessOptions.Builder builder, @NonNull VariantScope scope) {
        Set<String> pluginNames =
                Sets.newHashSet(
                        scope.getVariantData()
                                .getVariantConfiguration()
                                .getJackOptions()
                                .getPluginNames());
        builder.setJackPluginNames(pluginNames);
    }

    /** Gets the Jack incremental directory for the specified variant in the scope. */
    @NonNull
    private static File getJackIncrementalDir(@NonNull VariantScope scope) {
        String taskName =
                StringHelper.combineAsCamelCase(
                        ImmutableList.of("jackSources", scope.getFullVariantName()));
        return scope.getIncrementalDir(taskName);
    }

    /** Checks if we are able to run Jack in process. Optionally logs warning if we are unable. */
    private static boolean isInProcess(boolean tryInProcess, boolean logWarning) {
        if (!tryInProcess) {
            // no need to check heap size, we don't want to run in process
            return false;
        }
        final long DEFAULT_SUGGESTED_HEAP_SIZE = 1536 * 1024 * 1024; // 1.5 GiB
        long maxMemory = PerformanceUtils.getUserDefinedHeapSize();

        if (DEFAULT_SUGGESTED_HEAP_SIZE > maxMemory) {
            if (logWarning) {
                logger.warning(
                        "\nA larger heap for the Gradle daemon is required for running "
                                + "the Jack compiler in-process.\n\n"
                                + "It currently has %1$d MB.\n"
                                + "For faster builds, increase the maximum heap size for the "
                                + "Gradle daemon to at least %2$s MB.\n"
                                + "To do this set org.gradle.jvmargs=-Xmx%2$sM in the "
                                + "project gradle.properties.\n"
                                + "For more information see "
                                + "https://docs.gradle.org"
                                + "/current/userguide/build_environment.html\n",
                        maxMemory / (1024 * 1024),
                        // Add -1 and + 1 to round up the division
                        ((DEFAULT_SUGGESTED_HEAP_SIZE - 1) / (1024 * 1024)) + 1);
            }
            return false;
        } else {
            return true;
        }
    }

    private JackOptionsUtils() {
        // empty constructor
    }
}
