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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.tools.debugging.JdwpProcess
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.DebugViewDumpHandler
import com.android.ddmlib.IDevice
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit

/**
 * Implementation of the ddmlib [Client] interface based on a [JdwpProcess] instance.
 */
internal class AdblibClientWrapper(
    private val deviceClientManager: AdbLibDeviceClientManager,
    private val iDevice: IDevice,
    val jdwpProcess: JdwpProcess
) : Client {

    val clientDataWrapper = ClientData(this, jdwpProcess.pid)

    override fun getDevice(): IDevice {
        return iDevice
    }

    override fun isDdmAware(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getClientData(): ClientData {
        return clientDataWrapper
    }

    override fun kill() {
        TODO("Not yet implemented")
    }

    override fun isValid(): Boolean {
        return jdwpProcess.scope.isActive &&
                jdwpProcess.processPropertiesFlow.value.exception == null
    }

    override fun getDebuggerListenPort(): Int {
        TODO("Not yet implemented")
    }

    override fun isDebuggerAttached(): Boolean {
        TODO("Not yet implemented")
    }

    override fun executeGarbageCollector() {
        TODO("Not yet implemented")
    }

    override fun startMethodTracer() {
        TODO("Not yet implemented")
    }

    override fun stopMethodTracer() {
        TODO("Not yet implemented")
    }

    override fun startSamplingProfiler(samplingInterval: Int, timeUnit: TimeUnit?) {
        TODO("Not yet implemented")
    }

    override fun stopSamplingProfiler() {
        TODO("Not yet implemented")
    }

    override fun requestAllocationDetails() {
        TODO("Not yet implemented")
    }

    override fun enableAllocationTracker(enabled: Boolean) {
        TODO("Not yet implemented")
    }

    override fun notifyVmMirrorExited() {
        TODO("Not yet implemented")
    }

    override fun listViewRoots(replyHandler: DebugViewDumpHandler?) {
        TODO("Not yet implemented")
    }

    override fun captureView(
        viewRoot: String,
        view: String,
        handler: DebugViewDumpHandler
    ) {
        TODO("Not yet implemented")
    }

    override fun dumpViewHierarchy(
        viewRoot: String,
        skipChildren: Boolean,
        includeProperties: Boolean,
        useV2: Boolean,
        handler: DebugViewDumpHandler
    ) {
        TODO("Not yet implemented")
    }

    override fun dumpDisplayList(viewRoot: String, view: String) {
        TODO("Not yet implemented")
    }
}
