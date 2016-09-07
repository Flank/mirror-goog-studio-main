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
import com.android.ddmlib.adbserver.CommandHandler;
import com.android.ddmlib.adbserver.DeviceState;
import com.android.ddmlib.adbserver.FakeAdbServer;

import java.net.Socket;

/**
 * DeviceComamndHandlers handle commands directed at a device. This includes host-prefix: commands
 * as per the protocol doc, as well as device commands after calling host:transport[-*].
 */
public abstract class DeviceCommandHandler extends CommandHandler {

    /**
     * This is the main execution method of the command.
     *
     * @param fakeAdbServer  Fake ADB Server itself.
     * @param responseSocket Socket for this connection.
     * @param device         Target device for the command, if any.
     * @param args           Arguments for the command.
     * @return a boolean, with true meaning keep the connection alive, false to close the connection
     */
    public abstract boolean invoke(@NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket, @NonNull DeviceState device, @NonNull String args);
}
