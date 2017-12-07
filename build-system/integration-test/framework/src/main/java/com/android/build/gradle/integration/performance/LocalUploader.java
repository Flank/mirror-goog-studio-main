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

package com.android.build.gradle.integration.performance;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.android.utils.FileUtils;
import com.android.utils.Pair;
import com.google.api.client.util.Preconditions;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalUploader implements ProfileUploader {
    public static final LocalUploader INSTANCE = new LocalUploader();

    private static final String ENVIRONMENT_VARIABLE = "LOCAL_BENCHMARK_LOCATION";

    private LocalUploader() {}

    @Nullable
    public File getOutputFolder() {
        String folderLocation = System.getenv(ENVIRONMENT_VARIABLE);
        if (folderLocation == null) {
            return null;
        }
        return new File(folderLocation);
    }

    @Override
    public void uploadData(@NonNull List<Logging.GradleBenchmarkResult> benchmarkResults)
            throws IOException {
        Preconditions.checkNotNull(benchmarkResults);
        Preconditions.checkArgument(!benchmarkResults.isEmpty(), "got an empty list of results");

        // Create a CSV file.
        File folder = getOutputFolder();
        if (folder == null) {
            return;
        }
        if (!folder.exists()) {
            FileUtils.mkdirs(folder);
        }
        if (folder.exists() && !folder.isDirectory()) {
            FileUtils.delete(folder);
            FileUtils.mkdirs(folder);
        }
        String fileName = "bench_" + Instant.now().toString().replace(':', '_') + ".csv";

        try (PrintWriter printWriter =
                new PrintWriter(new FileOutputStream(new File(folder, fileName)))) {
            for (Logging.GradleBenchmarkResult benchmarkResult : benchmarkResults) {
                // Build a human-readable map of project and variant names.
                Map<Long, Pair<String, Map<Long, String>>> projects = new HashMap<>();
                for (GradleBuildProject project : benchmarkResult.getProfile().getProjectList()) {
                    Map<Long, String> variants = new HashMap<>();
                    for (GradleBuildVariant variant : project.getVariantList()) {
                        variants.put(variant.getId(), "VARIANT_" + variant.getId());
                    }
                    if (variants.isEmpty()) {
                        continue;
                    }
                    projects.put(
                            project.getId(),
                            Pair.of(
                                    project.getAndroidPlugin().name() + "_" + project.getId(),
                                    variants));
                }
                if (projects.isEmpty()) {
                    continue;
                }

                // Write the test header.
                printWriter
                        .append("Benchmark,")
                        .append(benchmarkResult.getBenchmark().name())
                        .append('\n')
                        .append("Benchmark mode,")
                        .append(benchmarkResult.getBenchmarkMode().name())
                        .append('\n')
                        .append("Compiler,")
                        .append(benchmarkResult.getFlags().getCompiler().name())
                        .append('\n')
                        .append("Dex in process,")
                        .append(benchmarkResult.getFlags().getDexInProcess().name())
                        .append('\n')
                        .append("Java8LangSupport,")
                        .append(benchmarkResult.getFlags().getJava8LangSupport().name())
                        .append("\n\nProject,Variant,Type,Duration\n");

                for (GradleBuildProfileSpan span : benchmarkResult.getProfile().getSpanList()) {
                    Pair<String, Map<Long, String>> project = projects.get(span.getProject());
                    if (project == null) {
                        continue;
                    }
                    String variantName = project.getSecond().get(span.getVariant());
                    if (variantName == null) {
                        continue;
                    }
                    String taskName;
                    switch (span.getType()) {
                        case TASK_TRANSFORM:
                        case TASK_TRANSFORM_PREPARATION:
                            GradleTransformExecutionType transform =
                                    GradleTransformExecutionType.forNumber(
                                            span.getTransform().getType());
                            if (transform == null) {
                                continue;
                            }
                            taskName = transform.name();
                            break;
                        case TASK_EXECUTION:
                            GradleTaskExecutionType task =
                                    GradleTaskExecutionType.forNumber(span.getTask().getType());
                            if (task == null) {
                                continue;
                            }
                            taskName = task.name();
                            break;
                        default:
                            taskName = span.getType().name();
                    }

                    printWriter
                            .append(project.getFirst())
                            .append(',')
                            .append(variantName)
                            .append(',')
                            .append(taskName)
                            .append(',')
                            .append(Long.toString(span.getDurationInMs()))
                            .append('\n');
                }
            }
        }
    }
}
