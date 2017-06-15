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

package com.android.tools.device.internal.adb;

import com.android.annotations.NonNull;
import java.util.Arrays;

/**
 * Features supported by adb, this is a mix of both host server specific features and daemon
 * specific features.
 *
 * <p>See system/core/adb/transport.h for the canonical list of features.
 */
public enum AdbFeature {
    SHELL2("shell_v2"),
    CMD("cmd"),
    STAT2("stat_v2"),
    LIBUSB("libusb"),
    PUSH_SYNC("push_sync"),
    UNKNOWN("unknown"); // Unknown just in case we ever are ever not up-to-date with adb

    private final String id;

    AdbFeature(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public static AdbFeature fromFeatureId(@NonNull String featureId) {
        return Arrays.stream(values())
                .filter(feature -> featureId.equals(feature.id))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
