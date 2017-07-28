/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.ir.client;

import static com.android.tools.ir.common.ProtocolConstants.MESSAGE_EOF;
import static com.android.tools.ir.common.ProtocolConstants.PROTOCOL_IDENTIFIER;
import static com.android.tools.ir.common.ProtocolConstants.PROTOCOL_VERSION;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.utils.ILogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;

/**
 * Wrapper for talking to either the hotswap service or the run-as service.
 */
public class ServiceCommunicator {

    private static final String LOCAL_HOST = "127.0.0.1";

    @NonNull
    private final String mPackageName;

    @NonNull
    private final ILogger mLogger;

    private final int mLocalPort;

    public ServiceCommunicator(@NonNull String packageName,
                               @NonNull ILogger logger,
                               int port) {
        mPackageName = packageName;
        mLogger = logger;
        mLocalPort = port;
    }

    @NonNull
    public <T> T talkToService(@NonNull IDevice device, @NonNull Communicator<T> communicator)
            throws IOException {
        try {
            device.createForward(mLocalPort, mPackageName,
                    IDevice.DeviceUnixSocketNamespace.ABSTRACT);
        }
        catch (TimeoutException | AdbCommandRejectedException e) {
            throw new IOException(e);
        }
        try {
            return talkToServiceWithinPortForward(communicator, mLocalPort);
        } finally {
            try {
                device.removeForward(mLocalPort, mPackageName,
                                     IDevice.DeviceUnixSocketNamespace.ABSTRACT);
            }
            catch (IOException | TimeoutException | AdbCommandRejectedException e) {
                // we don't worry that much about failures while removing port forwarding
                mLogger.warning("Exception while removing port forward: " + e);
            }
        }
    }

    private static <T> T talkToServiceWithinPortForward(@NonNull Communicator<T> communicator,
            int localPort) throws IOException {
        try (Socket socket = new Socket(LOCAL_HOST, localPort)) {
            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                output.writeLong(PROTOCOL_IDENTIFIER);
                output.writeInt(PROTOCOL_VERSION);

                socket.setSoTimeout(2 * 1000); // Allow up to 2 seconds before timing out
                int version = input.readInt();
                if (version != PROTOCOL_VERSION) {
                    String msg = String.format(Locale.US,
                            "Client and server protocol versions don't match (%1$d != %2$d)",
                            version, PROTOCOL_VERSION);
                    throw new IOException(msg);
                }

                socket.setSoTimeout(communicator.getTimeout());
                T value = communicator.communicate(input, output);
                output.writeInt(MESSAGE_EOF);
                return value;
            }
        }
    }
}
