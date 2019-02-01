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
import java.io.IOException;
import java.net.Socket;

/** host:features returns list of features. */
public class FeaturesCommandHandler extends HostCommandHandler {

    @NonNull public static final String COMMAND = "features";
    @NonNull public static final String HOST_COMMAND = "host-features";

    @NonNull
    public static final String sFeatures = "push_sync,fixed_push_mkdir,shell_v2,apex,stat_v2,abb,abb_exec";

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @Nullable DeviceState device,
            @NonNull String args) {
        try {
            CommandHandler.writeOkayResponse(
                    responseSocket.getOutputStream(), sFeatures); // Send ok and list of features.
        } catch (IOException ignored) {
        }

        return false;
    }
}
