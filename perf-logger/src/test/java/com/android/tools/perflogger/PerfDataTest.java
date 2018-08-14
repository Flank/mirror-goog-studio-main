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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;

public class PerfDataTest {

    @Test
    public void testCommit() throws Exception {
        PerfData pd = new PerfData();

        Benchmark b1 =
                new Benchmark.Builder("Test Benchmark 1")
                        .setProject("Test Project")
                        .setDescription("This is a test Benchmark object.")
                        .build();
        Benchmark b2 =
                new Benchmark.Builder("Test Benchmark 2")
                        .setProject("Test Project")
                        .setMetadata(ImmutableMap.of("key1", "value1", "key2", "value2"))
                        .build();

        pd.addBenchmark(b1);
        pd.addBenchmark(b2);

        try (StringWriter writer = new StringWriter()) {
            pd.commit(writer);
            String jsonString = writer.toString();

            Gson gson = new Gson();
            List<FakeBenchmark> actual =
                    gson.fromJson(jsonString, new TypeToken<List<FakeBenchmark>>() {}.getType());
            List<FakeBenchmark> expected =
                    ImmutableList.of(
                            new FakeBenchmark(
                                    "Test Benchmark 1",
                                    "Test Project",
                                    "This is a test Benchmark object.",
                                    null),
                            new FakeBenchmark(
                                    "Test Benchmark 2",
                                    "Test Project",
                                    null,
                                    ImmutableMap.of("key1", "value1", "key2", "value2")));

            assertThat(actual).containsExactlyElementsIn(expected);
        }
    }

    /**
     * Boilerplate data class. The actual {@link Benchmark} class is difficult to deserialize with
     * GSON becuase it uses {@link ImmutableMap}, but a simple data class suffices to verify the
     * generated JSON.
     *
     * <p>These variable names/types mirror the JSON fields expected by the script that parses the
     * perf_data.json files. Therefore, these names should not change except to mirror changes in
     * the pipeline.
     */
    private static class FakeBenchmark {
        private String name;
        private String projectName;
        private String description;
        private Map<String, String> metadata;

        public FakeBenchmark(
                String name, String projectName, String description, Map<String, String> metadata) {
            this.name = name;
            this.projectName = projectName;
            this.description = description;
            this.metadata = metadata;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FakeBenchmark that = (FakeBenchmark) o;
            return Objects.equals(name, that.name)
                    && Objects.equals(projectName, that.projectName)
                    && Objects.equals(description, that.description)
                    && Objects.equals(metadata, that.metadata);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, projectName, description, metadata);
        }

        /** Implementing toString() improves error messages for failed assertions. */
        @Override
        public String toString() {
            return "FakeBenchmark{"
                    + "name='"
                    + name
                    + '\''
                    + ", projectName='"
                    + projectName
                    + '\''
                    + ", description='"
                    + description
                    + '\''
                    + ", metadata="
                    + metadata
                    + '}';
        }
    }
}
