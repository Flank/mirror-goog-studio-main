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

import android.os.Debug;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class VmStatsSampler extends Thread {

    public static final String NAME = "Android Studio Profiler Supportlib VmStatsSampler";

    private static final long SLEEP_TIME_NS = TimeUnit.MILLISECONDS.toNanos(250);

    private CountDownLatch mRunning = new CountDownLatch(1);

    public VmStatsSampler() {
        super(NAME);
    }

    @Override
    public void run() {
        while (mRunning.getCount() > 0) {
            try {
                long startTime = System.nanoTime();

                int freedCount = Debug.getGlobalFreedCount();
                int allocatedCount = Debug.getGlobalAllocCount();
                int gcCount = Debug.getGlobalGcInvocationCount();
                sendVmStats(allocatedCount, freedCount, gcCount);

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

    public static native void sendVmStats(int allocCount, int freeCount, int gcCount);
}
