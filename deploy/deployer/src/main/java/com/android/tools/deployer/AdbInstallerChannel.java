/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.deployer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/** An abstraction layer over SocketChannel with timeout on both read and write. */
class AdbInstallerChannel implements AutoCloseable {

    private final SocketChannel channel;

    private final Selector readSelector;
    private final SelectionKey readKey;

    private final Selector writeSelector;
    private final SelectionKey writeKey;

    private final ReentrantLock lock = new ReentrantLock(true);

    AdbInstallerChannel(SocketChannel c) throws IOException {
        channel = c;
        channel.configureBlocking(false);

        readSelector = Selector.open();
        readKey = channel.register(readSelector, SelectionKey.OP_READ);

        writeSelector = Selector.open();
        writeKey = channel.register(writeSelector, SelectionKey.OP_WRITE);
    }

    /**
     * Fully read from the socket into the ByteBuffer. Upon return, the buffer remaining space is
     * guaranteed to be zero.
     *
     * @param buffer where to store data read from the socket
     * @param timeOutMs timeout in milliseconds (for the full operation to complete)
     * @throws IOException If not enough data could be read from the socket before timeout.
     */
    void read(ByteBuffer buffer, long timeOutMs) throws IOException {
        checkLock();
        while (buffer.remaining() != 0) {
            readSelector.select(timeOutMs);
            if (!readKey.isReadable()) {
                String template = "InstallerChannel: Read %d bytes failed (%d ms)";
                String msg = String.format(Locale.US, template, buffer.remaining(), timeOutMs);
                throw new IOException(msg);
            }
            int read = channel.read(buffer);
            if (read == -1) {
                // The socket was remotely closed.
                break;
            }
        }

        if (buffer.remaining() != 0) {
            String template = "Unable to read %d bytes (read %d)";
            String msg = String.format(Locale.US, template, buffer.capacity(), buffer.limit());
            throw new IOException(msg);
        }

        buffer.rewind();
    }

    /**
     * Fully write the buffer into the socket. Upon return, the buffer remaining bytes is guaranteed
     * to be zero
     *
     * @param buffer data to be sent
     * @param timeOutMs timeout in milliseconds (for the full operation to complete)
     * @throws IOException If buffer cannot be fully written within timeout or if socket was
     *     remotely closed
     */
    void write(ByteBuffer buffer, long timeOutMs) throws IOException {
        checkLock();
        while (buffer.remaining() != 0) {
            writeSelector.select(timeOutMs);
            if (!writeKey.isWritable()) {
                String template = "InstallerChannel: Write %d bytes failed (%d ms)";
                String msg = String.format(Locale.US, template, buffer.remaining(), timeOutMs);
                throw new IOException(msg);
            }
            // We cannot detect remote close from write() returned value.
            // If the socket is remotely closed, a IOException: Broken pipe will
            // be thrown.
            channel.write(buffer);
        }
    }

    @Override
    public void close() throws IOException {
        try (Channel c = channel;
                Selector r = readSelector;
                Selector w = writeSelector) {}
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public void checkLock() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Channel lock must be acquired before read/write");
        }
    }
}
