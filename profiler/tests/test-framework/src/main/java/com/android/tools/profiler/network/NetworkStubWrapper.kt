
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
package com.android.tools.profiler.network
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.NetworkProfiler
import com.android.tools.profiler.proto.NetworkServiceGrpc
import com.android.tools.transport.TestUtils
import com.android.tools.transport.grpc.Grpc
import java.util.function.Predicate
import java.util.function.Supplier

class NetworkStubWrapper(val networkStub: NetworkServiceGrpc.NetworkServiceBlockingStub) {
    companion object {
        /**
         * Convenience method for creating a wrapper when you don't need to create the underlying
         * stub yourself.
         */
        @JvmStatic
        fun create(grpc: Grpc): NetworkStubWrapper {
            return NetworkStubWrapper(NetworkServiceGrpc.newBlockingStub(grpc.channel))
        }
    }
    /**
     * Fetch an http range that have at least one element in it. This method will block until a
     * non-empty range is returned.
     */
    fun getNonEmptyHttpRange(session: Common.Session): NetworkProfiler.HttpRangeResponse {
        return TestUtils.waitForAndReturn(
                createHttpRangeSupplier(session),
                Predicate { result -> result.dataList.size > 0 })
    }

    fun createHttpRangeSupplier(session: Common.Session): Supplier<NetworkProfiler.HttpRangeResponse> {
        return Supplier {
            networkStub.getHttpRange(
                    NetworkProfiler.HttpRangeRequest.newBuilder()
                            .setSession(session)
                            .setStartTimestamp(0L)
                            .setEndTimestamp(Long.MAX_VALUE)
                            .build())
        }
    }
    fun getHttpDetails(connId: Long, type: NetworkProfiler.HttpDetailsRequest.Type): NetworkProfiler.HttpDetailsResponse {
        return networkStub.getHttpDetails(
                NetworkProfiler.HttpDetailsRequest.newBuilder()
                        .setConnId(connId)
                        .setType(type)
                        .build())
    }
    /**
     * Returns the payload id for the requested [connId].
     *
     * This operation may block, because it takes some time after connections are first
     * tracked before a payload ID is generated.
     */
    fun getPayloadId(connId: Long, type: NetworkProfiler.HttpDetailsRequest.Type): String {
        return TestUtils.Kt.waitForAndReturn(
                {
                    val result = getHttpDetails(connId, type)
                    if (type == NetworkProfiler.HttpDetailsRequest.Type.RESPONSE_BODY) {
                        result.responseBody.payloadId
                    } else {
                        result.requestBody.payloadId
                    }
                },
                { payloadId -> payloadId.isNotEmpty() })
    }
}
