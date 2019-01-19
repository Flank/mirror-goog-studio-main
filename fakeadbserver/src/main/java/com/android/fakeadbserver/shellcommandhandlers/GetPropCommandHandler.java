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

/** shell:getprop gets the device properties of the specified device. */
public class GetPropCommandHandler extends SimpleShellHandler {

    public GetPropCommandHandler() {
        super("getprop");
    }

    @Override
    public void execute(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @NonNull DeviceState device,
            @Nullable String args) {
        try {
            OutputStream stream = responseSocket.getOutputStream();
            CommandHandler.writeOkay(stream); // Send ok first.
            String builder =
                    "# This is some build info\n"
                            + "# This is more build info\n\n"
                            + "[ro.product.manufacturer]: ["
                            + device.getManufacturer()
                            + "]\n"
                            + "[ro.product.model]: ["
                            + device.getModel()
                            + "]\n"
                            + "[ro.build.version.release]: ["
                            + device.getBuildVersionRelease()
                            + "]\n"
                            + "[ro.build.version.sdk]: ["
                            + device.getBuildVersionSdk()
                            + "]\n";
            stream.write(builder.getBytes(Charsets.UTF_8));
        } catch (IOException ignored) {
        }
    }
}
