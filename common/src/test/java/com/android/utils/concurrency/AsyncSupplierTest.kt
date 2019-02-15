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

import com.android.testutils.VirtualTimeScheduler
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.UnsupportedOperationException
import java.time.Duration
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class AsyncSupplierTest {
  /**
   * Executor that fails if something is scheduled
   */
  object DoNotRunExecutor : AbstractExecutorService() {
    override fun execute(command: Runnable?) = throw UnsupportedOperationException("Tried to schedule a task on the DoNotRunExecutor")
    override fun isTerminated(): Boolean = false
    override fun shutdown() = throw UnsupportedOperationException()
    override fun shutdownNow(): MutableList<Runnable> = throw UnsupportedOperationException()
    override fun isShutdown(): Boolean = false
    override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean = throw UnsupportedOperationException()
  }

  @Test
  fun doNotPrefetchByDefault() {
    CachedAsyncSupplier(compute = { 2 }, executor = DoNotRunExecutor)
  }

  @Test
  fun expireValue() {
    val virtualExecutor = VirtualTimeScheduler()
    var counter = 0
    var upToDate = true

    val supplier = CachedAsyncSupplier(compute = { ++counter },
                                       isUpToDate = { upToDate },
                                       executor = virtualExecutor)
    supplier.get()
    virtualExecutor.advanceBy(10)
    assertEquals(1, counter)

    // The value is up to date go get() will return the existing value and won't schedule an update
    supplier.get()
    virtualExecutor.advanceBy(10)
    assertEquals(1, counter)

    upToDate = false
    supplier.get()
    virtualExecutor.advanceBy(10)
    assertEquals(2, counter)
    assertEquals(2, supplier.now)
    virtualExecutor.advanceBy(10)
    assertEquals(3, supplier.now)
    upToDate = true
    assertEquals(3, supplier.now)
  }

  @Test
  fun testSupplierWithTimestamp() {
    var counter = 0
    var clock = 0L
    val supplierWithTimestamp = CachedAsyncSupplierWithTimestamp(compute = { ++counter },
                                                                 isUpToDate = { timestamp, _ -> clock == timestamp },
                                                                 executor = MoreExecutors.newDirectExecutorService(),
                                                                 timestampSource = { clock })
    supplierWithTimestamp.get()
    assertEquals(1, counter)
    supplierWithTimestamp.get()
    assertEquals(1, counter)

    // This will expire the existing value
    clock = 3
    supplierWithTimestamp.get()
    assertEquals(2, counter)
  }

  @Test
  fun testAutoRefreshCachedValue() {
    var counter = 0
    val virtualExecutor = VirtualTimeScheduler()

    // For testing, we create a CachedAsyncSupplier that is always out of date and will always compute the new value
    val expensiveSupplier = CachedAsyncSupplier(compute = { ++counter },
                                                isUpToDate = { false },
                                                executor = MoreExecutors.newDirectExecutorService())
    AsyncSupplierRefresher(asyncSupplier = expensiveSupplier,
                           executor = virtualExecutor,
                           refreshDuration = Duration.ofSeconds(30))

    for (i in 0..2) {
      // Advancing 15 seconds should not trigger a refresh
      virtualExecutor.advanceBy(15, TimeUnit.SECONDS)
      assertEquals(i, counter)
      // Now we are over the threshold and a refresh should happen
      virtualExecutor.advanceBy(15, TimeUnit.SECONDS)
      assertEquals(i + 1, counter)
    }
  }
}