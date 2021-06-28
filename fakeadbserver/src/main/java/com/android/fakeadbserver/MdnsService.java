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

package com.android.fakeadbserver;

import com.android.annotations.NonNull;
import java.net.InetAddress;
import java.util.Objects;

public final class MdnsService {

    @NonNull private final String mInstanceName;

    @NonNull private final String mServiceName;

    @NonNull private final InetAddress mIpAddress;

    private final int mPort;

    public MdnsService(
            @NonNull String instanceName,
            @NonNull String serviceName,
            @NonNull InetAddress ipAddress,
            int port) {
        mInstanceName = instanceName;
        mServiceName = serviceName;
        mIpAddress = ipAddress;
        mPort = port;
    }

    @NonNull
    public String getInstanceName() {
        return mInstanceName;
    }

    @NonNull
    public String getServiceName() {
        return mServiceName;
    }

    @NonNull
    public InetAddress getIpAddress() {
        return mIpAddress;
    }

    public int getPort() {
        return mPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MdnsService state = (MdnsService) o;
        return mInstanceName.equals(state.mInstanceName)
                && mServiceName.equals(state.mServiceName)
                && mIpAddress.equals(state.mIpAddress)
                && mPort == state.mPort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mInstanceName, mServiceName, mIpAddress, mPort);
    }
}
