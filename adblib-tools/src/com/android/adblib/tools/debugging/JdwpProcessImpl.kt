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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceSelector
import com.android.adblib.thisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job

/**
 * Implementation of [JdwpProcess]
 */
internal class JdwpProcessImpl(
  private val session: AdbLibSession,
  override val device: DeviceSelector,
  private val deviceScope: CoroutineScope,
  override val pid: Int
) : JdwpProcess, AutoCloseable {

    private val logger = thisLogger(session)

    override val scope = deviceScope.createChildScope()

    override val processPropertiesFlow: StateFlow<JdwpProcessProperties>
        get() = TODO("Not yet implemented")

    override fun close() {
        val msg = "Closing coroutine scope of JDWP process $pid"
        logger.debug { msg }
        scope.cancel(msg)
    }
}

private fun CoroutineScope.createChildScope(job: Job = SupervisorJob(coroutineContext.job)): CoroutineScope {
    return CoroutineScope(this.coroutineContext + job)
}
