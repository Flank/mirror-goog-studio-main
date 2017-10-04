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

import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsRequest.Type;
import com.android.tools.profiler.proto.NetworkServiceGrpc.NetworkServiceBlockingStub;

/** Wrapper of stub calls that is shared among tests. */
final class NetworkStubWrapper {
    private static final int TIMEOUT_MS = 5000;
    private static final int SLEEP_TIME_MS = 200;
    private final NetworkServiceBlockingStub myNetworkStub;

    NetworkStubWrapper(NetworkServiceBlockingStub networkStub) {
        myNetworkStub = networkStub;
    }

    NetworkProfiler.HttpRangeResponse getAllHttpRange(int processId) {
        return myNetworkStub.getHttpRange(
                NetworkProfiler.HttpRangeRequest.newBuilder()
                        .setProcessId(processId)
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
    String getResponsePayloadId(long connectionId) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < TIMEOUT_MS + SLEEP_TIME_MS) {
            NetworkProfiler.HttpDetailsResponse responseBodyDetails =
                    getHttpDetails(connectionId, Type.RESPONSE_BODY);
            String payloadId = responseBodyDetails.getResponseBody().getPayloadId();
            if (!payloadId.isEmpty()) {
                return payloadId;
            }
            try {
                Thread.sleep(SLEEP_TIME_MS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.out);
            }
        }
        return "";
    }
}
