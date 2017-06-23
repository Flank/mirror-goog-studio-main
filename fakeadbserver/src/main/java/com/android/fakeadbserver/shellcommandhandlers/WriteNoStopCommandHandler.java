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
import com.android.fakeadbserver.CommandHandler;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/** shell:write-no-stop continuously write to the output stream without stopping. */
public class WriteNoStopCommandHandler extends ShellCommandHandler {

    @NonNull public static final String COMMAND = "write-no-stop";

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @NonNull DeviceState device,
            @Nullable String args) {
        OutputStream stream = null;
        try {
            try {
                stream = responseSocket.getOutputStream();
                CommandHandler.writeOkay(stream); // Send ok first.
                String testMessage = "write-no-stop test in progress\n";
                while (true) {
                    stream.write(testMessage.getBytes(Charsets.UTF_8));
                    Thread.sleep(200);
                }
            } finally {
                if (stream != null) {
                    stream.flush();
                    stream.close();
                }
            }
        } catch (IOException | InterruptedException ignored) {
        }
        return false;
    }
}
