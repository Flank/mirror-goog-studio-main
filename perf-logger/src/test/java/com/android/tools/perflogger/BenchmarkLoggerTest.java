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
package com.android.tools.perflogger;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.perflogger.BenchmarkLogger.Benchmark;
import com.android.tools.perflogger.BenchmarkLogger.MetricSample;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class BenchmarkLoggerTest {
    @Rule public TestName myTestName = new TestName();

    @Test
    public void testCommitWithNoData() throws Exception {
        BenchmarkLogger logger = new BenchmarkLogger(myTestName.getMethodName());
        logger.commit();

        File outputDir = logger.getOutputDirectory();
        assertThat(outputDir.exists()).isTrue();
        assertThat(outputDir.listFiles()).asList().isEmpty();
    }

    @Test
    public void testMetricNameRenamed() throws Exception {
        BenchmarkLogger logger = new BenchmarkLogger("DEAD[BEEF]");
        assertThat(logger.getMetricName()).isEqualTo("DEAD-BEEF-");
    }

    @Test
    public void testSingleBenchmark() throws Exception {
        BenchmarkLogger logger = new BenchmarkLogger(myTestName.getMethodName());
        Benchmark benchmark = new Benchmark("AS BenchmarkLogger Test");
        logger.addSamples(
                benchmark,
                new MetricSample(1, 10),
                new MetricSample(2, 20),
                new MetricSample(3, 30));
        logger.commit();

        File outputFile =
                new File(
                        logger.getOutputDirectory(),
                        String.format("%s.json", logger.getMetricName()));
        assertThat(outputFile.exists()).isTrue();
        String expected =
                "{\n"
                        + "  \"metric\": \""
                        + myTestName.getMethodName()
                        + "\",\n"
                        + "  \"benchmarks\": [\n"
                        + "    {\n"
                        + "      \"benchmark\": \"AS BenchmarkLogger Test\",\n"
                        + "      \"data\": {\n"
                        + "        \"1\": 10,\n"
                        + "        \"2\": 20,\n"
                        + "        \"3\": 30\n"
                        + "      }\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}";
        InputStream outputStream = new FileInputStream(outputFile);
        String output = CharStreams.toString(new InputStreamReader(outputStream, Charsets.UTF_8));
        assertThat(output).isEqualTo(expected);
    }

    @Test
    public void testMultipleBenchmarks() throws Exception {
        BenchmarkLogger logger = new BenchmarkLogger(myTestName.getMethodName());
        Benchmark benchmark1 = new Benchmark("AS BenchmarkLogger Test1");
        logger.addSamples(
                benchmark1,
                new MetricSample(1, 10),
                new MetricSample(2, 20),
                new MetricSample(3, 30));
        Benchmark benchmark2 = new Benchmark("AS BenchmarkLogger Test2");
        logger.addSamples(
                benchmark2,
                new MetricSample(4, 40),
                new MetricSample(5, 50),
                new MetricSample(6, 60));
        logger.commit();

        File outputFile =
                new File(
                        logger.getOutputDirectory(),
                        String.format("%s.json", logger.getMetricName()));
        String expected =
                "{\n"
                        + "  \"metric\": \""
                        + myTestName.getMethodName()
                        + "\",\n"
                        + "  \"benchmarks\": [\n"
                        + "    {\n"
                        + "      \"benchmark\": \"AS BenchmarkLogger Test1\",\n"
                        + "      \"data\": {\n"
                        + "        \"1\": 10,\n"
                        + "        \"2\": 20,\n"
                        + "        \"3\": 30\n"
                        + "      }\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"benchmark\": \"AS BenchmarkLogger Test2\",\n"
                        + "      \"data\": {\n"
                        + "        \"4\": 40,\n"
                        + "        \"5\": 50,\n"
                        + "        \"6\": 60\n"
                        + "      }\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}";
        InputStream outputStream = new FileInputStream(outputFile);
        String output = CharStreams.toString(new InputStreamReader(outputStream, Charsets.UTF_8));
        assertThat(output).isEqualTo(expected);
    }
}
