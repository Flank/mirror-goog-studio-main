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

public class AdbConstants {
    /** Default host where the adb server is started. */
    public static final String DEFAULT_HOST = null; // localhost

    /** Default port where the adb server is started. */
    public static final int DEFAULT_PORT = 5037;

    /** Magic port number signifying that adb should pick its own free port. */
    public static final int ANY_PORT = 0;
}
