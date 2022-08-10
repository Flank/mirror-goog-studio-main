/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.testutils.CoroutineTestUtils.yieldUntil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class SharedSerializedFlowTest : AdbLibToolsTestBase() {

    @Test
    fun testFlowWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlow(session, flow {
            for (i in 1..5) {
                emit(MyTestValue(i))
            }
        }))

        // Act
        val values = sharedFlow.flow.toList()

        // Assert
        Assert.assertEquals(5, values.size)
        Assert.assertEquals(1, values[0].id)
        Assert.assertEquals(2, values[1].id)
        Assert.assertEquals(3, values[2].id)
        Assert.assertEquals(4, values[3].id)
        Assert.assertEquals(5, values[4].id)
    }

    @Test
    fun testEmitCallsToMultipleFlowsAreSerialized(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val consumerFlowCount = 10
        val startSemaphore = Semaphore(consumerFlowCount, consumerFlowCount)
        val sharedFlow = registerCloseable(SharedSerializedFlow(session, flow {
            repeat(consumerFlowCount) {
                startSemaphore.acquire()
            }
            for (i in 1..5) {
                emit(MyTestValue(i))
            }
        }))

        // Act
        val def = (1..consumerFlowCount).map {
            async {
                sharedFlow.flow.map {
                    val start = System.nanoTime()
                    delay(1)
                    val end = System.nanoTime()
                    FlowTimeSpan(start, end)
                }.onStart {
                    startSemaphore.release()
                }.toList()
            }
        }
        def.awaitAll()

        // Assert

        // Sort spans per start nano
        val timespans = def.map { it.await() }.flatten().sortedBy { it.startNano }
        Assert.assertEquals(5 * consumerFlowCount, timespans.size)

        // Assert that the end nano of each entry is <= the start nano of the next entry,
        // which verifies that all "emit" calls to consumer flows were never made
        // concurrently
        timespans.fold(FlowTimeSpan(Long.MIN_VALUE, Long.MIN_VALUE)) { prev, cur ->
            Assert.assertTrue(
                "'${prev.endNano} <= ${cur.startNano}' is expected to be true",
                prev.endNano <= cur.startNano
            )
            cur
        }
    }

    @Test
    fun testCloseTerminatesAllFlows(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val consumerFlowCount = 10
        val startSemaphore = Semaphore(consumerFlowCount, consumerFlowCount)
        val sharedFlow = registerCloseable(SharedSerializedFlow(session, flow {
            repeat(consumerFlowCount) {
                startSemaphore.acquire()
            }
            while (true) {
                emit(MyTestValue(10))
                delay(5)
            }
        }))

        // Act
        val emitCallCount = AtomicInteger(0)
        val def = (1..consumerFlowCount).map {
            async {
                sharedFlow.flow.map {
                    emitCallCount.incrementAndGet()
                    it
                }.onStart {
                    startSemaphore.release()
                }.toList()
            }
        }
        yieldUntil {
            emitCallCount.get() >= 50
        }
        sharedFlow.close()

        // Assert
        def.forEach {
            assertThrows<CancellationException> { it.await() }
        }
    }

    @Test
    fun testExceptionIsThrownAfterClose(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlow(session, flow {
            while (true) {
                emit(MyTestValue(10))
                delay(5)
            }
        }))

        // Act
        sharedFlow.close()
        exceptionRule.expect(ClosedFlowException::class.java)
        sharedFlow.flow.collect {
        }
    }

    @Test
    fun testUpstreamFlowExceptionIsPropagated(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlow(session, flow {
            emit(MyTestValue(5))
            delay(5)
            throw IOException("Exception from flow")
        }))

        // Act
        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("Exception from flow")
        sharedFlow.flow.toList()
    }

    @Test
    fun testUpstreamFlowExceptionIsPropagatedOnNewFlows(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlow(session, flow {
            emit(MyTestValue(5))
            delay(5)
            throw IOException("Exception from flow")
        }))

        // Act
        val res1 = runCatching { sharedFlow.flow.toList() }
        val res2 = runCatching { sharedFlow.flow.toList() }
        val res3 = runCatching { sharedFlow.flow.toList() }

        // Assert
        Assert.assertTrue(res1.isFailure)
        assertThrows<IOException> { res1.getOrThrow() }
        Assert.assertTrue(res2.isFailure)
        assertThrows<IOException> { res2.getOrThrow() }
        Assert.assertTrue(res3.isFailure)
        assertThrows<IOException> { res3.getOrThrow() }
    }

    @Test
    fun testUpstreamFlowExceptionIsPropagatedOnlyWhenCollected(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlow(session, flow {
            emit(MyTestValue(5))
            delay(5)
            throw IOException("Exception from flow")
        }))

        // Act
        val value = sharedFlow.flow.first()

        // Assert
        Assert.assertTrue(value.id == 5)
    }

    @Test
    fun testUpstreamFlowExceptionIsPropagatedOnSecondCollect(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlow(session, flow {
            emit(MyTestValue(5))
            delay(5)
            throw IOException("Exception from flow")
        }))
        sharedFlow.flow.first()

        // Act
        val res1 = runCatching { sharedFlow.flow.toList() }

        // Assert
        Assert.assertTrue(res1.isFailure)
        assertThrows<IOException> { res1.getOrThrow() }
    }

    @Test
    fun testDownstreamFlowExceptionIsPropagated(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlow(session, flow {
            while(true) {
                emit(MyTestValue(5))
                delay(5)
            }
        }))

        // Act
        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("Exception from flow")
        sharedFlow.flow.collect {
            throw IOException("Exception from flow")
        }
    }


    data class MyTestValue(var id: Int = 0)
    data class FlowTimeSpan(val startNano: Long, val endNano: Long)
}
