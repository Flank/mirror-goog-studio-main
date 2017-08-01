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

package com.android.ddmlib;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.jdwp.JdwpAgent;
import com.android.ddmlib.jdwp.JdwpCommands;
import com.android.ddmlib.jdwp.JdwpInterceptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import junit.framework.TestCase;
import org.easymock.EasyMock;

public class DebuggerTest extends TestCase {
    private static final int TIMEOUT_MS = 10 * 1000;

    private final TestLog testLog = new TestLog();
    private EmptyAdbServer emptyAdbServer;
    private SocketChannel debuggerClientChannel;
    private SocketChannel emptyAdbServerClientChannel;
    private Client client;
    private Debugger debugger;
    private CompletableFuture<Void> emptyAdbServerFuture;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Log.setLevel(Log.LogLevel.DEBUG);
        Log.addLogger(testLog);

        emptyAdbServer = new EmptyAdbServer();
        emptyAdbServerFuture =
                runInThread(
                        "Empty ADB server",
                        () -> {
                            emptyAdbServer.run();
                            return null;
                        });

        ClientTracker deviceMonitor = EasyMock.createMock(ClientTracker.class);

        emptyAdbServerClientChannel = SocketChannel.open();
        Device device = new Device(deviceMonitor, "11", IDevice.DeviceState.ONLINE);
        client = new Client(device, emptyAdbServerClientChannel, 1);

        debuggerClientChannel = SocketChannel.open();
        debugger = new Debugger(client, 0);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (client != null) {
                client.close(false);
            }

            if (debuggerClientChannel != null) {
                debuggerClientChannel.close();
            }

            if (emptyAdbServer != null) {
                emptyAdbServer.close();
            }

