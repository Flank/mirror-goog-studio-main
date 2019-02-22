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

package com.android.tools.deployer.devices;

import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.tools.deployer.devices.shell.Shell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class FakeDevice {

    public static final String MANUFACTURER = "Manufacturer";
    public static final String MODEL = "Model";
    private static final String ABI = "x86";

    private final String version;
    private final String api;
    private final Shell shell;
    private final Map<String, String> props;
    private final Map<String, byte[]> files;
    private final List<byte[]> apks;

    private final Map<Integer, List<byte[]>> sessions;

    public FakeDevice(String version, String api) {
        this.version = version;
        this.api = api;
        this.shell = new Shell();
        this.props = new TreeMap<>();
        this.props.put("ro.product.manufacturer", MANUFACTURER);
        this.props.put("ro.product.model", MODEL);
        this.props.put("ro.product.cpu.abilist", ABI);
        this.props.put("ro.build.version.release", version);
        this.props.put("ro.build.version.sdk", api);
        this.files = new HashMap<>();
        this.apks = new ArrayList<>();
        this.sessions = new HashMap<>();
    }

    public void connectTo(FakeAdbServer server) throws ExecutionException, InterruptedException {
        DeviceState device =
                server.connectDevice(
                                UUID.randomUUID().toString(),
                                MANUFACTURER,
                                MODEL,
                                version,
                                api,
                                DeviceState.HostConnectionType.USB)
                        .get();
        device.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);
    }

    public Map<String, String> getProps() {
        return props;
    }

    @Override
    public String toString() {
        return "API " + api + " (" + version + ")";
    }

    public Shell getShell() {
        return shell;
    }

    public byte[] readFile(String path) {
        return files.get(path);
    }

    public void writeFile(String name, byte[] data) {
        files.put(name, data);
    }

    public void addApk(byte[] file) {
        apks.add(file);
    }

    public List<byte[]> getApps() {
        return apks;
    }

    public int createSession() {
        int session = sessions.size() + 1;
        sessions.put(session, new ArrayList<>());
        return session;
    }

    public void writeToSession(int session, byte[] apk) {
        List<byte[]> apks = sessions.get(session);
        apks.add(apk);
    }

    public boolean isValidSession(int session) {
        return sessions.get(session) != null;
    }

    public void commitSession(int session) {
        for (byte[] apk : sessions.get(session)) {
            addApk(apk);
        }
        sessions.put(session, null);
    }

    public void abandonSession(int session) {
        sessions.put(session, null);
    }

    public boolean supportsJvmti() {
        return apiLevelAtLeast(26);
    }

    public boolean apiLevelAtLeast(int level) {
        return Integer.parseInt(api) >= level;
    }
}
