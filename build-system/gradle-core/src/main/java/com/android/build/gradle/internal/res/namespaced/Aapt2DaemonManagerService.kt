/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("Aapt2DaemonManagerService")

package com.android.build.gradle.internal.res.namespaced

import com.android.annotations.concurrency.GuardedBy
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.builder.internal.aapt.v2.Aapt2DaemonImpl
import com.android.builder.internal.aapt.v2.Aapt2DaemonManager
import com.android.builder.internal.aapt.v2.Aapt2DaemonTimeouts
import com.android.ide.common.process.ProcessException
import com.android.repository.Revision
import com.android.sdklib.BuildToolInfo
import com.android.utils.ILogger
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Utilities related to AAPT2 Daemon management.
 */

private val daemonTimeouts = Aapt2DaemonTimeouts()
private val daemonExpiryTimeSeconds = TimeUnit.MINUTES.toSeconds(3)
private val maintenanceIntervalSeconds = TimeUnit.MINUTES.toSeconds(1)

private data class AaptServiceKey(val aapt2Version: Revision) :
        WorkerActionServiceRegistry.ServiceKey<Aapt2DaemonManager> {
    override val type: Class<Aapt2DaemonManager> get() = Aapt2DaemonManager::class.java
}

private class RegisteredAaptService(override val service: Aapt2DaemonManager)
    : WorkerActionServiceRegistry.RegisteredService<Aapt2DaemonManager> {
    override fun shutdown() {
        service.shutdown()
    }
}

/** Intended for use from worker actions. */
@Throws(ProcessException::class, IOException::class)
fun <T: Any>useAaptDaemon(
        aapt2Version: Revision,
        serviceRegistry: WorkerActionServiceRegistry = WorkerActionServiceRegistry.INSTANCE,
        block: (Aapt2DaemonManager.LeasedAaptDaemon) -> T) : T {
    return getAaptDaemon(aapt2Version, serviceRegistry).use(block)
}

/** Intended for use from java worker actions. */
@JvmOverloads
fun getAaptDaemon(
        aapt2Version: Revision,
        serviceRegistry: WorkerActionServiceRegistry = WorkerActionServiceRegistry.INSTANCE) : Aapt2DaemonManager.LeasedAaptDaemon =
        serviceRegistry.getService(AaptServiceKey(aapt2Version)).service.leaseDaemon()



/** Registers an AAPT2 daemon manager for the given build tools, keyed from version. Idempotent. */
fun registerAaptService(
        buildToolInfo: BuildToolInfo,
        logger: ILogger,
        serviceRegistry: WorkerActionServiceRegistry) {
    serviceRegistry.registerService(AaptServiceKey(buildToolInfo.revision), {
        val aaptExecutablePath = buildToolInfo.getPath(BuildToolInfo.PathId.AAPT2)
        val manager = Aapt2DaemonManager(logger = logger,
                daemonFactory = { displayId ->
                    Aapt2DaemonImpl(
                            displayId = "#$displayId",
                            aaptExecutable = Paths.get(aaptExecutablePath),
                            daemonTimeouts = daemonTimeouts,
                            logger = logger)
                },
                expiryTime = daemonExpiryTimeSeconds,
                expiryTimeUnit = TimeUnit.SECONDS,
                listener = Aapt2DaemonManagerMaintainer())
        RegisteredAaptService(manager)
    })
}

/**
 * Responsible for scheduling maintenance on the Aapt2Service.
 */
private class Aapt2DaemonManagerMaintainer : Aapt2DaemonManager.Listener {
    @GuardedBy("this")
    private var maintainExecutor: ScheduledExecutorService? = null
    @GuardedBy("this")
    private var maintainAction: ScheduledFuture<*>? = null

    @Synchronized
    override fun firstDaemonStarted(manager: Aapt2DaemonManager) {
        maintainExecutor = Executors.newSingleThreadScheduledExecutor()
        maintainAction = maintainExecutor!!.
                scheduleAtFixedRate(
                        manager::maintain,
                        daemonExpiryTimeSeconds + maintenanceIntervalSeconds,
                        maintenanceIntervalSeconds,
                        TimeUnit.SECONDS)
    }

    @Synchronized
    override fun lastDaemonStopped() {
        maintainAction!!.cancel(false)
        maintainExecutor!!.shutdown()
        maintainAction = null
        maintainExecutor = null
    }
}

