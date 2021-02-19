/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.deployer.devices;

public enum DeviceId {
    API_19("4.4", 19),
    API_21("5.0", 21),
    API_22("5.1", 22),
    API_23("6.0", 23),
    API_24("7.0", 24),
    API_25("7.1", 25),
    API_26("8.0", 26),
    API_27("8.1", 27),
    API_28("9.0", 28),
    API_29("10.0", 29),
    API_30("11.0", 30),
    API_31("12.0", 31);

    public static final DeviceId MIN_VALUE = DeviceId.values()[0];
    public static final DeviceId MAX_VALUE = DeviceId.values()[DeviceId.values().length - 1];

    private final String version;
    private final int api;

    DeviceId(String version, int api) {
        this.version = version;
        this.api = api;
    }

    public String version() {
        return version;
    }

    public int api() {
        return api;
    }
}
