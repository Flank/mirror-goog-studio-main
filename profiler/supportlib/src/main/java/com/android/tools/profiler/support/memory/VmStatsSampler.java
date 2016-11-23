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

    public static final String NAME = "Android Studio Profiler Supportlib VmStatsSampler";

    private static final long SLEEP_TIME_NS = TimeUnit.MILLISECONDS.toNanos(250);

    // The following string resources are used for querying runtime stats for api >= 23
    private static final String GC_COUNT_STAT = "art.gc.gc-count";

    private CountDownLatch mRunning = new CountDownLatch(1);

    private int mPreviousGcCount;

    public VmStatsSampler() {
        super(NAME);
    }

    @Override
    public void run() {
        mPreviousGcCount = getGcCount();
        while (mRunning.getCount() > 0) {
            try {
                long startTime = System.nanoTime();

                int freedCount = Debug.getGlobalFreedCount();
                int allocatedCount = Debug.getGlobalAllocCount();
                int gcCount = getGcCount();
                sendVmStats(allocatedCount, freedCount, gcCount - mPreviousGcCount);
                mPreviousGcCount = gcCount;

                long endTime = System.nanoTime();
                if (SLEEP_TIME_NS > endTime - startTime) {
                    mRunning.await(SLEEP_TIME_NS - (endTime - startTime), TimeUnit.NANOSECONDS);
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
        } catch (InterruptedException ignored) {}
    }

    private int getGcCount() {
        int sdkVersion = Build.VERSION.SDK_INT;
        int gcCount;
        if (sdkVersion >= Build.VERSION_CODES.LOLLIPOP) {
            String stat = Debug.getRuntimeStat(GC_COUNT_STAT);
            gcCount = Integer.parseInt(stat);
        }
        else {
            gcCount = Debug.getGlobalGcInvocationCount();
        }

        return gcCount;
    }

    public static native void sendVmStats(int allocCount, int freeCount, int gcCount);
}
