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

import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.android.tools.profiler.proto.TransportServiceGrpc.TransportServiceBlockingStub

/**
 * A wrapping class that provides useful utility methods on top of [TransportServiceGrpc.TransportServiceBlockingStub]
 */
class TransportStubWrapper(val transportStub: TransportServiceBlockingStub) {
    companion object {
        /**
         * Convenience method for creating a wrapper when you don't need to create the underlying
         * stub yourself.
         */
        @JvmStatic
        fun create(grpc: Grpc): TransportStubWrapper {
            return TransportStubWrapper(TransportServiceGrpc.newBlockingStub(grpc.channel))
        }
    }

    /**
     * Convenience method for fetching a transport stub's contents by ID and converting it into a UTF8
     * string.
     */
    fun toBytes(id: String): String {
        if (id.isBlank()) return ""

        val bytesRequest = Transport.BytesRequest.newBuilder().setId(id).build()
        return transportStub.getBytes(bytesRequest).contents.toStringUtf8()
    }
}