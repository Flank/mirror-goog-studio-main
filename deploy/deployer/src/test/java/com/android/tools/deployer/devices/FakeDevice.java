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
import com.android.testutils.TestUtils;
import com.android.tools.deployer.ApkParser;
import com.android.tools.deployer.devices.shell.Shell;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final List<AndroidProcess> processes;
    private final User shellUser;
    private final int zygotepid;
    private final File logcat;
    private final File fakeApp;
    private final File fakeShell;
    private List<User> users;
    private int pid;

    private final Map<Integer, Session> sessions;

    private File storage;
    @Nullable private DeviceState deviceState;
    public final Server shellServer;

    public FakeDevice(DeviceId deviceId) throws IOException {
        this(deviceId.version(), deviceId.api());
    }

    public FakeDevice(String version, int api) throws IOException {
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
        this.processes = new ArrayList<>();
        this.users = new ArrayList<>();
        this.pid = 10000;
        // Set up
        this.shellUser = addUser(2000, "shell");
        this.storage = Files.createTempDirectory("storage").toFile();
        this.storage.deleteOnExit();
        this.zygotepid = runProcess(0, "zygote64");
        this.logcat = File.createTempFile("logs", "txt");
        this.shellServer =
                NettyServerBuilder.forPort(0).addService(new FakeDeviceService(this)).build();
        this.fakeShell = getFakeShell();
        this.fakeApp = getFakeApp();

        setUp();
    }

    private void setUp() throws IOException {
        // Assume all devices have /data/local/tmp created, if this is not true ddmlib already fails to install
        File file = new File(storage, "data/local/tmp");
        file.mkdirs();

        File runAsFrom = getRunas();
        File runAs = new File(storage, "system/bin/run-as");
        if (runAsFrom.exists()) {
          runAs.getParentFile().mkdirs();
          Files.copy(runAsFrom.toPath(), runAs.toPath());
        }

        shellServer.start();

        System.out.printf("Fake device: %s started up.\n", version);
        System.out.printf("  sd-card at: %s\n", storage.getAbsolutePath());
        System.out.printf("  logcat at: %s\n", logcat.getAbsolutePath());
        System.out.printf("  External shell at port: %d\n", shellServer.getPort());
    }

    private File getRunas() {
        return getBin("tools/base/deploy/installer/tests/fake_runas");
    }

    private File getFakeShell() {
        return getBin("tools/base/deploy/installer/tests/fake_shell");
    }

    private File getFakeApp() {
        return getBin("tools/base/deploy/installer/tests/fake_app");
    }

    private File getBin(String path) {
        File root = TestUtils.getWorkspaceRoot().toFile();
        File file = new File(root, path);
        if (!file.exists()) {
            // Running from IJ
            file = new File(root, "bazel-bin/" + path);
        }
        return file;
    }

    public void connectTo(FakeAdbServer server) throws ExecutionException, InterruptedException {
        deviceState =
                server.connectDevice(
                                UUID.randomUUID().toString(),
                                MANUFACTURER,
                                MODEL,
                                version,
                                String.valueOf(api),
                                DeviceState.HostConnectionType.USB)
                        .get();
        deviceState.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

        // Connect running apps to FakeADB.
        for (int i = 0; i < processes.size(); i++) {
            AndroidProcess p = processes.get(i);
            // TODO: handle renamed processes
            deviceState.startClient(
                    p.pid,
                    p.application.user.uid,
                    p.application.packageName,
                    p.application.packageName,
                    false);
        }
    }

    public boolean isDevice(DeviceState state) {
        return deviceState == state;
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

    public Set<String> getApps(int uid) {
        Set<String> results = new HashSet<>();
        for (Map.Entry<String, Application> entry : apps.entrySet()) {
            if (entry.getValue().user.uid == uid) {
                results.add(entry.getKey());
            }
        }
        return results;
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

    public boolean runApp(String pkgName) throws IOException {
        Application app = apps.get(pkgName);
        boolean running = false;
        for (AndroidProcess process : processes) {
            if (process.application == app) {
                running = true;
                break;
            }
        }
        if (app != null && !running) {
            int pid = runProcess(app.user.uid, app.packageName);
            AndroidProcess process = new AndroidProcess(pid, app);
            processes.add(process);
            // TODO: get proper user, and pass proper boolean for isWaiting (support wait-for-debugger)
            if (deviceState != null) {
                deviceState.startClient(pid, app.user.uid, pkgName, pkgName, false);
            }
            File data = new File(getStorage(), app.dataPath);
            File agents = new File(data, "code_cache/startup_agents");
            if (supportsStartupAgents() && agents.exists()) {
                for (File agent : agents.listFiles()) {
                    // We need to re-create this because of how FakeDevice native code handles the
                    // test environment root. TODO: Fix this.
                    String path = app.dataPath + "/code_cache/startup_agents/" + agent.getName();
                    // Need a blocking request so we wait until the instrumentation completes.
                    process.attachAgentBlocking(path + "=" + app.dataPath);
                }
            }
            return true;
        }
        return false;
    }


    public void stopApp(String pkgName) throws IOException {
        Application app = apps.get(pkgName);
        List<AndroidProcess> toRemove = new ArrayList<>();
        for (AndroidProcess process : processes) {
            if (process.application == app) {
                toRemove.add(process);
            }
        }
        processes.removeAll(toRemove);
        for (AndroidProcess process : toRemove) {
            final Path proc = getStorage().toPath().resolve("proc/" + process.pid);
            FileUtils.deleteRecursivelyIfExists(proc.toFile());
            process.shutdown();
        }
    }

    public List<AndroidProcess> getProcesses() {
        return processes;
    }

    private int runProcess(int uid, String cmdline) throws IOException {
        int pid = this.pid++;
        final Path proc = getStorage().toPath().resolve("proc/" + pid);
        Files.createDirectories(proc);
        Files.write(proc.resolve("cmdline"), cmdline.getBytes(Charsets.UTF_8));
        Files.write(
                proc.resolve("stat"),
                String.format("%d name R %d", pid, zygotepid).getBytes(Charsets.UTF_8));
        Files.write(proc.resolve(".uid"), String.format("%d", uid).getBytes(Charsets.UTF_8));
        return pid;
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

        String dataPath = "/data/data/" + packageName;
        File dataDir = new File(getStorage(), dataPath);
        FileUtils.deleteRecursivelyIfExists(dataDir);
        dataDir.mkdirs();

        File codeCache = new File(dataDir, "code_cache");
        codeCache.mkdir();

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
        Application app = new Application(packageName, apks, appPath, dataPath, user, versionCode);
        apps.put(packageName, app);
        return new InstallResult(InstallResult.Error.SUCCESS, 0, versionCode);
    }

    private User addUser(int uid, String name) {
        User user = new User(uid, name);
        users.add(user);
        return user;
    }

    User getUser(int uid) {
        for (User user : users) {
            if (user.uid == uid) {
                return user;
            }
        }
        return null;
    }

    public void abandonSession(int session) {
        sessions.put(session, null);
    }

    public boolean supportsJvmti() {
        return getApi() >= 26;
    }

    public boolean supportsStartupAgents() {
        return getApi() >= 30;
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

    public File getStorage() {
        return storage;
    }

    public User getShellUser() {
        return shellUser;
    }

    public RunResult executeScript(String cmd, byte[] input) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(input);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            return new RunResult(
                    getShell().execute(cmd, getShellUser(), out, in, this),
                    out.toByteArray());
        }
    }

    public Application getApplication(String pkg) {
        return apps.get(pkg);
    }

    public int copyFile(String source, String dest) throws IOException {
        File from = new File(getStorage(), source);
        File to = new File(getStorage(), dest);
        Files.copy(from.toPath(), to.toPath());
        return 0;
    }

    public void copyDirRecursively(String source, String dest) throws IOException {
        File from = new File(getStorage(), source);
        File to = new File(getStorage(), dest);
        FileUtils.deleteRecursivelyIfExists(to);
        FileUtils.copyDirectory(from, to);
    }

    public boolean isDirectory(String path) throws IOException {
        return new File(getStorage(), path).isDirectory();
    }

    public boolean attachAgent(int pid, String agent) {
        for (AndroidProcess process : processes) {
            if (process.pid == pid) {
                process.attachAgent(agent);
                return true;
            }
        }
        return false;
    }

    public void shutdown() throws Exception {
        for (AndroidProcess process : processes) {
            process.shutdown();
        }
        shellServer.shutdown();
        FileUtils.deleteDirectoryContents(storage);
    }

    public File getLogcatFile() {
        return logcat;
    }

    public void putEnv(User user, Map<String, String> env) {
        env.put("FAKE_DEVICE_PORT", String.valueOf(shellServer.getPort()));
        env.put("FAKE_DEVICE_ROOT", storage.getAbsolutePath());
        env.put("FAKE_DEVICE_LOGCAT", logcat.getAbsolutePath());
        env.put("FAKE_DEVICE_SHELL", fakeShell.getAbsolutePath());
        env.put("FAKE_DEVICE_API_LEVEL", String.valueOf(api));
        env.put("FAKE_DEVICE_UID", String.valueOf(user.uid));
    }

    public static class Application {
        public final String packageName;
        public final String path;
        public final String dataPath;
        public final User user;
        public final List<Apk> apks;
        public final int versionCode;

        public Application(
                String packageName,
                List<Apk> apks,
                String path,
                String dataPath,
                User user,
                int versionCode) {
            this.packageName = packageName;
            this.apks = apks;
            this.path = path;
            this.dataPath = dataPath;
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

    public static class RunResult {
        public final int value;
        public final byte[] output;

        public RunResult(int value, byte[] output) {
            this.value = value;
            this.output = output;
        }
    }

    public class AndroidProcess {
        private final Process process;
        private final FakeAppGrpc.FakeAppBlockingStub stub;
        private final ManagedChannel channel;
        public final int pid;
        public final Application application;

        public AndroidProcess(int pid, Application application) throws IOException {
            this.pid = pid;
            this.application = application;
            final ProcessBuilder pb = new ProcessBuilder(fakeApp.getAbsolutePath());
            putEnv(application.user, pb.environment());
            this.process = pb.start();
            final BufferedReader reader =
                    new BufferedReader(new InputStreamReader(this.process.getInputStream()));
            final Matcher matcher =
                    Pattern.compile("Fake-Device-Port: (\\d+)").matcher(reader.readLine());
            if (!matcher.matches()) {
                throw new IllegalStateException("Invalid first server line");
            }
            int port = Integer.valueOf(matcher.group(1));
            this.channel = NettyChannelBuilder.forAddress("localhost", port).usePlaintext().build();
            this.stub = FakeAppGrpc.newBlockingStub(channel);
        }

        public boolean attachAgent(String agent) {
            final Proto.AttachAgentRequest.Builder req = makeAgentRequest(agent);
            stub.attachAgent(req.build());
            return true;
        }

        public boolean attachAgentBlocking(String agent) {
            final Proto.AttachAgentRequest.Builder req = makeAgentRequest(agent);
            req.setBlocking(true);
            stub.attachAgent(req.build());
            return true;
        }

        public void shutdown() {
            process.destroyForcibly();
            channel.shutdownNow();
            try {
                if (!channel.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.err.println("Channel did not terminate properly");
                }
            } catch (InterruptedException e) {
                System.err.println("Channel did not terminate properly: " + e.getMessage());
            }
        }

        private Proto.AttachAgentRequest.Builder makeAgentRequest(String agent) {
            final Proto.AttachAgentRequest.Builder req = Proto.AttachAgentRequest.newBuilder();
            final int i = agent.indexOf('=');
            if (i >= 0) {
                req.setPath(agent.substring(0, i));
                req.setOptions(agent.substring(i + 1));
            } else {
                req.setPath(agent);
            }
            return req;
        }
    }
}
