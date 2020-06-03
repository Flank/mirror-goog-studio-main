/*
 * Copyright (C) 2020 The Android Open Source Project
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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fake implementation of HandlerThread, that is actually backed by Executor.
 *
 * <p>It is good enough for emulation of simple "post to another thread" behavior, but don't use it
 * for testing of more complicate behaviors of real android Handler.
 */
public class HandlerThread extends Thread {
    private Looper mLooper;
    private Runnable mTarget;
    private ExecutorService mExecutor;
    private boolean mFirstStartCall = true;

    public HandlerThread(String name) {
        setName(name);
        mExecutor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            mTarget = r;
                            return HandlerThread.this;
                        });
    }

    @Override
    public synchronized void start() {
        // Something shady is happening here:
        // because we rely on the executor for tasks execution,
        // it receives a privilege to start us for real:
        // the empty task sent to it will trick
        // this executor into calling "start" on this again.
        // This second reentrant call is handled by real
        // super.start().
        // One more note: there is a guarantee that first ".start"
        // will be from outside instead of an executor, because
        // the executor is published via looper only after HandlerThread
        // is started. That behavior is actually not that far from real
        // android behavior, because Looper there too is available only thread
        // is started, though real implementation is more nuanced.
        if (mFirstStartCall) {
            mFirstStartCall = false;
            mExecutor.execute(() -> {});
        } else {
            super.start();
            mLooper = new ExecutorLooper(mExecutor);
        }
    }

    @Override
    public void run() {
        mTarget.run();
    }

    public Looper getLooper() {
        return mLooper;
    }

    public boolean quitSafely() {
        mExecutor.shutdown();
        return true;
    }

    static class ExecutorLooper extends Looper {
        private final Executor mExecutor;

        ExecutorLooper(Executor executor) {
            this.mExecutor = executor;
        }

        public Executor getExecutor() {
            return mExecutor;
        }
    }
}
