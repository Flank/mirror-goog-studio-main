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

import com.android.ddmlib.*;
import com.android.sdklib.AndroidVersion;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AdbClient {
    private final IDevice device;
    private final ILogger logger;

    public AdbClient(IDevice device, ILogger logger) {
        this.device = device;
        this.logger = logger;
    }

    enum InstallStatus {
        OK,

        // From PackageManager#installStatusToString
        INSTALL_FAILED_ALREADY_EXISTS,
        INSTALL_FAILED_INVALID_APK,
        INSTALL_FAILED_INVALID_URI,
        INSTALL_FAILED_INSUFFICIENT_STORAGE,
        INSTALL_FAILED_DUPLICATE_PACKAGE,
        INSTALL_FAILED_NO_SHARED_USER,
        INSTALL_FAILED_UPDATE_INCOMPATIBLE,
        INSTALL_FAILED_SHARED_USER_INCOMPATIBLE,
        INSTALL_FAILED_MISSING_SHARED_LIBRARY,
        INSTALL_FAILED_REPLACE_COULDNT_DELETE,
        INSTALL_FAILED_DEXOPT,
        INSTALL_FAILED_OLDER_SDK,
        INSTALL_FAILED_CONFLICTING_PROVIDER,
        INSTALL_FAILED_NEWER_SDK,
        INSTALL_FAILED_TEST_ONLY,
        INSTALL_FAILED_CPU_ABI_INCOMPATIBLE,
        INSTALL_FAILED_MISSING_FEATURE,
        INSTALL_FAILED_CONTAINER_ERROR,
        INSTALL_FAILED_INVALID_INSTALL_LOCATION,
        INSTALL_FAILED_MEDIA_UNAVAILABLE,
        INSTALL_FAILED_VERIFICATION_TIMEOUT,
        INSTALL_FAILED_VERIFICATION_FAILURE,
        INSTALL_FAILED_PACKAGE_CHANGED,
        INSTALL_FAILED_UID_CHANGED,
        INSTALL_FAILED_VERSION_DOWNGRADE,
        INSTALL_PARSE_FAILED_NOT_APK,
        INSTALL_PARSE_FAILED_BAD_MANIFEST,
        INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION,
        INSTALL_PARSE_FAILED_NO_CERTIFICATES,
        INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES,
        INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING,
        INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME,
        INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID,
        INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
        INSTALL_PARSE_FAILED_MANIFEST_EMPTY,
        INSTALL_FAILED_INTERNAL_ERROR,
        INSTALL_FAILED_USER_RESTRICTED,
        INSTALL_FAILED_DUPLICATE_PERMISSION,
        INSTALL_FAILED_NO_MATCHING_ABIS,
        INSTALL_FAILED_ABORTED,
        INSTALL_FAILED_BAD_DEX_METADATA,
        INSTALL_FAILED_MISSING_SPLIT,
        INSTALL_FAILED_BAD_SIGNATURE,
        INSTALL_FAILED_WRONG_INSTALLED_VERSION,

        DEVICE_NOT_RESPONDING,
        INCONSISTENT_CERTIFICATES,
        NO_CERTIFICATE,
        DEVICE_NOT_FOUND,
        SHELL_UNRESPONSIVE,
        MULTI_APKS_NO_SUPPORTED_BELOW21,
        UNKNOWN_ERROR,
        SKIPPED_INSTALL, // no changes.
        ;
    }

    static class InstallResult {
        public final InstallStatus status;
        public final String reason;
        public final InstallMetrics metrics;

        InstallResult(InstallStatus status) {
            this.status = status;
            reason = null;
            metrics = null;
        }

        InstallResult(InstallStatus status, String reason, InstallMetrics metrics) {
            this.status = status;
            this.reason = reason;
            this.metrics = metrics;
        }
    }

    /** Executes the given command with no stdin and returns stdout as a byte[] */
    public byte[] shell(String[] parameters) throws IOException {
        return shell(parameters, null);
    }

    /**
     * Executes the given command and sends {@code input} to stdin and returns stdout as a byte[]
     */
    public byte[] shell(String[] parameters, InputStream input) throws IOException {
        ByteArrayOutputReceiver receiver;
        try (Trace ignored = Trace.begin("adb shell" + Arrays.toString(parameters))) {
            receiver = new ByteArrayOutputReceiver();
            device.executeShellCommand(
                    String.join(" ", parameters), receiver, 5, TimeUnit.MINUTES, input);
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
                    return new InstallResult(InstallStatus.MULTI_APKS_NO_SUPPORTED_BELOW21);
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
                    return ApkInstaller.parseInstallerResultErrorCode(code);
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    logger.warning(
                            "Unrecognized Installation Failure: %s\n%s\n", code, e.getMessage());
                }
            } else {
                Throwable cause = e.getCause();
                if (cause instanceof ShellCommandUnresponsiveException) {
                    return new InstallResult(InstallStatus.SHELL_UNRESPONSIVE);
                } else {
                    logger.warning("Installation Failure: %s\n", e.getMessage());
                    return new InstallResult(InstallStatus.UNKNOWN_ERROR, e.getMessage(), null);
                }
            }
            return new InstallResult(InstallStatus.UNKNOWN_ERROR);
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

}
