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
import com.android.fakeadbserver.CommandHandler;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import java.net.Socket;

/**
 * DeviceComamndHandlers handle commands directed at a device. This includes host-prefix: commands
 * as per the protocol doc, as well as device commands after calling host:transport[-*].
 */
public class DeviceCommandHandler extends CommandHandler {

    protected final String command;

    public DeviceCommandHandler(String command) {
        this.command = command;
    }

    /**
     * Processes the command and arguments. If this handler accepts it, it will execute it and
     * return true.
     */
    public boolean accept(
            @NonNull FakeAdbServer server,
            @NonNull Socket socket,
            @NonNull DeviceState device,
            @NonNull String command,
            @NonNull String args) {
        if (this.command.equals(command)) {
            invoke(server, socket, device, args);
            return true;
        }
        return false;
    }

    /**
     * Invokes this command. This method is only called if the handler accepts the command with its
     * arguments.
     */
    public void invoke(
            @NonNull FakeAdbServer server,
            @NonNull Socket socket,
            @NonNull DeviceState device,
            @NonNull String args) {}
}
