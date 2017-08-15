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
        boolean noRebind = false;
        if (args.startsWith("norebind:")) {
            noRebind = true;
            args = args.split(":", 2)[1];
        }
        String[] addressStrings = args.split(";");
        if (addressStrings.length != 2) {
            writeFailResponse(stream, "Invalid port string format given: " + args);
            return false;
        }
        String[] hostAddress = addressStrings[0].split(":");
        if (hostAddress.length != 2) {
            writeFailResponse(
                    stream, "Invalid host address string format given: " + addressStrings[0]);
            return false;
        }
        String hostTransport = hostAddress[0];
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
        try {
            hostPort = Integer.parseInt(hostAddress[1]);
        } catch (NumberFormatException ignored) {
            writeFailResponse(stream, "Invalid host port specified: " + hostAddress[1]);
            return false;
        }
        String[] deviceAddress = addressStrings[1].split(":");
        if (deviceAddress.length != 2) {
            writeFailResponse(
                    stream, "Invalid device address string format given: " + addressStrings[1]);
            return false;
        }
        String deviceTransport = deviceAddress[0];
        PortForwarder forwarder;
        switch (deviceTransport) {
            case "tcp":
                try {
                    int devicePort = Integer.parseInt(deviceAddress[1]);
                    forwarder = PortForwarder.createPortForwarder(hostPort, devicePort);
                } catch (NumberFormatException ignored) {
                    writeFailResponse(
                            stream, "Invalid device port or pid specified: " + deviceAddress[1]);
                    return false;
                }
                break;
            case "local":
                forwarder = PortForwarder.createUnixForwarder(hostPort, deviceAddress[1]);
                break;
            case "jdwp":
                writeFailResponse(stream, "JDWP connections not yet supported in fake ADB Server.");
                return false;
            default:
                writeFailResponse(stream, "Invalid device transport specified: " + deviceTransport);
                return false;
        }
        boolean bindOk = device.addPortForwarder(forwarder, noRebind);
        try {
            if (bindOk) {
                writeOkay(stream);
            } else {
                writeFailResponse(stream, "Could not bind to the specified forwarding ports.");
            }
        } catch (IOException ignored) {
            return false;
        }
        return bindOk;
    }
}
