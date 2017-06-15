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

package com.android.tools.device.internal.adb.commands;

import com.android.annotations.NonNull;
import com.android.tools.device.internal.adb.AdbFeature;
import com.android.tools.device.internal.adb.Connection;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class HostFeatures implements AdbCommand<Set<AdbFeature>> {
    private static final String COMMAND = "host-features";
    private static final String FEATURE_DELIMITER = ",";

    @NonNull
    @Override
    public String getName() {
        return COMMAND;
    }

    @NonNull
    @Override
    public String getQuery() {
        return HOST_COMMAND_PREFIX + COMMAND;
    }

    @NonNull
    @Override
    public Set<AdbFeature> execute(@NonNull Connection conn) throws IOException {
        CommandResult result = conn.executeCommand(this);

        if (!result.isOk()) {
            String msg = "Error retrieving feature set";
            String error = result.getError();
            if (error != null) {
                msg += ": " + error;
            }
            throw new IOException(msg);
        }

        int len = conn.readUnsignedHexInt().intValue();
        if (len == 0) {
            return ImmutableSet.of();
        }

        String response = conn.readString(len);

        return Arrays.stream(response.split(FEATURE_DELIMITER))
                .map(AdbFeature::fromFeatureId)
                .collect(Collectors.toSet());
    }
}
