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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.options.SyncOptions
import com.android.builder.tasks.BooleanLatch
import com.android.testutils.concurrency.OnDemandExecutorService
import com.google.common.io.Closer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicInteger

class AsyncResourceProcessorTest {

    private val closer = Closer.create()
    private val executor = OnDemandExecutorService()
    val forkJoinPool: ForkJoinPool by lazy {
        ForkJoinPool(2).also { closer.register(Closeable { it.shutdown() }) }
    }

    @After
    fun close() {
        closer.close()
    }

    @Test
    fun smokeTest() {
        val counter = AtomicInteger()

        createAsyncResourceProcessor(counter).use { processor ->
            processor.submit(FakeNoOpAnalyticsService()) {
                it.incrementAndGet()
            }
            assertThat(counter.get()).isEqualTo(0)
            executor.run(1)
            assertThat(counter.get()).isEqualTo(1)
        }
    }

    /** This test simulates what the verify library resources task does */
    @Test
    fun testCloseAwaitsExecutionCompletion() {
        val counter = AtomicInteger()

        // Steps for the processor to go through.
        val compileSubmitted = BooleanLatch()
        val awaitComplete = BooleanLatch()
        val linkSubmitted = BooleanLatch()
        val processorClosed = BooleanLatch()

        forkJoinPool.submit {
            createAsyncResourceProcessor(counter).use { processor ->
                processor.submit(FakeNoOpAnalyticsService()) {
                    it.incrementAndGet()
                }
                compileSubmitted.signal()
                Thread.yield()

                processor.await()
                awaitComplete.signal()
                Thread.yield()

                processor.submit(FakeNoOpAnalyticsService()) {
                   it.incrementAndGet()
                }
                linkSubmitted.signal()
                Thread.yield()
            }
            processorClosed.signal()
            Thread.yield()
        }

        compileSubmitted.await()
        assertWithMessage("processor await should be blocked on executor running").that(
            awaitComplete.isSignalled
        ).isFalse()
        assertThat(counter.get()).isEqualTo(0)
        executor.runAll()
        assertThat(counter.get()).isEqualTo(1)
        awaitComplete.await()
        assertThat(counter.get()).isEqualTo(1)
        linkSubmitted.await()
        assertThat(counter.get()).isEqualTo(1)
        executor.runAll()
        assertThat(counter.get()).isEqualTo(2)
        processorClosed.await()
        assertThat(counter.get()).isEqualTo(2)
    }

    private fun createAsyncResourceProcessor(counter: AtomicInteger): AsyncResourceProcessor<AtomicInteger> {
        return AsyncResourceProcessor(
            owner = "testTask",
            executor = executor,
            service = counter,
            errorFormatMode = SyncOptions.ErrorFormatMode.HUMAN_READABLE
        )
    }
}
