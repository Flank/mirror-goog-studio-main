/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.ide.common.caching;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;

/**
 */
public class CreatingCacheTest {

    private static class FakeFactory implements CreatingCache.ValueFactory<String, String> {
        @Override
        @NonNull
        public String create(@NonNull String key) {
            return key;
        }
    }

    private static class DelayedFactory implements CreatingCache.ValueFactory<String, String> {

        @NonNull
        private final CountDownLatch mLatch;

        public DelayedFactory(@NonNull CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        @NonNull
        public String create(@NonNull String key) {
            try {
                mLatch.await();
            } catch (InterruptedException ignored) {
            }
            return key;
        }
    }

    @Test
    public void testSingleThread() throws Exception {
        CreatingCache<String, String> cache = new CreatingCache<String, String>(new FakeFactory());

        String value1 = cache.get("key");
        assertThat(value1).isEqualTo("key");
        String value2 = cache.get("key");
        assertThat(value2).isEqualTo("key");

        // also check the value returned from the cache is the same for the 2 queries.
        assertThat(value1).isSameAs(value2);
    }

    private static class CacheRunnable implements Runnable {

        @NonNull
        private final CreatingCache<String, String> mCache;
        @Nullable
        private final CountDownLatch mLatch;

        private String mResult;
        private Exception mExceptionResult;

        CacheRunnable(@NonNull CreatingCache<String, String> cache) {
            this(cache, null);
        }

        /**
         * Creates a runnable, that will notify when it's pending on a query.
         *
         * @param cache the cache to query
         * @param latch the latch to countdown when the query is being processed.
         */
        CacheRunnable(
                @NonNull CreatingCache<String, String> cache,
                @Nullable CountDownLatch latch) {
            mCache = cache;
            mLatch = latch;
        }

        @Override
        public void run() {
            try {
                if (mLatch != null) {
                    mResult = mCache.get("foo", state -> mLatch.countDown());
                } else {
                    mResult = mCache.get("foo");
                }
            } catch (Exception e) {
                mExceptionResult = e;
            }
        }

        public String getResult() {
            return mResult;
        }

        public Exception getException() {
            return mExceptionResult;
        }
    }

    @Test
    public void testMultiThread() throws Exception {
        // the latch that controls whether the factory will "create" an item.
        CountDownLatch factoryLatch = new CountDownLatch(1);

        CreatingCache<String, String>
                cache = new CreatingCache<String, String>(new DelayedFactory(factoryLatch));

        // the latch that will be released when the runnable1 is pending its query.
        CountDownLatch latch1 = new CountDownLatch(1);

        CacheRunnable runnable1 = new CacheRunnable(cache, latch1);
        Thread t1 = new Thread(runnable1);
        t1.start();

        // wait on thread1 being waiting on the query, before creating thread2
        latch1.await();

        // the latch that will be released when the runnable1 is pending its query.
        CountDownLatch latch2 = new CountDownLatch(1);

        CacheRunnable runnable2 = new CacheRunnable(cache,latch2);
        Thread t2 = new Thread(runnable2);
        t2.start();

        // wait on thread2 being waiting on the query, before releasing the factory
        latch2.await();

        factoryLatch.countDown();

        // wait on threads being done.
        t1.join();
        t2.join();

        assertThat(runnable1.getResult()).isEqualTo("foo");
        assertThat(runnable2.getResult()).isEqualTo("foo");
        assertThat(runnable1.getResult()).isSameAs(runnable2.getResult());
    }

    @Test
    public void testExceptionInFactory() throws Exception {
        CreatingCache<String, String> cache =
                new CreatingCache<String, String>(
                        key -> {
                            throw new RuntimeException("boo!");
                        });

        // get a key, and swallow the exception (to allow the test to continue
        try {
            cache.get("key1");
        } catch (RuntimeException e) {
            if (!e.getMessage().equals("boo!")) {
                throw e;
            }
        }

        // attempt to clear the cache, to ensure the cache is in a good state.
        cache.clear();
    }

    @Test
    public void testExceptionInFactoryWithConcurrentSecondQuery() throws Exception {
        // the latch that controls whether the factory will "create" an item.
        CountDownLatch factoryLatch = new CountDownLatch(1);

        CreatingCache<String, String> cache =
                new CreatingCache<String, String>(
                        key -> {
                            try {
                                factoryLatch.await();
                            } catch (InterruptedException ignored) {
                            }
                            throw new RuntimeException("boo!");
                        });

        // the latch that will be released when the runnable1 is pending its query.
        CountDownLatch latch1 = new CountDownLatch(1);

        CacheRunnable runnable1 = new CacheRunnable(cache, latch1);
        Thread t1 = new Thread(runnable1);
        t1.start();

        // wait on thread1 being waiting on the query, before creating thread2
        latch1.await();

        // the latch that will be released when the runnable1 is pending its query.
        CountDownLatch latch2 = new CountDownLatch(1);

        CacheRunnable runnable2 = new CacheRunnable(cache, latch2);
        Thread t2 = new Thread(runnable2);
        t2.start();

        // wait on thread2 being waiting on the query, before releasing the factory
        latch2.await();

        factoryLatch.countDown();

        // wait on threads being done.
        t1.join();
        t2.join();

        assertThat(runnable1.getResult()).isNull();
        assertThat(runnable1.getException()).isInstanceOf(RuntimeException.class);

        assertThat(runnable2.getResult()).isNull();
        assertThat(runnable2.getException()).isInstanceOf(RuntimeException.class);

        // attempt to clear the cache, to ensure the cache is in a good state.
        cache.clear();
    }

    @Test(expected = IllegalStateException.class)
    public void testClear() throws Exception {
        // the latch that controls whether the factory will "create" an item.
        // this is never released in this test since we want to try clearing the cache while an
        // item is pending creation.
        CountDownLatch factoryLatch = new CountDownLatch(1);

        final CreatingCache<String, String>
                cache = new CreatingCache<String, String>(new DelayedFactory(factoryLatch));

        // the latch that will be released when the thread is pending its query.
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(new CacheRunnable(cache, latch)).start();

        // wait on thread to be waiting, before trying to clear the cache.
        latch.await();

        cache.clear();
    }
}