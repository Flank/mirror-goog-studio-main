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
import com.android.tools.device.internal.adb.commands.AdbCommand;
import com.android.tools.device.internal.adb.commands.CommandResult;
import com.google.common.primitives.UnsignedInteger;
import java.io.Closeable;
import java.io.IOException;

/** A {@link Connection} represents an open connection from an adb client to a server. */
public interface Connection extends Closeable {
    /** Returns the result of issuing the given command to the server endpoint. */
    @NonNull
    CommandResult executeCommand(@NonNull AdbCommand command) throws IOException;

    /** Issues a command that doesn't result in a result. */
    void issueCommand(@NonNull AdbCommand command) throws IOException;

    /**
     * Returns an integer formed by reading 4 bytes of data from the response and interpreting it as
     * a hex formatted integer.
     */
    @NonNull
    UnsignedInteger readUnsignedHexInt() throws IOException;

    @NonNull
    String readString(int len) throws IOException;
}
