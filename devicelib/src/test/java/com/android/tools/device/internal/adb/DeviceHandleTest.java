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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.testutils.truth.MoreTruth;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class DeviceHandleTest {
    @Test
    public void create_WithoutDevicePath() {
        DeviceHandle handle =
                DeviceHandle.create(
                        "2fe6c14a        device product:kltexx model:SM_G900F device:klte");

        assertThat(handle.getSerial()).isEqualTo("2fe6c14a");
        assertThat(handle.getConnectionState()).isEqualTo(ConnectionState.DEVICE);
        MoreTruth.assertThat(handle.getDevicePath()).isAbsent();
        assertThat(handle.getProduct().get()).isEqualTo("kltexx");
        assertThat(handle.getModel().get()).isEqualTo("SM_G900F");
        assertThat(handle.getDevice().get()).isEqualTo("klte");
    }

    @Test
    public void create_WithDevicePath() {
        DeviceHandle handle =
                DeviceHandle.create(
                        "0871182e               device usb:3-1 product:razorg model:Nexus_7 device:deb");

        assertThat(handle.getSerial()).isEqualTo("0871182e");
        assertThat(handle.getDevicePath().get()).isEqualTo("usb:3-1");
    }

    @Test
    public void create_InvalidListing() {
        try {
            DeviceHandle.create("2fe6c14a       product:kltexx model:SM_G900F device:klte");
            fail(
                    "DeviceHandle should not have been constructed if the connection state was missing");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Unknown connection state: product:kltexx");
        }
    }

    @Test
    public void create_offline() {
        DeviceHandle handle = DeviceHandle.create("CQEU5L7L8HBIV8DA       offline usb:20:22");
        assertThat(handle.getSerial()).isEqualTo("CQEU5L7L8HBIV8DA");
        assertThat(handle.getConnectionState()).isEqualTo(ConnectionState.OFFLINE);
    }

    @Test
    public void create_unauthorized() {
        DeviceHandle handle = DeviceHandle.create("CQEU5L7L8HBIV8DA       unauthorized");
        assertThat(handle.getSerial()).isEqualTo("CQEU5L7L8HBIV8DA");
        assertThat(handle.getConnectionState()).isEqualTo(ConnectionState.UNAUTHORIZED);
    }

    @Test
    public void equality() {
        EqualsVerifier.forClass(DeviceHandle.class).verify();
    }
}
