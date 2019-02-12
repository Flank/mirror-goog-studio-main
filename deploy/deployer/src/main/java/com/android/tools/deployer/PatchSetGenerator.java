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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.utils.Pair;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatchSetGenerator {

    public List<Deploy.PatchInstruction> generateFromEntries(
            List<ApkEntry> localEntries, List<ApkEntry> remoteEntries) {
        // Build the list of local apks.
        HashMap<String, Apk> localApks = new HashMap<>();
        for (ApkEntry file : localEntries) {
            localApks.put(file.apk.name, file.apk);
        }

        // Build the list of remote apks.
        HashMap<String, Apk> remoteApks = new HashMap<>();
        for (ApkEntry file : remoteEntries) {
            remoteApks.put(file.apk.name, file.apk);
        }
        return generateFromApkSets(remoteApks, localApks);
    }

    public List<Deploy.PatchInstruction> generateFromApkSets(
            HashMap<String, Apk> remoteApks, HashMap<String, Apk> localApks) {
        try {
            // Pair remote and local apks. Attempt to build an app delta.
            List<Pair<Apk, Apk>> pairs = new ArrayList<>();
            for (Map.Entry<String, Apk> localApk : localApks.entrySet()) {
                if (!remoteApks.keySet().contains(localApk.getValue().name)) {
                    return null;
                }
                pairs.add(Pair.of(localApk.getValue(), remoteApks.get(localApk.getValue().name)));
            }
            return generateFromPairs(pairs);
        } catch (IOException e) {

        }

        return null;
    }

    public List<Deploy.PatchInstruction> generateFromPairs(List<Pair<Apk, Apk>> pairs)
            throws IOException {
        ArrayList<Deploy.PatchInstruction> patches = new ArrayList<>();

        // Generate delta for each pairs.
        for (Pair<Apk, Apk> pair : pairs) {
            Apk localApk = pair.getFirst();
            Apk remoteApk = pair.getSecond();
            if (remoteApk.checksum.equals(localApk.checksum)) {
                continue;
            }
            Deploy.PatchInstruction instruction = generateDelta(remoteApk, localApk);
            patches.add(instruction);
        }
        return patches;
    }

    private Deploy.PatchInstruction generateDelta(Apk remoteApk, Apk localApk) throws IOException {
        PatchGenerator.Patch patch = new PatchGenerator().generate(remoteApk, localApk);
        return buildPatchInstruction(
                patch.destinationSize, patch.sourcePath, patch.instructions, patch.data);
    }

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
}
