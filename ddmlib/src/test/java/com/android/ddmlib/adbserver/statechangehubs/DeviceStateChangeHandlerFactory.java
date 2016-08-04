/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ddmlib.adbserver.statechangehubs;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.adbserver.DeviceState;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * A factory interface to create device event receivers. The {@link Supplier}'s return value
 * indicates if a possible error may have occurred (or a shut down signal was sent), and the calling
 * thread should terminate the processing task and release all open resources.
 */
public interface DeviceStateChangeHandlerFactory extends StateChangeHandlerFactory {

    @NonNull
    Supplier<HandlerResult> createDeviceListChangedHandler(
            @NonNull Collection<DeviceState> deviceList);

    @NonNull
    Supplier<HandlerResult> createDeviceStateChangedHandler(@NonNull DeviceState device,
            @NonNull IDevice.DeviceState status);

}
