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

package com.android.build.gradle.internal.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.utils.ILogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ConnectedDeviceTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    public IDevice mIDevice;

    @Mock
    public ILogger mLogger;

    private ConnectedDevice mDevice;

    @Before
    public void createDevice() {
        when(mIDevice.getSystemProperty(anyString())).thenReturn(Futures.immediateFuture(null));
        mDevice = new ConnectedDevice(mIDevice, mLogger,  10000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testGetAbisForLAndAbove() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_CPU_ABI_LIST))
                .thenReturn(Futures.immediateFuture("x86,x86_64"));
        assertThat(mDevice.getAbis()).containsExactly("x86", "x86_64");
    }


    @Test
    public void testGetSingleAbiForPreL() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_CPU_ABI))
                .thenReturn(Futures.immediateFuture("x86"));
        assertThat(mDevice.getAbis()).containsExactly("x86");
    }

    @Test
    public void testGetAbisForPreL() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_CPU_ABI))
                .thenReturn(Futures.immediateFuture("x86"));
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_CPU_ABI2))
                .thenReturn(Futures.immediateFuture("x86_64"));
        assertThat(mDevice.getAbis()).containsExactly("x86", "x86_64");
    }

    @Test
    public void testGetDensityFromDevice() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_DENSITY))
                .thenReturn(Futures.immediateFuture("480"));
        assertThat(mDevice.getDensity()).isEqualTo(480);
    }

    @Test
    public void testGetDensityFromEmulator() {
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_EMULATOR_DENSITY))
                .thenReturn(Futures.immediateFuture("380"));
        assertThat(mDevice.getDensity()).isEqualTo(380);
    }

    @Test
    public void testGetDensityTimeout() throws Exception {
        ListenableFuture<String> future =
                Futures.immediateFailedFuture(new TimeoutException("Future expected to time out"));

        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_DENSITY)).thenReturn(future);
        assertThat(mDevice.getDensity()).isEqualTo(-1);
    }


    @Test
    public void testGetDensityInfiniteTimeout() {
        ConnectedDevice device = new ConnectedDevice(mIDevice, mLogger, 0, TimeUnit.MILLISECONDS);
        when(mIDevice.getSystemProperty(IDevice.PROP_DEVICE_DENSITY))
                .thenReturn(Futures.immediateFuture("480"));
        assertThat(device.getDensity()).isEqualTo(480);
    }
}
