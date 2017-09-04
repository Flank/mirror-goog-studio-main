/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessEnvBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** A builder to create an information necessary to run Desugar in a separate JVM process. */
public final class DesugarProcessBuilder extends ProcessEnvBuilder<DesugarProcessBuilder> {
    public static final int MIN_SUPPORTED_API_TRY_WITH_RESOURCES = 19;
    private static final String DESUGAR_MAIN = "com.google.devtools.build.android.desugar.Desugar";

    @NonNull private final Path java8LangSupportJar;
    private final boolean verbose;
    @NonNull private final Map<Path, Path> inputsToOutputs;
    @NonNull private final List<Path> classpath;
    @NonNull private final List<Path> bootClasspath;
    private final int minSdkVersion;

    public DesugarProcessBuilder(
            @NonNull Path java8LangSupportJar,
            boolean verbose,
            @NonNull Map<Path, Path> inputsToOutputs,
            @NonNull List<Path> classpath,
            @NonNull List<Path> bootClasspath,
            int minSdkVersion) {
        this.java8LangSupportJar = java8LangSupportJar;
        this.verbose = verbose;
        this.inputsToOutputs = ImmutableMap.copyOf(inputsToOutputs);
        this.classpath = classpath;
        this.bootClasspath = bootClasspath;
        this.minSdkVersion = minSdkVersion;
    }

    @NonNull
    public JavaProcessInfo build() throws ProcessException, IOException {

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.addEnvironments(mEnvironment);

        builder.setClasspath(java8LangSupportJar.toString());
        builder.setMain(DESUGAR_MAIN);
        builder.addJvmArg("-Xmx64M");

        if (verbose) {
            builder.addArgs("--verbose");
        }

        inputsToOutputs.forEach(
                (in, out) -> {
                    builder.addArgs("--input", in.toString());
                    builder.addArgs("--output", out.toString());
                });
        classpath.forEach(c -> builder.addArgs("--classpath_entry", c.toString()));
        bootClasspath.forEach(b -> builder.addArgs("--bootclasspath_entry", b.toString()));

        builder.addArgs("--min_sdk_version", Integer.toString(minSdkVersion));
        if (minSdkVersion < MIN_SUPPORTED_API_TRY_WITH_RESOURCES) {
            builder.addArgs("--desugar_try_with_resources_if_needed");
        } else {
            builder.addArgs("--nodesugar_try_with_resources_if_needed");
        }
        builder.addArgs("--desugar_try_with_resources_omit_runtime_classes");

        return builder.createJavaProcess();
    }
}
