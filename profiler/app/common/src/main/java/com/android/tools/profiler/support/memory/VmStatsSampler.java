/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.profiler.support.memory;

import android.os.Build;
import android.os.Debug;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class VmStatsSampler extends Thread {

    public static final String NAME = "Studio:VmStats";

    private static final long SLEEP_TIME_NS = TimeUnit.MILLISECONDS.toNanos(250);

    // The following string resources are used for querying runtime stats for api >= 23
    private static final String GC_COUNT_STAT = "art.gc.gc-count";

    private final CountDownLatch mRunning = new CountDownLatch(1);
    private int mPreviousGcCount;
    private int mPreviousThreadLocalAllocCount;

    // Whether the sampler should monitor and log garbage collection events.
    private boolean mLogGc;

    public VmStatsSampler(boolean logGc) {
        super(NAME);
        mLogGc = logGc;
    }

    @Override
    public void run() {
        mPreviousGcCount = getGcCount();
        while (mRunning.getCount() > 0) {
            try {
                long startTimeNs = System.nanoTime();

                /**
                 * Keep track of allocation overhead needed to operate this sampler thread.
                 * Currently, logic like {@link Debug#getRuntimeStat(String)} and
                 * {@link CountDownLatch#await()} incur minor allocations. This loop eliminates the
                 * overhead from the global allocation count so the latter remains stable. Note
                 * that a similar operation applies to the global freed count but only when a GC is
                 * detected, accounting for all thread-local allocations up to that point. While
                 * the actual de-allocation behavior for the thread-local objects might differ, this
                 * solution gives a stable long-term trend which we deemed good enough.
                 */
                int threadAllocatedCount = Debug.getThreadAllocCount();
                int gcCount = getGcCount();
                if (gcCount - mPreviousGcCount > 0) {
                    mPreviousThreadLocalAllocCount = threadAllocatedCount;
                }
                int freedCount = Debug.getGlobalFreedCount() - mPreviousThreadLocalAllocCount;
                int allocatedCount = Debug.getGlobalAllocCount() - threadAllocatedCount;
                logAllocStats(allocatedCount, freedCount);
                if (gcCount - mPreviousGcCount > 0) {
                    if (mLogGc) {
                        logGcStats();
                    }
                    mPreviousGcCount = gcCount;
                }

                long deltaNs = System.nanoTime() - startTimeNs;
                if (SLEEP_TIME_NS > deltaNs) {
                    mRunning.await(SLEEP_TIME_NS - deltaNs, TimeUnit.NANOSECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                mRunning.countDown();
            }
        }
    }

    public void shutdown() {
        mRunning.countDown();
        try {
            join();
        } catch (InterruptedException ignored) {
        }
    }

    private int getGcCount() {
        int sdkVersion = Build.VERSION.SDK_INT;
        int gcCount;
        if (sdkVersion >= Build.VERSION_CODES.M) {
            String stat = Debug.getRuntimeStat(GC_COUNT_STAT);
            gcCount = Integer.parseInt(stat);
        } else {
            gcCount = Debug.getGlobalGcInvocationCount();
        }

        return gcCount;
    }

    public static native void logAllocStats(int allocCount, int freeCount);
    public static native void logGcStats();
}
