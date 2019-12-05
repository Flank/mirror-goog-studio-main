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

@file:JvmName("Aapt2Workers")

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.workers.WorkerExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool

private const val MAX_AAPT2_THREAD_POOL_SIZE = 8
private const val AAPT2_WORKERS_BUILD_SERVICE_NAME = "aapt2-workers-build-service"

/**
 *  Registers aapt2 workers build services. This makes it available for querying, but using
 * [getAapt2WorkersBuildService]
 */
fun registerAapt2WorkersBuildService(project: Project, projectOptions: ProjectOptions) {
    project.gradle.sharedServices.registerIfAbsent(
        AAPT2_WORKERS_BUILD_SERVICE_NAME,
        Aapt2WorkersBuildService::class.java
    ) {
        it.parameters.aapt2ThreadPoolSize.set(
            projectOptions[IntegerOption.AAPT2_THREAD_POOL_SIZE] ?: Integer.min(
                MAX_AAPT2_THREAD_POOL_SIZE,
                ForkJoinPool.getCommonPoolParallelism()
            )
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun getAapt2WorkersBuildService(project: Project): Provider<out Aapt2WorkersBuildService> =
    (project.gradle.sharedServices.registrations.getByName(AAPT2_WORKERS_BUILD_SERVICE_NAME)
            as BuildServiceRegistration<Aapt2WorkersBuildService, Aapt2WorkersBuildService.Params>)
        .service

/** Build service used to access shared thread pool used for aapt2. */
abstract class Aapt2WorkersBuildService : BuildService<Aapt2WorkersBuildService.Params>,
    AutoCloseable {
    interface Params : BuildServiceParameters {
        val aapt2ThreadPoolSize: Property<Int>
    }

    private val aapt2ThreadPool: ForkJoinPool = ForkJoinPool(parameters.aapt2ThreadPoolSize.get())

    @Synchronized
    fun getWorkerForAapt2(
        projectName: String, owner: String, worker: WorkerExecutor, enableGradleWorkers: Boolean
    ): WorkerExecutorFacade {
        return Workers.preferWorkers(
            projectName,
            owner,
            worker,
            enableGradleWorkers,
            aapt2ThreadPool
        )
    }

    @JvmOverloads
    @Synchronized
    fun getSharedExecutorForAapt2(
        projectName: String,
        owner: String,
        executor: ExecutorService? = aapt2ThreadPool
    ): WorkerExecutorFacade {
        return Workers.ProfileAwareExecutorServiceAdapter(projectName, owner, executor!!)
    }

    override fun close() {
        aapt2ThreadPool.shutdown()
    }
}

/** Use the same id for all keys as they are all used to access a single resource. */
private data class Aapt2WorkersBuildServiceKey(val id: String = AAPT2_WORKERS_BUILD_SERVICE_NAME) :
    WorkerActionServiceRegistry.ServiceKey<Aapt2WorkersBuildService> {
    override val type: Class<Aapt2WorkersBuildService>
        get() = Aapt2WorkersBuildService::class.java
}

fun Aapt2WorkersBuildService.registerForWorkers(): WorkerActionServiceRegistry.ServiceKey<Aapt2WorkersBuildService> {
    val key = Aapt2WorkersBuildServiceKey()
    val service = object : WorkerActionServiceRegistry.RegisteredService<Aapt2WorkersBuildService> {
        override val service: Aapt2WorkersBuildService
            get() = this@registerForWorkers

        override fun shutdown() {
            // lifecycle is managed by Gradle
        }
    }
    WorkerActionServiceRegistry.INSTANCE.registerService(key) { service }
    return key
}