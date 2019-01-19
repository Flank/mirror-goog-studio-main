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

package com.android.fakeadbserver.shellcommandhandlers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.DeviceState.LogcatChangeHandlerSubscriptionResult;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHandlerFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * shell:logcat command is a persistent command issued to grab the output of logcat. In this
 * implementation, the command handler can be driven to send messages to the client of the fake ADB
 * Server.
 */
public class LogcatCommandHandler extends SimpleShellHandler {

    public LogcatCommandHandler() {
        super("logcat");
    }

    @Override
    public void invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @NonNull DeviceState device,
            @Nullable String args) {
        List<String> parsedArgs = Arrays.asList(args == null ? new String[0] : args.split(" +"));

        int formatIndex = parsedArgs.indexOf("-v");
        if (formatIndex + 1 > parsedArgs.size()) {
            return;
        }

        String format = parsedArgs.get(formatIndex + 1);
        // TODO format the output according {@code format} argument.

        OutputStream stream;
        try {
            stream = responseSocket.getOutputStream();
            writeOkay(stream); // Send ok first.
        } catch (IOException ignored) {
            return;
        }

        LogcatChangeHandlerSubscriptionResult subscriptionResult =
                device.subscribeLogcatChangeHandler(
                        new ClientStateChangeHandlerFactory() {
                            @NonNull
                            @Override
                            public Callable<HandlerResult> createClientListChangedHandler() {
                                return () -> new HandlerResult(true);
                            }

                            @NonNull
                            @Override
                            public Callable<HandlerResult> createLogcatMessageAdditionHandler(
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
            return;
        }

        try {
            for (String message : subscriptionResult.mLogcatContents) {
                writeString(stream, message);
            }
            while (true) {
                try {
                    if (!subscriptionResult.mQueue.take().call().mShouldContinue) {
                        break;
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            device.getClientChangeHub().unsubscribe(subscriptionResult.mQueue);
        }
    }
}
