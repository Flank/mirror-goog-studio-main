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

package com.android.tools.agent.app.inspection.concurrent;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class HandlerThreadExecutor implements Executor {
    private final HandlerThread mThread;
    private final SafeHandler handler;

    public HandlerThreadExecutor(
            HandlerThread thread, Consumer<Throwable> uncaughtExceptionHandler) {
        mThread = thread;
        handler = new SafeHandler(thread.getLooper(), uncaughtExceptionHandler);
    }

    @Override
    public void execute(@NonNull Runnable command) {
        handler.post(command);
    }

    /**
     * Inherits logic from {@link HandlerThread}, all due commands will be delivered. Commands in
     * the future won't be delivered.
     */
    public void quitSafely() {
        mThread.quitSafely();
    }

    @NonNull
    public Handler getHandler() {
        return handler;
    }

    private static class SafeHandler extends Handler {
        private final Consumer<Throwable> mUncaughtExceptionHandler;

        public SafeHandler(@NonNull Looper looper, Consumer<Throwable> uncaughtExceptionHandler) {
            super(looper);
            mUncaughtExceptionHandler = uncaughtExceptionHandler;
        }

        @Override
        public void dispatchMessage(@NonNull Message msg) {
            // we manually handle crashes here instead of relying on the
            // Thread.uncaughtExceptionHandler
            // because it allows us to keep this thread running and clean our state related to this
            // inspection safely
            if (msg.getCallback() != null) {
                try {
                    msg.getCallback().run();
                } catch (Throwable th) {
                    mUncaughtExceptionHandler.accept(th);
                }
            } else {
                super.dispatchMessage(msg);
            }
        }
    }
}
