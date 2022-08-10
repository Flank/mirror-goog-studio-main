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

class SharedSerializedFlowWithReplayTest : AdbLibToolsTestBase() {

    @Test
    fun addReplayValueWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlowWithReplay(session, flow {
            for (i in 1..5) {
                emit(MyTestValue(i))
            }
        }, { it.id } ))

        // Act
        sharedFlow.flow.collect { testValue ->
            if (testValue.id in 2..4) {
                sharedFlow.addReplayValue(testValue)
            }
        }

        // Assert: The 3 replay values should be present (but nothing else, since the upstream flow
        // has fully been collected)
        val values = sharedFlow.flow.toList()
        var index = 0
        Assert.assertEquals(3, values.size)
        Assert.assertEquals(2, values[index++].id)
        Assert.assertEquals(3, values[index++].id)
        Assert.assertEquals(4, values[index++].id)
    }

    @Test
    fun addReplayValueDeduplicationWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlowWithReplay(session, flow {
            for (i in 1..5) {
                // We emit twice
                emit(MyTestValue(i))
                emit(MyTestValue(i))
            }
        }, { it.id } ))

        // Act
        sharedFlow.flow.collect { testValue ->
            if (testValue.id in 2..4) {
                sharedFlow.addReplayValue(testValue)
            }
        }

        // Assert: The 3 replay values should be present (but nothing else, since the upstream flow
        // has fully been collected)
        val values = sharedFlow.flow.toList()
        var index = 0
        Assert.assertEquals(3, values.size)
        Assert.assertEquals(2, values[index++].id)
        Assert.assertEquals(3, values[index++].id)
        Assert.assertEquals(4, values[index++].id)
    }

    @Test
    fun addReplayValueDeduplicationIsOptional(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = createDisconnectedSession()
        val sharedFlow = registerCloseable(SharedSerializedFlowWithReplay(session, flow {
            for (i in 1..5) {
                // We emit twice
                emit(MyTestClass(i))
                emit(MyTestClass(i))
            }
        }))

        // Act
        sharedFlow.flow.collect { testValue ->
            if (testValue.id in 2..4) {
                sharedFlow.addReplayValue(testValue)
            }
        }

        // Assert: The 3 replay values should be present (but nothing else, since the upstream flow
        // has fully been collected)
        val values = sharedFlow.flow.toList()
        var index = 0
        Assert.assertEquals(6, values.size)
        Assert.assertEquals(2, values[index++].id)
        Assert.assertEquals(2, values[index++].id)
        Assert.assertEquals(3, values[index++].id)
        Assert.assertEquals(3, values[index++].id)
        Assert.assertEquals(4, values[index++].id)
        Assert.assertEquals(4, values[index++].id)
    }

    data class MyTestValue(var id: Int = 0)

    class MyTestClass(var id: Int = 0)
}
