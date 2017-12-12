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

package com.android.build.gradle.integration.common.performance;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

import com.android.build.gradle.integration.common.fixture.RandomGradleBenchmark;
import com.android.build.gradle.integration.performance.LocalCSVProfileUploader;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LocalCSVProfileUploaderTest {
    private static final Pattern pattern = Pattern.compile("bench_.*\\.csv");
    private LocalCSVProfileUploader localUploader;

    @Rule public final TemporaryFolder testFolder = new TemporaryFolder();
    private File outDir;

    @Before
    public void setUp() throws IOException {
        outDir = testFolder.newFolder();
        localUploader = LocalCSVProfileUploader.toDirectory(outDir);
    }

    @Test
    public void checkOutput() throws Exception {
        Logging.GradleBenchmarkResult benchmarkResult =
                RandomGradleBenchmark.randomBenchmarkResult();

        localUploader.uploadData(ImmutableList.of(benchmarkResult));

        List<File> benchFiles = FileUtils.find(outDir, pattern);
        assertThat(benchFiles).hasSize(1);

        List<String> lines = Files.readAllLines(benchFiles.get(0).toPath());
        assertThat(lines.size()).isAtLeast(7);

        assertThat(lines.get(0)).startsWith("Benchmark,");
        assertThat(lines.get(1)).startsWith("Benchmark mode,");
        assertThat(lines.get(2)).startsWith("Compiler,");
        assertThat(lines.get(3)).startsWith("Dex in process,");
        assertThat(lines.get(4)).startsWith("Java8LangSupport");
        assertThat(lines.get(5)).isEmpty();
        assertThat(lines.get(6)).isEqualTo("Project,Variant,Type,Duration");

        int i = 7;
        for (GradleBuildProfileSpan span : benchmarkResult.getProfile().getSpanList()) {
            String line = lines.get(i);
            assertThat(line).endsWith(Long.toString(span.getDurationInMs()));
            i++;
        }
        assertThat(lines).hasSize(i);
    }
}
