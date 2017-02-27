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

package com.android.fakeadbserver.statechangehubs;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.DeviceState.DeviceStatus;
import java.util.Collection;

/**
 * This class is the primary class that effects the changes to device states and propagates the
 * changes to existing, registered monitoring connections.
 */
public final class DeviceStateChangeHub
        extends StateChangeHub<DeviceStateChangeHandlerFactory> {

    public void deviceListChanged(@NonNull Collection<DeviceState> deviceList) {
        synchronized (mHandlers) {
            mHandlers.forEach((stateChangeQueue, deviceChangeHandlerFactory) -> stateChangeQueue
                    .add(deviceChangeHandlerFactory.createDeviceListChangedHandler(deviceList)));
        }
    }

    public void deviceStatusChanged(@NonNull DeviceState device, @NonNull DeviceStatus status) {
        synchronized (mHandlers) {
            mHandlers.forEach(
                    (stateChangeQueue, deviceChangeHandlerFactory) ->
                            stateChangeQueue.add(
                                    deviceChangeHandlerFactory.createDeviceStateChangedHandler(
                                            device, status)));
        }
    }
}
