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

package com.android.tools.profiler;

import com.android.testutils.TestUtils;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.MedianWindowDeviationAnalyzer;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.*;
import org.junit.Test;

/**
 * Measures the sizes of the profiler libraries we generate and generates the data files for
 * uploading to the Studio perf tracking dashboards.
 */
public class NativeBinarySizeTest {
    // Project name for studio profilers' dashboards
    private static final String PROFILER_PROJECT_NAME = "Android Studio Profilers";

    @Test
    public void testLoggingBinarySize() {
        List<String> abis = Arrays.asList("x86", "x86_64", "armeabi-v7a", "arm64-v8a");
        Map<String, String> files =
                ImmutableMap.of(
                        "perfd", "perfd",
                        "perfa", "libperfa.so");

        Benchmark benchmark =
                new Benchmark.Builder("Profiler Native Binaries (bytes)")
                        .setProject(PROFILER_PROJECT_NAME)
                        .build();
        for (Map.Entry<String, String> file : files.entrySet()) {
            for (String abi : abis) {
                try {
                    // getWorkspaceFile asserts the file exists.
                    File binary =
                            TestUtils.getWorkspaceFile(
                                    String.format(
                                                    "tools/base/profiler/native/%s/android/",
                                                    file.getKey())
                                            + String.format("%s/%s", abi, file.getValue()));
                    benchmark.log(String.format("%s_%s", file.getKey(), abi),
                            binary.length(),
                            /* we don't expect this to deviate so tighten parameters to detect slightest regression */
                            new MedianWindowDeviationAnalyzer.Builder()
                                    .setRunInfoQueryLimit(5)
                                    .setRecentWindowSize(1)
                                    .setMedianCoeff(0.01)
                                    .setMadCoeff(0.0)
                                    .build());
                }
                catch (IllegalArgumentException ignored) {
                    // ignore binaries that are not built for certain architectures.
                }
            }
        }
    }
}
