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

package com.android.zipflinger;

import com.android.annotations.NonNull;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

class ZipWriter implements Closeable {
    private final FileChannel channel;

    public ZipWriter(FileChannel channel) {
        this.channel = channel;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    public void truncate(long size) throws IOException {
        channel.truncate(size);
    }

    public void position(long position) throws IOException {
        channel.position(position);
    }

    public long position() throws IOException {
        return channel.position();
    }

    public int write(@NonNull ByteBuffer buffer, long position) throws IOException {
        return channel.write(buffer, position);
    }

    public int write(@NonNull ByteBuffer buffer) throws IOException {
        return channel.write(buffer);
    }

    public void transferFrom(@NonNull FileChannel src, long position, long count)
            throws IOException {
        long transfered = 0;
        while (transfered != count) {
            transfered += src.transferTo(position + transfered, count - transfered, channel);
        }
    }
}
