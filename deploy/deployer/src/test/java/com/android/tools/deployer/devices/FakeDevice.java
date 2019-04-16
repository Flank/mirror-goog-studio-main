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
import com.android.tools.deployer.ApkParser;
import com.android.tools.deployer.devices.shell.Shell;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class FakeDevice {

    public static final String MANUFACTURER = "Manufacturer";
    public static final String MODEL = "Model";
    private static final String ABI = "x86";

    private final String version;
    private final int api;
    private final Shell shell;
    private final Map<String, String> props;
    private final Map<String, String> env;
    private final Map<String, String> apps;

    private final Map<Integer, List<byte[]>> sessions;
    private File storage;
    private File bridge;

    public FakeDevice(String version, int api) {
        this.version = version;
        this.api = api;
        this.shell = new Shell();
        this.props = new TreeMap<>();
        this.props.put("ro.product.manufacturer", MANUFACTURER);
        this.props.put("ro.product.model", MODEL);
        this.props.put("ro.product.cpu.abilist", ABI);
        this.props.put("ro.build.version.release", version);
        this.props.put("ro.build.version.sdk", String.valueOf(api));
        this.env = new HashMap<>();
        this.sessions = new HashMap<>();
        this.apps = new HashMap<>();
        this.storage = null;
    }

    public void connectTo(FakeAdbServer server) throws ExecutionException, InterruptedException {
        DeviceState device =
                server.connectDevice(
                                UUID.randomUUID().toString(),
                                MANUFACTURER,
                                MODEL,
                                version,
                                String.valueOf(api),
                                DeviceState.HostConnectionType.USB)
                        .get();
        device.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);
    }

    public Map<String, String> getProps() {
        return props;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    @Override
    public String toString() {
        return "API " + api + " (" + version + ")";
    }

    public Shell getShell() {
        return shell;
    }

    public boolean hasFile(String path) throws IOException {
        return new File(getStorage(), path).exists();
    }

    public byte[] readFile(String path) throws IOException {
        File file = new File(getStorage(), path);
        return Files.readAllBytes(file.toPath());
    }

    public void removeFile(String arg) throws IOException {
        File file = new File(getStorage(), arg);
        file.delete();
    }

    public void writeFile(String name, byte[] data, String mode) throws IOException {
        File file = new File(getStorage(), name);
        Files.write(file.toPath(), data);
        if (isModeExecutable(mode)) {
            makeExecutable(name);
        }
    }

    private boolean isModeExecutable(String mode) {
        for (char c : mode.toCharArray()) {
            if (((c - '0') & 0x1) == 0x1) {
                return true;
            }
        }
        return false;
    }

    public boolean isExecutable(String path) throws IOException {
        File exe = new File(getStorage(), path);
        return exe.canExecute();
    }

    public void install(byte[] file) throws IOException {
        int session = createSession();
        writeToSession(session, file);
        commitSession(session);
    }

    public Set<String> getApps() {
        return apps.keySet();
    }

    public String getAppPath(String pkg) {
        return apps.get(pkg);
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

    public void commitSession(int session) throws IOException {
        String appPath = null;
        String pkg = null;
        for (byte[] bytes : sessions.get(session)) {
            Path apk = Files.createTempFile(getStorage().toPath(), "apk", ".apk");
            Files.write(apk, bytes);
            ApkParser parser = new ApkParser();
            ApkParser.ApkDetails details = parser.getApkDetails(apk.toFile().getAbsolutePath());
            pkg = details.packageName;
            if (appPath == null) {
                appPath = "/data/app/" + pkg + "-" + UUID.randomUUID().toString();
            }
            File appDir = new File(getStorage(), appPath);
            appDir.mkdirs();
            Files.move(apk, new File(appDir, details.fileName).toPath());
        }
        if (pkg != null) {
            String previous = apps.get(pkg);
            if (previous != null) {
                FileUtils.deleteRecursivelyIfExists(new File(getStorage(), previous));
            }
            apps.put(pkg, appPath);
        }
        sessions.put(session, null);
    }

    public void abandonSession(int session) {
        sessions.put(session, null);
    }

    public boolean supportsJvmti() {
        return getApi() >= 26;
    }

    public int getApi() {
        return api;
    }

    public void mkdir(String arg, boolean parents) throws IOException {
        File dir = new File(getStorage(), arg);
        if (parents) {
            dir.mkdirs();
        } else {
            dir.mkdir();
        }
    }

    public void makeExecutable(String path) throws IOException {
        File exe = new File(getStorage(), path);
        exe.setExecutable(true);
    }

    public File getStorage() throws IOException {
        if (storage == null) {
            storage = Files.createTempDirectory("storage").toFile();
            // Assume all devices have /data/local/tmp created, if this is not true ddmlib already fails to install
            File file = new File(storage, "data/local/tmp");
            file.mkdirs();
        }
        return storage;
    }

    public void setShellBridge(File bridge) {
        this.bridge = bridge;
    }

    public File getShellBridge() {
        return bridge;
    }
}
