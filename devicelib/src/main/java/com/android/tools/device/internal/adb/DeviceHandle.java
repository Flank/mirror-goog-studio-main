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
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DeviceHandle {
    private static final String KEY_PRODUCT = "product";
    private static final String KEY_MODEL = "model";
    private static final String KEY_DEVICE = "device";

    private final String serial;
    private final ConnectionState state;
    private final String devPath;
    private final ImmutableMap<String, String> props;

    private DeviceHandle(
            @NonNull String serial,
            @NonNull ConnectionState connectionState,
            @Nullable String devicePath,
            @NonNull Map<String, String> props) {
        this.serial = serial;
        this.state = connectionState;
        this.devPath = devicePath;
        this.props = ImmutableMap.copyOf(props);
    }

    @NonNull
    public String getSerial() {
        return serial;
    }

    @NonNull
    public ConnectionState getConnectionState() {
        return state;
    }

    public Optional<String> getDevicePath() {
        return Optional.ofNullable(devPath);
    }

    public Optional<String> getProduct() {
        return Optional.ofNullable(props.get(KEY_PRODUCT));
    }

    public Optional<String> getModel() {
        return Optional.ofNullable(props.get(KEY_MODEL));
    }

    public Optional<String> getDevice() {
        return Optional.ofNullable(props.get(KEY_DEVICE));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DeviceHandle that = (DeviceHandle) o;
        return Objects.equals(serial, that.serial)
                && state == that.state
                && Objects.equals(devPath, that.devPath)
                && Objects.equals(props, that.props);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serial, state, devPath, props);
    }

    /**
     * Returns a {@link DeviceHandle} from adb's transport listing (both long and short forms)
     *
     * <p>In adb's terminology, each device is a "transport". The long listing for a transport
     * prints out the transport in the following format:
     *
     * <pre>serial-number connection-state-name device-path? (product:x model:y device:z)?</pre>
     *
     * The device path field is absent in the case of Windows. The product details are absent if the
     * device is not online.
     *
     * <p>The short listing only contains the first two fields (serial and connection state).
     *
     * @see <a
     *     href="https://android.googlesource.com/platform/system/core/+/master/adb/transport.cpp">system/core/adb/transport.cpp</a>
     */
    @NonNull
    public static DeviceHandle create(@NonNull String transport) {
        String[] components = transport.split("\\s+");
        if (components.length < 2) {
            String msg =
                    String.format(
                            Locale.US,
                            "transport listing expected to have atleast 2 components, got %1$d in '%2$s'",
                            components.length,
                            transport);
            throw new IllegalArgumentException(msg);
        }

        String serial = components[0];
        ConnectionState connectionState = ConnectionState.fromName(components[1]);

        if (components.length == 2) {
            return new DeviceHandle(serial, connectionState, null, Collections.emptyMap());
        }

        int propsIndex = 2;
        String devPath = null;

        String nextComponent = components[2];
        if (!nextComponent.startsWith(KEY_PRODUCT)) {
            // we have a device path
            devPath = nextComponent;
            propsIndex++;
        }

        Map<String, String> props = new HashMap<>();
        for (int i = propsIndex; i < components.length; i++) {
            String component = components[i];
            int index = component.indexOf(':');
            if (index < 0 || index == component.length() - 1) {
                continue;
            }

            String name = component.substring(0, index);
            String val = component.substring(index + 1);
            props.put(name, val);
        }

        return new DeviceHandle(serial, connectionState, devPath, props);
    }
}
