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

package com.android.ide.common.internal;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.testutils.classloader.SingleClassLoader;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.Test;

/**
 * Test cases for {@link ExecutorSingleton}.
 */
public class ExecutorSingletonTest {

    @NonNull private ExecutorService executorService = ExecutorSingleton.getExecutor();

    @Test
    public void testSingletons() {
        assertThat(ExecutorSingleton.getExecutor()).isSameAs(executorService);
    }

    @Test
    public void testSingletons_DifferentClassLoaders() throws Exception {
        // Load ExecutorSingleton with a custom class loader
        SingleClassLoader classLoader = new SingleClassLoader(ExecutorSingleton.class.getName());
        Class clazz = classLoader.load();
        assertThat(clazz.getClassLoader()).isNotEqualTo(ExecutorSingleton.class.getClassLoader());

        // Get ExecutorService from ExecutorSingleton loaded by the custom class loader
        //noinspection unchecked
        Method method = clazz.getMethod("getExecutor");
        ExecutorService executorService2 = (ExecutorService) method.invoke(null);

        // Assert that it is the same instance
        assertThat(executorService2).isSameAs(executorService);
    }

    @Test
    public void testShutdown() {
        ExecutorSingleton.shutdown();
        assertThat(executorService.isShutdown()).isTrue();

        // Check that it's okay to shutdown twice
        ExecutorSingleton.shutdown();
        assertThat(executorService.isShutdown()).isTrue();

        // Check that if we call getExecutor() again, we get a new instance
        ExecutorService executorService2 = ExecutorSingleton.getExecutor();
        assertThat(executorService2).isNotSameAs(executorService);

        // Check that if we call getExecutor() once more, we get the same instance
        ExecutorService executorService3 = ExecutorSingleton.getExecutor();
        assertThat(executorService3).isSameAs(executorService2);

        // Check that if we shutdown one more time, the current instance gets shut down too
        ExecutorSingleton.shutdown();
        assertThat(executorService3.isShutdown()).isTrue();
    }

    @Test
    public void testShutdown_DifferentClassLoaders() throws Exception {
        // Load ExecutorSingleton with a custom class loader
        SingleClassLoader classLoader = new SingleClassLoader(ExecutorSingleton.class.getName());
        Class clazz = classLoader.load();
        assertThat(clazz.getClassLoader()).isNotEqualTo(ExecutorSingleton.class.getClassLoader());

        // Shutdown ExecutorService from ExecutorSingleton loaded by the custom class loader
        //noinspection unchecked
        Method method = clazz.getMethod("shutdown");
        method.invoke(null);

        // Check that ExecutorService from the default class loader is shut down
        assertThat(executorService.isShutdown()).isTrue();
    }

    @Test
    public void testSetThreadPoolSize() {
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        assertThat(((ThreadPoolExecutor) executorService).getCorePoolSize())
                .isEqualTo(threadPoolSize);

        // Set a new thread pool size
        ExecutorSingleton.setThreadPoolSize(threadPoolSize + 1);

        // Do not shut down ExecutorService yet, expect that the new thread pool size does not take
        // effect
        assertThat(((ThreadPoolExecutor) ExecutorSingleton.getExecutor()).getCorePoolSize())
                .isEqualTo(threadPoolSize);

        // Now shut down ExecutorService and expect that the new thread pool size takes effect
        ExecutorSingleton.shutdown();
        assertThat(((ThreadPoolExecutor) ExecutorSingleton.getExecutor()).getCorePoolSize())
                .isEqualTo(threadPoolSize + 1);

        // Reset the thread pool size so the next test can run with the default thread pool size
        ExecutorSingleton.setThreadPoolSize(threadPoolSize);
        ExecutorSingleton.shutdown();
        assertThat(((ThreadPoolExecutor) ExecutorSingleton.getExecutor()).getCorePoolSize())
                .isEqualTo(threadPoolSize);
    }

    @Test
    public void testSetThreadPoolSize_DifferentClassLoaders() throws Exception {
        int threadPoolSize = Runtime.getRuntime().availableProcessors();

        // Load ExecutorSingleton with a custom class loader
        SingleClassLoader classLoader = new SingleClassLoader(ExecutorSingleton.class.getName());
        Class clazz = classLoader.load();
        assertThat(clazz.getClassLoader()).isNotEqualTo(ExecutorSingleton.class.getClassLoader());

        // Set a new thread pool size from ExecutorSingleton loaded by the custom class loader
        //noinspection unchecked
        Method method = clazz.getMethod("setThreadPoolSize", int.class);
        method.invoke(null, threadPoolSize + 1);

        // Do not shut down ExecutorService yet, expect that the new thread pool size does not take
        // effect
        assertThat(((ThreadPoolExecutor) ExecutorSingleton.getExecutor()).getCorePoolSize())
                .isEqualTo(threadPoolSize);

        // Now shut down ExecutorService and expect that the new thread pool size takes effect
        ExecutorSingleton.shutdown();
        assertThat(((ThreadPoolExecutor) ExecutorSingleton.getExecutor()).getCorePoolSize())
                .isEqualTo(threadPoolSize + 1);

        // Reset the thread pool size so the next test can run with the default thread pool size
        ExecutorSingleton.setThreadPoolSize(threadPoolSize);
        ExecutorSingleton.shutdown();
        assertThat(((ThreadPoolExecutor) ExecutorSingleton.getExecutor()).getCorePoolSize())
                .isEqualTo(threadPoolSize);
    }
}
