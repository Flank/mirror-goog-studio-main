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
import com.android.fakeadbserver.services.Service;
import com.android.fakeadbserver.services.ServiceManager;
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHandlerFactory;
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHub;
import com.android.fakeadbserver.statechangehubs.StateChangeQueue;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

public class DeviceState {

    private final ClientStateChangeHub mClientStateChangeHub = new ClientStateChangeHub();

    private final Map<String, DeviceFileState> mFiles = new HashMap<>();

    private final List<String> mLogcatMessages = new ArrayList<>();

    private final Map<Integer, ClientState> mClients = new HashMap<>();

    private final Map<Integer, PortForwarder> mPortForwarders = new HashMap<>();

    private final Map<Integer, PortForwarder> mReversePortForwarders = new HashMap<>();

    private final FakeAdbServer mServer;

    private final HostConnectionType mHostConnectionType;

    private final Set<String> mFeatures;

    private final int myTransportId;

    private final String mDeviceId;

    private final String mManufacturer;

    private final String mModel;

    private final String mBuildVersionRelease;

    private final String mBuildVersionSdk;

    private final String mCpuAbi;

    private DeviceStatus mDeviceStatus;

    private final ServiceManager mServiceManager;

    // Keep track of all PM commands invocation
    private final Vector<String> mPmLogs = new Vector<>();

    // Keep track of all cmd commands invocation
    private final Vector<String> mCmdLogs = new Vector<>();

    // Keep track of all ABB/ABB_EXEC commands invocation
    private final Vector<String> mAbbLogs = new Vector<>();

    DeviceState(
            @NonNull FakeAdbServer server,
            @NonNull String deviceId,
            @NonNull String manufacturer,
            @NonNull String model,
            @NonNull String release,
            @NonNull String sdk,
            @NonNull String cpuAbi,
            @NonNull HostConnectionType hostConnectionType,
            int transportId) {
        mServer = server;
        mDeviceId = deviceId;
        mManufacturer = manufacturer;
        mModel = model;
        mBuildVersionRelease = release;
        mBuildVersionSdk = sdk;
        mCpuAbi = cpuAbi;
        mFeatures = initFeatures(sdk);
        mHostConnectionType = hostConnectionType;
        myTransportId = transportId;
        mDeviceStatus = DeviceStatus.OFFLINE;
        mServiceManager = new ServiceManager();
    }

    DeviceState(@NonNull FakeAdbServer server, int transportId, @NonNull DeviceStateConfig config) {
        this(
                server,
                config.getSerialNumber(),
                config.getManufacturer(),
                config.getModel(),
                config.getBuildVersionRelease(),
                config.getBuildVersionSdk(),
                config.getCpuAbi(),
                config.getHostConnectionType(),
                transportId);
        config.getFiles().forEach(fileState -> mFiles.put(fileState.getPath(), fileState));
        mLogcatMessages.addAll(config.getLogcatMessages());
        mDeviceStatus = config.getDeviceStatus();
        config.getClients().forEach(clientState -> mClients.put(clientState.getPid(), clientState));
    }

    public void stop() {
        mClientStateChangeHub.stop();
    }

    @NonNull
    public String getDeviceId() {
        return mDeviceId;
    }

    @NonNull
    public String getCpuAbi() {
        return mCpuAbi;
    }

    @NonNull
    public String getManufacturer() {
        return mManufacturer;
    }

    @NonNull
    public String getModel() {
        return mModel;
    }

    @NonNull
    public String getBuildVersionRelease() {
        return mBuildVersionRelease;
    }

    @NonNull
    public String getBuildVersionSdk() {
        return mBuildVersionSdk;
    }

    @NonNull
    public DeviceStatus getDeviceStatus() {
        return mDeviceStatus;
    }

    public int getTransportId() {
        return myTransportId;
    }

    public void setDeviceStatus(@NonNull DeviceStatus status) {
        mDeviceStatus = status;
        mServer.getDeviceChangeHub().deviceStatusChanged(this, status);
    }

    @NonNull
    public ClientStateChangeHub getClientChangeHub() {
        return mClientStateChangeHub;
    }

    public void addLogcatMessage(@NonNull String message) {
        synchronized (mLogcatMessages) {
            mLogcatMessages.add(message);
            mClientStateChangeHub.logcatMessageAdded(message);
        }
    }

