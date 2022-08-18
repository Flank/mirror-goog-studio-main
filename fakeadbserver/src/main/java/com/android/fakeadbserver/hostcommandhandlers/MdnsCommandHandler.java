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
import com.android.fakeadbserver.CommandHandler;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.MdnsService;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/** host:mdns:check returns the status of mDNS support */
public class MdnsCommandHandler extends HostCommandHandler {

    @NonNull public static final String COMMAND = "mdns";

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @Nullable DeviceState device,
            @NonNull String args) {
        try {
            if ("check".equals(args)) {
                CommandHandler.writeOkayResponse(
                        responseSocket.getOutputStream(),
                        "mdns daemon version [FakeAdb implementation]\n");
            } else if ("services".equals(args)) {
                String result = formatMdnsServiceList(fakeAdbServer.getMdnsServicesCopy().get());
                CommandHandler.writeOkayResponse(responseSocket.getOutputStream(), result);
            } else {
                CommandHandler.writeFailResponse(
                        responseSocket.getOutputStream(), "Invalid mdns command");
            }
        } catch (IOException | ExecutionException ignored) {
            return false;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        return false;
    }

    private String formatMdnsServiceList(List<MdnsService> services) {
        StringBuilder sb = new StringBuilder();
        services.forEach(
                service -> {
                    sb.append(
                            String.format(
                                    Locale.US,
                                    "%s\t%s\t%s:%d\n",
                                    service.getInstanceName(),
                                    service.getServiceName(),
                                    service.getDeviceAddress().getHostString(),
                                    service.getDeviceAddress().getPort()));
                });
        return sb.toString();
    }
}
