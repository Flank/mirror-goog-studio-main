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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.common.utils.JacocoAgent;
import com.android.build.gradle.integration.performance.BenchmarkRecorder;
import com.android.prefs.AndroidLocation;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;

/**
 * Common flags shared by {@link BuildModel} and {@link RunGradleTasks}.
 *
 * @param <T> The concrete implementing class.
 */
@SuppressWarnings("unchecked") // Returning this as <T> in most methods.
public abstract class BaseGradleExecutor<T extends BaseGradleExecutor> {

    @NonNull
    final ProjectConnection projectConnection;

    @Nullable final BenchmarkRecorder benchmarkRecorder;

    @Nullable private final String heapSize;

    @NonNull final List<String> arguments = Lists.newArrayList();

    @NonNull final Path profilesDirectory;

    @NonNull final Path projectDirectory;
    protected boolean offline = true;

    @Nullable Logging.BenchmarkMode benchmarkMode;

    boolean enableInfoLogging;

    BaseGradleExecutor(
            @NonNull ProjectConnection projectConnection,
            @NonNull Path projectDirectory,
            @NonNull Path buildDotGradleFile,
            @Nullable BenchmarkRecorder benchmarkRecorder,
            @NonNull Path profilesDirectory,
            @Nullable String heapSize) {
        this.projectDirectory = projectDirectory;
        this.benchmarkRecorder = benchmarkRecorder;
        this.enableInfoLogging = benchmarkRecorder == null;
        this.projectConnection = projectConnection;
        if (!buildDotGradleFile.getFileName().toString().equals("build.gradle")) {
            arguments.add("--build-file=" + buildDotGradleFile.toString());
        }
        this.profilesDirectory = profilesDirectory;
        this.heapSize = heapSize;
    }

    /**
     * Return the default build cache location for a project.
     */
    public File getBuildCacheDir() {
        return new File(projectDirectory.toFile(), ".buildCache");
    }

    /**
     * Upload this builds detailed profile as a benchmark.
     */
    public T recordBenchmark(
            @NonNull Logging.BenchmarkMode benchmarkMode) {
        Preconditions.checkState(
                benchmarkRecorder != null,
                "BenchmarkRecorder must be set for this GradleTestProject when it is created in "
                        + "order to record a benchmark.");
        this.benchmarkMode = benchmarkMode;

        // Disable the build cache for all benchmarks, until we figure out how to measure its
        // impact.
        withProperty(AndroidGradleOptions.PROPERTY_ENABLE_BUILD_CACHE, "false");

        // Explicitly specify the aapt1, until we start recording both.
        withProperty(AndroidGradleOptions.PROPERTY_ENABLE_AAPT2, "false");

        return (T) this;
    }

    /**
     * Add additional build arguments.
     */
    public T withArguments(@NonNull List<String> arguments) {
        this.arguments.addAll(arguments);
        return (T) this;
    }

    /**
     * Add an additional build argument.
     */
    public T withArgument(String argument) {
        arguments.add(argument);
        return (T) this;
    }

    public T withProperty(@NonNull String propertyName, @NonNull String value) {
        withArgument("-P" + propertyName + "=" + value);
        return (T) this;
    }

    public T withProperty(@NonNull String propertyName, int value) {
        withArgument("-P" + propertyName + "=" + value);
        return (T) this;
    }

    /**
     * Whether --info is passed or not. Default is true.
     */
    public T withEnableInfoLogging(boolean enableInfoLogging) {
        this.enableInfoLogging = enableInfoLogging;
        return (T) this;
    }

    public T withLocalAndroidSdkHome() throws IOException {
        Path localAndroidSdkHome = projectDirectory.resolve("android_sdk_home");
        Files.createDirectories(localAndroidSdkHome);
        withArgument(
                String.format(
                        "-D%s=%s",
                        AndroidLocation.EnvVar.ANDROID_SDK_HOME.getName(),
                        localAndroidSdkHome.toAbsolutePath()));

        return (T) this;
    }

    public T withoutOfflineFlag() {
        this.offline = false;
        return (T) this;
    }

    protected List<String> getOfflineFlag() {
        if (offline) {
            return ImmutableList.of("--offline");
        } else {
            return Collections.emptyList();
        }
    }

    protected void setJvmArguments(@NonNull LongRunningOperation launcher) {
        List<String> jvmArguments = new ArrayList<>();

        if (!Strings.isNullOrEmpty(heapSize)) {
            jvmArguments.add("-Xmx" + heapSize);
        }

        jvmArguments.add("-XX:MaxPermSize=1024m");

        String debugIntegrationTest = System.getenv("DEBUG_INNER_TEST");
        if (!Strings.isNullOrEmpty(debugIntegrationTest)) {
            jvmArguments.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5006");
        }

        if (JacocoAgent.isJacocoEnabled()) {
            jvmArguments.add(JacocoAgent.getJvmArg());
        }

        launcher.setJvmArguments(Iterables.toArray(jvmArguments, String.class));
    }
}