    @Nullable
    public LogcatChangeHandlerSubscriptionResult subscribeLogcatChangeHandler(
            @NonNull ClientStateChangeHandlerFactory handlerFactory) {
        synchronized (mLogcatMessages) {
            StateChangeQueue queue = getClientChangeHub().subscribe(handlerFactory);
            if (queue == null) {
                return null;
            }

            return new LogcatChangeHandlerSubscriptionResult(
                    queue, new ArrayList<>(mLogcatMessages));
        }
    }

    public void createFile(@NonNull DeviceFileState file) {
        synchronized (mFiles) {
            mFiles.put(file.getPath(), file);
        }
    }

    @Nullable
    public DeviceFileState getFile(@NonNull String filepath) {
        synchronized (mFiles) {
            return mFiles.get(filepath);
        }
    }

    public void deleteFile(@NonNull String filepath) {
        synchronized (mFiles) {
            mFiles.remove(filepath);
        }
    }

    @NonNull
    public ClientState startClient(
            int pid, int uid, @NonNull String packageName, boolean isWaiting) {
        return startClient(pid, uid, packageName, packageName, isWaiting);
    }

    @NonNull
    public ClientState startClient(
            int pid,
            int uid,
            @NonNull String processName,
            @NonNull String packageName,
            boolean isWaiting) {
        synchronized (mClients) {
            ClientState clientState =
                    new ClientState(pid, uid, processName, packageName, isWaiting);
            mClients.put(pid, clientState);
            mClientStateChangeHub.clientListChanged();
            return clientState;
        }
    }

    public void stopClient(int pid) {
        synchronized (mClients) {
            mClients.remove(pid);
            mClientStateChangeHub.clientListChanged();
        }
    }

    @Nullable
    public ClientState getClient(int pid) {
        synchronized (mClients) {
            return mClients.get(pid);
        }
    }

    @NonNull
    public ImmutableMap<Integer, PortForwarder> getAllPortForwarders() {
        synchronized (mPortForwarders) {
            return ImmutableMap.copyOf(mPortForwarders);
        }
    }

    @NonNull
    public ImmutableMap<Integer, PortForwarder> getAllReversePortForwarders() {
        synchronized (mReversePortForwarders) {
            return ImmutableMap.copyOf(mReversePortForwarders);
        }
    }

    public boolean addPortForwarder(@NonNull PortForwarder forwarder, boolean noRebind) {
        synchronized (mPortForwarders) {
            if (noRebind) {
                return mPortForwarders.computeIfAbsent(
                                forwarder.getSource().mPort, port -> forwarder)
                        == forwarder;
            } else {
                // Just overwrite the previous forwarder.
                mPortForwarders.put(forwarder.getSource().mPort, forwarder);
                return true;
            }
        }
    }

    public boolean addReversePortForwarder(@NonNull PortForwarder forwarder, boolean noRebind) {
        synchronized (mReversePortForwarders) {
            if (noRebind) {
                return mReversePortForwarders.computeIfAbsent(
                                forwarder.getSource().mPort, port -> forwarder)
                        == forwarder;
            } else {
                // Just overwrite the previous forwarder.
                mReversePortForwarders.put(forwarder.getSource().mPort, forwarder);
                return true;
            }
        }
    }

    public boolean removePortForwarder(int hostPort) {
        synchronized (mPortForwarders) {
            return mPortForwarders.remove(hostPort) != null;
        }
    }

    public boolean removeReversePortForwarder(int hostPort) {
        synchronized (mReversePortForwarders) {
            return mReversePortForwarders.remove(hostPort) != null;
        }
    }

    public void removeAllPortForwarders() {
        synchronized (mPortForwarders) {
            mPortForwarders.clear();
        }
    }

    public void removeAllReversePortForwarders() {
        synchronized (mReversePortForwarders) {
            mReversePortForwarders.clear();
        }
    }

    @NonNull
    public HostConnectionType getHostConnectionType() {
        return mHostConnectionType;
    }

    @NonNull
    public String getClientListString() {
        synchronized (mClients) {
            return mClients.values()
                    .stream()
                    .map(clientState -> Integer.toString(clientState.getPid()))
                    .collect(Collectors.joining("\n"));
        }
    }

