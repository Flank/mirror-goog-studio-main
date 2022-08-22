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
package com.android.adblib.tools

import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Future
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Collection of utility functions for invoking `"suspend"` functions from Java code.
 */
object JavaBridge {

    /**
     * Allows invoking any suspending function from a Java [block] using a
     * [CancellableContinuation], returning a [JavaDeferred] that completes when the
     * suspending function completes (exceptionally or not).
     *
     * The returned [JavaDeferred] can be converted into a specific implementation
     * of [Future], for example [ListenableFuture](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-guava/kotlinx.coroutines.guava/as-listenable-future.html)
     * or [CompletableFuture][java.util.concurrent.CompletableFuture] (see `Deferred.asCompletableFuture`).
     *
     * Example:
     *
     *     AdbSession session = (...)
     *     JavaDeferred<Integer> version = JavaBridge.invokeAsync(session, continuation ->
     *         session.getHostServices().version(continuation)
     *     );
     *     System.out.println("ADB internal version number is " + version.awaitBlocking());
     */
    @JvmStatic
    @JvmOverloads
    fun <T> invokeAsync(
        session: AdbSession,
        block: CheckedFunction<CancellableContinuation<T>, Any?>,
        scope: CoroutineScope = session.scope
    ): JavaDeferred<T> {
        val deferred = scope.async<T>(session.host.ioDispatcher) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val result = block.accept(continuation)
                    if (result != COROUTINE_SUSPENDED) {
                        // Handle case where coroutine terminated right away with a value
                        // (which should be type "T" if caller did not use unsafe casts)
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(result as T)
                    }
                } catch (t: Throwable) {
                    continuation.resumeWithException(t)
                }
            }
        }
        return JavaDeferred(session, scope, deferred)
    }

    /**
     * Allows invoking any [suspending function][block] from Java, blocking the calling
     * thread until the coroutine completes.
     *
     * Throws an [IllegalStateException] if the current thread is not allowed to issue
     * blocking calls (see [AdbSessionHost.isEventDispatchThread]).
     *
     * **Note** Consider using [invokeAsync] as an alternative, as [runBlocking] blocks
     *  the calling thread and may lead to thread starvation if used too liberally.
     *
     * Example:
     *
     *    int version = JavaBridge.runBlocking(continuation ->
     *        session.getHostServices().version(continuation)
     *    );
     *    System.out.println("ADB internal version number is " + version);
     *
     * @see invokeAsync
     * @see kotlinx.coroutines.runBlocking
     * @see AdbSessionHost.isEventDispatchThread
     */
    @JvmStatic
    @JvmOverloads
    fun <T> runBlocking(
        session: AdbSession,
        block: CheckedFunction<CancellableContinuation<T>, Any?>,
        scope: CoroutineScope = session.scope,
        timeoutMillis: Long = Long.MAX_VALUE
    ): T {
        return invokeAsync(session, block, scope).awaitBlocking(timeoutMillis)
    }

    fun throwIfEventDispatchThread(session: AdbSession) {
        if (session.host.isEventDispatchThread) {
            throw IllegalStateException("Running a blocking operation on a event dispatch thread is not allowed")
        }
    }
}

/**
 * A Java friendly wrapper for a [Deferred].
 */
class JavaDeferred<T>(
    val session: AdbSession,
    val scope: CoroutineScope,
    val deferred: Deferred<T>
) {

    /**
     * @see Deferred.isActive
     */
    val isActive: Boolean
        get() = deferred.isActive

    /**
     * @see Deferred.isCancelled
     */
    val isCancelled: Boolean
        get() = deferred.isCancelled

    /**
     * @see Deferred.isCompleted
     */
    val isCompleted: Boolean
        get() = deferred.isCompleted

    /**
     * Adds a callback that is invoked (on a [AdbSessionHost.ioDispatcher] thread) when
     * this [Deferred] completes.
     */
    fun addCallback(callback: CheckedBiConsumer<Throwable?, T?>) {
        scope.launch(session.host.ioDispatcher) {
            try {
                val result = deferred.await()
                callback.accept(null, result)
            } catch (t: Throwable) {
                callback.accept(t, null)
            }
        }
    }

    /**
     * Waits for the [Deferred] to complete, blocking the calling thread.
     *
     * Throws an [IllegalStateException] if the current thread is not allowed to issue
     * blocking calls (see [AdbSessionHost.isEventDispatchThread]).
     */
    @JvmOverloads
    fun awaitBlocking(timeoutMillis: Long = Long.MAX_VALUE): T {
        JavaBridge.throwIfEventDispatchThread(session)

        // Note: This could be optimized once "Deferred.getCompleted()" is finalized.
        return runBlocking {
            withTimeout(timeoutMillis) {
                deferred.await()
            }
        }
    }
}

/**
 * A [java.util.function.Function] which may throw.
 *
 * @param T the type of value supplied to this function.
 * @param R the type of result of this function.
 */
@FunctionalInterface
interface CheckedFunction<T, R> {

    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     * @return the function result
     * @throws Throwable if an error occurs
     */
    @Throws(Throwable::class)
    fun accept(t: T): R
}

/**
 * A [java.util.function.BiConsumer] which may throw.
 *
 * @param T the type of the first argument to the operation
 * @param U the type of the second argument to the operation
 */
@FunctionalInterface
interface CheckedBiConsumer<T, U> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     * @throws Throwable if an error occurs
     */
    @Throws(Throwable::class)
    fun accept(t: T, u: U)
}
