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
import com.android.tools.device.internal.adb.commands.CommandBuffer;
import com.android.tools.device.internal.adb.commands.CommandResult;
import com.google.common.base.Charsets;
import com.google.common.primitives.UnsignedInteger;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class ChannelConnection implements Connection {
    private final ReadableByteChannel readChannel;
    private final WritableByteChannel writeChannel;

    public ChannelConnection(
            @NonNull ReadableByteChannel readChannel, @NonNull WritableByteChannel writeChannel) {
        this.readChannel = readChannel;
        this.writeChannel = writeChannel;
    }

    @Override
    public void close() throws IOException {
        readChannel.close();
        writeChannel.close();
    }

    @Override
    @NonNull
    public CommandResult executeCommand(@NonNull CommandBuffer buffer) throws IOException {
        issueCommand(buffer);
        String status = readString(4);
        if ("OKAY".equals(status)) {
            return CommandResult.OKAY;
        } else if ("FAIL".equals(status)) {
            return CommandResult.createError(readError());
        } else {
            return CommandResult.createError("Protocol Fault: " + status);
        }
    }

    @Override
    public void issueCommand(@NonNull CommandBuffer buffer) throws IOException {
        byte[] command = buffer.toByteArray();
        writeFully(String.format("%04X", command.length).getBytes(Charsets.UTF_8));
        writeFully(command);
    }

    @Nullable
    private String readError() throws IOException {
        int len = readUnsignedHexInt().intValue();
        return len > 0 ? readString(len) : null;
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

    private void readFully(@NonNull byte[] data) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data);
        while (buf.position() != buf.limit()) {
            readChannel.read(buf);
        }
    }

    private void writeFully(byte[] data) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data);
        while (buf.position() != buf.limit()) {
            writeChannel.write(buf);
        }
    }
}
