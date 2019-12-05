/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.utils.concurrency

import com.android.testutils.concurrency.OnDemandExecutorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvictingExecutorTest {
    @Test
    fun testQueueing() {
        val testExecutor = OnDemandExecutorService()
        var evictingExecutor = EvictingExecutor(testExecutor, 1)

        var result = 0

        // One element queueing
        evictingExecutor.execute { result++ }
        evictingExecutor.execute { result++ }
        evictingExecutor.execute { result++ }
        assertEquals(1, testExecutor.queueSize)
        testExecutor.runAll()
        // Only one task was executed
        assertEquals(1, result)

        result = 0
        evictingExecutor = EvictingExecutor(testExecutor, 2)
        evictingExecutor.execute { result++ }
        evictingExecutor.execute { result++ }
        evictingExecutor.execute { result++ }
        assertEquals(2, testExecutor.queueSize)
        testExecutor.runAll()
        assertEquals(2, result)
        assertFalse(evictingExecutor.isShutdown)
        assertFalse(evictingExecutor.isTerminated)
    }

    @Test
    fun testShutdownNow() {
        val testExecutor = OnDemandExecutorService()
        val evictingExecutor = EvictingExecutor(testExecutor, 10)

        var result = 0

        evictingExecutor.execute { result++ }
        evictingExecutor.execute { result++ }
        evictingExecutor.execute { result++ }
        testExecutor.run(1)
        assertEquals(2, testExecutor.queueSize)
        // At least two tasks must be in the queue
        assertTrue(evictingExecutor.shutdownNow().size > 2)
        assertTrue(evictingExecutor.isShutdown)
        assertTrue(evictingExecutor.isTerminated)
        assertTrue(testExecutor.isShutdown)
        assertTrue(testExecutor.isTerminated)
    }
}