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

package com.android.instantapp.utils;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Lists;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mockito.ArgumentMatchers;

/** Utils for tests. */
public class InstantAppTests {

    /** Generates a mock device that can be configured. */
    public static class DeviceGenerator {
        @NonNull private final IDevice myDevice;
        @NonNull private final Set<String> myRegisteredCommands;

        public DeviceGenerator() {
            myDevice = mock(IDevice.class);
            myRegisteredCommands = new HashSet<>();
        }

        @NonNull
        private DeviceGenerator setDefaultEmptyShellResponse() throws Throwable {
            doAnswer(
                            invocation -> {
                                IShellOutputReceiver receiver = invocation.getArgument(1);
                                byte[] output = "".getBytes(Charset.defaultCharset());
                                receiver.addOutput(output, 0, output.length);
                                receiver.flush();
                                return null;
                            })
                    .when(myDevice)
                    .executeShellCommand(
                            ArgumentMatchers.argThat(
                                    command -> !myRegisteredCommands.contains(command)),
                            notNull());
            return this;
        }

        @NonNull
        public DeviceGenerator setResponseToCommand(
                @NonNull String command, @NonNull String response) throws Throwable {
            myRegisteredCommands.add(command);
            doAnswer(
                            invocation -> {
                                IShellOutputReceiver receiver = invocation.getArgument(1);
                                byte[] output = response.getBytes(Charset.defaultCharset());
                                receiver.addOutput(output, 0, output.length);
                                receiver.flush();
                                return null;
                            })
                    .when(myDevice)
                    .executeShellCommand(eq(command), notNull());
            return this;
        }

        @NonNull
        public DeviceGenerator setArchitectures(@NonNull String... archs) {
            when(myDevice.getAbis()).thenReturn(Lists.newArrayList(archs));
            return this;
        }

        @NonNull
        public DeviceGenerator setApiLevel(int apiLevel, @Nullable String codename) {
            when(myDevice.getVersion()).thenReturn(new AndroidVersion(apiLevel, codename));
            return this;
        }

        @NonNull
        public DeviceGenerator setVersionOfPackage(@NonNull String pkgName, long version)
                throws Throwable {
            return setResponseToCommand(
                    "dumpsys package " + pkgName, "versionCode=" + version + " \n");
        }

        @NonNull
        public DeviceGenerator setGoogleAccountLogged() throws Throwable {
            return setResponseToCommand(
                    "dumpsys account", "Account {name=bla@google.com, type=com.google}\n");
        }

        @NonNull
        public DeviceGenerator setOsBuildType(@NonNull String osBuildType) {
            when(myDevice.getProperty("ro.build.tags")).thenReturn(osBuildType);
            return this;
        }

        @NonNull
        public DeviceGenerator setManufacturer(@NonNull String manufacturer) {
            when(myDevice.getProperty("ro.product.manufacturer")).thenReturn(manufacturer);
            return this;
        }

        @NonNull
        public DeviceGenerator setAndroidDevice(@NonNull String androidDevice) {
            when(myDevice.getProperty("ro.product.device")).thenReturn(androidDevice);
            return this;
        }

        @NonNull
        public DeviceGenerator setProduct(@NonNull String product) {
            when(myDevice.getProperty("ro.product.name")).thenReturn(product);
            return this;
        }

        @NonNull
        public DeviceGenerator setHardware(@NonNull String hardware) {
            when(myDevice.getProperty("ro.hardware")).thenReturn(hardware);
            return this;
        }

        @NonNull
        public DeviceGenerator setOnline() {
            when(myDevice.isOnline()).thenReturn(true);
            return this;
        }

        @NonNull
        public DeviceGenerator setIsEmulator(boolean isEmulator) {
            when(myDevice.isEmulator()).thenReturn(isEmulator);
            return this;
        }

        @NonNull
        public IDevice getDevice() throws Throwable {
            return setDefaultEmptyShellResponse().myDevice;
        }

        @NonNull
        public DeviceGenerator setLogcat(@NonNull List<String> messages) throws Throwable {
            doAnswer(
                            invocation -> {
                                IShellOutputReceiver receiver = invocation.getArgument(1);
                                for (String message : messages) {
                                    byte[] output = message.getBytes(Charset.defaultCharset());
                                    receiver.addOutput(output, 0, output.length);
                                }
                                receiver.flush();
                                return null;
                            })
                    .when(myDevice)
                    .executeShellCommand(eq("logcat -v long"), notNull(), anyInt());
            return this;
        }
    }
}
