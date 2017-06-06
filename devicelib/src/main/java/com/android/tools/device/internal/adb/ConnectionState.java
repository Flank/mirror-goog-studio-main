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

/**
 * State of an adb connection.
 *
 * <p>Note: this matches the ConnectionState enum defined in system/core/adb/adb.h
 */
public enum ConnectionState {
    OFFLINE("offline"),
    BOOTLOADER("bootloader"),
    DEVICE("device"),
    HOST("host"),
    RECOVERY("recovery"),
    NOPERM("no permissions"),
    SIDELOAD("sideload"),
    UNAUTHORIZED("unauthorized"),
    UNKNOWN("unknown");

    private final String prefix;

    ConnectionState(@NonNull String namePrefix) {
        this.prefix = namePrefix;
    }

    @NonNull
    public static ConnectionState fromName(@NonNull String name) {
        for (ConnectionState state : values()) {
            if (name.startsWith(state.prefix)) {
                return state;
            }
        }

        throw new IllegalArgumentException("Unknown connection state: " + name);
    }
}
