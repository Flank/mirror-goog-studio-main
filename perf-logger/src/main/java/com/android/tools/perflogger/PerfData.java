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

package com.android.tools.perflogger;

import com.android.testutils.TestUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Container for {@link Benchmark} objects that writes benchmark-specific information for one or
 * more Perfgate Benchmarks to a single file. This includes the plain-text "description" field, as
 * well as the "metadata" field.
 *
 * <p>If you wish to write information to multiple Perfgate Benchmarks in a single Bazel test, all
 * of the {@link Benchmark} objects generated must be added to the same PerfData instance.
 *
 * <p>Usage example:
 *
 * <pre>
 * PerfData perfData = new PerfData();
 * Benchmark b1 = new Benchmark.Builder("Benchmark 1")
 *     .setDescription("Android Studio Performance Test").build();
 * pd.addBenchmark(b1);
 * pd.commit();
 * </pre>
 */
public class PerfData {
    private static final String FILE_NAME = "perf_data.json";

    private List<Benchmark> benchmarks = new ArrayList<>();

    //TODO avoid duplicate Benchmarks
    public void addBenchmark(Benchmark benchmark) {
        benchmarks.add(benchmark);
    }

    /** Serializes all of the performance data to a default location in JSON format. */
    public void commit() throws IOException {
        File outputDirectory = TestUtils.getTestOutputDir();
        try (Writer jsonWriter = new FileWriter(new File(outputDirectory, FILE_NAME))) {
            commit(jsonWriter);
        }
    }

    /** Serializes all of the performance data to the given {@link Writer} in JSON format. */
    @VisibleForTesting
    void commit(Writer jsonWriter) {
        Gson gson =
                new GsonBuilder()
                        .setPrettyPrinting()
                        .excludeFieldsWithoutExposeAnnotation()
                        .create();
        gson.toJson(benchmarks, jsonWriter);
    }
}
