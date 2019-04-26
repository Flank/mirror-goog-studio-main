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


import static com.android.tools.deployer.AdbClient.InstallStatus.OK;
import static com.android.tools.deployer.AdbClient.InstallStatus.SKIPPED_INSTALL;

import com.android.ddmlib.InstallReceiver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.FileDiff;
import com.android.utils.ILogger;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class ApkInstaller {

    private enum DeltaInstallStatus {
        SUCCESS,
        UNKNOWN,
        ERROR,
        DISABLED,
        CANNOT_GENERATE_DELTA,
        API_NOT_SUPPORTED,
        DUMP_FAILED,
        PATCH_SIZE_EXCEEDED,
        NO_CHANGES,
        DUMP_UNKNOWN_PACKAGE,
    }

    private static class DeltaInstallResult {
        final DeltaInstallStatus status;
        final String packageManagerOutput;

        private DeltaInstallResult(DeltaInstallStatus status, String output) {
            this.status = status;
            packageManagerOutput = output;
        }

        private DeltaInstallResult(DeltaInstallStatus status) {
            this(status, "");
        }
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

    /** @return true if if installation happened. False if installation was skipped */
    public boolean install(
            String packageName,
            List<String> apks,
            InstallOptions options,
            Deployer.InstallMode installMode,
            Collection<DeployMetric> metrics)
            throws DeployerException {
        DeltaInstallResult deltaInstallResult =
                new DeltaInstallResult(DeltaInstallStatus.UNKNOWN, "");

        // First attempt to delta install.
        boolean allowReinstall = true;
        long deltaInstallStart = System.nanoTime();
        try {
            deltaInstallResult =
                    deltaInstall(apks, options, allowReinstall, installMode, packageName);
        } catch (DeployerException e) {
            logger.info("Unable to delta install: '%s'", e.getDetails());
        }

        AdbClient.InstallResult result = new AdbClient.InstallResult(OK);
        switch (deltaInstallResult.status) {
            case SUCCESS:
                {
                    // Success means that the install procedure finished on device. There could
                    // still be errors in the output if the installation was not finished.
                    DeployMetric metric = new DeployMetric("DELTAINSTALL", deltaInstallStart);

                    InstallReceiver installReceiver = new InstallReceiver();
                    String[] lines = deltaInstallResult.packageManagerOutput.split("\\n");
                    installReceiver.processNewLines(lines);
                    installReceiver.done();
                    if (installReceiver.isSuccessfullyCompleted()) {
                        metric.finish(DeltaInstallStatus.SUCCESS.name(), metrics);
                    } else {
                        result = parseInstallerResultErrorCode(installReceiver.getErrorCode());
                        metric.finish(
                                DeltaInstallStatus.ERROR.name() + "." + result.status.name(),
                                metrics);
                    }

                    // If the binary patching failed, we will experience a signature failure,
                    // so in that case only we fall back to a normal install.
                    switch (result.status) {
                        case NO_CERTIFICATE:
                        case INSTALL_PARSE_FAILED_NO_CERTIFICATES:
                            result = adb.install(apks, options.getFlags(), allowReinstall);
                            long installStartTime = System.nanoTime();
                            DeployMetric installResult =
                                    new DeployMetric("INSTALL", installStartTime);
                            installResult.finish(result.status.name(), metrics);
                            break;
                        default:
                            // Don't fallback
                    }
                    break;
                }
            case ERROR:
            case UNKNOWN:
            case DISABLED:
            case CANNOT_GENERATE_DELTA:
            case API_NOT_SUPPORTED:
            case DUMP_FAILED:
            case DUMP_UNKNOWN_PACKAGE:
            case PATCH_SIZE_EXCEEDED:
                {
                    // Delta install could not be attempted (app not install or delta above limit or API
                    // not supported),
                    DeployMetric deltaNotPatchableMetric =
                            new DeployMetric("DELTAINSTALL", deltaInstallStart);
                    deltaNotPatchableMetric.finish(deltaInstallResult.status.name(), metrics);

                    long installStartedNs = System.nanoTime();
                    result = adb.install(apks, options.getFlags(), allowReinstall);

                    DeployMetric installResult = new DeployMetric("INSTALL", installStartedNs);
                    installResult.finish(result.status.name(), metrics);
                    break;
                }
            case NO_CHANGES:
                {
                    result = new AdbClient.InstallResult(SKIPPED_INSTALL);
                    DeployMetric installMetric = new DeployMetric("INSTALL");
                    installMetric.finish(result.status.name(), metrics);
                    try {
                        adb.shell(new String[] {"am", "force-stop", packageName});
                    } catch (IOException e) {
                        throw DeployerException.installFailed(
                                SKIPPED_INSTALL, "Failure to kill " + packageName);
                    }
                    break;
                }
        }

        // If the install succeeded and returned specific metrics about push/install
        // times, record those metrics as well.
        if (result.metrics != null) {
            metrics.add(
                    new DeployMetric(
                            "DDMLIB_UPLOAD",
                            result.metrics.getUploadStartNs(),
                            result.metrics.getUploadFinishNs()));
            metrics.add(
                    new DeployMetric(
                            "DDMLIB_INSTALL",
                            result.metrics.getInstallStartNs(),
                            result.metrics.getInstallFinishNs()));
        }
        String message = message(result);
        switch (result.status) {
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

        boolean installed = true;
        if (result.status == SKIPPED_INSTALL) {
            installed = false;
        } else if (result.status != OK) {
            throw DeployerException.installFailed(result.status, message);
        }
        return installed;
    }

    DeltaInstallResult deltaInstall(
            List<String> apks,
            InstallOptions options,
            boolean allowReinstall,
            Deployer.InstallMode installMode,
            String packageName)
            throws DeployerException {
        if (installMode != Deployer.InstallMode.DELTA) {
            return new DeltaInstallResult(DeltaInstallStatus.DISABLED);
        }

        // We use "cmd" on the device side which was only added in Android N (API 24)
        // Note that we also use "install-create" which was only added in Android LOLLIPOP (API 21)
        // so this check should factor in these limitations.
        if (!adb.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.N)) {
            return new DeltaInstallResult(DeltaInstallStatus.API_NOT_SUPPORTED);
        }

        List<ApkEntry> localEntries = new ApkParser().parsePaths(apks);
        ApplicationDumper.Dump dump;
        try {
            dump = new ApplicationDumper(installer).dump(localEntries);
        } catch (DeployerException e) {
            if (e.getError() == DeployerException.Error.DUMP_UNKNOWN_PACKAGE) {
                return new DeltaInstallResult(DeltaInstallStatus.DUMP_UNKNOWN_PACKAGE);
            } else {
                return new DeltaInstallResult(DeltaInstallStatus.DUMP_FAILED);
            }
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
        if (patches == null) {
            return new DeltaInstallResult(DeltaInstallStatus.CANNOT_GENERATE_DELTA);
        } else if (patches.isEmpty()) {
            return new DeltaInstallResult(DeltaInstallStatus.NO_CHANGES);
        }

        // We use inheritance if there are more than one apks, and if the manifests
        // have not changed
        boolean inherit = canInherit(apks.size(), new ApkDiffer().diff(dump, localEntries));
        builder.setInherit(inherit);
        builder.addAllPatchInstructions(patches);
        builder.setPackageName(packageName);

        Deploy.DeltaInstallRequest request = builder.build();
        // Check that size if not beyond the limit.
        if (request.getSerializedSize() > PatchSetGenerator.MAX_PATCHSET_SIZE) {
            return new DeltaInstallResult(DeltaInstallStatus.PATCH_SIZE_EXCEEDED);
        }

        // Send delta install request.
        Deploy.DeltaInstallResponse res;
        try {
            res = installer.deltaInstall(request);
        } catch (IOException e) {
            return new DeltaInstallResult(DeltaInstallStatus.UNKNOWN);
        }

        DeltaInstallStatus status =
                (res.getStatus() == Deploy.DeltaInstallResponse.Status.OK)
                        ? DeltaInstallStatus.SUCCESS
                        : DeltaInstallStatus.ERROR;
        return new DeltaInstallResult(status, res.getInstallOutput());
    }

    public static boolean canInherit(int apkCount, List<FileDiff> diff) {
        boolean inherit = apkCount > 1;
        if (inherit) {
            for (FileDiff fileDiff : diff) {
                if (fileDiff.oldFile.name.equals("AndroidManifest.xml")) {
                    inherit = false;
                }
            }
        }
        return inherit;
    }

    public static AdbClient.InstallResult parseInstallerResultErrorCode(String errorCode) {
        try {
            return new AdbClient.InstallResult(AdbClient.InstallStatus.valueOf(errorCode));
        } catch (Exception e) {
            return new AdbClient.InstallResult(
                    AdbClient.InstallStatus.UNKNOWN_ERROR, errorCode, null);
        }
    }

    private String message(AdbClient.InstallResult result) {
        switch (result.status) {
            case INSTALL_FAILED_VERSION_DOWNGRADE:
                return "The device already has a newer version of this application.";
            case DEVICE_NOT_RESPONDING:
                return "Device not responding.";
            case INSTALL_FAILED_UPDATE_INCOMPATIBLE:
            case INCONSISTENT_CERTIFICATES:
                return "The device already has an application with the same package but a different signature.";
            case INSTALL_FAILED_DEXOPT:
                return "The device might have stale dexed jars that don't match the current version (dexopt error).";
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
            case INSTALL_FAILED_USER_RESTRICTED:
                return "Installation via USB is disabled.";
            case INSTALL_FAILED_INVALID_APK:
                return "The APKs are invalid.";
            default:
                return "Installation failed due to: '" + result.reason + "'";
        }
    }
}
