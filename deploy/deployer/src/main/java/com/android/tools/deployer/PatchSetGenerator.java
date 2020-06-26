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
import com.android.tools.idea.protobuf.ByteString;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatchSetGenerator {

    // Maximum patchset size that can be pushed to the device to attempt a
    // delta push. This value was chosen based on how much RAM is likely to
    // be available on device as well as what percentage of a large app size
    // (80 MiB) it represent (50%).
    public static final int MAX_PATCHSET_SIZE = 40 * 1024 * 1024; // 40 MiB

    private ILogger logger;
    private final WhenNoChanges whenNoChanges;

    public enum WhenNoChanges {
        GENERATE_PATCH_ANYWAY, // This results in an apk patch containing the CD/EOCD.
        GENERATE_EMPTY_PATCH,
    }

    public PatchSetGenerator(WhenNoChanges whenNoChanges, ILogger logger) {
        this.logger = logger;
        this.whenNoChanges = whenNoChanges;
    }

    public PatchSet generateFromApks(List<Apk> localApks, List<Apk> remoteApks) {
        // Build the list of local apks.
        HashMap<String, Apk> localApkMap = new HashMap<>();
        for (Apk apk : localApks) {
            localApkMap.put(apk.name, apk);
        }

        // Build the list of remote apks.
        HashMap<String, Apk> remoteApkMap = new HashMap<>();
        for (Apk apk : remoteApks) {
            remoteApkMap.put(apk.name, apk);
        }
        return generateFromApkSets(remoteApkMap, localApkMap);
    }

    public PatchSet generateFromApkSets(
            HashMap<String, Apk> remoteApks, HashMap<String, Apk> localApks) {
        try {
            if (remoteApks.size() != localApks.size()) {
                return PatchSet.INVALID;
            }
            // Pair remote and local apks. Attempt to build an app delta.
            List<Pair<Apk, Apk>> pairs = new ArrayList<>();
            for (Map.Entry<String, Apk> localApk : localApks.entrySet()) {
                if (!remoteApks.keySet().contains(localApk.getValue().name)) {
                    return PatchSet.INVALID;
                }
                pairs.add(Pair.of(localApk.getValue(), remoteApks.get(localApk.getValue().name)));
            }
            return generateFromPairs(pairs);
        } catch (IOException e) {

        }

        return PatchSet.INVALID;
    }

    public PatchSet generateFromPairs(List<Pair<Apk, Apk>> pairs) throws IOException {
        ArrayList<Deploy.PatchInstruction> patches = new ArrayList<>();

        boolean noChanges = true;
        for (Pair<Apk, Apk> pair : pairs) {
            Apk localApk = pair.getFirst();
            Apk remoteApk = pair.getSecond();
            if (!remoteApk.checksum.equals(localApk.checksum)) {
                noChanges = false;
                break;
            }
        }

        // If nothing has changed, return an empty list of patches.
        if (noChanges && whenNoChanges == WhenNoChanges.GENERATE_EMPTY_PATCH) {
            return PatchSet.NO_CHANGES;
        }

        long patchSizes = 0;
        // Generate delta for each pairs.
        for (Pair<Apk, Apk> pair : pairs) {
            Apk localApk = pair.getFirst();
            Apk remoteApk = pair.getSecond();
            Deploy.PatchInstruction instruction = null;
            if (localApk.checksum.equals(remoteApk.checksum)) {
                // If the APKs are equal, generate a full clean patch instead of a delta which
                // will have holes due to "extra" fields and gaps between ZIP entries. This allows
                // to skip feeding the APK altogether on the device by using install-create -p.
                instruction = generateCleanPatch(remoteApk, localApk);
            } else {
                PatchGenerator.Patch patch =
                        new PatchGenerator(logger).generate(remoteApk, localApk);
                switch (patch.status) {
                    case SizeThresholdExceeded:
                        return PatchSet.SIZE_THRESHOLD_EXCEEDED;
                    case Ok:
                        break;
                    default:
                        throw new IllegalStateException("Unhandled PatchSet status");
                }
                instruction =
                        buildPatchInstruction(
                                patch.destinationSize,
                                patch.sourcePath,
                                patch.instructions,
                                patch.data);
            }

            patchSizes += instruction.getInstructions().size() + instruction.getPatches().size();
            if (patchSizes > MAX_PATCHSET_SIZE) {
                return PatchSet.SIZE_THRESHOLD_EXCEEDED;
            }

            patches.add(instruction);
        }
        assert pairs.size() == patches.size();
        return new PatchSet(patches);
    }

    private Deploy.PatchInstruction buildPatchInstruction(
            long size, String remotePath, ByteBuffer instruction, ByteBuffer data) {
        Deploy.PatchInstruction.Builder patchInstructionBuilder =
                Deploy.PatchInstruction.newBuilder();
        patchInstructionBuilder.setSrcAbsolutePath(remotePath);
        patchInstructionBuilder.setPatches(ByteString.copyFrom(data));
        patchInstructionBuilder.setInstructions(ByteString.copyFrom(instruction));
        patchInstructionBuilder.setDstFilesize(size);

        return patchInstructionBuilder.build();
    }

    private Deploy.PatchInstruction generateCleanPatch(Apk remoteApk, Apk localApk)
            throws IOException {
        Deploy.PatchInstruction.Builder patchInstructionBuilder =
                Deploy.PatchInstruction.newBuilder();

        PatchGenerator.Patch patch =
                new PatchGenerator(logger).generateCleanPatch(remoteApk, localApk);
        patchInstructionBuilder.setSrcAbsolutePath(patch.sourcePath);
        patchInstructionBuilder.setDstFilesize(patch.destinationSize);
        return patchInstructionBuilder.build();
    }
}
