/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.transport

import com.android.tools.profiler.proto.Agent
import com.android.tools.profiler.proto.AgentServiceGrpc
import com.android.tools.profiler.proto.Common
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import org.junit.rules.ExternalResource
import java.net.ServerSocket
import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class AgentRule : ExternalResource() {

    private lateinit var server: Server
    private lateinit var socket: ServerSocket
    private var origConfigAddr: Long = 0

    val events: BlockingQueue<Common.Event> = LinkedBlockingQueue()
    val payloads: MutableMap<Int, ByteArray> = mutableMapOf()

    override fun before() {
        socket = ServerSocket(0)
        socket.close()
        server = NettyServerBuilder
            .forPort(socket.localPort)
            .addService(TestAgentServiceImpl(events, payloads))
            .build()
            .start()
        try {
            origConfigAddr = setUpAgentForTest("localhost:" + socket.localPort)
        }
        catch (e: UnsatisfiedLinkError) {
            println("Your test native library must depend on\n" +
                    "//tools/base/transport/test-framework:native_test_support, or your test must depend on\n" +
                    "//tools/base/transport/test-framework:libagentrule-jni.so and you must load agentrule-jni")
            throw e
        }
    }

    override fun after() {
        resetAgent(origConfigAddr)
        server.shutdownNow()
    }

    private external fun setUpAgentForTest(channelName: String): Long
    private external fun resetAgent(origConfigAddr: Long)

    private class TestAgentServiceImpl(
        private val events: Queue<Common.Event>,
        private val payloads: MutableMap<Int, ByteArray>
    ) : AgentServiceGrpc.AgentServiceImplBase() {
        override fun sendEvent(
            request: Agent.SendEventRequest?,
            responseObserver: StreamObserver<Agent.EmptyResponse>?
        ) {
            request?.event?.let { events.add(it) }
            responseObserver?.onNext(Agent.EmptyResponse.getDefaultInstance())
            responseObserver?.onCompleted()
        }

        override fun sendBytes(
            request: Agent.SendBytesRequest,
            responseObserver: StreamObserver<Agent.EmptyResponse>?
        ) {
            if (!request.isComplete) {
                payloads[request.name.toInt()] = request.bytes.toByteArray()
            }
            responseObserver?.onNext(Agent.EmptyResponse.getDefaultInstance())
            responseObserver?.onCompleted()
        }
    }
}