            if (emptyAdbServerFuture != null) {
                emptyAdbServerFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            Log.removeLogger(testLog);
        } finally {
            super.tearDown();
        }
    }

    public void testSmallPacket() throws Exception {
        // Prepare
        connectToDebugger();
        sendHandshake();

        // Act
        sendReceivePacket(100);

        // Assert
        assertThat(debugger.getReadBufferCapacity())
                .isEqualTo(debugger.getReadBufferInitialCapacity());
    }

    public void testLargePacket() throws Exception {
        // Prepare
        connectToDebugger();
        sendHandshake();

        // Act
        sendReceivePacket(debugger.getReadBufferInitialCapacity() * 5);

        // Assert
        assertThat(debugger.getReadBufferCapacity())
                .isGreaterThan(debugger.getReadBufferInitialCapacity() * 4);
    }

    public void testBufferShrinksAfterLargePacket() throws Exception {
        // Prepare
        connectToDebugger();
        sendHandshake();

        // Act
        sendReceivePacket(debugger.getReadBufferInitialCapacity() * 5);
        sendReceivePacket(debugger.getReadBufferInitialCapacity() / 5);

        // Assert
        assertThat(debugger.getReadBufferCapacity())
                .isEqualTo(debugger.getReadBufferInitialCapacity());
    }

    public void testOverflowPacket() throws Exception {
        // Prepare
        connectToDebugger();
        sendHandshake();

        // Act
        sendReceivePacket(debugger.getReadBufferMaximumCapacity() + 1024);

        // Assert
        assertThat(debugger.getConnectionState())
                .isEqualTo(Debugger.ConnectionState.ST_NOT_CONNECTED);
    }

    private void connectToDebugger() throws Exception {
        assertThat(debugger.getConnectionState())
                .isEqualTo(Debugger.ConnectionState.ST_NOT_CONNECTED);

        CompletableFuture<Void> futureAdbConnect =
                runInThread(
                        "Connect to Empty ADB server",
                        () -> {
                            InetSocketAddress addr =
                                    new InetSocketAddress(
                                            InetAddress.getByName("localhost"), //$NON-NLS-1$
                                            emptyAdbServer.getListenPort());

                            emptyAdbServerClientChannel.connect(addr);
                            emptyAdbServerClientChannel.configureBlocking(false);
                            return null;
                        });
        waitFuture(futureAdbConnect);

        CompletableFuture<Void> futureConnect =
                runInThread(
                        "Connect to Debugger",
                        () -> {
                            InetSocketAddress addr =
                                    new InetSocketAddress(
                                            InetAddress.getByName("localhost"), //$NON-NLS-1$
                                            debugger.getListenPort());

                            debuggerClientChannel.connect(addr);
                            debuggerClientChannel.configureBlocking(false);
                            return null;
                        });
        waitFuture(futureConnect);

        CompletableFuture<Void> futureAccept =
                runInThread(
                        "Accept connection",
                        () -> {
                            debugger.accept();
                            return null;
                        });
        waitFuture(futureAccept);

        assertThat(debugger.getConnectionState())
                .isEqualTo(Debugger.ConnectionState.ST_AWAIT_SHAKE);
    }

    private void sendHandshake() throws Exception {
        assertThat(debugger.getConnectionState())
                .isEqualTo(Debugger.ConnectionState.ST_AWAIT_SHAKE);
        CompletableFuture<Void> futureWrite =
                runInThread(
                        "Send packet to debugger",
                        () -> {
                            sendHandshakeWorker();
                            return null;
                        });
        waitFuture(futureWrite);
    }

    private void sendReceivePacket(int packetSize) throws Exception {
        CompletableFuture<Void> futureWrite =
                runInThread(
                        "Send packet to debugger",
                        () -> {
                            ByteBuffer buf =
                                    ByteBuffer.allocate(JdwpPacket.JDWP_HEADER_LEN + packetSize);
                            JdwpPacket packet = new JdwpPacket(buf);
                            packet.finishPacket(
                                    JdwpCommands.SET_VM,
                                    JdwpCommands.CMD_VM_CREATESTRING,
                                    packetSize);
                            packet.write(debuggerClientChannel);
                            return null;
                        });

        CompletableFuture<Void> futureRead =
                runInThread(
                        "Receive JDWP reply from debugger",
                        () -> {
                            AtomicBoolean packetReceived = new AtomicBoolean(false);
                            JdwpInterceptor interceptor =
                                    new JdwpInterceptor() {
                                        @Nullable
                                        @Override
                                        public JdwpPacket intercept(
                                                @NonNull JdwpAgent agent,
                                                @NonNull JdwpPacket packet) {
                                            packetReceived.set(true);
                                            return packet;
                                        }
                                    };
                            debugger.addJdwpInterceptor(interceptor);

                            while (true) {
                                debugger.processChannelData();

                                // Debugger is disconnected (after error or connection closed)
                                if (debugger.getConnectionState()
                                        == Debugger.ConnectionState.ST_NOT_CONNECTED) {
                                    break;
                                }
                                // JDWP Packet has been received
                                if (packetReceived.get()) {
                                    break;
                                }
                            }

                            debugger.removeJdwpInterceptor(interceptor);
                            return null;
                        });

        waitFuture(futureWrite, futureRead);
    }

    private void sendHandshakeWorker() throws IOException {
        ByteBuffer tempBuffer = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
        JdwpHandshake.putHandshake(tempBuffer);
        int expectedLen = tempBuffer.position();
        tempBuffer.flip();
        if (debuggerClientChannel.write(tempBuffer) != expectedLen)
            throw new IOException("partial handshake write");
    }

    private static void waitFuture(CompletableFuture<?>... cfs)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> f = CompletableFuture.allOf(cfs);
        assertThat(f.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isNull();
    }

    private static <T> CompletableFuture<T> runInThread(String name, Callable<T> callable) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Thread t =
                new Thread(
                        () -> {
                            Log.d("test", String.format("[%s] Thread start", name));
                            try {
                                result.complete(callable.call());
                            } catch (Throwable t1) {
                                Log.d("test", String.format("[%s] error: %s", name, t1));
                                result.completeExceptionally(t1);
                            }
                            Log.d("test", String.format("[%s] stop", name));
                        });
        t.setName(name);
        t.setDaemon(true);
        t.start();
        return result;
    }

    private static class TestLog implements Log.ILogOutput {
        @Override
        public void printLog(Log.LogLevel logLevel, String tag, String message) {
            System.out.println(String.format("%s: %s: %s", logLevel, tag, message));
        }

        @Override
        public void printAndPromptLog(Log.LogLevel logLevel, String tag, String message) {
            printLog(logLevel, tag, message);
        }
    }

    /**
     * A simplified version of an ADB server that swallows all incoming data on its socket channel
     * without replying to any request.
     */
    private static class EmptyAdbServer {
        private final ServerSocketChannel mListenChannel;
        private final int mListenPort;
        private SocketChannel mChannel;

        public EmptyAdbServer() throws IOException {
            mListenChannel = ServerSocketChannel.open();
            mListenChannel.configureBlocking(true);

            InetSocketAddress addr =
                    new InetSocketAddress(
                            InetAddress.getByName("localhost"), //$NON-NLS-1$
                            0);
            mListenChannel.socket().setReuseAddress(true); // enable SO_REUSEADDR
            mListenChannel.socket().bind(addr);
            mListenPort = mListenChannel.socket().getLocalPort();
        }

        public void run() throws IOException {
            mChannel = mListenChannel.accept();
            mChannel.configureBlocking(true);

            ByteBuffer buf = ByteBuffer.allocate(8 * 1024);
            while (true) {
                try {
                    buf.clear();
                    mChannel.read(buf);
                } catch (ClosedChannelException e) {
                    Log.d("test", "Empty ADB server connection closed, exiting thread");
                    break;
                }
                if (buf.position() > 0) {
                    Log.d(
                            "test",
                            String.format(
                                    "Empty ADB server: Received %d bytes from client",
                                    buf.position()));
                }
            }
        }

        public void close() throws IOException {
            if (mChannel != null) {
                mChannel.close();
            }
            mListenChannel.close();
        }

        public int getListenPort() {
            return mListenPort;
        }
    }
}
