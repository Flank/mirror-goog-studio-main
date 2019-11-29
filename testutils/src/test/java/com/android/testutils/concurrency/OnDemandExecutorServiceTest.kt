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
package com.android.testutils.concurrency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnDemandExecutorServiceTest {
    @Test
    fun testQueueing() {
        val executor = OnDemandExecutorService()
        var count = 0

        assertEquals(0, executor.queueSize)

        executor.submit { count++ }
        assertEquals(0, count)
        assertEquals(1, executor.queueSize)

        repeat(99) { executor.submit { count++ } }
        assertEquals(100, executor.queueSize)
        executor.run(10)
        assertEquals(90, executor.queueSize)
        assertEquals(10, count)

        assertEquals(90, executor.runAll())
        assertEquals(100, count)

        executor.submit { count++ }
        assertEquals(1, executor.queueSize)
        try {
            executor.run(10)
            fail("run 10 should have thrown NoSuchElementException. Only 1 task was waiting")
        } catch (e: NoSuchElementException) {
            assertEquals(101, count)
        }
        assertFalse(executor.isShutdown)
        assertFalse(executor.isTerminated)
    }

    @Test
    fun testShutdownNow() {
        var executor = OnDemandExecutorService()

        executor.submit { }
        executor.submit { }
        executor.submit { }
        assertEquals(3, executor.shutdownNow().size)
        assertTrue(executor.isShutdown)
        assertTrue(executor.isTerminated)

        executor = OnDemandExecutorService()
        executor.submit { }
        executor.submit { }
        executor.submit { }
        executor.run(1)
        assertEquals(2, executor.shutdownNow().size)
        assertTrue(executor.isShutdown)
        assertTrue(executor.isTerminated)
    }
}