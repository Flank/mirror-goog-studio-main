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
package com.android.fakeadbserver.hostcommandhandlers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.PortForwarder;
import com.android.fakeadbserver.devicecommandhandlers.ForwardArgs;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * host-prefix:forward ADB command adds a port forward to the connected device. This implementation
 * only handles tcp sockets, and not Unix domain sockets.
 */
public class ForwardCommandHandler extends HostCommandHandler {

    @NonNull public static final String COMMAND = "forward";

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @Nullable DeviceState device,
            @NonNull String args) {
        assert device != null;
        OutputStream stream;
        try {
            stream = responseSocket.getOutputStream();
        } catch (IOException ignored) {
            return false;
        }
        ForwardArgs forwardArgs = ForwardArgs.parse(args);

        String hostTransport = forwardArgs.getFromTransport();
        switch (hostTransport) {
            case "tcp":
                break;
            case "local":
                writeFailResponse(
                        stream, "Host Unix domain sockets not supported in fake ADB Server.");
                return false;
            default:
                writeFailResponse(stream, "Invalid host transport specified: " + hostTransport);
                return false;
        }
        int hostPort;
        Integer hostPortToSendBack = null;
        try {
            hostPort = Integer.parseInt(forwardArgs.getFromTransportArg());
            if (hostPort == 0) {
                // This is to emulate ADB Server behavior of picking an available port
                // This is currently hard-coded as we don't actually create sockets
                hostPort = 40_000 + (int) (Math.random() * 100);
            }
            hostPortToSendBack = hostPort;
        } catch (NumberFormatException ignored) {
            writeFailResponse(
                    stream, "Invalid host port specified: " + forwardArgs.getFromTransportArg());
            return false;
        }
        String deviceTransport = forwardArgs.getToTransport();
        PortForwarder forwarder;
        switch (deviceTransport) {
            case "tcp":
                try {
                    int devicePort = Integer.parseInt(forwardArgs.getToTransportArg());
                    forwarder = PortForwarder.createPortForwarder(hostPort, devicePort);
                } catch (NumberFormatException ignored) {
                    writeFailResponse(
                            stream,
                            "Invalid device port or pid specified: "
                                    + forwardArgs.getToTransportArg());
                    return false;
                }
                break;
            case "local":
                forwarder =
                        PortForwarder.createUnixForwarder(
                                hostPort, forwardArgs.getToTransportArg());
                break;
            case "jdwp":
                writeFailResponse(stream, "JDWP connections not yet supported in fake ADB Server.");
                return false;
            default:
                writeFailResponse(stream, "Invalid device transport specified: " + deviceTransport);
                return false;
        }
        boolean bindOk = device.addPortForwarder(forwarder, forwardArgs.getNorebind());
        try {
            // We send 2 OKAY answers: 1st OKAY is connect, 2nd OKAY is status.
            // See
            // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1058
            writeOkay(stream);
            if (bindOk) {
                if (hostPortToSendBack != null) {
                    writeOkayResponse(stream, hostPortToSendBack.toString());
                } else {
                    writeOkay(stream);
                }
            } else {
                writeFailResponse(stream, "Could not bind to the specified forwarding ports.");
            }
        } catch (IOException ignored) {
        }

        // We always close the connection, as per ADB protocol spec.
        return false;
    }
}
