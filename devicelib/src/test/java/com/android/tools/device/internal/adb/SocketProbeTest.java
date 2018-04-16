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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class SocketProbeTest {
    @Test
    public void adbServerRunning_freePort() throws Exception {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(null), 0);
        assertThat(new SocketProbe().probe(address, 500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    public void adbServerRunning_nonAdbServer() throws Exception {
        // create a disconnecting server and wait for it to start up
        CountDownLatch createLatch = new CountDownLatch(1);
        CountDownLatch acceptLatch = new CountDownLatch(1);
        DisconnectingServer ds = new DisconnectingServer(createLatch, acceptLatch);
        ForkJoinTask<?> serverTask = ForkJoinPool.commonPool().submit(ds);

        assertThat(createLatch.await(100, TimeUnit.MILLISECONDS)).isTrue();

        // now attempt to check if adb is running at the server's port
        try {
            InetSocketAddress addr =
                    new InetSocketAddress(InetAddress.getByName(null), ds.getPort());

            // adb server shouldn't have been running since we are connecting to our own server
            assertThat(new SocketProbe().probe(addr, 50, TimeUnit.MILLISECONDS)).isNull();

            // verify that isAdbServerRunning() actually established connection to the server
            // launched above. If there was an exception, rethrow it here so the test fails
            // with a meaningful stack trace.
            if (ds.getException() != null) {
                throw ds.getException();
            }
            assertThat(acceptLatch.getCount()).isEqualTo(0);
        } finally {
            serverTask.cancel(true);
        }
    }

    // A simple server that waits for a single connection, as soon as the connection is established,
    // it is disconnected and the server terminates.
    private static class DisconnectingServer implements Runnable {
        private final CountDownLatch createLatch;
        private final CountDownLatch acceptLatch;
        private volatile int port;
        private volatile Exception exception;

        /**
         * Constructs a {@link DisconnectingServer}.
         *
         * @param createLatch a latch that will be counted down once the server has bound to a port.
         *     Calls to {@link #getPort()} will return the port the server is listening at after
         *     this latch is counted down
         * @param acceptLatch a latch indicating that is counted down once the server accepts a
         *     connection.
         */
        DisconnectingServer(
                @NonNull CountDownLatch createLatch, @NonNull CountDownLatch acceptLatch) {
            this.createLatch = createLatch;
            this.acceptLatch = acceptLatch;
        }

        @Override
        public void run() {
            try (ServerSocket s = new ServerSocket(0)) {
                port = s.getLocalPort();
                createLatch.countDown();

                try (Socket ignored = s.accept()) {
                    acceptLatch.countDown();
                    // just accepts a connection and immediately closes it
                }
            } catch (IOException e) {
                System.err.println(e.toString());
                exception = e;
            }
        }

        int getPort() throws Exception {
            if (exception != null) {
                throw exception;
            }
            return port;
        }

        Exception getException() {
            return exception;
        }
    }
}
