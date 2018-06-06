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
import com.android.tools.perflogger.Metric;
import java.io.File;

/**
 * Facility to listen to {@link BenchmarkTest} events.
 *
 * <p>Implementations should be careful when measuring "wall time" execution which can be unreliable
 * in the presence of several listeners. Instead implementations should measure performance by
 * retrieving that information from the build execution itself rather than through this interface
 * callbacks.
 */
public interface BenchmarkListener {

    /**
     * callback to provide extra configuration for the {@link Gradle} instance.
     *
     * @param home the user.home for the test instance.
     * @param gradle the gradle instance that will run the test.
     */
    void configure(File home, Gradle gradle);

    /**
     * notification of benchmark creation with the {@link Benchmark} instance and the {#link Metric}
     * that will log all the benchmark metrics.
     */
    void benchmarkStarting(Benchmark benchmark, Metric logger);

    /** notification of benchmark completion */
    void benchmarkDone();

    /**
     * notification of a test iteration start. if the test requested several iterations, this method
     * will be called as many times as the test requested iterations, otherwise only once. for each
     * iteration, {@link #iterationDone} will be called before the next [iterationStarting] is
     * called.
     */
    void iterationStarting();

    /** notification of a test iteration completion. */
    void iterationDone();
}
