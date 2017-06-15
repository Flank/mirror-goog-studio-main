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
import com.android.build.gradle.integration.common.utils.JacocoAgent;
import com.android.build.gradle.integration.performance.BenchmarkRecorder;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.StringOption;
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
    @NonNull private final List<String> arguments = Lists.newArrayList();
    @NonNull private final ProjectOptionsBuilder options = new ProjectOptionsBuilder();
    @NonNull final Path profilesDirectory;
    @NonNull final Path projectDirectory;
    @Nullable private final String heapSize;
    @Nullable Logging.BenchmarkMode benchmarkMode;
    @NonNull private LoggingLevel loggingLevel = LoggingLevel.INFO;
    private boolean offline = true;
    private boolean localAndroidSdkHome = false;


    BaseGradleExecutor(
            @NonNull ProjectConnection projectConnection,
            @NonNull Consumer<GradleBuildResult> lastBuildResultConsumer,
            @NonNull Path projectDirectory,
            @NonNull Path buildDotGradleFile,
            @Nullable BenchmarkRecorder benchmarkRecorder,
            @NonNull Path profilesDirectory,
            @Nullable String heapSize) {
        this.lastBuildResultConsumer = lastBuildResultConsumer;
        this.projectDirectory = projectDirectory;
        this.benchmarkRecorder = benchmarkRecorder;
        if (benchmarkRecorder != null) {
            this.loggingLevel = LoggingLevel.LIFECYCLE;
        }
        this.projectConnection = projectConnection;
        if (!buildDotGradleFile.getFileName().toString().equals("build.gradle")) {
            arguments.add("--build-file=" + buildDotGradleFile.toString());
        }
        this.profilesDirectory = profilesDirectory;
        this.heapSize = heapSize;
        with(StringOption.BUILD_CACHE_DIR, getBuildCacheDir().getAbsolutePath());
    }


    /** Return the default build cache location for a project. */
    public final File getBuildCacheDir() {
        return new File(projectDirectory.toFile(), ".buildCache");
    }

    /** Upload this builds detailed profile as a benchmark. */
    public final T recordBenchmark(@NonNull Logging.BenchmarkMode benchmarkMode) {
        Preconditions.checkState(
                benchmarkRecorder != null,
                "BenchmarkRecorder must be set for this GradleTestProject when it is created in "
                        + "order to record a benchmark.");
        this.benchmarkMode = benchmarkMode;

        return (T) this;
    }

    public final T with(@NonNull BooleanOption option, boolean value) {
        options.booleans.put(option, value);
        return (T) this;
    }

    public final T with(@NonNull OptionalBooleanOption option, boolean value) {
        options.optionalBooleans.put(option, value);
        return (T) this;
    }

    public final T with(@NonNull IntegerOption option, int value) {
        options.integers.put(option, value);
        return (T) this;
    }

    public final T with(@NonNull StringOption option, @NonNull String value) {
        options.strings.put(option, value);
        return (T) this;
    }

    @Deprecated
    @NonNull
    public T withProperty(@NonNull String propertyName, @NonNull String value) {
        withArgument("-P" + propertyName + "=" + value);
        return (T) this;
    }

    /** Add additional build arguments. */
    public final T withArguments(@NonNull List<String> arguments) {
        for (String argument : arguments) {
            withArgument(argument);
        }
        return (T) this;
    }

    /** Add an additional build argument. */
    public final T withArgument(String argument) {
        if (argument.startsWith("-Pandroid")) {
            throw new IllegalArgumentException("Use with(Option, Value) instead.");
        }
        arguments.add(argument);
        return (T) this;
    }

    public T withEnableInfoLogging(boolean enableInfoLogging) {
        return withLoggingLevel(enableInfoLogging ? LoggingLevel.INFO : LoggingLevel.LIFECYCLE);
    }

    public T withLoggingLevel(@NonNull LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
        return (T) this;
    }

    public final T withLocalAndroidSdkHome() {
        localAndroidSdkHome = true;
        return (T) this;
    }

    public final T withoutOfflineFlag() {
        this.offline = false;
        return (T) this;
    }

    protected final List<String> getArguments() throws IOException {
        List<String> arguments = new ArrayList<>();
        arguments.addAll(this.arguments);
        arguments.addAll(options.getArguments());

        if (loggingLevel.getArgument() != null) {
            arguments.add(loggingLevel.getArgument());
        }

        arguments.add("-Dfile.encoding=" + System.getProperty("file.encoding"));
        arguments.add("-Dsun.jnu.encoding=" + System.getProperty("sun.jnu.encoding"));

        // Don't search in parent folders for a settings.gradle file.
        arguments.add("--no-search-upward");

        if (offline) {
            arguments.add("--offline");
        }

        Path androidSdkHome;
        if (localAndroidSdkHome) {
            androidSdkHome = projectDirectory.getParent().resolve("android_sdk_home");
        } else {
            androidSdkHome = GradleTestProject.ANDROID_SDK_HOME.toPath();
        }

        Files.createDirectories(androidSdkHome);

        arguments.add(
                String.format(
                        "-D%s=%s",
                        "ANDROID_SDK_HOME",
                        androidSdkHome.toAbsolutePath()));

        return arguments;
    }

    protected final void setJvmArguments(@NonNull LongRunningOperation launcher) {
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
