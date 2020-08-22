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

import com.android.annotations.Trace;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.FileDiff;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApkPreInstaller {

    /**
     * Thrown for cases where we bail out of using delta install. This is distinct from the case
     * where the APK is unchanged and we don't need to install anything.
     */
    public static class DeltaPreInstallException extends Exception {
        public DeltaPreInstallException(String msg) {
            super(msg);
        }

        public DeltaPreInstallException(Exception e) {
            super(e);
        }
    }

    public static final String SKIPPED_INSTALLATION = "<SKIPPED-INSTALLATION>";

    private final AdbClient adb;
    private final Installer installer;
    private final ILogger logger;

    public ApkPreInstaller(AdbClient adb, Installer installer, ILogger logger) {
        this.adb = adb;
        this.installer = installer;
        this.logger = logger;
    }

    /**
     * Push local .apks files to a device.
     *
     * <p>First try to perform a fast "delta push" during which only the new parts of the apks are
     * transferred to the device. Fallback to standard "install-multiple" full pull if delta push
     * fails.
     *
     * @param remoteContent All APK entries on the device
     * @param localContent All APK entries on the local host
     * @return Session ID of the install session.
     */
    @Trace
    public String preinstall(
            ApplicationDumper.Dump remoteContent, List<Apk> localContent, List<FileDiff> diffs)
            throws DeployerException {
        // Build the list of local apks.
        HashMap<String, Apk> localApks = new HashMap<>();
        for (Apk file : localContent) {
            localApks.put(file.name, file);
        }

        // Build the list of remote apks.
        HashMap<String, Apk> remoteApks = new HashMap<>();
        for (Apk file : remoteContent.apks) {
            remoteApks.put(file.name, file);
        }

        // Make sure all apks have the same package name and extract it.
        String packageName = null;
        for (Apk apk : localApks.values()) {
            if (packageName == null) {
                packageName = apk.packageName;
            }
            if (!packageName.equals(apk.packageName)) {
                throw DeployerException.appIdChanged(packageName, apk.packageName);
            }
        }

        // Attempt a DeltaPreinstall first and fallback on a FullPreinstall if it fails.
        try {
            return deltaPreinstall(localApks, remoteApks, packageName, diffs);
        } catch (DeltaPreInstallException e) {
            return fullPreinstall(localApks, packageName);
        }
    }

    @Trace
    /** @return Session ID. Empty if all APKs are unchanged from the device. */
    private String deltaPreinstall(
            HashMap<String, Apk> localApks,
            HashMap<String, Apk> remoteApks,
            String packageName,
            List<FileDiff> diffs)
            throws DeltaPreInstallException {
        try {
            // Pair remote and local apks. Attempt to build an app delta.
            List<Pair<Apk, Apk>> pairs = new ArrayList<>();
            for (Map.Entry<String, Apk> localApk : localApks.entrySet()) {
                if (!remoteApks.keySet().contains(localApk.getValue().name)) {
                    throw new DeltaPreInstallException("APK names changed.");
                }
                pairs.add(Pair.of(localApk.getValue(), remoteApks.get(localApk.getValue().name)));
            }

            Deploy.InstallInfo.Builder pushRequestBuilder = Deploy.InstallInfo.newBuilder();
            PatchSet patchSet =
                    new PatchSetGenerator(
                                    PatchSetGenerator.WhenNoChanges.GENERATE_EMPTY_PATCH, logger)
                            .generateFromPairs(pairs);
            switch (patchSet.getStatus()) {
                case NoChanges:
                    return SKIPPED_INSTALLATION;
                case Invalid:
                    throw new DeltaPreInstallException("Unable to generate patch");
                case SizeThresholdExceeded:
                    throw new DeltaPreInstallException("Patches too big.");
                case Ok:
                    break;
                default:
                    throw new IllegalStateException("Unhandled PatchSet status");
            }

            List<Deploy.PatchInstruction> patches = patchSet.getPatches();
            for (Deploy.PatchInstruction patch : patches) {
                logger.info("Patch size %d", patch.getSerializedSize());
            }

            pushRequestBuilder.addAllPatchInstructions(patches);

            boolean inherit =
                    ApkInstaller.canInherit(localApks.size(), diffs, Deployer.InstallMode.DELTA);
            pushRequestBuilder.setInherit(inherit);
            pushRequestBuilder.setPackageName(packageName);

            // Send the deltaPreinstall request here.
            Deploy.InstallInfo info = pushRequestBuilder.build();
            Deploy.DeltaPreinstallResponse response = installer.deltaPreinstall(info);
            if (response.getStatus().equals(Deploy.DeltaStatus.OK)) {
                return response.getSessionId();
            } else {
                throw new DeltaPreInstallException("Failed to delta pre-install on device.");
            }
        } catch (IOException e) {
            logger.error(e, "Unable to deltaInstall");
            throw new DeltaPreInstallException(e);
        }
    }

    @Trace
    private String fullPreinstall(HashMap<String, Apk> fullApks, String packageName)
            throws DeployerException {

        long totalSize = 0;
        try {
            for (Apk apk : fullApks.values()) {
                totalSize += Files.size(Paths.get(apk.path));
            }
        } catch (IOException e) {
            throw DeployerException.preinstallFailed(e.getMessage());
        }

        String sessionId;
        try {
            List<String> installOptions =
                    Arrays.asList(
                            "package",
                            "install-create",
                            "-t",
                            "-r",
                            "--dont-kill",
                            "-S",
                            Long.toString(totalSize));
            String skipVerificationString = adb.getSkipVerificationOption(packageName);
            if (skipVerificationString != null) {
                installOptions.add(skipVerificationString);
            }
            byte[] rawResponse =
                    adb.binder(installOptions.toArray(new String[installOptions.size()]), null);
            // Parse result which should be in the form:
            // "Success: created install session [X]" where X is the session id.
            String stringResponse = new String(rawResponse, StandardCharsets.UTF_8);
            if (!stringResponse.startsWith("Success: created install session [")) {
                throw DeployerException.preinstallFailed(
                        "Unable to create session : " + stringResponse);
            }
            sessionId =
                    stringResponse.substring(
                            stringResponse.indexOf('[') + 1, stringResponse.indexOf(']'));
        } catch (IOException e) {
            throw DeployerException.preinstallFailed(e.getMessage());
        }

        for (Apk apk : fullApks.values()) {
            try {
                FileInputStream stream = new FileInputStream(new File(apk.path));
                long size = Files.size(Paths.get(apk.path));
                byte[] rawResponse =
                        adb.binder(
                                new String[] {
                                    "package",
                                    "install-write",
                                    "-S",
                                    Long.toString(size),
                                    sessionId,
                                    apk.name
                                },
                                stream);
                String stringResponse = new String(rawResponse, "UTF-8");
                if (!stringResponse.startsWith("Success")) {
                    adb.abortSession(sessionId);
                    throw DeployerException.preinstallFailed(stringResponse);
                }
            } catch (IOException e) {
                adb.abortSession(sessionId);
                throw DeployerException.preinstallFailed(e.getMessage());
            }
        }
        return sessionId;
    }
}
