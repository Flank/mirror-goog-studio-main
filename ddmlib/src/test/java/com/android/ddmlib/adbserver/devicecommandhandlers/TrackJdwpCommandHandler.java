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

package com.android.ddmlib.adbserver.devicecommandhandlers;

import com.android.annotations.NonNull;
import com.android.ddmlib.adbserver.ClientState;
import com.android.ddmlib.adbserver.DeviceState;
import com.android.ddmlib.adbserver.FakeAdbServer;
import com.android.ddmlib.adbserver.statechangehubs.ClientStateChangeHandlerFactory;
import com.android.ddmlib.adbserver.statechangehubs.StateChangeHandlerFactory;
import com.android.ddmlib.adbserver.statechangehubs.StateChangeQueue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * track-jdwp tracks the device's Android Client list, sending change messages whenever a client
 * is added/removed, or have its state changed.
 */
public class TrackJdwpCommandHandler extends DeviceCommandHandler {

    @NonNull
    public static final String COMMAND = "track-jdwp";

    @Override
    public boolean invoke(@NonNull FakeAdbServer fakeAdbServer, @NonNull Socket responseSocket,
            @NonNull DeviceState device, @NonNull String args) {
        OutputStream stream;
        try {
            stream = responseSocket.getOutputStream();
        } catch (IOException e) {
            return false;
        }

        StateChangeQueue queue = device.getClientChangeHub()
                .subscribe(new ClientStateChangeHandlerFactory() {
                    @NonNull
                    @Override
                    public Supplier<HandlerResult> createClientListChangedHandler(
                            @NonNull Collection<ClientState> clientList) {
                        return () -> {
                            try {
                                String clientListString = clientList.stream()
                                        .map(clientState -> Integer.toString(clientState.getPid()))
                                        .collect(Collectors.joining("\n"));
                                write4ByteHexIntString(stream, clientListString.length());
                                writeString(stream, clientListString);
                                return new StateChangeHandlerFactory.HandlerResult(true);
                            } catch (IOException ignored) {
                                return new StateChangeHandlerFactory.HandlerResult(false);
                            }
                        };
                    }

                    @NonNull
                    @Override
                    public Supplier<HandlerResult> createLogcatMessageAdditionHandler(
                            @NonNull String message) {
                        return () -> new HandlerResult(true);
                    }
                });

        if (queue == null) {
            return false; // Server has shutdown before we are able to start listening to the queue.
        }

        try {
            writeOkay(stream); // Send ok first.

            while (true) {
                if (!queue.take().get().mShouldContinue) {
                    break;
                }
            }
        } catch (IOException | InterruptedException ignored) {
        } finally {
            device.getClientChangeHub().unsubscribe(queue);
        }

        return false;
    }
}
