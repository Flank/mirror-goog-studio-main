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
import com.android.tools.device.internal.adb.commands.CommandBuffer;
import com.google.common.primitives.UnsignedInteger;
import java.io.Closeable;
import java.io.IOException;

/** A {@link Connection} represents an open connection from an adb client to a server. */
public interface Connection extends Closeable {
    /** Issues the given command to the server endpoint. */
    void writeCommand(@NonNull CommandBuffer buffer) throws IOException;

    /** Returns whether adb server responded "OKAY" for the issued command. */
    boolean isOk() throws IOException;

    /** Returns the error message from adb server following a "FAIL" response. */
    @NonNull
    String getError() throws IOException;

    /**
     * Returns an integer formed by reading 4 bytes of data from the response and interpreting it as
     * a hex formatted integer.
     */
    @NonNull
    UnsignedInteger readUnsignedHexInt() throws IOException;
}
