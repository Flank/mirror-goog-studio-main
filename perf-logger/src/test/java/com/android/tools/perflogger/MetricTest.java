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
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class MetricTest {
    @Rule public TestName myTestName = new TestName();

    @Test
    public void testCommitWithNoData() throws Exception {
        Metric metric = new Metric(myTestName.getMethodName());
        metric.commit();

        File outputDir = metric.getOutputDirectory();
        assertThat(outputDir.exists()).isTrue();
        assertThat(outputDir.listFiles()).asList().isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMetricNameException() {
        String name = "All the \"!valid\" [characters],&\\'<>`";
        new Metric(name);
    }

    @Test
    public void testMetricNameValid() {
        String name = "ABCDEFGHIJKlmnopqrstuvwxyz1234567890-_.";
        Metric logger = new Metric(name);
        assertThat(logger.getMetricName()).isEqualTo(name);
    }

    @Test
    public void testSingleBenchmark() throws Exception {
        Metric metric = new Metric(myTestName.getMethodName());
        Benchmark benchmark = new Benchmark.Builder("AS Metric Test").build();
        metric.addSamples(
                benchmark,
                new MetricSample(1, 10),
                new MetricSample(2, 20),
                new MetricSample(3, 30));
        metric.commit();

        File outputFile =
                new File(
                        metric.getOutputDirectory(),
                        String.format("%s.json", metric.getMetricName()));
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
    public void testWindowDeviationAnalyzers() throws Exception {
        Metric metric = new Metric(myTestName.getMethodName());
        Benchmark benchmark = new Benchmark.Builder("AS Metric Test").build();
        List<Analyzer> analyzers =
                ImmutableList.of(
                  new WindowDeviationAnalyzer.Builder()
                                .addMeanTolerance(new WindowDeviationAnalyzer.MeanToleranceParams.Builder()
                                        .setConstTerm(20.0)
                                        .setMeanCoeff(0.1)
                                        .setStddevCoeff(1.0)
                                        .build()).
                                build(),
                  new WindowDeviationAnalyzer.Builder()
                                .setMetricAggregate(Analyzer.MetricAggregate.MEDIAN)
                                .setRunInfoQueryLimit(24)
                                .setRecentWindowSize(5)
                                .addMeanTolerance(new WindowDeviationAnalyzer.MeanToleranceParams.Builder().build())
                                .addMedianTolerance(new WindowDeviationAnalyzer.MedianToleranceParams.Builder().build())
                                .build());
        metric.setAnalyzers(benchmark, analyzers);
        metric.addSamples(
                benchmark,
                new MetricSample(1, 10),
                new MetricSample(2, 20),
                new MetricSample(3, 30));
        metric.commit();

        File outputFile =
                new File(
                        metric.getOutputDirectory(),
                        String.format("%s.json", metric.getMetricName()));
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
                        + "      },\n"
                        + "      \"analyzers\": [\n"
                        + "        {\n"
                        + "          \"type\": \"WindowDeviationAnalyzer\",\n"
                        + "          \"metricAggregate\": \"MEAN\",\n"
                        + "          \"runInfoQueryLimit\": \"50\",\n"
                        + "          \"recentWindowSize\": \"11\",\n"
                        + "          \"toleranceParams\": [\n"
                        + "            {\n"
                        + "              \"type\": \"Mean\",\n"
                        + "              \"constTerm\": \"20.0\",\n"
                        + "              \"meanCoeff\": \"0.1\",\n"
                        + "              \"stddevCoeff\": \"1.0\"\n"
                        + "            }\n"
                        + "          ]\n"
                        + "        },\n"
                        + "        {\n"
                        + "          \"type\": \"WindowDeviationAnalyzer\",\n"
                        + "          \"metricAggregate\": \"MEDIAN\",\n"
                        + "          \"runInfoQueryLimit\": \"24\",\n"
                        + "          \"recentWindowSize\": \"5\",\n"
                        + "          \"toleranceParams\": [\n"
                        + "            {\n"
                        + "              \"type\": \"Mean\",\n"
                        + "              \"constTerm\": \"0.0\",\n"
                        + "              \"meanCoeff\": \"0.05\",\n"
                        + "              \"stddevCoeff\": \"2.0\"\n"
                        + "            },\n"
                        + "            {\n"
                        + "              \"type\": \"Median\",\n"
                        + "              \"constTerm\": \"0.0\",\n"
                        + "              \"medianCoeff\": \"0.05\",\n"
                        + "              \"madCoeff\": \"1.0\"\n"
                        + "            }\n"
                        + "          ]\n"
                        + "        }\n"
                        + "      ]\n"
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
        Metric metric = new Metric(myTestName.getMethodName());
        Benchmark benchmark1 = new Benchmark.Builder("AS Metric Test1").build();
        metric.addSamples(
                benchmark1,
                new MetricSample(1, 10),
                new MetricSample(2, 20),
                new MetricSample(3, 30));
        Benchmark benchmark2 = new Benchmark.Builder("AS Metric Test2").build();
        metric.addSamples(
                benchmark2,
                new MetricSample(4, 40),
                new MetricSample(5, 50),
                new MetricSample(6, 60));
        Benchmark benchmark3 =
                new Benchmark.Builder("AS Metric Test2").setProject("Custom Project").build();
        metric.addSamples(
                benchmark3,
                new MetricSample(7, 70),
                new MetricSample(8, 80),
                new MetricSample(9, 90));
        metric.commit();

        File outputFile =
                new File(
                        metric.getOutputDirectory(),
                        String.format("%s.json", metric.getMetricName()));
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
