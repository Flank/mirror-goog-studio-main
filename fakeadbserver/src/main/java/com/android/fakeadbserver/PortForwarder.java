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
import com.android.annotations.Nullable;

/**
 * Represents a port forward pair from {@link #mSource} to {@link #mDestination}.
 */
public final class PortForwarder {

    /**
     * An invalid/uninitialized port number.
     */
    public static final int INVALID_PORT = -1;

    private final ForwarderSource mSource = new ForwarderSource();

    private final ForwarderDestination mDestination = new ForwarderDestination();

    @NonNull
    public static PortForwarder createPortForwarder(int hostPort, int port) {
        PortForwarder forwarder = new PortForwarder(hostPort);
        forwarder.mDestination.mPort = port;
        return forwarder;
    }

    @NonNull
    public static PortForwarder createJdwpForwarder(int hostPort, int pid) {
        PortForwarder forwarder = new PortForwarder(hostPort);
        forwarder.mDestination.mJdwpPid = pid;
        return forwarder;
    }

    @NonNull
    public static PortForwarder createUnixForwarder(int hostPort, @NonNull String unixDomain) {
        PortForwarder forwarder = new PortForwarder(hostPort);
        forwarder.mDestination.mUnixDomain = unixDomain;
        return forwarder;
    }

    /** Use one of the static factory methods to create an instance of this class. */
    private PortForwarder(int hostPort) {
        mSource.mPort = hostPort;
    }

    public ForwarderSource getSource() {
        return mSource;
    }

    public ForwarderDestination getDestination() {
        return mDestination;
    }

    public static class ForwarderSource {

        int mPort = INVALID_PORT;

        public int getPort() {
            return mPort;
        }
    }

    public static class ForwarderDestination {

        int mPort = INVALID_PORT;
        int mJdwpPid = INVALID_PORT;
        String mUnixDomain = null;

        public int getPort() {
            return mPort;
        }

        public int getJdwpPid() {
            return mJdwpPid;
        }

        @Nullable
        public String getUnixDomain() {
            return mUnixDomain;
        }
    }
}
