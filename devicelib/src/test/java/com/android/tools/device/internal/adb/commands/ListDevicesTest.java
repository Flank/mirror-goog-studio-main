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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.testutils.truth.MoreTruth;
import com.android.tools.device.internal.adb.DeviceHandle;
import com.android.tools.device.internal.adb.StreamConnection;
import com.google.common.base.Charsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;

public class ListDevicesTest {
    private ByteArrayOutputStream commandStream;
    private ListDevices listDevices;

    @Before
    public void setup() {
        commandStream = new ByteArrayOutputStream();
        listDevices = new ListDevices();
    }

    @Test
    public void list_nominal() throws IOException {
        String response = "emulator-5554          device product:sdk_google_phone_x86 model:Android_SDK_built_for_x86 device:generic_x86\n"
                        + "412KPGS0147439         device usb:2-1.4.3 product:lenok model:G_Watch_R device:lenok\n"
                        + "0871182e               device product:razorg model:Nexus_7 device:deb";
        byte[] responseData = createOkayResponse(response);
        StreamConnection connection = new StreamConnection(new ByteArrayInputStream(responseData),
                commandStream);
        List<DeviceHandle> devices = listDevices.execute(connection);
        assertThat(devices.size()).isEqualTo(3);
        assertThat(devices.get(0).getSerial()).isEqualTo("emulator-5554");
        assertThat(devices.get(1).getSerial()).isEqualTo("412KPGS0147439");
        assertThat(devices.get(2).getSerial()).isEqualTo("0871182e");
        MoreTruth.assertThat(devices.get(2).getDevicePath()).isAbsent();
    }

    @Test
    public void list_empty() throws IOException {
        byte[] responseData = createOkayResponse("");
        StreamConnection connection =
                new StreamConnection(new ByteArrayInputStream(responseData), commandStream);
        List<DeviceHandle> devices = listDevices.execute(connection);
        assertThat(devices.isEmpty()).isTrue();
    }

    @Test
    public void list_error() throws IOException {
        byte[] responseData = "FAIL0003err".getBytes(Charsets.UTF_8);
        StreamConnection connection = new StreamConnection(new ByteArrayInputStream(responseData),
                commandStream);
        try {
            listDevices.execute(connection);
            fail("Expected to throw an IOException when server returns a failure message");
        } catch (IOException e) {
            assertThat(e.getMessage()).isEqualTo("Error retrieving device list: err");
        }
    }

    @NonNull
    private static byte[] createOkayResponse(@NonNull String payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("OKAY");
        sb.append(String.format(Locale.US, "%04X", payload.length()));
        sb.append(payload);
        return sb.toString().getBytes(Charsets.UTF_8);
    }
}