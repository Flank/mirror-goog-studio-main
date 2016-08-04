/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ddmlib.adbserver.hostcommandhandlers;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.adbserver.DeviceState;
import com.android.ddmlib.adbserver.FakeAdbServer;
import com.android.ddmlib.adbserver.statechangehubs.DeviceStateChangeHandlerFactory;
import com.android.ddmlib.adbserver.statechangehubs.StateChangeHandlerFactory.HandlerResult;
import com.android.ddmlib.adbserver.statechangehubs.StateChangeQueue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * host:track-devices is a persistent connection that tracks device connection/disconnections, as
 * well as device state changes (such as coming online, going offline, etc...). Every time an event
 * occurs, the list of connected devices and their states are sent.
 */
public class TrackDevicesCommandHandler extends HostCommandHandler {

    @NonNull
    public static final String COMMAND = "track-devices";

    @NonNull
    private static Supplier<HandlerResult> sendDeviceList(@NonNull Socket responseSocket,
            @NonNull FakeAdbServer server) {
        return () -> {
            try {
                OutputStream stream = responseSocket.getOutputStream();
                String deviceListString = ListDevicesCommandHandler
                        .formatDeviceList(server.getDeviceListCopy().get());
                write4ByteHexIntString(stream, deviceListString.length());
                stream.write(deviceListString.getBytes(US_ASCII));
                stream.flush();
                return new HandlerResult(true);
            } catch (InterruptedException | ExecutionException | IOException e) {
                return new HandlerResult(false);
            }
        };
    }

    @Override
    public boolean invoke(@NonNull FakeAdbServer fakeAdbServer, @NonNull Socket responseSocket,
            @Nullable DeviceState device, @NonNull String args) {
        StateChangeQueue queue = fakeAdbServer.getDeviceChangeHub()
                .subscribe(new DeviceStateChangeHandlerFactory() {
                    @NonNull
                    @Override
                    public Supplier<HandlerResult> createDeviceListChangedHandler(
                            @NonNull Collection<DeviceState> deviceList) {
                        return sendDeviceList(responseSocket, fakeAdbServer);
                    }

                    @NonNull
                    @Override
                    public Supplier<HandlerResult> createDeviceStateChangedHandler(
                            @NonNull DeviceState device,
                            @NonNull IDevice.DeviceState status) {
                        return sendDeviceList(responseSocket, fakeAdbServer);
                    }
                });

        if (queue == null) {
            return false; // Server has shutdown before we are able to start listening to the queue.
        }

        try {
            writeOkay(responseSocket.getOutputStream()); // Send ok first.

            // Then send over the full list of devices before going into monitoring mode.
            sendDeviceList(responseSocket, fakeAdbServer).get();

            while (true) {
                try {
                    // Grab a command from the queue (take()), and execute the command (get(), as
                    // defined above in the DeviceStateChangeHandlerFactory) as-is in the current
                    // thread so that we can send the message in the opened connection (which only
                    // exists in the current thread).
                    if (!queue.take().get().mShouldContinue) {
                        break;
                    }
                } catch (InterruptedException ignored) {
                    // Most likely server going into shutdown, so quit out of the loop.
                    break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            fakeAdbServer.getDeviceChangeHub().unsubscribe(queue);
        }

        return false; // The only we can get here is if the connection/server was terminated.
    }
}
