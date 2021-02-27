/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"BusyWait", "VariableNotUsedInsideIf"})
public final class Looper {
    private enum PollMode {
        RUNNING,
        QUIT_SAFELY,
        QUIT_NOW,
    }

    @Nullable private static Looper sMainLooper = null;
    private static final Map<Thread, Looper> sLoopers = new HashMap<>();

    public static void prepareMainLooper() {
        if (sMainLooper != null) {
            throw new IllegalStateException("Main looper already prepared");
        }
        prepare();
        sMainLooper = sLoopers.get(Thread.currentThread());
    }

    public static void prepare() {
        sLoopers.computeIfAbsent(Thread.currentThread(), thread -> new Looper());
    }

    @NonNull
    public static Looper getMainLooper() {
        if (sMainLooper == null) {
            throw new IllegalStateException(
                    "Must call `prepareMainLooper` first (or it already shut down)");
        }
        return sMainLooper;
    }

    @NonNull
    public static Looper myLooper() {
        Looper l = sLoopers.get(Thread.currentThread());
        if (l == null) {
            throw new IllegalStateException("Must call `prepare` first");
        }
        return l;
    }

    public static void loop() {
        Looper looper = myLooper();
        try {
            looper.loopImpl();
        } catch (InterruptedException ignored) {

        }

        if (looper == sMainLooper) {
            sMainLooper = null;
        }
        sLoopers.remove(Thread.currentThread());
    }

    private final Object mLock = new Object();
    private final ArrayDeque<Runnable> mPendingWork = new ArrayDeque<>();

    private volatile PollMode mPollMode = PollMode.RUNNING;

    public boolean isCurrentThread() {
        return sLoopers.get(Thread.currentThread()) == this;
    }

    public void quit() {
        synchronized (mLock) {
            mPollMode = PollMode.QUIT_NOW;
        }
    }

    public void quitSafely() {
        synchronized (mLock) {
            if (mPollMode == PollMode.RUNNING) {
                mPollMode = PollMode.QUIT_SAFELY;
            }
        }
    }

    boolean post(@NonNull Runnable r) {
        synchronized (mLock) {
            if (mPollMode == PollMode.RUNNING) {
                mPendingWork.add(r);
                return true;
            }
        }
        return false;
    }

    private void loopImpl() throws InterruptedException {
        while (mPollMode != PollMode.QUIT_NOW) {
            @Nullable Runnable work;
            synchronized (mLock) {
                work = mPendingWork.poll();
                if (mPollMode == PollMode.QUIT_SAFELY && work == null) {
                    mPollMode = PollMode.QUIT_NOW;
                }
            }

            if (work != null) {
                work.run();
            }

            Thread.sleep(10);
        }
    }

    @VisibleForTesting
    public static Map<Thread, Looper> getLoopers() {
        return sLoopers;
    }
}
