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
import com.android.build.gradle.options.BooleanOption;
import com.android.prefs.AndroidLocation;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;

/**
 * Common flags shared by {@link BuildModel} and {@link RunGradleTasks}.
 *
 * @param <T> The concrete implementing class.
 */
@SuppressWarnings("unchecked") // Returning this as <T> in most methods.
public abstract class BaseGradleExecutor<T extends BaseGradleExecutor> {

    private static final boolean VERBOSE =
            !Strings.isNullOrEmpty(System.getenv().get("CUSTOM_TEST_VERBOSE"));

    @NonNull
    final ProjectConnection projectConnection;
    @NonNull final Consumer<GradleBuildResult> lastBuildResultConsumer;
    @Nullable final BenchmarkRecorder benchmarkRecorder;
    @NonNull final List<String> arguments = Lists.newArrayList();
    @NonNull final Path profilesDirectory;
    @NonNull final Path projectDirectory;
    @Nullable private final String heapSize;
    @Nullable Logging.BenchmarkMode benchmarkMode;
    boolean enableInfoLogging;
    private boolean offline = true;
    private boolean localAndroidSdkHome = false;
    private boolean enablePreDexBuildCache = true;
    private boolean enableAaptV2 = true;

    BaseGradleExecutor(
            @NonNull ProjectConnection projectConnection,
            @NonNull Consumer<GradleBuildResult> lastBuildResultConsumer,
            @NonNull Path projectDirectory,
            @NonNull Path buildDotGradleFile,
            @Nullable BenchmarkRecorder benchmarkRecorder,
            @NonNull Path profilesDirectory,
            boolean dependencyResolutionAtExecution,
            @Nullable String heapSize) {
        this.lastBuildResultConsumer = lastBuildResultConsumer;
        this.projectDirectory = projectDirectory;
        this.benchmarkRecorder = benchmarkRecorder;
        this.enableInfoLogging = benchmarkRecorder == null;
        this.projectConnection = projectConnection;
        if (!buildDotGradleFile.getFileName().toString().equals("build.gradle")) {
            arguments.add("--build-file=" + buildDotGradleFile.toString());
        }
        arguments.add(
                "-P"
                        + BooleanOption.ENABLE_IMPROVED_DEPENDENCY_RESOLUTION.getPropertyName()
                        + "="
                        + dependencyResolutionAtExecution);
        this.profilesDirectory = profilesDirectory;
        this.heapSize = heapSize;
    }

    private static String propertyArg(@NonNull String name, @NonNull String value) {
        return "-P" + name + "=" + value;
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

        return (T) this;
    }

    public T disablePreDexBuildCache() {
        enablePreDexBuildCache = false;
        return (T) this;
    }

    public T disableAaptV2() {
        enableAaptV2 = false;
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
        withArgument(propertyArg(propertyName, value));
        return (T) this;
    }

    public T withProperty(@NonNull String propertyName, int value) {
        withArgument(propertyArg(propertyName, Integer.toString(value)));
        return (T) this;
    }

    /**
     * Whether --info is passed or not. Default is true.
     */
    public T withEnableInfoLogging(boolean enableInfoLogging) {
        this.enableInfoLogging = enableInfoLogging;
        return (T) this;
    }

    public T withLocalAndroidSdkHome() {
        localAndroidSdkHome = true;

        return (T) this;
    }

    public T withoutOfflineFlag() {
        this.offline = false;
        return (T) this;
    }

    protected List<String> getCommonArguments() throws IOException {
        List<String> arguments = new ArrayList<>();

        arguments.add("-Dfile.encoding=" + System.getProperty("file.encoding"));
        arguments.add("-Dsun.jnu.encoding=" + System.getProperty("sun.jnu.encoding"));

        if (offline) {
            arguments.add("--offline");
        }

        Path androidSdkHome;
        if (localAndroidSdkHome) {
            androidSdkHome = projectDirectory.getParent().resolve("android_sdk_home");
        } else {
            androidSdkHome = GradleTestProject.ANDROID_SDK_HOME.toPath();
        }

        if (!enableAaptV2) {
            arguments.add(propertyArg(AndroidGradleOptions.PROPERTY_ENABLE_AAPT2, "false"));
        }

        if (!enablePreDexBuildCache) {
            arguments.add(
                    propertyArg(AndroidGradleOptions.PROPERTY_ENABLE_PREDEX_BUILD_CACHE, "false"));
        }

        Files.createDirectories(androidSdkHome);

        arguments.add(
                String.format(
                        "-D%s=%s",
                        AndroidLocation.EnvVar.ANDROID_SDK_HOME.getName(),
                        androidSdkHome.toAbsolutePath()));

        return arguments;
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

    protected static void setStandardOut(
            @NonNull LongRunningOperation launcher, @NonNull OutputStream stdout) {
        if (VERBOSE) {
            launcher.setStandardOutput(new TeeOutputStream(stdout, System.out));
        } else {
            launcher.setStandardOutput(stdout);
        }
    }

    protected static void setStandardError(
            @NonNull LongRunningOperation launcher, @NonNull OutputStream stderr) {
        if (VERBOSE) {
            launcher.setStandardError(new TeeOutputStream(stderr, System.err));
        } else {
            launcher.setStandardError(stderr);
        }
    }
}
