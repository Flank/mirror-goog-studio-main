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

import com.android.tools.perflogger.Metric.MetricSample;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class MetricTest {
    @Rule public TestName myTestName = new TestName();

    @Test
    public void testCommitWithNoData() throws Exception {
        Metric logger = new Metric(myTestName.getMethodName());
        logger.commit();

        File outputDir = logger.getOutputDirectory();
        assertThat(outputDir.exists()).isTrue();
        assertThat(outputDir.listFiles()).asList().isEmpty();
    }

    @Test
    public void testMetricNameRenamed() throws Exception {
        Metric logger = new Metric("DEAD[BEEF]");
        assertThat(logger.getMetricName()).isEqualTo("DEAD-BEEF-");
        logger = new Metric("DEAD BEEF");
        assertThat(logger.getMetricName()).isEqualTo("DEAD-BEEF");
    }

    @Test
    public void testSingleBenchmark() throws Exception {
        Metric logger = new Metric(myTestName.getMethodName());
        Benchmark benchmark = new Benchmark.Builder("AS Metric Test").build();
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
                        + "      \"benchmark\": \"AS Metric Test\",\n"
                        + "      \"project\": \"Perfgate for Android Studio\",\n"
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

        // Delete file to prevent it from being uploaded.
        outputFile.delete();
    }

    @Test
    public void testMultipleBenchmarks() throws Exception {
        Metric logger = new Metric(myTestName.getMethodName());
        Benchmark benchmark1 = new Benchmark.Builder("AS Metric Test1").build();
        logger.addSamples(
                benchmark1,
                new MetricSample(1, 10),
                new MetricSample(2, 20),
                new MetricSample(3, 30));
        Benchmark benchmark2 = new Benchmark.Builder("AS Metric Test2").build();
        logger.addSamples(
                benchmark2,
                new MetricSample(4, 40),
                new MetricSample(5, 50),
                new MetricSample(6, 60));
        Benchmark benchmark3 =
                new Benchmark.Builder("AS Metric Test2").setProject("Custom Project").build();
        logger.addSamples(
                benchmark3,
                new MetricSample(7, 70),
                new MetricSample(8, 80),
                new MetricSample(9, 90));
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
                        + "      \"benchmark\": \"AS Metric Test1\",\n"
                        + "      \"project\": \"Perfgate for Android Studio\",\n"
                        + "      \"data\": {\n"
                        + "        \"1\": 10,\n"
                        + "        \"2\": 20,\n"
                        + "        \"3\": 30\n"
                        + "      }\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"benchmark\": \"AS Metric Test2\",\n"
                        + "      \"project\": \"Perfgate for Android Studio\",\n"
                        + "      \"data\": {\n"
                        + "        \"4\": 40,\n"
                        + "        \"5\": 50,\n"
                        + "        \"6\": 60\n"
                        + "      }\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"benchmark\": \"AS Metric Test2\",\n"
                        + "      \"project\": \"Custom Project\",\n"
                        + "      \"data\": {\n"
                        + "        \"7\": 70,\n"
                        + "        \"8\": 80,\n"
                        + "        \"9\": 90\n"
                        + "      }\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}";
        InputStream outputStream = new FileInputStream(outputFile);
        String output = CharStreams.toString(new InputStreamReader(outputStream, Charsets.UTF_8));
        assertThat(output).isEqualTo(expected);

        // Delete file to prevent it from being uploaded.
        outputFile.delete();
    }
}
