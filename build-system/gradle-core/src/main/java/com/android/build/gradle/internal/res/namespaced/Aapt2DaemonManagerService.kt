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

import com.android.SdkConstants
import com.android.annotations.concurrency.GuardedBy
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.builder.internal.aapt.v2.Aapt2DaemonImpl
import com.android.builder.internal.aapt.v2.Aapt2DaemonManager
import com.android.builder.internal.aapt.v2.Aapt2DaemonTimeouts
import com.android.ide.common.process.ProcessException
import com.android.utils.ILogger
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import java.io.File
import java.io.IOException
import java.nio.file.Files
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

sealed class Aapt2ServiceKey : WorkerActionServiceRegistry.ServiceKey<Aapt2DaemonManager> {
    final override val type: Class<Aapt2DaemonManager> get() = Aapt2DaemonManager::class.java
}

private data class Aapt2FileServiceKey(val file: File) : Aapt2ServiceKey()

private class RegisteredAaptService(override val service: Aapt2DaemonManager)
    : WorkerActionServiceRegistry.RegisteredService<Aapt2DaemonManager> {
    override fun shutdown() {
        service.shutdown()
    }
}

/** Intended for use from worker actions. */
@Throws(ProcessException::class, IOException::class)
fun <T: Any>useAaptDaemon(
    aapt2ServiceKey: Aapt2ServiceKey,
    serviceRegistry: WorkerActionServiceRegistry = WorkerActionServiceRegistry.INSTANCE,
    block: (Aapt2DaemonManager.LeasedAaptDaemon) -> T) : T {
    return getAaptDaemon(aapt2ServiceKey, serviceRegistry).use(block)
}

/** Intended for use from java worker actions. */
@JvmOverloads
fun getAaptDaemon(
    aapt2ServiceKey: Aapt2ServiceKey,
    serviceRegistry: WorkerActionServiceRegistry = WorkerActionServiceRegistry.INSTANCE)
        : Aapt2DaemonManager.LeasedAaptDaemon =
    serviceRegistry.getService(aapt2ServiceKey).service.leaseDaemon()

@JvmOverloads
fun registerAaptService(
    aapt2FromMaven: FileCollection,
    logger: ILogger,
    serviceRegistry: WorkerActionServiceRegistry = WorkerActionServiceRegistry.INSTANCE
): Aapt2ServiceKey {
    val dir = aapt2FromMaven.singleFile
    val key = Aapt2FileServiceKey(dir)
    val aaptExecutablePath = dir.toPath().resolve(SdkConstants.FN_AAPT2)

    if (!Files.exists(aaptExecutablePath)) {
        throw InvalidUserDataException(
            "Specified AAPT2 executable does not exist: $aaptExecutablePath. "
                +"Must supply one of aapt2 from maven or custom location.")
    }

    serviceRegistry.registerService(key) {
        val manager = Aapt2DaemonManager(logger = logger,
            daemonFactory = { displayId ->
                Aapt2DaemonImpl(
                    displayId = "#$displayId",
                    aaptExecutable = aaptExecutablePath,
                    daemonTimeouts = daemonTimeouts,
                    logger = logger)
            },
            expiryTime = daemonExpiryTimeSeconds,
            expiryTimeUnit = TimeUnit.SECONDS,
            listener = Aapt2DaemonManagerMaintainer())
        RegisteredAaptService(manager)
    }
    return key
}

/**
 * Responsible for scheduling maintenance on the Aapt2Service.
 *
 * There are three ways the daemons can all be shut down.
 * 1. An explicit call of [Aapt2DaemonManager.shutdown]. (e.g. at the end of each build invocation.)
 * 2. All the daemons being timed out by the logic in [Aapt2DaemonManager.maintain].
 *    Calls to maintain are scheduled below, and only while there are daemons running to avoid
 *    leaking a thread.
 * 3. The JVM shutdown hook, which like (2) is only kept registered while daemons are running.
 */
private class Aapt2DaemonManagerMaintainer : Aapt2DaemonManager.Listener {
    @GuardedBy("this")
    private var maintainExecutor: ScheduledExecutorService? = null
    @GuardedBy("this")
    private var maintainAction: ScheduledFuture<*>? = null
    @GuardedBy("this")
    private var shutdownHook: Thread? = null

    @Synchronized
    override fun firstDaemonStarted(manager: Aapt2DaemonManager) {
        maintainExecutor = Executors.newSingleThreadScheduledExecutor()
        maintainAction = maintainExecutor!!.
                scheduleAtFixedRate(
                        manager::maintain,
                        daemonExpiryTimeSeconds + maintenanceIntervalSeconds,
                        maintenanceIntervalSeconds,
                        TimeUnit.SECONDS)
        shutdownHook = Thread { shutdown(manager) }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    @Synchronized
    override fun lastDaemonStopped() {
        maintainAction!!.cancel(false)
        maintainExecutor!!.shutdown()
        maintainAction = null
        maintainExecutor = null
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook!!)
            shutdownHook = null
        }
    }

    private fun shutdown(manager: Aapt2DaemonManager) {
        // Unregister the hook, as shutting down the daemon manager will trigger lastDaemonStopped()
        // and removeShutdownHook throws if called during shutdown.
        synchronized(this) {
            this.shutdownHook = null
        }
        manager.shutdown()
    }
}

