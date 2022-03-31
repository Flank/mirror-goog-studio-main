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
import java.net.Socket;

/** host:features returns list of features. */
// TODO: Refactor this class. Split in two. One for Device features and one fore Host features.
public class FeaturesCommandHandler extends HostCommandHandler {

    @NonNull public static final String COMMAND = "features";
    @NonNull public static final String HOST_COMMAND = "host-features";

    @NonNull
    public static final String COMMON_FEATURES = "push_sync,fixed_push_mkdir,shell_v2,apex,stat_v2";

    private static final String DEVICE_30_FEATURES = "abb,abb_exec";

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @Nullable DeviceState device,
            @NonNull String args) {
        try {

            String features = COMMON_FEATURES;

            // This is a host-features request
            if (device == null) {
                features += "," + DEVICE_30_FEATURES;
            } else {
                // This is a device features request
                if (Integer.parseInt(device.getBuildVersionSdk()) >= 30) {
                    features += "," + DEVICE_30_FEATURES;
                }
            }
            CommandHandler.writeOkayResponse(
                    responseSocket.getOutputStream(), features); // Send ok and list of features.
        } catch (Exception ignored) {
        }

        return false;
    }
}
