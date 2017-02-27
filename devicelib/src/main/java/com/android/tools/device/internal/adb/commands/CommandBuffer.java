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

import com.android.annotations.NonNull;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

/**
 * A {@link CommandBuffer} is a thin wrapper over a {@link ByteArrayDataOutput}, that exposes
 * methods that make it easy to construct commands to the adb server.
 */
public class CommandBuffer {
    private final ByteArrayDataOutput data;

    public CommandBuffer() {
        this(16);
    }

    public CommandBuffer(int initialCapacity) {
        data = ByteStreams.newDataOutput(initialCapacity);
    }

    @NonNull
    public byte[] toByteArray() {
        return data.toByteArray();
    }

    @NonNull
    public CommandBuffer writeHostCommand(@NonNull HostService service) {
        data.write(service.getCommand());
        return this;
    }
}
