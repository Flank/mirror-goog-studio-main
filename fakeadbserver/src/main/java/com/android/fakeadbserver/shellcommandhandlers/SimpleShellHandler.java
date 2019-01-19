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
import com.android.fakeadbserver.FakeAdbServer;
import java.net.Socket;

/**
 * A specialized version of shell handlers that assumes the command are of the form "exe arg1 arg2".
 * For more complex handlers extend {@code ShellHandler} directly.
 */
public abstract class SimpleShellHandler extends ShellHandler {

    private final String executable;

    protected SimpleShellHandler(String executable) {
        this.executable = executable;
    }

    @Override
    public boolean accept(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @NonNull DeviceState device,
            @Nullable String cmd) {
        String[] split = cmd.split(" ", 2);
        if (executable.equals(split[0])) {
            invoke(fakeAdbServer, responseSocket, device, split.length > 1 ? split[1] : "");
            return true;
        }
        return false;
    }

    /**
     * This is the main execution method of the command.
     *
     * @param fakeAdbServer Fake ADB Server itself.
     * @param responseSocket Socket for this connection.
     * @param device Target device for the command, if any.
     * @param args Arguments for the command, if any.
     */
    public abstract void invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @NonNull DeviceState device,
            @Nullable String args);
}
