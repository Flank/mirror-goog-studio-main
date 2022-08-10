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

import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.CoroutineTestUtils.runBlockingWithTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ReferenceCountedResourceTest : AdbLibToolsTestBase() {

    @Test
    fun testUnderlyingResourceIsNotCreatedRightAway() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val res = MyTestResource()
        /*val ref = */ReferenceCountedResource(session) {
            res.creationCount.incrementAndGet()
            res
        }

        // Assert
        Assert.assertEquals(0, res.creationCount.get())
        Assert.assertFalse(res.closed.get())
    }

    @Test
    fun testUnderlyingResourceIsCreatedLazily() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val res = MyTestResource()
        val ref = ReferenceCountedResource(session) {
            res.creationCount.incrementAndGet()
            res
        }

        // Act
        ref.retain()

        // Assert
        Assert.assertEquals(1, res.creationCount.get())
        Assert.assertFalse(res.closed.get())
    }

    @Test
    fun testUnderlyingResourceIsClosedOnRelease() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val res = MyTestResource()
        val ref = ReferenceCountedResource(session) {
            res.creationCount.incrementAndGet()
            res
        }

        // Act
        ref.retain()
        ref.release()

        // Assert
        Assert.assertEquals(1, res.creationCount.get())
        Assert.assertTrue(res.closed.get())
    }

    @Test
    fun testUnderlyingResourceIsClosedOnClose() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val res = MyTestResource()
        val ref = ReferenceCountedResource(session) {
            res.creationCount.incrementAndGet()
            res
        }

        // Act
        ref.retain()
        ref.close()

        // Assert
        Assert.assertEquals(1, res.creationCount.get())
        Assert.assertTrue(res.closed.get())
    }

    @Test
    fun testUnderlyingResourceIsCreatedOnlyOnce() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val res = MyTestResource()
        val ref = ReferenceCountedResource(session) {
            res.creationCount.incrementAndGet()
            res
        }

        // Act
        ref.retained()
        ref.retained()

        // Assert
        Assert.assertEquals(1, res.creationCount.get())
        Assert.assertFalse(res.closed.get())
    }

    @Test
    fun testUnderlyingResourceIsCreatedOnlyOnceFromConcurrentThreads() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val res = MyTestResource()
        val ref = ReferenceCountedResource(session) {
            res.creationCount.incrementAndGet()
            res
        }

        // Act
        val jobs = mutableListOf<Job>()
        for(i in 1..100) {
            jobs.add(launch {
                for (j in 1..100) {
                    ref.retain()
                    // Release all except first one to ensure resource is not closed
                    if (j >= 2) {
                        ref.release()
                    }
                }
            })
        }
        jobs.joinAll()

        // Assert
        Assert.assertEquals(1, res.creationCount.get())
        Assert.assertFalse(res.closed.get())
    }

    @Test
    fun testUnderlyingResourceIsClosedFromConcurrentThreads() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val res = MyTestResource()
        val ref = ReferenceCountedResource(session) {
            res.creationCount.incrementAndGet()
            res.closed.set(false)
            res
        }

        // Act
        val jobs = mutableListOf<Job>()
        for(i in 1..100) {
            jobs.add(launch {
                for (j in 1..100) {
                    ref.retained().use { }
                }
            })
        }
        jobs.joinAll()

        // Assert
        Assert.assertTrue(res.creationCount.get() >= 1)
        Assert.assertTrue(res.closed.get())
    }

    @Test
    fun testUnderlyingResourceIsCreatedOnlyOnceEvenIfDelayed() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val res = MyTestResource()
        val ref = ReferenceCountedResource(session) {
            delay(100)
            res.creationCount.incrementAndGet()
            res
        }

        // Act
        joinAll(launch { ref.retain() }, launch { ref.retain() })

        // Assert
        Assert.assertEquals(1, res.creationCount.get())
        Assert.assertFalse(res.closed.get())
    }

    @Test
    fun testUnderlyingResourceCreationIsCancelledIfRefIsClosedEarly() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val res = MyTestResource()
        val ref = ReferenceCountedResource(session) {
            delay(10_000)
            res.creationCount.incrementAndGet()
            res
        }

        // Act
        val job = launch {
            ref.retain()
        }
        delay(100) // This is much lower than the resource creation delay
        ref.close() // this should cancel the job to create the resource instance
        job.join() // this makes sure cancellation is propagated to the "retain" call

        // Assert
        Assert.assertEquals(0, res.creationCount.get())
        Assert.assertFalse(res.closed.get())
        Assert.assertTrue(job.isCancelled)
    }

    class MyTestResource : AutoCloseable {
        val creationCount = AtomicInteger()
        val closed = AtomicBoolean(false)

        override fun close() {
            closed.set(true)
        }
    }
}
