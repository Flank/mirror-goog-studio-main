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

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.tracer.Trace;
import com.android.utils.Pair;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApkPusher {

    public static final String APK_DIRECTORY = Deployer.BASE_DIRECTORY + "/apks";

    private final AdbClient adb;
    private final Installer installer;

    public ApkPusher(AdbClient adb, Installer installer) {
        this.adb = adb;
        this.installer = installer;
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
    public List<String> push(List<ApkEntry> remoteContent, List<ApkEntry> localContent)
            throws DeployerException {
        try (Trace unused = Trace.begin("push")) {
            // Build the list of local apks.
            HashMap<String, Apk> localApks = new HashMap<>();
            for (ApkEntry file : localContent) {
                localApks.put(file.apk.name, file.apk);
            }

            // Build the list of remote apks.
            HashMap<String, Apk> remoteApks = new HashMap<>();
            for (ApkEntry file : remoteContent) {
                remoteApks.put(file.apk.name, file.apk);
            }

            // Attempt a delta push
            try {
                List<String> remoteApkLocations = deltaPush(localApks, remoteApks);
                if (!remoteApkLocations.isEmpty()) {
                    return remoteApkLocations;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Fall back on full push if delta push failed (happens if the delta push generated
            // a patch bigger than 40 MiB and aborted.
            return fullPush(localApks);
        }
    }

    private List<String> deltaPush(HashMap<String, Apk> localApks, HashMap<String, Apk> remoteApks)
            throws IOException {
        List<String> pushedApkLocations = new ArrayList<>();
        // Pair remote and local apks. Attempt to build an app delta.
        List<Pair<Apk, Apk>> pairs = new ArrayList<>();
        for (Map.Entry<String, Apk> localApk : localApks.entrySet()) {
            if (!remoteApks.keySet().contains(localApk.getValue().name)) {
                return pushedApkLocations;
            }
            pairs.add(Pair.of(localApk.getValue(), remoteApks.get(localApk.getValue().name)));
        }

        Deploy.DeltaPushRequest.Builder pushRequestBuilder = Deploy.DeltaPushRequest.newBuilder();
        if (!generateDeltas(pushRequestBuilder, pairs)) {
            return pushedApkLocations;
        }

        Deploy.DeltaPushRequest request = pushRequestBuilder.build();
        // Don't push more than 40 MiB delta since it has to fit in RAM on the device.
        if (request.getSerializedSize() > 40 * 1024 * 1024) {
            return pushedApkLocations;
        }

        // Send the deltaPush request here.
        Deploy.DeltaPushResponse response = installer.deltaPush(request);
        for (String path : response.getApksAbsolutePathsList()) {
            pushedApkLocations.add(path);
        }

        if (response.getStatus().equals(Deploy.DeltaPushResponse.Status.OK)) {
            return pushedApkLocations;
        } else {
            pushedApkLocations.clear();
            return pushedApkLocations;
        }
    }

    private boolean generateDeltas(
            Deploy.DeltaPushRequest.Builder pushRequestBuilder, List<Pair<Apk, Apk>> pairs)
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

    private Deploy.PatchInstruction buildPatchInstruction(
            long size, String remotePath, ByteBuffer instruction, ByteBuffer data) {
        Trace.begin("building proto request");

        Deploy.PatchInstruction.Builder patchInstructionBuidler =
                Deploy.PatchInstruction.newBuilder();
        patchInstructionBuidler.setSrcAbsolutePath(remotePath);
        patchInstructionBuidler.setPatches(ByteString.copyFrom(data));
        patchInstructionBuidler.setInstructions(ByteString.copyFrom(instruction));
        patchInstructionBuidler.setDstFilesize(size);

        Deploy.PatchInstruction patch = patchInstructionBuidler.build();

        Trace.end();
        return patch;
    }

    private Deploy.PatchInstruction generateDelta(Apk remoteApk, Apk localApk) throws IOException {
        PatchGenerator.Patch patch = new PatchGenerator().generate(remoteApk, localApk);
        return buildPatchInstruction(
                patch.destinationSize, patch.sourcePath, patch.instructions, patch.data);
    }

    private List<String> fullPush(HashMap<String, Apk> fullApks) throws DeployerException {
        try {
            List<String> apkPaths = new ArrayList<>();
            adb.shell(
                    new String[] {"rm", "-r", APK_DIRECTORY, ";", "mkdir", "-p", APK_DIRECTORY},
                    null);
            for (Apk apk : fullApks.values()) {
                String target = APK_DIRECTORY + "/" + Paths.get(apk.path).getFileName();
                adb.push(apk.path, target);
                apkPaths.add(target);
            }
            return apkPaths;
        } catch (IOException e) {
            throw new DeployerException(DeployerException.Error.ERROR_PUSHING_APK, e);
        }
    }
}
