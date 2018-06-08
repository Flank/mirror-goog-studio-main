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

import com.android.tools.perflogger.Benchmark;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@SuppressWarnings("unused")
public class GradleProfileLogger implements BenchmarkListener {
    File testLogsDir;
    Benchmark benchmark;

    @Override
    public void configure(File home, Gradle gradle) {
        testLogsDir = new File(new File(home, "test_logs"), "project");
        gradle.addArgument("-Pandroid.advanced.profileOutputDir=" + testLogsDir.getAbsolutePath());
    }

    @Override
    public void benchmarkStarting(Benchmark benchmark) {
        this.benchmark = benchmark;
    }

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

        new ProfilerToBenchmarkAdapter().adapt(gradleBuildProfile, benchmark);
    }

    private static void cleanOutputDir(File outputDir) {
        File[] files = outputDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            file.delete();
        }
    }
}
