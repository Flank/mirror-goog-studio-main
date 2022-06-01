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
import com.android.annotations.Nullable;
import com.android.fakeadbserver.devicecommandhandlers.AbbCommandHandler;
import com.android.fakeadbserver.devicecommandhandlers.AbbExecCommandHandler;
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler;
import com.android.fakeadbserver.devicecommandhandlers.FakeSyncCommandHandler;
import com.android.fakeadbserver.devicecommandhandlers.ReverseForwardCommandHandler;
import com.android.fakeadbserver.devicecommandhandlers.TrackJdwpCommandHandler;
import com.android.fakeadbserver.execcommandhandlers.CatExecCommandHandler;
import com.android.fakeadbserver.execcommandhandlers.CmdExecCommandHandler;
import com.android.fakeadbserver.execcommandhandlers.GetPropExecCommandHandler;
import com.android.fakeadbserver.execcommandhandlers.PackageExecCommandHandler;
import com.android.fakeadbserver.execcommandhandlers.PingExecCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.FeaturesCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.ForwardCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.GetDevPathCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.GetSerialNoCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.GetStateCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.KillCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.KillForwardAllCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.KillForwardCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.ListDevicesCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.ListForwardCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.MdnsCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.PairCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.TrackDevicesCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.VersionCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.CatCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.CmdCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.DumpsysCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.GetPropCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.LogcatCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.PackageManagerCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.RmCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.SetPropCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.WindowManagerCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.WriteNoStopCommandHandler;
import com.android.fakeadbserver.shellv2commandhandlers.CatV2CommandHandler;
import com.android.fakeadbserver.shellv2commandhandlers.GetPropV2CommandHandler;
import com.android.fakeadbserver.shellv2commandhandlers.ShellProtocolEchoV2CommandHandler;
import com.android.fakeadbserver.statechangehubs.DeviceStateChangeHub;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * See {@link FakeAdbServerTest#testInteractiveServer()} for example usage.
 */
public final class FakeAdbServer implements AutoCloseable {

    private final ServerSocket mServerSocket;

    /**
     * The {@link CommandHandler}s have internal state. To allow for reentrancy, instead of using a
     * pre-allocated {@link CommandHandler} object, the constructor is passed in and a new object is
     * created as-needed.
     */
    private final Map<String, Supplier<HostCommandHandler>> mHostCommandHandlers = new HashMap<>();

    private final List<DeviceCommandHandler> mHandlers = new ArrayList<>();

    private final Map<String, DeviceState> mDevices = new HashMap<>();

    private final Set<MdnsService> mMdnsServices = new HashSet<>();

    private final DeviceStateChangeHub mDeviceChangeHub = new DeviceStateChangeHub();

    private final AtomicInteger mLastTransportId = new AtomicInteger();

    // This is the executor for accepting incoming connections as well as handling the execution of
    // the commands over the connection. There is one task for accepting connections, and multiple
    // tasks to handle the execution of the commands.
    private final ExecutorService mThreadPoolExecutor =
            Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder()
                            .setNameFormat("fake-adb-server-connection-pool-%d")
                            .build());

    private Future<?> mConnectionHandlerTask = null;

    // All "external" server controls are synchronized through a central executor, much like the EDT
    // thread in Swing.
    private ExecutorService mMainServerThreadExecutor = Executors.newSingleThreadExecutor();

    private volatile boolean mServerKeepAccepting = false;

    private Set<String> mFeatures;
    private static final Set<String> DEFAULT_FEATURES =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "push_sync",
                                    "fixed_push_mkdir",
                                    "shell_v2",
                                    "apex",
                                    "stat_v2",
                                    "cmd",
                                    "abb",
                                    "abb_exec")));

    private FakeAdbServer() throws IOException {
        this(DEFAULT_FEATURES);
    }

    private FakeAdbServer(Set<String> features) throws IOException {
        mServerSocket = new ServerSocket();
        mFeatures = features;
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
                                    // Socket can not be closed in finally block, because a separate thread will
                                    // read from the socket. Closing the socket leads to a race condition.
                                    //noinspection SocketOpenedButNotSafelyClosed
                                    Socket socket = mServerSocket.accept();
                                    ConnectionHandler handler = new ConnectionHandler(this, socket);
                                    mThreadPoolExecutor.execute(handler);
                                } catch (IOException ignored) {
                                    // close() is called in a separate thread, and will cause accept() to throw an
                                    // exception if closed here.
                                }
                            }
                        });
    }

    @NonNull
    public InetAddress getInetAddress() {
        return mServerSocket.getInetAddress();
    }

    public int getPort() {
        return mServerSocket.getLocalPort();
    }

    /** This method allows for the caller thread to wait until the server shuts down. */
    public boolean awaitServerTermination() throws InterruptedException {
        return awaitServerTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
    }

    public boolean awaitServerTermination(long time, TimeUnit unit) throws InterruptedException {
        return mMainServerThreadExecutor.awaitTermination(time, unit)
                && mThreadPoolExecutor.awaitTermination(time, unit);
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
        } catch (RejectedExecutionException ignored) {
            // The server has already been closed once
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
     * @param deviceId           is the unique device ID of the device
     * @param manufacturer       is the manufacturer name of the device
     * @param deviceModel        is the model name of the device
     * @param release            is the Android OS version of the device
     * @param sdk                is the SDK version of the device
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
                        hostConnectionType,
                        newTransportId());
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

    void addDevice(DeviceStateConfig deviceConfig) {
        DeviceState device = new DeviceState(this, this.newTransportId(), deviceConfig);
        this.mDevices.put(device.getDeviceId(), device);
    }

    public Future<?> addMdnsService(@NonNull MdnsService service) {
        if (mConnectionHandlerTask == null) {
            assert !mMdnsServices.contains(service);
            mMdnsServices.add(service);
            return Futures.immediateFuture(null);
        } else {
            return mMainServerThreadExecutor.submit(
                    () -> {
                        assert !mMdnsServices.contains(service);
                        mMdnsServices.add(service);
                        return null;
                    });
        }
    }

    @NonNull
    public Future<?> removeMdnsService(@NonNull MdnsService service) {
        if (mConnectionHandlerTask == null) {
            mMdnsServices.remove(service);
            return Futures.immediateFuture(null);
        } else {
            return mMainServerThreadExecutor.submit(
                    () -> {
                        mMdnsServices.remove(service);
                        return null;
                    });
        }
    }

    /**
     * Thread-safely gets a copy of the mDNS service list. This is useful for asynchronous handlers.
     */
    @NonNull
    public Future<List<MdnsService>> getMdnsServicesCopy() {
        return mMainServerThreadExecutor.submit(() -> new ArrayList<>(mMdnsServices));
    }

    private int newTransportId() {
        return mLastTransportId.incrementAndGet();
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

    public HostCommandHandler getHostCommandHandler(String command) {
        Supplier<HostCommandHandler> supplier = mHostCommandHandlers.get(command);
        if (supplier != null) {
            return supplier.get();
        }
        return null;
    }

    public List<DeviceCommandHandler> getHandlers() {
        return mHandlers;
    }

    @NonNull
    public FakeAdbServerConfig getCurrentConfig() {
        FakeAdbServerConfig config = new FakeAdbServerConfig();

        config.getHostHandlers().putAll(mHostCommandHandlers);
        config.getDeviceHandlers().addAll(mHandlers);
        config.getMdnsServices().addAll(mMdnsServices);
        mDevices.forEach(
                (serial, device) -> {
                    DeviceStateConfig deviceConfig = device.getConfig();
                    config.getDevices().add(deviceConfig);
                });

        return config;
    }

    public static final class Builder {

        @NonNull private final FakeAdbServer mServer;

        @Nullable private FakeAdbServerConfig mConfig;

        public Builder() throws IOException {
            mServer = new FakeAdbServer();
        }

        /** Used to restore a {@link FakeAdbServer} instance from a previously running instance */
        public Builder setConfig(@NonNull FakeAdbServerConfig config) {
            mConfig = config;
            return this;
        }

        /**
         * Sets the handler for a specific host ADB command. This only needs to be called if the
         * test author requires additional functionality that is not provided by the default {@link
         * CommandHandler}s.
         *
         * @param command            The ADB protocol string of the command.
         * @param handlerConstructor The constructor for the handler.
         */
        @NonNull
        public Builder setHostCommandHandler(
                @NonNull String command, @NonNull Supplier<HostCommandHandler> handlerConstructor) {
            mServer.mHostCommandHandlers.put(command, handlerConstructor);
            return this;
        }

        /**
         * Adds the handler for a device command. Handlers added last take priority over existing
         * handlers.
         */
        @NonNull
        public Builder addDeviceHandler(@NonNull DeviceCommandHandler handler) {
            mServer.mHandlers.add(0, handler);
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
                    ListDevicesCommandHandler.LONG_COMMAND, () -> new ListDevicesCommandHandler(true));
            setHostCommandHandler(
                    TrackDevicesCommandHandler.COMMAND, TrackDevicesCommandHandler::new);
            setHostCommandHandler(
                    TrackDevicesCommandHandler.LONG_COMMAND,
                    () -> new TrackDevicesCommandHandler(true));
            setHostCommandHandler(ForwardCommandHandler.COMMAND, ForwardCommandHandler::new);
            setHostCommandHandler(KillForwardCommandHandler.COMMAND,
                    KillForwardCommandHandler::new);
            setHostCommandHandler(
                    KillForwardAllCommandHandler.COMMAND, KillForwardAllCommandHandler::new);
            setHostCommandHandler(
                    ListForwardCommandHandler.COMMAND, ListForwardCommandHandler::new);
            setHostCommandHandler(FeaturesCommandHandler.COMMAND, FeaturesCommandHandler::new);
            setHostCommandHandler(FeaturesCommandHandler.HOST_COMMAND, FeaturesCommandHandler::new);
            setHostCommandHandler(VersionCommandHandler.COMMAND, VersionCommandHandler::new);
            setHostCommandHandler(MdnsCommandHandler.COMMAND, MdnsCommandHandler::new);
            setHostCommandHandler(PairCommandHandler.COMMAND, PairCommandHandler::new);
            setHostCommandHandler(GetStateCommandHandler.COMMAND, GetStateCommandHandler::new);
            setHostCommandHandler(GetSerialNoCommandHandler.COMMAND, GetSerialNoCommandHandler::new);
            setHostCommandHandler(GetDevPathCommandHandler.COMMAND, GetDevPathCommandHandler::new);

            addDeviceHandler(new TrackJdwpCommandHandler());
            addDeviceHandler(new FakeSyncCommandHandler());
            addDeviceHandler(new ReverseForwardCommandHandler());

            // Exec commands
            addDeviceHandler(new CatExecCommandHandler());
            addDeviceHandler(new CmdExecCommandHandler());
            addDeviceHandler(new GetPropExecCommandHandler());
            addDeviceHandler(new PackageExecCommandHandler());
            addDeviceHandler(new PingExecCommandHandler());
            addDeviceHandler(new RmCommandHandler());

            addDeviceHandler(new LogcatCommandHandler());
            addDeviceHandler(new GetPropCommandHandler());
            addDeviceHandler(new GetPropV2CommandHandler());
            addDeviceHandler(new SetPropCommandHandler());
            addDeviceHandler(new WriteNoStopCommandHandler());
            addDeviceHandler(new PackageManagerCommandHandler());
            addDeviceHandler(new WindowManagerCommandHandler());
            addDeviceHandler(new CmdCommandHandler());
            addDeviceHandler(new DumpsysCommandHandler());
            addDeviceHandler(new CatCommandHandler());
            addDeviceHandler(new CatV2CommandHandler());
            addDeviceHandler(new ShellProtocolEchoV2CommandHandler());
            addDeviceHandler(new AbbCommandHandler());
            addDeviceHandler(new AbbExecCommandHandler());

            return this;
        }

        @NonNull
        public FakeAdbServer build() {
            if (mConfig != null) {
                mConfig.getHostHandlers().forEach(this::setHostCommandHandler);
                mConfig.getDeviceHandlers().forEach(this::addDeviceHandler);
                mConfig.getDevices().forEach(mServer::addDevice);
                mConfig.getMdnsServices().forEach(mServer::addMdnsService);
            }
            return mServer;
        }

        public void setFeatures(@NonNull Set<String> features) {
            mServer.setFeatures(features);
        }
    }

    public Set<String> getFeatures() {
        return mFeatures;
    }

    public void setFeatures(Set<String> features) {
        mFeatures = features;
    }
}
