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

import java.util.List;

public class ApkInstaller {

    private final AdbClient adb;
    private final UIService service;

    public ApkInstaller(AdbClient adb, UIService service) {
        this.adb = adb;
        this.service = service;
    }

    public void install(String packageName, List<String> apks, InstallOptions options)
            throws DeployerException {
        AdbClient.InstallResult result = adb.install(apks, options.getFlags());
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
                    result = adb.install(apks, options.getFlags());
                    message = message(result);
                }
                break;
            default:
                // Fall through
        }

        if (result != AdbClient.InstallResult.OK) {
            throw new DeployerException(DeployerException.Error.INSTALL_FAILED, result, message);
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
            default:
                return "Installation failed";
        }
    }
}
