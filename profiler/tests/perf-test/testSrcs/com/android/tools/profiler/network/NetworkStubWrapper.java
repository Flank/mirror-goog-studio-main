/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profiler.network;

import com.android.tools.profiler.TestUtils;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsRequest.Type;
import com.android.tools.profiler.proto.NetworkServiceGrpc.NetworkServiceBlockingStub;
import java.util.function.Supplier;

/** Wrapper of stub calls that is shared among tests. */
final class NetworkStubWrapper {
    private final NetworkServiceBlockingStub myNetworkStub;

    NetworkStubWrapper(NetworkServiceBlockingStub networkStub) {
        myNetworkStub = networkStub;
    }

    NetworkProfiler.HttpRangeResponse getAllHttpRange(Session session) {
        return myNetworkStub.getHttpRange(
                NetworkProfiler.HttpRangeRequest.newBuilder()
                        .setSession(session)
                        .setStartTimestamp(0L)
                        .setEndTimestamp(Long.MAX_VALUE)
                        .build());
    }

    NetworkProfiler.HttpDetailsResponse getHttpDetails(long connId, Type type) {
        return myNetworkStub.getHttpDetails(
                NetworkProfiler.HttpDetailsRequest.newBuilder()
                        .setConnId(connId)
                        .setType(type)
                        .build());
    }

    /**
     * Returns payload id after repeatedly fetch it until it is non-empty or time out, otherwise,
     * returns empty string. Because there is some time gap between connection tracking and payload
     * track complete, it need some time to set the payload id.
     */
    String getPayloadId(final long connectionId, final Type type) {
        final Supplier<String> getPayloadId =
                () -> {
                    NetworkProfiler.HttpDetailsResponse result = getHttpDetails(connectionId, type);
                    return type == Type.RESPONSE_BODY
                            ? result.getResponseBody().getPayloadId()
                            : result.getRequestBody().getPayloadId();
                };
        TestUtils.waitFor(() -> !getPayloadId.get().isEmpty());
        return getPayloadId.get();
    }
}