    @NonNull
    public DeviceStateConfig getConfig() {
        return new DeviceStateConfig(
                mDeviceId,
                new ArrayList<>(mFiles.values()),
                new ArrayList<>(mLogcatMessages),
                new ArrayList<>(mClients.values()),
                mHostConnectionType,
                mManufacturer,
                mModel,
                mBuildVersionRelease,
                mBuildVersionSdk,
                mCpuAbi,
                mDeviceStatus);
    }

    private static Set<String> initFeatures(String sdk) {
        Set<String> features =
                new HashSet<>(
                        Arrays.asList("push_sync", "fixed_push_mkdir", "shell_v2", "apex,stat_v2"));
        try {
            int api = Integer.parseInt(sdk);
            if (api >= 24) {
                features.add("cmd");
            }
            if (api >= 30) {
                features.add("abb");
                features.add("abb_exec");
            }
        } catch (NumberFormatException e) {
            // Cannot add more features based on API level since it is not the expected integer
            // This is expected in many of our test that don't pass a correct value but instead
            // pass "sdk". In such case, we return the default set of features.
            // TODO: Fix adblist test to not send "sdk" and delete this catch.
        }
        return Collections.unmodifiableSet(features);
    }

    public Set<String> getFeatures() {
        return mFeatures;
    }

    public ServiceManager getServiceManager() {
        return mServiceManager;
    }

    public void setActivityManager(Service newActivityManager) {
        mServiceManager.setActivityManager(newActivityManager);
    }

    public void addPmLog(String cmd) {
        mPmLogs.add(cmd);
    }

    public List<String> getPmLogs() {
        return (List<String>) mPmLogs.clone();
    }

    public void addCmdLog(String cmd) {
        mCmdLogs.add(cmd);
    }

    public List<String> getCmdLogs() {
        return (List<String>) mCmdLogs.clone();
    }

    public void addAbbLog(String cmd) {
        mAbbLogs.add(cmd);
    }

    public List<String> getAbbLogs() {
        return (List<String>) mAbbLogs.clone();
    }

    /**
     * The state of a device.
     */
    public enum DeviceStatus {
        BOOTLOADER("bootloader"), //$NON-NLS-1$
        /** bootloader mode with is-userspace = true though `adb reboot fastboot` */
        FASTBOOTD("fastbootd"), //$NON-NLS-1$
        OFFLINE("offline"), //$NON-NLS-1$
        ONLINE("device"), //$NON-NLS-1$
        RECOVERY("recovery"), //$NON-NLS-1$
        /**
         * Device is in "sideload" state either through `adb sideload` or recovery menu
         */
        SIDELOAD("sideload"), //$NON-NLS-1$
        UNAUTHORIZED("unauthorized"), //$NON-NLS-1$
        DISCONNECTED("disconnected"), //$NON-NLS-1$
        ;

        private final String mState;

        DeviceStatus(String state) {
            mState = state;
        }

        /**
         * Returns a {DeviceStatus} from the string returned by <code>adb devices</code>.
         *
         * @param state the device state.
         * @return a {DeviceStatus} object or <code>null</code> if the state is unknown.
         */
        @Nullable
        public static DeviceStatus getState(String state) {
            for (DeviceStatus deviceStatus : values()) {
                if (deviceStatus.mState.equals(state)) {
                    return deviceStatus;
                }
            }
            return null;
        }

        public String getState() {
            return mState;
        }
    }

    public enum HostConnectionType {
        USB,
        LOCAL
    }

    /**
     * This class represents the result of calling {@link
     * #subscribeLogcatChangeHandler(ClientStateChangeHandlerFactory)}. This is needed to
     * synchronize between adding the listener and getting the correct lines from the logcat buffer.
     */
    public static final class LogcatChangeHandlerSubscriptionResult {

        @NonNull
        public final StateChangeQueue mQueue;

        @NonNull
        public final List<String> mLogcatContents;

        public LogcatChangeHandlerSubscriptionResult(@NonNull StateChangeQueue queue,
                @NonNull List<String> logcatContents) {
            mQueue = queue;
            mLogcatContents = logcatContents;
        }
    }
}
