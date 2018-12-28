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

package com.android.fakeadbserver.devicecommandhandlers;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHandlerFactory;
import com.android.fakeadbserver.statechangehubs.StateChangeHandlerFactory;
import com.android.fakeadbserver.statechangehubs.StateChangeQueue;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Callable;

/**
 * track-jdwp tracks the device's Android Client list, sending change messages whenever a client
 * is added/removed, or have its state changed.
 */
public class TrackJdwpCommandHandler extends DeviceCommandHandler {

    @NonNull
    public static final String COMMAND = "track-jdwp";

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @NonNull DeviceState device,
            @NonNull String args) {
        OutputStream stream;
        try {
            stream = responseSocket.getOutputStream();
        } catch (IOException e) {
            return false;
        }

        StateChangeQueue queue =
                device.getClientChangeHub()
                        .subscribe(
                                new ClientStateChangeHandlerFactory() {
                                    @NonNull
                                    @Override
                                    public Callable<HandlerResult>
                                            createClientListChangedHandler() {
                                        return () -> {
                                            try {
                                                sendDeviceList(device, stream);
                                                return new StateChangeHandlerFactory.HandlerResult(
                                                        true);
                                            } catch (IOException ignored) {
                                                return new StateChangeHandlerFactory.HandlerResult(
                                                        false);
                                            }
                                        };
                                    }

                                    @NonNull
                                    @Override
                                    public Callable<HandlerResult>
                                            createLogcatMessageAdditionHandler(
                                                    @NonNull String message) {
                                        return () -> new HandlerResult(true);
                                    }
                                });

        if (queue == null) {
            return false; // Server has shutdown before we are able to start listening to the queue.
        }

        try {
            writeOkay(stream); // Send ok first.

            sendDeviceList(device, stream); // Then send the initial device list.

            while (true) {
                if (!queue.take().call().mShouldContinue) {
                    break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            device.getClientChangeHub().unsubscribe(queue);
        }

        return false;
    }

    private static void sendDeviceList(@NonNull DeviceState device, @NonNull OutputStream stream)
            throws IOException {
        String clientListString = device.getClientListString();
        write4ByteHexIntString(stream, clientListString.length());
        writeString(stream, clientListString);
    }
}
