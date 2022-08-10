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

import com.android.adblib.AdbSession
import com.android.adblib.thisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Provides serialized access to a resource that should be active only when there is
 * one or more active consumer. This is essentially a "pool" where each consumer ends
 * up sharing access to a single underlying resource.
 *
 * This is useful when there is a need to coordinate multiple consumers of a single shared
 * resource that is expensive to acquire.
 *
 * For example, consumers may need to access a resource concurrently at times and durations
 * that are not known ahead of time.
 *
 * * The resource is acquired through an arbitrary [factory] coroutine
 * * Subscribing to the resource is done through a coroutine, creating the resource
 *   lazily as soon as the first subscriber arrives.
 * * Unsubscribing from the resource is done through a regular method (as opposed to
 *   a coroutine) so that the method can be called from any [AutoCloseable.close]
 *   method
 * * [Closing][close] an instance of this class ensures the underlying resource
 *   is also [closed][close] synchronously, cancelling any pending subscribers.
 * * Instances of this class are safe to use concurrently from multiple threads.
 */
internal class ReferenceCountedResource<T : AutoCloseable>(
    session: AdbSession,
    /**
     * The [CoroutineContext] used to run [factory] when a new resource instance is needed
     */
    factoryContext: CoroutineContext = EmptyCoroutineContext,
    /**
     * The [factory] coroutine, i.e. the coroutine that is called when a new instance of the
     * underlying resource is needed. It is guaranteed [factory] will never be executed
     * concurrently.
     */
    private val factory: suspend () -> T,
) : AutoCloseable {

    private val logger = thisLogger(session)

    private val scope = CoroutineScope(factoryContext)

    /**
     * Lock for [referenceCount], [currentFactoryJob] and [closed]
     */
    private val lock = Any()

    private var referenceCount = 0

    private var currentFactoryJob: Deferred<T>? = null

    private var closed: Boolean = false

    /**
     * Acquire the resource if needed and increment the reference count
     */
    suspend fun retain(): T {
        return synchronized(lock) {
            if (closed) {
                throw IllegalStateException("ReferenceCountedResource has been closed")
            }
            retainAsync()
        }.await()
    }

    /**
     * Decrement the reference count and [AutoCloseable.close] the resource if this was
     * the last reference.
     */
    fun release() {
        synchronized(lock) {
            if (--referenceCount == 0) {
                cancelFactoryJob()
            }
            logger.verbose { "release: ref. count after=$referenceCount" }
        }
    }

    override fun close() {
        val closing = synchronized(lock) {
            if (!closed) {
                closed = true
                true
            } else {
                false
            }
        }

        if (closing) {
            logger.debug { "Closing" }
            scope.cancel("ResourcePool has been closed")
            cancelFactoryJob()
        }
    }

    private fun retainAsync(): Deferred<T> {
        logger.verbose { "retain: ref. count before=$referenceCount" }
        return if (referenceCount++ == 0) {
            logger.debug { "Creating resource from factory" }
            assert(currentFactoryJob == null)
            scope.async {
                val value = factory()
                logger.debug { "Created resource from factory: $value" }
                value
            }.also {
                currentFactoryJob = it
            }
        } else {
            // We have a positive reference count, and we were not the first one, so we
            // are guaranteed to have a pending job
            assert(currentFactoryJob != null)
            currentFactoryJob
                ?: throw IllegalStateException("ReferenceCountedResource job should be initialized")
        }
    }

    private fun cancelFactoryJob() {
        var toClose: T? = null
        synchronized(lock) {
            logger.debug { "Cancelling current job: $currentFactoryJob" }
            currentFactoryJob?.let { deferred ->
                deferred.invokeOnCompletion {
                    if (it == null) {
                        @OptIn(ExperimentalCoroutinesApi::class)
                        toClose = deferred.getCompleted()
                    }
                }
                deferred.cancel()
            }
            currentFactoryJob = null
        }
        // We need to close the value if we completed
        toClose?.let {
            logger.debug { "Calling close() on value: $it" }
            it.close()
        }
    }
}
