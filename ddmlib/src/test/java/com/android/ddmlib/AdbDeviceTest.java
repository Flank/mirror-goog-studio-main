/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ddmlib;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AdbDeviceTest {
    @Test
    public void parserTest() {
        String[] inputStrings =
                new String[] {
                    "FA78B1A99999           unauthorized usb:1-10 transport_id:4",
                    "FA78B1A99999           device usb:1-10 product:walleye model:Pixel_2 device:walleye transport_id:4",
                    "(no serial number)     device",
                    "BAADF00D               no permissions (problem descriptios goes here)"
                };

        AdbDevice[] expected =
                new AdbDevice[] {
                    new AdbDevice("FA78B1A99999", IDevice.DeviceState.UNAUTHORIZED),
                    new AdbDevice("FA78B1A99999", IDevice.DeviceState.ONLINE),
                    new AdbDevice(null, IDevice.DeviceState.ONLINE),
                    new AdbDevice("BAADF00D", null)
                };

        for (int idx = 0; idx < inputStrings.length; idx++) {
            String input = inputStrings[idx];

            assertEquals(
                    "Parsing " + input, expected[idx], AdbDevice.parseAdbLine(inputStrings[idx]));
        }
    }
}
