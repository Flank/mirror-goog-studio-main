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
import com.android.tools.deployer.model.ApkEntry;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApkPreInstaller {

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
     * @return Paths of the apks pushed on the device
     */
    @Trace
    public String preinstall(ApplicationDumper.Dump remoteContent, List<ApkEntry> localContent)
            throws DeployerException {
        String sessionId = "";

        // Build the list of local apks.
        HashMap<String, Apk> localApks = new HashMap<>();
        for (ApkEntry file : localContent) {
            localApks.put(file.apk.name, file.apk);
        }

        // Build the list of remote apks.
        HashMap<String, Apk> remoteApks = new HashMap<>();
        for (ApkEntry file : remoteContent.apkEntries) {
            remoteApks.put(file.apk.name, file.apk);
        }

        // Attempt a DeltaPreinstall first and fallback on a FullPreinstall if it fails.
        sessionId = deltaPreinstall(localApks, remoteApks);
        if (sessionId.isEmpty()) {
            return fullPreinstall(localApks);
        } else {
            return sessionId;
        }
    }

    @Trace
    private String deltaPreinstall(
            HashMap<String, Apk> localApks, HashMap<String, Apk> remoteApks) {
        try {
            // Pair remote and local apks. Attempt to build an app delta.
            List<Pair<Apk, Apk>> pairs = new ArrayList<>();
            for (Map.Entry<String, Apk> localApk : localApks.entrySet()) {
                if (!remoteApks.keySet().contains(localApk.getValue().name)) {
                    return "";
                }
                pairs.add(Pair.of(localApk.getValue(), remoteApks.get(localApk.getValue().name)));
            }

            Deploy.DeltaPreinstallRequest.Builder pushRequestBuilder =
                    Deploy.DeltaPreinstallRequest.newBuilder();
            if (!generateDeltas(pushRequestBuilder, pairs)) {
                return "";
            }

            Deploy.DeltaPreinstallRequest request = pushRequestBuilder.build();
            // Don't push more than 40 MiB delta since it has to fit in RAM on the device.
            if (request.getSerializedSize() > 40 * 1024 * 1024) {
                return "";
            }

            // Send the deltaPreinstall request here.
            Deploy.DeltaPreinstallResponse response = installer.deltaPreinstall(request);
            if (response.getStatus().equals(Deploy.DeltaPreinstallResponse.Status.OK)) {
                return response.getSessionId();
            } else {
                return "";
            }
        } catch (IOException e) {
            logger.error(e, "Unable to deltaInstall");
            return "";
        }
    }

    private boolean generateDeltas(
            Deploy.DeltaPreinstallRequest.Builder pushRequestBuilder, List<Pair<Apk, Apk>> pairs)
            throws IOException {
        // Generate delta for each pairs.
        for (Pair<Apk, Apk> pair : pairs) {
            Apk localApk = pair.getFirst();
            Apk remoteApk = pair.getSecond();
            Deploy.PatchInstruction instruction = generateDelta(remoteApk, localApk);
            pushRequestBuilder.addPatchInstructions(instruction);
        }
        return true;
    }

    @Trace
    private Deploy.PatchInstruction buildPatchInstruction(
            long size, String remotePath, ByteBuffer instruction, ByteBuffer data) {
        Deploy.PatchInstruction.Builder patchInstructionBuidler =
                Deploy.PatchInstruction.newBuilder();
        patchInstructionBuidler.setSrcAbsolutePath(remotePath);
        patchInstructionBuidler.setPatches(ByteString.copyFrom(data));
        patchInstructionBuidler.setInstructions(ByteString.copyFrom(instruction));
        patchInstructionBuidler.setDstFilesize(size);

        Deploy.PatchInstruction patch = patchInstructionBuidler.build();
        return patch;
    }

    private Deploy.PatchInstruction generateDelta(Apk remoteApk, Apk localApk) throws IOException {
        PatchGenerator.Patch patch = new PatchGenerator().generate(remoteApk, localApk);
        return buildPatchInstruction(
                patch.destinationSize, patch.sourcePath, patch.instructions, patch.data);
    }

    @Trace
    private String fullPreinstall(HashMap<String, Apk> fullApks) throws DeployerException {

        long totalSize = 0;
        try {
            for (Apk apk : fullApks.values()) {
                totalSize += Files.size(Paths.get(apk.path));
            }
        } catch (IOException e) {
            throw new DeployerException(DeployerException.Error.UNABLE_TO_PREINSTALL, e);
        }

        String sessionId;
        try {
            byte[] rawResponse =
                    adb.shell(
                            new String[] {
                                "cmd",
                                "package",
                                "install-create",
                                "-t",
                                "-r",
                                "--dont-kill",
                                "-S",
                                Long.toString(totalSize)
                            },
                            null);
            // Parse result which should be in the form:
            // "Success: created install session [X]" where X is the session id.
            String stringResponse = new String(rawResponse, "UTF-8");
            if (!stringResponse.startsWith("Success: created install session [")) {
                throw new DeployerException(
                        DeployerException.Error.UNABLE_TO_PREINSTALL,
                        "Unable to create session : " + stringResponse);
            }
            sessionId =
                    stringResponse.substring(
                            stringResponse.indexOf('[') + 1, stringResponse.indexOf(']'));
        } catch (IOException e) {
            throw new DeployerException(DeployerException.Error.UNABLE_TO_PREINSTALL, e);
        }

        for (Apk apk : fullApks.values()) {
            try {
                FileInputStream stream = new FileInputStream(new File(apk.path));
                long size = Files.size(Paths.get(apk.path));
                byte[] rawResponse =
                        adb.shell(
                                new String[] {
                                    "cmd",
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
                    throw new DeployerException(
                            DeployerException.Error.UNABLE_TO_PREINSTALL, stringResponse);
                }
            } catch (IOException e) {
                throw new DeployerException(DeployerException.Error.UNABLE_TO_PREINSTALL, e);
            }
        }
        return sessionId;
    }
}
