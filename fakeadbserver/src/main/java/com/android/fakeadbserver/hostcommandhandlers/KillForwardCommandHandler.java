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
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * host-prefix:killforward ADB command removes a port forward from the specified local port. This
 * implementation only handles tcp sockets, and not Unix domain sockets.
 */
public class KillForwardCommandHandler extends HostCommandHandler {

    @NonNull public static final String COMMAND = "killforward";

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

        String[] hostAddress = args.split(":");
        switch (hostAddress[0]) {
            case "tcp":
                break;
            case "local":
                writeFailResponse(
                        stream, "Host Unix domain sockets not supported in fake ADB Server.");
                return false;
            default:
                writeFailResponse(stream, "Invalid host transport specified: " + hostAddress[0]);
                return false;
        }
        int hostPort;
        try {
            hostPort = Integer.parseInt(hostAddress[1]);
        } catch (NumberFormatException ignored) {
            writeFailResponse(stream, "Invalid port specified: " + hostAddress[1]);
            return false;
        }

        if (!device.removePortForwarder(hostPort)) {
            writeFailResponse(stream, "Could not successfully remove forward.");
            return false;
        }

        try {
            writeOkay(stream);
        } catch (IOException ignored) {
            return false;
        }
        return true;
    }
}
