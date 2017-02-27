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
import com.google.common.base.Charsets;
import com.google.common.primitives.UnsignedInteger;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamConnection implements Connection {
    /** Response header from adb server that indicates that the command succeeded. */
    public static final String STATUS_OKAY = "OKAY";

    private final BufferedInputStream is;
    private final BufferedOutputStream os;

    public StreamConnection(@NonNull InputStream is, @NonNull OutputStream os) {
        this.is = new BufferedInputStream(is);
        this.os = new BufferedOutputStream(os);
    }

    @Override
    public void close() throws IOException {
        is.close();
        os.close();
    }

    @Override
    public void writeCommand(@NonNull CommandBuffer buffer) throws IOException {
        byte[] command = buffer.toByteArray();
        os.write(String.format("%04X", command.length).getBytes(Charsets.UTF_8));
        os.write(command);
        os.flush();
    }

    @Override
    public boolean isOk() throws IOException {
        return STATUS_OKAY.equals(readString(4));
    }

    @NonNull
    @Override
    public String getError() throws IOException {
        return readString(readUnsignedHexInt().intValue());
    }

    @NonNull
    @Override
    public UnsignedInteger readUnsignedHexInt() throws IOException {
        return UnsignedInteger.valueOf(readString(4), 16);
    }

    @NonNull
    @Override
    public String readString(int len) throws IOException {
        byte[] data = new byte[len];
        readFully(data);
        return new String(data, Charsets.UTF_8);
    }

    private int readFully(@NonNull byte[] data) throws IOException {
        int len = 0;

        while (len < data.length) {
            int r = is.read(data, len, data.length - len);
            if (r <= 0) {
                throw new IOException(
                        "End of Stream before fully reading " + data.length + " bytes");
            }

            len += r;
        }

        return len;
    }
}
