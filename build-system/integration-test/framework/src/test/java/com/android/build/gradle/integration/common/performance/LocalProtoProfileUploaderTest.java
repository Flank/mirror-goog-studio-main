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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.RandomGradleBenchmark;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.performance.LocalProtoProfileUploader;
import com.android.build.gradle.integration.performance.ProfileUploader;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LocalProtoProfileUploaderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    public Path dir;
    public ProfileUploader uploader;

    @Before
    public void setUp() throws IOException {
        dir = tmp.newFolder().toPath();
        uploader = LocalProtoProfileUploader.toDir(dir);
    }

    @Test
    public void singleFile() throws IOException {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        uploader.uploadData(Arrays.asList(RandomGradleBenchmark.randomBenchmarkResult()));
        assertThat(Files.walk(dir).filter(Files::isRegularFile).count()).isEqualTo(1);
    }

    @Test
    public void multipleFiles() throws IOException {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        uploader.uploadData(
                Arrays.asList(
                        RandomGradleBenchmark.randomBenchmarkResult(),
                        RandomGradleBenchmark.randomBenchmarkResult(),
                        RandomGradleBenchmark.randomBenchmarkResult()));

        assertThat(Files.walk(dir).filter(Files::isRegularFile).count()).isEqualTo(3);
    }

    @Test
    public void filesAreParseableProtos() throws IOException {
        AssumeUtil.assumeNotWindowsBot(); // https://issuetracker.google.com/70931936
        List<GradleBenchmarkResult> in =
                Arrays.asList(
                        RandomGradleBenchmark.randomBenchmarkResult(),
                        RandomGradleBenchmark.randomBenchmarkResult(),
                        RandomGradleBenchmark.randomBenchmarkResult());

        uploader.uploadData(in);

        List<Path> files =
                Files.walk(dir).filter(Files::isRegularFile).collect(Collectors.toList());
        List<GradleBenchmarkResult> out = new ArrayList<>(files.size());

        for (Path file : files) {
            out.add(GradleBenchmarkResult.parseFrom(Files.readAllBytes(file)));
        }

        assertThat(in).containsExactlyElementsIn(out);
    }
}
