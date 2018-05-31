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

import com.android.tools.profiler.proto.Profiler;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Contains helper methods for perf tests. */
public final class TestUtils {
    private static final int RETRY_INTERVAL_MILLIS = 200;
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    /**
     * Run the loop and wait until condition is true. This method does NOT timeout so you should
     * only wait for conditions that are guaranteed to eventually be true.
     */
    public static void waitFor(Supplier<Boolean> condition) {
        while (!condition.get()) {
            try {
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.out);
            }
        }
    }

    /**
     * Wait forever until condition is true, as the wait time is undertermined when multiple tests
     * run parallel. See {@link #waitForAndReturn(Supplier, Predicate, int)}.
     */
    public static <T> T waitForAndReturn(Supplier<T> supplier, Predicate<T> condition) {
        return waitForAndReturn(supplier, condition, NO_LIMIT);
    }

    /**
     * Run the loop and wait until condition is true or the retry limit is reached. Returns the
     * result afterwards.
     *
     * @param supplier a function that returns the desired result.
     * @param condition tests whether the result is desired.
     * @param retryLimit Limit to retry before return the result.
     * @param <T> type of the desired result.
     * @return the result from the last run (condition met or timeout).
     */
    public static <T> T waitForAndReturn(
            Supplier<T> supplier, Predicate<T> condition, int retryLimit) {
        T result = supplier.get();
        int count = 0;
        while (!condition.test(result) && count < retryLimit) {
            System.out.println("Retrying condition");
            ++count;
            try {
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.out);
                break;
            }
            result = supplier.get();
        }
        return result;
    }

    /**
     * Helper to get {@link getProfilerStub().getBytes()} response, it is shared across all
     * profilers.
     */
    public static String getBytes(GrpcUtils grpcUtils, String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return "";
        }
        Profiler.BytesRequest stackRequest =
                Profiler.BytesRequest.newBuilder().setId(traceId).build();
        return grpcUtils.getProfilerStub().getBytes(stackRequest).getContents().toStringUtf8();
    }

    /** @return True if the given SDK level is newer than Android O. */
    public static boolean isOPlusDevice(int sdkLevel) {
        return sdkLevel >= 26;
    }
}
