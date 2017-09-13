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
    private final NetworkServiceBlockingStub myNetworkStub;

    NetworkStubWrapper(NetworkServiceBlockingStub networkStub) {
        myNetworkStub = networkStub;
    }

    public NetworkProfiler.HttpRangeResponse getAllHttpRange(int processId) {
        return myNetworkStub.getHttpRange(
                NetworkProfiler.HttpRangeRequest.newBuilder()
                        .setProcessId(processId)
                        .setStartTimestamp(0L)
                        .setEndTimestamp(Long.MAX_VALUE)
                        .build());
    }

    public NetworkProfiler.HttpDetailsResponse getHttpDetails(long connId, Type type) {
        return myNetworkStub.getHttpDetails(
                NetworkProfiler.HttpDetailsRequest.newBuilder()
                        .setConnId(connId)
                        .setType(type)
                        .build());
    }
}
