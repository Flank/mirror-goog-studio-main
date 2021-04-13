/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.InstallMetrics;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AdbClient {
    private static final Map<String, Deploy.Arch> ABI_MAP =
            ImmutableMap.of(
                    "arm64-v8a", Deploy.Arch.ARCH_64_BIT,
                    "armeabi-v7a", Deploy.Arch.ARCH_32_BIT,
                    "x86_64", Deploy.Arch.ARCH_64_BIT,
                    "x86", Deploy.Arch.ARCH_32_BIT);

    private final IDevice device;
    private final ILogger logger;

    public static final long DEFAULT_TIMEOUT = 5;
    public static final TimeUnit DEFAULT_TIMEUNIT = TimeUnit.MINUTES;

    public AdbClient(IDevice device, ILogger logger) {
        this.device = device;
        this.logger = logger;
    }

    public static class InstallResult {
        public final InstallStatus status;
        public final String reason;
        public final InstallMetrics metrics;

        InstallResult(InstallStatus status, String reason) {
            this.status = status;
            this.reason = reason;
            metrics = null;
        }

        InstallResult(InstallStatus status, String reason, InstallMetrics metrics) {
            this.status = status;
            this.reason = reason;
            this.metrics = metrics;
        }
    }

    public SocketChannel rawExec(String executable, String[] parameters)
            throws AdbCommandRejectedException, IOException, TimeoutException {
        return device.rawExec(executable, parameters);
    }

    /** Executes the given command with no stdin and returns stdout as a byte[] */
    public byte[] shell(String[] parameters) throws IOException {
        return shell(parameters, null);
    }

    public byte[] shell(String[] parameters, InputStream input) throws IOException {
        return shell(
                parameters, input, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT);
    }

    /**
     * Executes the given command and sends {@code input} to stdin and returns stdout as a byte[]
     */
    public byte[] shell(
            String[] parameters, InputStream input, long maxTimeOutMs, TimeUnit timeUnit)
            throws IOException {
        ByteArrayOutputReceiver receiver;
        try (Trace ignored = Trace.begin("adb shell" + Arrays.toString(parameters))) {
            receiver = new ByteArrayOutputReceiver();
            device.executeShellCommand(
                    String.join(" ", parameters), receiver, maxTimeOutMs, timeUnit, input);
            return receiver.toByteArray();
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | TimeoutException e) {
            throw new IOException(e);
        }
    }

    /**
     * Executes the given Binder command and sends {@code input} to stdin and returns stdout as a
     * byte[]
     */
    public byte[] binder(String[] parameters, InputStream input) throws IOException {
        logger.info("BINDER: " + String.join(" ", parameters));
        ByteArrayOutputReceiver receiver;
        try (Trace ignored = Trace.begin("binder" + Arrays.toString(parameters))) {
            receiver = new ByteArrayOutputReceiver();
            device.executeBinderCommand(parameters, receiver, 5, TimeUnit.MINUTES, input);
            return receiver.toByteArray();
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | TimeoutException e) {
            throw new IOException(e);
        }
    }

    public InstallResult install(List<String> apks, List<String> options, boolean reinstall) {
        List<File> files = apks.stream().map(File::new).collect(Collectors.toList());
        try {
            if (device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.LOLLIPOP)) {
                device.installPackages(files, reinstall, options, 5, TimeUnit.MINUTES);
                return new InstallResult(InstallStatus.OK, null, device.getLastInstallMetrics());
            } else {
                if (apks.size() != 1) {
                    return new InstallResult(
                            InstallStatus.MULTI_APKS_NO_SUPPORTED_BELOW21,
                            "Splits are not supported below API 21");
                } else {
                    device.installPackage(apks.get(0), reinstall, options.toArray(new String[0]));
                    return new InstallResult(
                            InstallStatus.OK, null, device.getLastInstallMetrics());
                }
            }
        } catch (InstallException e) {
            String code = e.getErrorCode();
            if (code != null) {
                try {
                    return ApkInstaller.toInstallerResult(code, e.getMessage());
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    logger.warning(
                            "Unrecognized Installation Failure: %s\n%s\n", code, e.getMessage());
                }
            } else {
                Throwable cause = e.getCause();
                if (cause instanceof ShellCommandUnresponsiveException) {
                    return new InstallResult(InstallStatus.SHELL_UNRESPONSIVE, e.getMessage());
                } else {
                    logger.warning("Installation Failure: %s\n", e.getMessage());
                    return new InstallResult(InstallStatus.UNKNOWN_ERROR, e.getMessage(), null);
                }
            }
            return new InstallResult(InstallStatus.UNKNOWN_ERROR, "Unknown Error");
        }
    }

    public boolean uninstall(String packageName) {
        try {
            device.uninstallPackage(packageName);
            return true;
        } catch (InstallException e) {
        }
        return false;
    }

    public List<String> getAbis() {
        return device.getAbis();
    }

    /**
     * Gets the PIDs of the given package name. R+ only.
     *
     * @return a {@link List} of PIDs, or null if this isn't supported on the device.
     */
    public List<Integer> getPids(String packageName) {
        if (!device.supportsFeature(IDevice.Feature.REAL_PKG_NAME)) {
            throw new IllegalStateException(
                    String.format(
                            "Device %s, do not support REAL_PKG_NAME", device.getSerialNumber()));
        }
        List<Integer> results = new ArrayList<>();
        for (Client client : device.getClients()) {
            if (packageName.equals(client.getClientData().getPackageName())) {
                results.add(client.getClientData().getPid());
            }
        }
        return results;
    }

    public Deploy.Arch getArch(List<Integer> pids) {
        Deploy.Arch result = Deploy.Arch.ARCH_UNKNOWN;
        for (int pid : pids) {
            Deploy.Arch curProc = getArch(pid);
            if (result == Deploy.Arch.ARCH_UNKNOWN) {
                result = curProc;
            } else if (curProc != Deploy.Arch.ARCH_UNKNOWN && result != curProc) {
                // We can't throw an exception here; this happens when you have a webview process.
                logger.warning("Mixed ABIs detected: %s and %s", result, curProc);
            }
        }
        return result;
    }

    /**
     * The application will run with the most-preferred device ABI that the application also
     * natively supports. An application with no native libraries automatically runs with the
     * most-preferred device ABI.
     */
    public Deploy.Arch getArchFromApk(List<Apk> apks) throws DeployerException {
        HashSet<String> appSupported = new HashSet<>();
        for (Apk apk : apks) {
            appSupported.addAll(apk.libraryAbis);
        }

        List<String> deviceSupported = getAbis();
        if (deviceSupported.isEmpty()) {
            throw DeployerException.unsupportedArch();
        }

        // No native libraries means we use the first device-preferred ABI.
        if (appSupported.isEmpty()) {
            String abi = deviceSupported.get(0);
            return ABI_MAP.get(abi);
        }

        for (String abi : deviceSupported) {
            if (appSupported.contains(abi)) {
                return ABI_MAP.get(abi);
            }
        }

        throw DeployerException.unsupportedArch();
    }

    private Deploy.Arch getArch(int pid) {
        for (Client client : device.getClients()) {
            if (client.getClientData().getPid() != pid) {
                continue;
            }

            String abi = client.getClientData().getAbi();
            if (abi == null) {
                return Deploy.Arch.ARCH_UNKNOWN;
            } else if (abi.startsWith("32-bit")) {
                return Deploy.Arch.ARCH_32_BIT;
            } else if (abi.startsWith("64-bit")) {
                return Deploy.Arch.ARCH_64_BIT;
            } else {
                return Deploy.Arch.ARCH_UNKNOWN;
            }
        }
        return Deploy.Arch.ARCH_UNKNOWN;
    }

    public void push(String from, String to) throws IOException {
        try (Trace ignored = Trace.begin("adb push")) {
            device.pushFile(from, to);
        } catch (SyncException | TimeoutException | AdbCommandRejectedException e) {
            throw new IOException(e);
        }
    }

    public AndroidVersion getVersion() {
        return device.getVersion();
    }

    public String getSerial() {
        return device.getSerialNumber();
    }

    // TODO: Replace this to void copying the full byte[] incurred when calling stream.toByteArray()
    private class ByteArrayOutputReceiver implements IShellOutputReceiver {

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        @Override
        public void addOutput(byte[] data, int offset, int length) {
            stream.write(data, offset, length);
        }

        @Override
        public void flush() {}

        @Override
        public boolean isCancelled() {
            return false;
        }

        byte[] toByteArray() {
            return stream.toByteArray();
        }
    }

    // TODO: Returning a String is not enough since it delegates parsing that String to the caller.
    // This method should return an AbortSessionResponse object, built on top of a ShellResponse object
    // with a status code and the raw output string. Parsing the string output should be done in
    // AbortSessionResponse.
    public String abortSession(String sessionId) {
        String prefix =
                device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.N)
                        ? "cmd package"
                        : "pm";

        String[] command = {prefix, "install-abandon", sessionId};

        String response;
        try {
            byte[] bytes = shell(command);
            response = new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            response = e.getMessage();
        }
        return response;
    }

    public String getSkipVerificationOption(String packageName) {
        return ApkVerifierTracker.getSkipVerificationInstallationFlag(device, packageName);
    }
}
