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

import com.android.annotations.Nullable;
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
import java.util.SortedMap;
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
    private final Map<String, Application> apps;
    private final User shellUser;
    private List<User> users;

    private final Map<Integer, Session> sessions;
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
        this.users = new ArrayList<>();
        this.storage = null;
        this.shellUser = addUser(2000, "shell");
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

    public InstallResult install(byte[] file) throws IOException {
        int session = createSession(null);
        writeToSession(session, file);
        return commitSession(session);
    }

    public Set<String> getApps() {
        return apps.keySet();
    }

    @Nullable
    public List<String> getAppPaths(String pkg) {
        Application app = apps.get(pkg);
        if (app == null) {
            return null;
        }
        List<String> paths = new ArrayList<>();
        for (Apk apk : app.apks) {
            paths.add(app.path + "/" + apk.details.fileName);
        }
        return paths;
    }

    public int createSession(String inherit) {
        int id = sessions.size() + 1;
        sessions.put(id, new Session(id, inherit));
        return id;
    }

    public void writeToSession(int id, byte[] apk) {
        sessions.get(id).apks.add(apk);
    }

    public boolean isValidSession(int session) {
        return sessions.get(session) != null;
    }

    public InstallResult commitSession(int id) throws IOException {
        Session session = sessions.get(id);

        String packageName = null;
        int versionCode = 0;

        SortedMap<String, byte[]> stage = new TreeMap<>(); // sorted by the apk name
        Map<String, ApkParser.ApkDetails> details = new HashMap<>();
        Application inherit = apps.get(session.inherit);
        if (inherit != null) {
            packageName = inherit.packageName;
            versionCode = inherit.versionCode;
            for (Apk apk : inherit.apks) {
                byte[] bytes =
                        Files.readAllBytes(
                                new File(getStorage(), inherit.path + "/" + apk.details.fileName)
                                        .toPath());
                stage.put(apk.details.fileName, bytes);
                details.put(apk.details.fileName, apk.details);
            }
        }

        for (byte[] bytes : session.apks) {
            Path tmp = Files.createTempFile(getStorage().toPath(), "apk", ".apk");
            Files.write(tmp, bytes);
            ApkParser parser = new ApkParser();
            ApkParser.ApkDetails apkDetails = parser.getApkDetails(tmp.toFile().getAbsolutePath());
            stage.put(apkDetails.fileName, bytes);
            details.put(apkDetails.fileName, apkDetails);
        }

        if (stage.isEmpty()) {
            throw new IllegalArgumentException("No apks added");
        }

        for (ApkParser.ApkDetails apkDetails : details.values()) {
            if (packageName == null) {
                packageName = apkDetails.packageName;
                versionCode = apkDetails.versionCode;
            } else if (versionCode != apkDetails.versionCode) {
                return new InstallResult(
                        InstallResult.Error.INSTALL_FAILED_INVALID_APK,
                        versionCode,
                        apkDetails.versionCode);
            }
        }

        Application previous = apps.get(packageName);
        if (previous != null) {
            if (previous.versionCode > versionCode) {
                return new InstallResult(
                        InstallResult.Error.INSTALL_FAILED_VERSION_DOWNGRADE,
                        previous.versionCode,
                        versionCode);
            }
            FileUtils.deleteRecursivelyIfExists(new File(getStorage(), previous.path));
        }

        String appPath = "/data/app/" + packageName + "-" + UUID.randomUUID().toString();
        int uid = apps.keySet().size() + 1;
        File appDir = new File(getStorage(), appPath);
        appDir.mkdirs();

        List<Apk> apks = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : stage.entrySet()) {
            String fileName = entry.getKey();
            byte[] bytes = entry.getValue();
            Files.write(new File(appDir, fileName).toPath(), bytes);
            apks.add(new Apk(details.get(fileName)));
        }
        // Using a similar numbering and naming scheme as android:
        // See https://android.googlesource.com/platform/system/core/+/master/libcutils/include/private/android_filesystem_config.h
        User user = addUser(10000 + id, "u0_a" + id);
        Application app = new Application(packageName, apks, appPath, user, versionCode);
        apps.put(packageName, app);
        return new InstallResult(InstallResult.Error.SUCCESS, 0, versionCode);
    }

    private User addUser(int uid, String name) {
        User user = new User(uid, name);
        users.add(user);
        return user;
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

    public User getShellUser() {
        return shellUser;
    }

    public Application getApplication(String pkg) {
        return apps.get(pkg);
    }

    public static class Application {
        public final String packageName;
        public final String path;
        public final User user;
        public final List<Apk> apks;
        public final int versionCode;

        public Application(
                String packageName, List<Apk> apks, String path, User user, int versionCode) {
            this.packageName = packageName;
            this.apks = apks;
            this.path = path;
            this.user = user;
            this.versionCode = versionCode;
        }
    }

    public static class Apk {
        public final ApkParser.ApkDetails details;

        public Apk(ApkParser.ApkDetails details) {
            this.details = details;
        }
    }

    public static class User {
        public final String name;
        public final int uid;

        public User(int uid, String name) {
            this.name = name;
            this.uid = uid;
        }
    }

    class Session {
        public final int id;
        public final List<byte[]> apks;
        public final String inherit;

        Session(int id, String inherit) {
            this.id = id;
            this.inherit = inherit;
            apks = new ArrayList<>();
        }
    }

    public static class InstallResult {
        public final Error error;
        public final int previous;
        public final int value;

        public InstallResult(Error error, int previous, int value) {
            this.error = error;
            this.previous = previous;
            this.value = value;
        }

        public enum Error {
            SUCCESS,
            INSTALL_FAILED_VERSION_DOWNGRADE,
            INSTALL_FAILED_INVALID_APK,
        }
    }
}
