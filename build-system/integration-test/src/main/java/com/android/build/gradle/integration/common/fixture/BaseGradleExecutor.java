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
import com.android.build.gradle.integration.performance.BenchmarkMode;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Common flags shared by {@link BuildModel} and {@link RunGradleTasks}.
 *
 * @param <T> The concrete implementing class.
 */
@SuppressWarnings("unchecked") // Returning this as <T> in most methods.
public abstract class BaseGradleExecutor<T extends BaseGradleExecutor> {

    @NonNull
    final ProjectConnection mProjectConnection;

    @Nullable
    private String heapSize;

    @NonNull
    final List<String> mArguments = Lists.newArrayList();

    boolean mBenchmarkEnabled = false;

    @Nullable
    String mBenchmarkName;

    @Nullable
    BenchmarkMode mBenchmarkMode;

    boolean enableInfoLogging = true;

    BaseGradleExecutor(
            @NonNull ProjectConnection projectConnection,
            @NonNull File buildDotGradleFile,
            @Nullable String heapSize) {
        mProjectConnection = projectConnection;
        if (!buildDotGradleFile.getName().equals("build.gradle")) {
            mArguments.add("--build-file=" + buildDotGradleFile.getPath());
        }
        this.heapSize = heapSize;
    }

    /**
     * Upload this builds detailed profile as a benchmark.
     */
    public T recordBenchmark(
            @NonNull String benchmarkName,
            @NonNull BenchmarkMode benchmarkMode) {
        mBenchmarkEnabled = true;
        mBenchmarkName = benchmarkName;
        mBenchmarkMode = benchmarkMode;
        return (T) this;
    }

    /**
     * Add additional build arguments.
     */
    public T withArguments(@NonNull List<String> arguments) {
        this.mArguments.addAll(arguments);
        return (T) this;
    }

    /**
     * Add an additional build argument.
     */
    public T withArgument(String argument) {
        mArguments.add(argument);
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

    void setJvmArguments(@NonNull LongRunningOperation launcher) {
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
