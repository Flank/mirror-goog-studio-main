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

import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AppInspectionExecutors {
    private static final String STUDIO_PREFIX = "Studio:";

    public static Executor DISK_IO_EXECUTOR =
            Executors.newFixedThreadPool(
                    4,
                    new ThreadFactory() {
                        private static final String THREAD_NAME_STEM =
                                STUDIO_PREFIX + "ins_io%d [app-inspection io pool]";

                        private final AtomicInteger mThreadId = new AtomicInteger(0);

                        @SuppressLint("DefaultLocale")
                        @Override
                        public Thread newThread(@NonNull Runnable r) {
                            Thread t = new Thread(r);
                            t.setName(String.format(THREAD_NAME_STEM, mThreadId.getAndIncrement()));
                            return t;
                        }
                    });

    public static HandlerThreadExecutor newHandlerThreadExecutor(
            String inspectorId, Consumer<Throwable> crashListener) {
        // Thread name is prepared in the special way,
        // because the native level name is cut to first 15 characters.
        // Thus STUDIO_PREFIX and shortened inspector id are squeezed in first 15 characters.
        // On java level full thread name is preserved, so full information about
        // inspector added at the end for better readability in tools relying on java thread name.
        String name =
                STUDIO_PREFIX
                        + shortenedInspectorId(inspectorId)
                        + " ["
                        + inspectorId
                        + " primary]";
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new HandlerThreadExecutor(thread, crashListener);
    }

    /**
     * Threads on the native level have 16 characters limit including last `\0`. First 7 characters
     * are taken by "Studio:" prefix, as a result we have 8 characters for ourselves
     */
    private static String shortenedInspectorId(String inspectorId) {
        String[] split = inspectorId.split("\\.");
        if (split.length == 1) {
            // no dot in original id, so simply take first letters
            return inspectorId.substring(0, min(8, inspectorId.length()));
        } else {
            String first = split[split.length - 2];
            String firstShort = first.substring(0, min(4, first.length()));
            String second = split[split.length - 1];
            String secondShort = second.substring(0, min(3, first.length()));
            return firstShort + "." + secondShort;
        }
    }

    /**
     * Creates an executor that delegates a work to the given one and
     * forwards all uncaught exceptions to {@code crashListener}.
     * <p>
     * This way it is possible to share an executor across multiple
     * inspectors and correctly attribute exceptions to inspector
     * that failed.
     */
    public static Executor newDelegateExecutor(
            Executor delegate, Consumer<Throwable> crashListener) {
        return command ->
                delegate.execute(
                        () -> {
                            try {
                                command.run();
                            } catch (Throwable th) {
                                crashListener.accept(th);
                            }
                        });
    }
}
