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
package com.android.sdklib.deviceprovisioner

import com.android.annotations.concurrency.GuardedBy
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An action that runs periodically, but when needed, we can immediately trigger a refresh. After a
 * manually-triggered refresh, we wait the usual period before running again.
 *
 * If the action throws an exception, it will propagate to the parent scope, and no further actions
 * will be scheduled.
 *
 * If runNow() is invoked while the action is running, the action will be cancelled, and then run
 * again. Note that cancellation is cooperative, and thus it's possible for the action to still be
 * running when the next invocation starts.
 */
class PeriodicAction(
  private val scope: CoroutineScope,
  private val period: Duration,
  private val action: suspend () -> Unit
) {
  private val lock = Object()

  /** The current job, which is set to null when we are shut down. */
  @GuardedBy("lock") private var job: Job? = Job() // unused non-null job to start

  init {
    runAfterDelay()
  }

  private fun updateJob(block: suspend () -> Unit): Job {
    synchronized(lock) {
      (job ?: throw CancellationException()).cancel()
      return scope.launch { block() }.also { job = it }
    }
  }

  private fun runAfterDelay(): Job = updateJob {
    delay(period.toMillis())
    action()
    runAfterDelay()
  }

  fun runNow(): Job = updateJob {
    action()
    runAfterDelay()
  }

  fun cancel() {
    synchronized(lock) {
      job?.cancel()
      job = null
    }
  }
}
