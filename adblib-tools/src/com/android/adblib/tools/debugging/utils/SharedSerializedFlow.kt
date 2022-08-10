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

import com.android.adblib.AdbLogger
import com.android.adblib.AdbSession
import com.android.adblib.thisLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Provides **thread-safe** access to multiple instances of [Flow] of [T] values
 * returned by a single [upstreamFlow]. The behavior is similar to [SharedFlow],
 * with a few subtle differences:
 * * [upstreamFlow] is collected only when there is at least one active downstream flow
 * * Downstream flows end when [upstreamFlow] ends
 * * Downstream flows started after [upstreamFlow] end immediately
 * * Active downstream flows are invoked sequentially for each value of [upstreamFlow],
 *   so that there is a guarantee only a single tread at a time has access any given [T]
 *   value at any single time. This is useful if [T] is mutable and not thread-safe.
 * * Unlike [SharedFlow] instances of [SharedSerializedFlow] are not [SharedFlow],
 *   instead downstream flows are created by calling [flow], which return basic [Flow]
 *   instances.
 *
 * Notes on exception handling:
 * * An exception for [upstreamFlow] is forwarded to all active consumer flows, after
 *   which the [upstreamFlow] is inactive and no more elements are collected. The exception
 *   is also propagated immediately to any new downstream flow.
 * * An exception when emitting a value to any active downstream flow is forwarded
 *   to the downstream flow collector, as usual.
 *
 * Conceptually, the implementation works like this (in a coroutine running until
 * [close] is called):
 *
 *    val activeFlowCollectors = (list of active flow collectors at any given time)
 *    while (true) {
 *        (...wait until activeFlowCollectors.isNotEmpty()...)
 *        val value = producer()
 *        foreach (f: activeFlowCollectors) {
 *            f.emit(value)
 *        }
 *    }
 */
internal open class SharedSerializedFlow<T>(
    /**
     * The [AdbSession] used to give access to [AdbLogger] instance
     */
    session: AdbSession,
    /**
     * The [Flow] to collect values from. There is a guarantee the flow is collected at most
     * one time. When the flow collection ends (successfully or with an exception), the flow
     * is never collected again.
     */
    private val upstreamFlow: Flow<T>
) : AutoCloseable {

    private val logger = thisLogger(session)

    private val upstreamFlowScope = CoroutineScope(session.host.ioDispatcher)

    private val activeFlows = CopyOnWriteArrayList<ActiveFlow<T>>()

    private val resumeChannel = Channel<Unit>(UNLIMITED)

    init {
        upstreamFlowScope.launch {
            collectUpstreamFlow()
        }
    }

    /**
     * The thread-safe [Flow] that wraps the [upstreamFlow].
     */
    val flow: Flow<T> = flow {
        withActiveFlow { activeFlow ->
            onStartCollectNewActiveFlow(this)
            while (true) {
                // Wait for value from consumer coroutine
                logger.verbose { "Waiting for value from upstream flow" }
                val result = try {
                    activeFlow.channel.receive()
                } catch (e: ClosedReceiveChannelException) {
                    // We are done
                    break
                }
                try {
                    logger.verbose { "Got value from upstream flow, emitting it to local flow: $result" }
                    result.onSuccess {
                        emit(it)
                    }.onFailure {
                        throw it
                    }
                } finally {
                    // Send it back to consumer coroutine
                    logger.verbose { "Done with value, sending it back to upstream flow coroutine" }
                    activeFlow.channel.send(result)
                }
            }
        }
    }

    private suspend fun withActiveFlow(block: suspend (ActiveFlow<T>) -> Unit) {
        coroutineScope {
            val activeFlow = ActiveFlow<T>(this)
            activeFlows.add(activeFlow)
            try {
                // Wake up upstream flow coroutine
                try {
                    resumeChannel.send(Unit)
                } catch (e: ClosedSendChannelException) {
                    throw ClosedFlowException("Flow has been closed", e)
                }
                block(activeFlow)
            } finally {
                activeFlows.remove(activeFlow)
            }
        }
    }

    private suspend fun collectUpstreamFlow() {
        // Wait for at least one active flow to wake us up
        waitForNewActiveFlow()

        try {
            upstreamFlow.collect { value ->
                logger.verbose { "Value received from upstream flow: $value" }
                emitValue(Result.success(value))

                while (activeFlows.isEmpty()) {
                    // Wait for at least one active flow to wake us up
                    waitForNewActiveFlow()
                }
            }
            logger.debug { "Upstream flow collection has ended normally" }
            endAllFlowsWithEmptyResult()
        } catch (t: Throwable) {
            logger.debug(t) { "Upstream flow collection has thrown an exception" }
            endAllFlowsWithError(t)
        }
    }

    private suspend fun emitValue(value: Result<T>) {
        // Sends the packet to all active flows
        activeFlows.forEach { activeFlow ->
            logger.debug { "Sending value '$value' to active flow '$activeFlow'" }

            // Give value to this active flow and wait for it to tell us when done
            try {
                activeFlow.channel.send(value)
            } catch (e: ClosedSendChannelException) {
                // Ignore, as this may be part of normal shutdown
                logger.info(e) { "ActiveFlow error when sending value" }
                return@forEach
            }
            try {
                val newValue = activeFlow.channel.receive()
                assert(newValue == value) { "The flow should send us back the same value" }
            } catch (e: ClosedReceiveChannelException) {
                // Ignore, as this may be part of normal shutdown
                logger.info(e) { "ActiveFlow error when sending value" }
                return@forEach
            }
        }
    }

    private suspend fun endAllFlowsWithEmptyResult() {
        while (true) {
            // Close all active flows since we are done with the upstream flow
            activeFlows.forEach {
                it.channel.close()
            }

            // Wait for at least one active flow to wake us up
            waitForNewActiveFlow()
        }
    }

    /**
     * The upstream flow had an error. We never retry, instead we
     * emit the exception to all existing (and new) active flows
     * until [close] is called.
     */
    private suspend fun endAllFlowsWithError(upstreamFlowError: Throwable) {
        while (true) {
            // Upstream flow had an error, send it to active flows
            emitValue(Result.failure(upstreamFlowError))

            // Wait for at least one active flow to wake us up
            waitForNewActiveFlow()
        }
    }

    private suspend fun waitForNewActiveFlow() {
        try {
            resumeChannel.receive()
        } catch (e: ClosedReceiveChannelException) {
            logger.debug { "Channel has been closed, cancelling coroutine" }
            throw CancellationException("Channel has been closed", e)
        }
    }

    override fun close() {
        logger.debug { "Closing" }
        val exception = CancellationException("SharedSerializedFlow has been closed")
        resumeChannel.close()
        upstreamFlowScope.cancel(exception)
        activeFlows.forEach { activeFlow ->
            activeFlow.channel.close(exception)
            activeFlow.scope.cancel(exception)
        }
    }

    protected open suspend fun onStartCollectNewActiveFlow(flowCollector: FlowCollector<T>) {
        // Nothing to do in base class
    }

    protected class ActiveFlow<T>(val scope: CoroutineScope) {

        /**
         * [Channel] used to send [T] values to this flow collector
         */
        val channel = Channel<Result<T>>()
    }
}

internal class ClosedFlowException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
