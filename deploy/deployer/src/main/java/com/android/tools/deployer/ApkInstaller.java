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

import static com.android.tools.deployer.AdbClient.InstallResult.INSTALL_PARSE_FAILED_NO_CERTIFICATES;
import static com.android.tools.deployer.AdbClient.InstallResult.OK;
import static com.android.tools.deployer.DeployerException.Error.INSTALL_FAILED;

import com.android.ddmlib.InstallReceiver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.ApkEntry;
import com.android.utils.ILogger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApkInstaller {

    private enum DeltaInstallStatus {
        SUCCESS,
        ERROR,
        NOT_PATCHABLE, // Patch size would be beyond limit or app is not installed.
    }

    private class DeltaInstallResult {
        DeltaInstallStatus status = DeltaInstallStatus.SUCCESS;
        String packageManagerOutput = "";
    }

    private final AdbClient adb;
    private final UIService service;
    private final Installer installer;
    private final ILogger logger;

    public ApkInstaller(AdbClient adb, UIService service, Installer installer, ILogger logger) {
        this.adb = adb;
        this.service = service;
        this.installer = installer;
        this.logger = logger;
    }

    public List<InstallMetric> install(
            String packageName,
            List<String> apks,
            InstallOptions options,
            Deployer.InstallMode installMode)
            throws DeployerException {
        DeltaInstallResult deltaInstallResult = null;
        List<InstallMetric> metrics = new ArrayList<>();

        // First attempt to delta install.
        boolean allowReinstall = true;
        long deltaInstallStart = System.currentTimeMillis();
        try {
            deltaInstallResult = deltaInstall(apks, options, allowReinstall, installMode);
        } catch (DeployerException e) {
            logger.error(e, "Unable to delta install");
        }

        AdbClient.InstallResult result = OK;
        switch (deltaInstallResult.status) {
            case SUCCESS:
                result = OK;
                InstallMetric metric = new InstallMetric("DELTAINSTALL_SUCCESS", deltaInstallStart);
                metric.finish(metrics);
                break;
            case ERROR:
                InstallReceiver installReceiver = new InstallReceiver();
                String[] lines = deltaInstallResult.packageManagerOutput.split("\\n");
                installReceiver.processNewLines(lines);
                result = parseInstallerResultErrorCode(installReceiver.getErrorCode());
                boolean deltaInstallFailedOnCertificate =
                        (result == INSTALL_PARSE_FAILED_NO_CERTIFICATES);
                result = adb.install(apks, options.getFlags(), allowReinstall);
                if (result == OK && deltaInstallFailedOnCertificate) {
                    // ADB install succeeded where delta install failed. This may happen if there is a
                    // CRC+size+compressed_size collision in the clear/dirty analysis when we build the
                    // patch. It is extremely unlikely but we should try to keep track of it.
                    InstallMetric deltaFailedMetric =
                            new InstallMetric("DELTAINSTALL_FAILEDONCERTIF", deltaInstallStart);
                    deltaFailedMetric.finish(metrics);
                }
                break;
            case NOT_PATCHABLE:
                // Delta install could not be attempted (app not install or delta above limit or API
                // not supported),
                InstallMetric instalMetric = new InstallMetric("INSTALL_SUCCESS");
                result = adb.install(apks, options.getFlags(), allowReinstall);
                instalMetric.finish(metrics);
                break;
        }

        String message = message(result);
        switch (result) {
            case INSTALL_FAILED_UPDATE_INCOMPATIBLE:
            case INCONSISTENT_CERTIFICATES:
            case INSTALL_FAILED_VERSION_DOWNGRADE:
            case INSTALL_FAILED_DEXOPT:
                StringBuilder sb = new StringBuilder();
                sb.append(message).append("\n");
                sb.append(
                        "In order to proceed, you will have to uninstall the existing application");
                sb.append("\n\nWARNING: Uninstalling will remove the application data!\n\n");
                sb.append("Do you want to uninstall the existing application?");
                if (service.prompt(sb.toString())) {
                    adb.uninstall(packageName);
                    result = adb.install(apks, options.getFlags(), allowReinstall);
                    message = message(result);
                }
                break;
            default:
                // Fall through
        }

        if (result != OK) {
            throw new DeployerException(INSTALL_FAILED, result, message, result.getReason());
        }
        return metrics;
    }

    DeltaInstallResult deltaInstall(
            List<String> apks,
            InstallOptions options,
            boolean allowReinstall,
            Deployer.InstallMode installMode)
            throws DeployerException {
        DeltaInstallResult deltaInstallResult = new DeltaInstallResult();

        if (installMode != Deployer.InstallMode.DELTA) {
            deltaInstallResult.status = DeltaInstallStatus.NOT_PATCHABLE;
            return deltaInstallResult;
        }

        // We use "cmd" on the device side which was only added in Android N (API 24)
        // Note that we also use "install-create" which was only added in Android LOLLIPOP (API 21)
        // so this check should factor in these limitations.
        if (!adb.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.N)) {
            deltaInstallResult.status = DeltaInstallStatus.NOT_PATCHABLE;
            return deltaInstallResult;
        }

        List<ApkEntry> localEntries = new ApkParser().parsePaths(apks);
        ApplicationDumper.Dump dump;
        try {
            dump = new ApplicationDumper(installer).dump(localEntries);
        } catch (DeployerException e) {
            deltaInstallResult.status = DeltaInstallStatus.NOT_PATCHABLE;
            return deltaInstallResult;
        }

        // Send deltaInstall request
        Deploy.DeltaInstallRequest.Builder builder = Deploy.DeltaInstallRequest.newBuilder();
        builder.addAllOptions(options.getFlags());
        // We need to match what happens in ddmlib implementation of installPackages where
        // a "-r" is added.
        if (allowReinstall) {
            builder.addOptions("-r");
        }
        List<Deploy.PatchInstruction> patches =
                new PatchSetGenerator().generateFromEntries(localEntries, dump.apkEntries);
        if (patches == null || patches.isEmpty()) {
            deltaInstallResult.status = DeltaInstallStatus.NOT_PATCHABLE;
            return deltaInstallResult;
        }
        builder.addAllPatchInstructions(patches);

        // Send delta install request.
        Deploy.DeltaInstallResponse res;
        try {
            res = installer.deltaInstall(builder.build());
        } catch (IOException e) {
            deltaInstallResult.status = DeltaInstallStatus.ERROR;
            return deltaInstallResult;
        }

        // Retrieve and parse result.
        if (res.getStatus() == Deploy.DeltaInstallResponse.Status.OK) {
            deltaInstallResult.status = DeltaInstallStatus.SUCCESS;
        } else {
            deltaInstallResult.status = DeltaInstallStatus.ERROR;
            deltaInstallResult.packageManagerOutput = res.getInstallOutput();
        }
        return deltaInstallResult;
    }

    public static AdbClient.InstallResult parseInstallerResultErrorCode(String errorCode) {
        try {
            return AdbClient.InstallResult.valueOf(errorCode);
        } catch (Exception e) {
            AdbClient.InstallResult result = AdbClient.InstallResult.UNKNOWN_ERROR;
            result.setReason(errorCode);
            return result;
        }
    }

    private String message(AdbClient.InstallResult result) {
        switch (result) {
            case INSTALL_FAILED_VERSION_DOWNGRADE:
                return "The device already has a newer version of this application.";
            case DEVICE_NOT_RESPONDING:
                return "Device not responding";
            case INSTALL_FAILED_UPDATE_INCOMPATIBLE:
            case INCONSISTENT_CERTIFICATES:
                return "The device already has an application with the same package but a different signature.";
            case INSTALL_FAILED_DEXOPT:
                return "The device possibly has stale dexed jars that don't match the current version (dexopt error)";
            case NO_CERTIFICATE:
                return "The APK was either not signed, or signed incorrectly.";
            case INSTALL_FAILED_OLDER_SDK:
                return "The application's minSdkVersion is newer than the device API level.";
            case DEVICE_NOT_FOUND:
                return "The device has been disconnected.";
            case SHELL_UNRESPONSIVE:
                return "The device timed out while trying to install the application.";
            case INSTALL_FAILED_INSUFFICIENT_STORAGE:
                return "The device needs more free storage to install the application (extra space is needed in addition to APK size).";
            case MULTI_APKS_NO_SUPPORTED_BELOW21:
                return "Multi-APK app installation is not supported on devices with API level < 21.";
            case INSTALL_PARSE_FAILED_NO_CERTIFICATES:
                return "APK signature verification failed.";
            default:
                return "Installation failed due to: '" + result.getReason() + "'";
        }
    }
}
