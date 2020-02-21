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
package com.android.tools.transport.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

/**
 * Class which wraps gRPC initialization.
 */
class Grpc(socket: String, port: Int) {
    val channel = connectGrpc(socket, port)

    private fun connectGrpc(socket: String, port: Int): ManagedChannel {
        val stashedContextClassLoader = Thread.currentThread().contextClassLoader

        Thread.currentThread().contextClassLoader = ManagedChannelBuilder::class.java.classLoader
        val channel = ManagedChannelBuilder.forAddress(socket, port).usePlaintext().build()

        Thread.currentThread().contextClassLoader = stashedContextClassLoader
        return channel
    }
}
