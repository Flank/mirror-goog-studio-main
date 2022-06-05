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

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.idea.protobuf.CodedInputStream;
import com.android.tools.idea.protobuf.CodedOutputStream;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channel;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/** An abstraction layer over SocketChannel with timeout on both read and write. */
class AdbInstallerChannel implements AutoCloseable {

    // MessagePipeWrapper magic number; should be kept in sync with message_pipe_wrapper.cc
    private static final byte[] MAGIC_NUMBER = {
        (byte) 0xAC,
        (byte) 0xA5,
        (byte) 0xAC,
        (byte) 0xA5,
        (byte) 0xAC,
        (byte) 0xA5,
        (byte) 0xAC,
        (byte) 0xA5
    };

    private final SocketChannel channel;

    private final Selector readSelector;
    private final SelectionKey readKey;

    private final Selector writeSelector;
    private final SelectionKey writeKey;

    private final ReentrantLock lock = new ReentrantLock(true);

    private final ILogger logger;

    // TCP buffer size on all three platforms we support default to a few hundreds KiB.
    // Because we work with non-blocking sockets, even a multi-MiB ByteBuffer will be
    // written in batches that cannot exceed that TCP buffer size. This timeout affects
    // how long to wait for a socket to be available before EACH write operations.
    // Is is set so that it can only fails if the other party stops processing data.
    private static final long PER_WRITE_TIME_OUT = TimeUnit.SECONDS.toMillis(5);

    AdbInstallerChannel(SocketChannel c, ILogger logger) throws IOException {
        channel = c;
        channel.configureBlocking(false);

        readSelector = Selector.open();
        readKey = channel.register(readSelector, SelectionKey.OP_READ);

        writeSelector = Selector.open();
        writeKey = channel.register(writeSelector, SelectionKey.OP_WRITE);

        this.logger = logger;
    }

    /**
     * Fully read from the socket into the ByteBuffer. Upon return, the buffer remaining space is
     * guaranteed to be zero.
     *
     * @param buffer where to store data read from the socket
     * @param timeOutMs timeout in milliseconds (for the full operation to complete)
     * @throws IOException If not enough data could be read from the socket before timeout.
     */
    private void read(ByteBuffer buffer, long timeOutMs) throws IOException {
        checkLock();
        long deadline = System.currentTimeMillis() + timeOutMs;
        while (true) {
            // Everything was received
            if (buffer.remaining() == 0) {
                break;
            }

            long timeout = Math.max(0, deadline - System.currentTimeMillis());
            readSelector.select(timeout);

            int read = channel.read(buffer);
            if (read == 0 || System.currentTimeMillis() >= deadline) {
                // If we timeout, the Installer could still write in the socket. These bytes would
                // be stored in the OS buffer and returned on the next request, effectively
                // desyncing. We have no choice but to the close the connection at this point.
                close();

                // Select timed out or deadline expired.
                String template = "InstallerChannel.select: Timeout on read after %dms";
                String msg = String.format(Locale.US, template, timeOutMs);
                throw new IOException(msg);
            }

            if (read == -1) {
                // The socket was remotely closed.
                break;
            }
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
    private void write(ByteBuffer buffer, long timeOutMs) throws IOException, TimeoutException {
        checkLock();
        long deadline = System.currentTimeMillis() + timeOutMs;
        while (true) {
            // Everything was sent
            if (buffer.remaining() == 0) {
                break;
            }

            if (System.currentTimeMillis() >= deadline) {
                throw new TimeoutException("InstallerChannel write timeout");
            }

            long timeout = Math.min(PER_WRITE_TIME_OUT, deadline - System.currentTimeMillis());
            timeout = Math.max(0, timeout);
            writeSelector.select(timeout);

            // We cannot detect remote close from write() returned value.
            // If the socket is remotely closed, a IOException: Broken pipe will
            // be thrown.
            int written = channel.write(buffer);

            // Check for select timeout
            if (written == 0) {
                throw new TimeoutException("InstallerChannel write timeout");
            }
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

    boolean writeRequest(Deploy.InstallerRequest request, long timeOutMs) throws TimeoutException {
        ByteBuffer bytes = wrap(request);
        try {
            write(bytes, timeOutMs);
        } catch (IOException | ClosedSelectorException e) {
            // If the connection has been broken an IOException 'broken pipe' will be received here.
            return false;
        }
        return bytes.remaining() == 0;
    }

    Deploy.InstallerResponse readResponse(long timeOutMs) {
        try {
            ByteBuffer bufferMarker = ByteBuffer.allocate(MAGIC_NUMBER.length);
            read(bufferMarker, timeOutMs);

            if (!Arrays.equals(MAGIC_NUMBER, bufferMarker.array())) {
                String garbage = new String(bufferMarker.array(), Charsets.UTF_8);
                logger.info("Read '" + garbage + "' from socket");
                return null;
            }

            ByteBuffer bufferSize =
                    ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            read(bufferSize, timeOutMs);
            int responseSize = bufferSize.getInt();
            if (responseSize < 0) {
                return null;
            }

            ByteBuffer bufferPayload = ByteBuffer.allocate(responseSize);
            read(bufferPayload, timeOutMs);
            return unwrap(bufferPayload);
        } catch (IOException e) {
            // If the connection has been broken an IOException 'broken pipe' will be received here.
            logger.warning("Error while reading InstallerChannel");
            return null;
        }
    }

    private Deploy.InstallerResponse unwrap(ByteBuffer buffer) {
        buffer.rewind();
        try {
            CodedInputStream cis = CodedInputStream.newInstance(buffer);
            return Deploy.InstallerResponse.parser().parseFrom(cis);
        } catch (IOException e) {
            // All in-memory buffers, should not happen
            throw new IllegalStateException(e);
        }
    }

    private ByteBuffer wrap(Deploy.InstallerRequest message) {
        int messageSize = message.getSerializedSize();
        int headerSize = MAGIC_NUMBER.length + Integer.BYTES;
        byte[] buffer = new byte[headerSize + messageSize];

        // Write size in the buffer.
        ByteBuffer headerWriter = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        headerWriter.put(MAGIC_NUMBER);
        headerWriter.putInt(messageSize);

        // Write protobuffer payload in the buffer.
        try {
            CodedOutputStream cos = CodedOutputStream.newInstance(buffer, headerSize, messageSize);
            message.writeTo(cos);
        } catch (IOException e) {
            // In memory buffers, should not happen
            throw new IllegalStateException(e);
        }
        return ByteBuffer.wrap(buffer);
    }

    boolean isClosed() {
        return !channel.isOpen();
    }
}
