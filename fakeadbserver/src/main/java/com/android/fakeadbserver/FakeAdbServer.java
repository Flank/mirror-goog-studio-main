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

package com.android.fakeadbserver;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler;
import com.android.fakeadbserver.devicecommandhandlers.TrackJdwpCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.KillCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.ListDevicesCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.TrackDevicesCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.GetPropCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.LogcatCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.ShellCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.WriteNoStopCommandHandler;
import com.android.fakeadbserver.statechangehubs.DeviceStateChangeHub;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** See {@link FakeAdbServerTest#testInteractiveServer()} for example usage. */
public final class FakeAdbServer implements AutoCloseable {

    private final ServerSocket mServerSocket;

    /**
     * The {@link CommandHandler}s have internal state. To allow for reentrancy, instead of using a
     * pre-allocated {@link CommandHandler} object, the constructor is passed in and a new object is
     * created as-needed.
     */
    private final Map<String, Supplier<HostCommandHandler>> mHostCommandHandlers = new HashMap<>();

    private final Map<String, Supplier<DeviceCommandHandler>> mDeviceCommandHandlers =
            new HashMap<>();

    private final Map<String, Supplier<ShellCommandHandler>> mShellCommandHandlers =
            new HashMap<>();

    private final Map<String, DeviceState> mDevices = new HashMap<>();

    private final DeviceStateChangeHub mDeviceChangeHub = new DeviceStateChangeHub();

    // This is the executor for accepting incoming connections as well as handling the execution of
    // the commands over the connection. There is one task for accepting connections, and multiple
    // tasks to handle the execution of the commands.
    private final ExecutorService mThreadPoolExecutor =
            new ThreadPoolExecutor(
                    6,
                    21,
                    1,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(Integer.MAX_VALUE),
                    new ThreadFactoryBuilder()
                            .setNameFormat("fake-adb-server-connection-pool-%d")
                            .build());

    private Future<?> mConnectionHandlerTask = null;

    // All "external" server controls are synchronized through a central executor, much like the EDT
    // thread in Swing.
    private ExecutorService mMainServerThreadExecutor = Executors.newSingleThreadExecutor();

    private volatile boolean mServerKeepAccepting = false;

    private FakeAdbServer() throws IOException {
        mServerSocket = new ServerSocket();
    }

    public void start() throws IOException {
        assert mConnectionHandlerTask == null; // Do not reuse the server.

        mServerSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        mServerSocket.setReuseAddress(true);
        mServerKeepAccepting = true;

        mConnectionHandlerTask =
                mThreadPoolExecutor.submit(
                        () -> {
                            while (mServerKeepAccepting) {
                                try {
                                    Socket socket = mServerSocket.accept();
                                    mThreadPoolExecutor.submit(
                                            new ConnectionHandler(
                                                    this,
                                                    socket,
                                                    mHostCommandHandlers,
                                                    mDeviceCommandHandlers,
                                                    mShellCommandHandlers));
                                } catch (IOException ignored) {
                                    // close() is called in a separate thread, and will cause accept() to throw an
                                    // exception.
                                }
                            }
                        });
    }

    public int getPort() {
        return mServerSocket.getLocalPort();
    }

    /**
     * This method allows for the caller thread to wait until the server shuts down.
     */
    public void awaitServerTermination() throws InterruptedException {
        mMainServerThreadExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
    }

    /**
     * Stops the server.
     *
     * @return a {@link Future} if the caller needs to wait until the server is stopped.
     */
    public Future<?> stop() {
        return mMainServerThreadExecutor.submit(
                () -> {
                    if (!mServerKeepAccepting) {
                        return;
                    }
                    mServerKeepAccepting = false;

                    mDeviceChangeHub.stop();
                    mDevices.forEach((id, device) -> device.stop());

                    mConnectionHandlerTask.cancel(true);
                    try {
                        mServerSocket.close();
                    } catch (IOException ignored) {
                    }

                    // Note: Use "shutdownNow()" to ensure threads of long running tasks are interrupted, as opposed
                    // to merely waiting for the tasks to finish. This is because mThreadPoolExecutor is used to
                    // run CommandHandler implementations, and some of them (e.g. TrackJdwpCommandHandler) wait
                    // indefinitely on queues and expect to be interrupted as a signal to terminate.
                    mThreadPoolExecutor.shutdownNow();
                    mMainServerThreadExecutor.shutdown();
                });
    }

    @Override
    public void close() throws Exception {
        try {
            stop().get();
        } catch (InterruptedException ignored) {
            // Catch InterruptedException as specified by JavaDoc.
        }
    }

    /**
     * Grabs the DeviceStateChangeHub from the server. This should only be used for implementations
     * for handlers that inherit from {@link CommandHandler}. The purpose of the hub is to propagate
     * server events to existing connections with the server.
     *
     * <p>For example, if {@link #connectDevice(String, String, String, String, String,
     * DeviceState.HostConnectionType)} is called, an event will be sent through the {@link
     * DeviceStateChangeHub} to all open connections waiting on host:track-devices messages.
     */
    @NonNull
    public DeviceStateChangeHub getDeviceChangeHub() {
        return mDeviceChangeHub;
    }

    /**
     * Connects a device to the ADB server. Must be called on the EDT/main thread.
     *
     * @param deviceId is the unique device ID of the device
     * @param manufacturer is the manufacturer name of the device
     * @param deviceModel is the model name of the device
     * @param release is the Android OS version of the device
     * @param sdk is the SDK version of the device
     * @param hostConnectionType is the simulated connection type to the device @return the future
     * @return a future to allow synchronization of the side effects of the call
     */
    public Future<DeviceState> connectDevice(
            @NonNull String deviceId,
            @NonNull String manufacturer,
            @NonNull String deviceModel,
            @NonNull String release,
            @NonNull String sdk,
            @NonNull DeviceState.HostConnectionType hostConnectionType) {
        DeviceState device =
                new DeviceState(
                        this,
                        deviceId,
                        manufacturer,
                        deviceModel,
                        release,
                        sdk,
                        hostConnectionType);
        if (mConnectionHandlerTask == null) {
            assert !mDevices.containsKey(deviceId);
            mDevices.put(deviceId, device);
            return Futures.immediateFuture(device);
        } else {
            return mMainServerThreadExecutor.submit(
                    () -> {
                        assert !mDevices.containsKey(deviceId);
                        mDevices.put(deviceId, device);
                        mDeviceChangeHub.deviceListChanged(mDevices.values());
                        return device;
                    });
        }
    }

    /**
     * Removes a device from the ADB server. Must be called on the EDT/main thread.
     *
     * @param deviceId is the unique device ID of the device
     * @return a future to allow synchronization of the side effects of the call
     */
    public Future<?> disconnectDevice(@NonNull String deviceId) {
        return mMainServerThreadExecutor.submit(
                () -> {
                    assert mDevices.containsKey(deviceId);
                    mDevices.remove(deviceId);
                    mDeviceChangeHub.deviceListChanged(mDevices.values());
                });
    }

    /**
     * Thread-safely gets a copy of the device list. This is useful for asynchronous handlers.
     */
    @NonNull
    public Future<List<DeviceState>> getDeviceListCopy() {
        return mMainServerThreadExecutor.submit(() -> new ArrayList<>(mDevices.values()));
    }

    public static final class Builder {

        @NonNull private final FakeAdbServer mServer;

        public Builder() throws IOException {
            mServer = new FakeAdbServer();
        }

        /**
         * Sets the handler for a specific host ADB command. This only needs to be called if the
         * test author requires additional functionality that is not provided by the default {@link
         * CommandHandler}s.
         *
         * @param command The ADB protocol string of the command.
         * @param handlerConstructor The constructor for the handler.
         */
        @NonNull
        public Builder setHostCommandHandler(
                @NonNull String command, @NonNull Supplier<HostCommandHandler> handlerConstructor) {
            mServer.mHostCommandHandlers.put(command, handlerConstructor);
            return this;
        }

        /**
         * Sets the handler for a specific device ADB command. This only needs to be called if the
         * test author requires additional functionality that is not provided by the default {@link
         * CommandHandler}s.
         *
         * @param command The ADB device protocol string of the command.
         * @param handlerConstructor The constructor for the handler.
         */
        @NonNull
        public Builder setDeviceCommandHandler(
                @NonNull String command,
                @NonNull Supplier<DeviceCommandHandler> handlerConstructor) {
            mServer.mDeviceCommandHandlers.put(command, handlerConstructor);
            return this;
        }

        /**
         * Sets the handler for a specific device ADB shell command. This only needs to be called if
         * the test author requires additional functionality that is not provided by the default
         * {@link CommandHandler}s.
         *
         * @param command The shell command string.
         * @param handlerConstructor The constructor for the handler.
         */
        @NonNull
        public Builder setShellCommandHandler(
                @NonNull String command,
                @NonNull Supplier<ShellCommandHandler> handlerConstructor) {
            mServer.mShellCommandHandlers.put(command, handlerConstructor);
            return this;
        }

        /**
         * Installs the default set of host command handlers. The user may override any command
         * handler.
         */
        @NonNull
        public Builder installDefaultCommandHandlers() {
            setHostCommandHandler(KillCommandHandler.COMMAND, KillCommandHandler::new);
            setHostCommandHandler(
                    ListDevicesCommandHandler.COMMAND, ListDevicesCommandHandler::new);
            setHostCommandHandler(
                    TrackDevicesCommandHandler.COMMAND, TrackDevicesCommandHandler::new);

            setDeviceCommandHandler(TrackJdwpCommandHandler.COMMAND, TrackJdwpCommandHandler::new);

            setShellCommandHandler(LogcatCommandHandler.COMMAND, LogcatCommandHandler::new);
            setShellCommandHandler(GetPropCommandHandler.COMMAND, GetPropCommandHandler::new);
            setShellCommandHandler(
                    WriteNoStopCommandHandler.COMMAND, WriteNoStopCommandHandler::new);

            return this;
        }

        @NonNull
        public FakeAdbServer build() {
            return mServer;
        }
    }
}
