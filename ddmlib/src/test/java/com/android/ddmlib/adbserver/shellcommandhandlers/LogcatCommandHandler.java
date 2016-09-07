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

package com.android.ddmlib.adbserver.shellcommandhandlers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.adbserver.ClientState;
import com.android.ddmlib.adbserver.DeviceState;
import com.android.ddmlib.adbserver.DeviceState.LogcatChangeHandlerSubscriptionResult;
import com.android.ddmlib.adbserver.FakeAdbServer;
import com.android.ddmlib.adbserver.statechangehubs.ClientStateChangeHandlerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * shell:logcat command is a persistent command issued to grab the output of logcat. In this
 * implementation, the command handler can be driven to send messages to the client of the fake
 * ADB Server.
 */
public class LogcatCommandHandler extends ShellCommandHandler {

    @NonNull
    public static final String COMMAND = "logcat";

    @Override
    public boolean invoke(@NonNull FakeAdbServer fakeAdbServer, @NonNull Socket responseSocket,
            @NonNull DeviceState device, @Nullable String args) {
        List<String> parsedArgs = Arrays.asList(args == null ? new String[0] : args.split(" +"));

        int formatIndex = parsedArgs.indexOf("-v");
        if (formatIndex + 1 > parsedArgs.size()) {
            return false;
        }

        String format = parsedArgs.get(formatIndex + 1);
        // TODO format the output according {@code format} argument.

        OutputStream stream;
        try {
            stream = responseSocket.getOutputStream();
            writeOkay(stream); // Send ok first.
        } catch (IOException ignored) {
            return false;
        }

        LogcatChangeHandlerSubscriptionResult subscriptionResult
                = device.subscribeLogcatChangeHandler(new ClientStateChangeHandlerFactory() {
            @NonNull
            @Override
            public Supplier<HandlerResult> createClientListChangedHandler(
                    @NonNull Collection<ClientState> clientList) {
                return () -> new HandlerResult(true);
            }

            @NonNull
            @Override
            public Supplier<HandlerResult> createLogcatMessageAdditionHandler(
                    @NonNull String message) {
                return () -> {
                    try {
                        stream.write(message.getBytes(Charset.defaultCharset()));
                    } catch (IOException ignored) {
                        return new HandlerResult(false);
                    }
                    return new HandlerResult(true);
                };
            }
        });

        if (subscriptionResult == null) {
            return false;
        }

        try {
            for (String message : subscriptionResult.mLogcatContents) {
                writeString(stream, message);
            }
            while (true) {
                try {
                    if (!subscriptionResult.mQueue.take().get().mShouldContinue) {
                        break;
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            device.getClientChangeHub().unsubscribe(subscriptionResult.mQueue);
        }

        return false;
    }
}
