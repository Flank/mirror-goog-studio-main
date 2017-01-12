/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ide.common.internal;

import com.android.annotations.NonNull;
import com.android.ide.common.util.JvmWideVariable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton executor service.
 */
public class ExecutorSingleton {

    @NonNull
    private static final JvmWideVariable<ExecutorService> sExecutorService =
            new JvmWideVariable<>(
                    ExecutorSingleton.class.getName(),
                    "sExecutorService",
                    ExecutorService.class,
                    null);

    @NonNull
    private static final JvmWideVariable<Integer> sThreadPoolSize =
            new JvmWideVariable<>(
                    ExecutorSingleton.class.getName(),
                    "sThreadPoolSize",
                    Integer.class,
                    Runtime.getRuntime().availableProcessors());

    @NonNull
    public static ExecutorService getExecutor() {
        return sExecutorService.doSupplierSynchronized(() -> {
            if (sExecutorService.get() == null) {
                sExecutorService.set(Executors.newFixedThreadPool(sThreadPoolSize.get()));
            }
            return sExecutorService.get();
        });
    }

    @NonNull
    public static void shutdown() {
        sExecutorService.doRunnableSynchronized(() -> {
            if (sExecutorService.get() != null) {
                sExecutorService.get().shutdown();
                sExecutorService.set(null);
            }
        });
    }

    /**
     * Changes the thread pool size for the singleton ExecutorService.
     *
     * <b>Caution</b>: This will have no effect if getExecutor() has already been called until the
     * executor is shutdown and reinitialized.
     *
     * @param threadPoolSize the number of threads to use.
     */
    public static void setThreadPoolSize(int threadPoolSize) {
        sExecutorService.doRunnableSynchronized(() -> {
            sThreadPoolSize.set(threadPoolSize);
        });
    }
}
