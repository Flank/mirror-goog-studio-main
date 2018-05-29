/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.gradle;

import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.android.tools.perflogger.BenchmarkLogger;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@SuppressWarnings("unused")
public class GradleProfileLogger implements BenchmarkListener {
    File testLogsDir;

    @Override
    public void configure(File home, Gradle gradle) {
        testLogsDir = new File(new File(home, "test_logs"), "project");
        gradle.addArgument("-Pandroid.advanced.profileOutputDir=" + testLogsDir.getAbsolutePath());
    }

    @Override
    public void benchmarkStarting(BenchmarkLogger.Benchmark benchmark, BenchmarkLogger logger) {}

    @Override
    public void benchmarkDone() {}

    @Override
    public void iterationStarting() {
        cleanOutputDir(testLogsDir);
    }

    @Override
    public void iterationDone() {
        File[] rawProtoFiles = testLogsDir.listFiles();
        if (rawProtoFiles == null) {
            throw new RuntimeException("Expected one profile output, but got nothing");
        }
        if (rawProtoFiles.length != 1) {
            throw new RuntimeException(
                    "Expected one profile output, but got " + rawProtoFiles.length);
        }

        GradleBuildProfile gradleBuildProfile;
        try {
            gradleBuildProfile =
                    GradleBuildProfile.parseFrom(
                            new BufferedInputStream(new FileInputStream(rawProtoFiles[0])));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (GradleBuildProfileSpan span : gradleBuildProfile.getSpanList()) {
            if (span.getType() == GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION) {
                if (span.getTask().getType() == GradleTaskExecutionType.TRANSFORM_VALUE) {
                    System.out.println(
                            "Transform "
                                    + GradleTransformExecutionType.forNumber(
                                            span.getTransform().getType())
                                    + "("
                                    + span.getTransform().getType()
                                    + ")"
                                    + " : "
                                    + span.getDurationInMs());
                } else {
                    GradleTaskExecutionType gradleTaskExecutionType =
                            GradleTaskExecutionType.forNumber(span.getTask().getType());
                    System.out.println(
                            "Task "
                                    + gradleTaskExecutionType
                                    + "("
                                    + span.getTask().getType()
                                    + ") : "
                                    + span.getDurationInMs());
                }
            }
        }
    }

    private static void cleanOutputDir(File outputDir) {
        File[] files = outputDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            file.delete();
        }
    }
}
