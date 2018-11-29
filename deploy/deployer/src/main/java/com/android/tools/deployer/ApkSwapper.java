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
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.google.protobuf.ByteString;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An object that can perform swaps via an installer or custom redefiners. */
public class ApkSwapper {
    private final Installer installer;
    private final String packageName;
    private final boolean restart;
    private final Map<Integer, ClassRedefiner> redefiners;

    /**
     * @param installer used to perform swaps on device.
     * @param packageName the name of the package to swap
     * @param restart whether to restart the application or not.
     * @param redefiners an additional set of redefiners that will swap the given pid's.
     */
    public ApkSwapper(
            Installer installer,
            String packageName,
            boolean restart,
            Map<Integer, ClassRedefiner> redefiners) {
        this.installer = installer;
        this.packageName = packageName;
        this.restart = restart;
        this.redefiners = redefiners;
    }

    /**
     * Performs the swap.
     *
     * @param newFiles the new files, used to determine the process names.
     * @param apkPaths the paths where the new apk's are already on device.
     * @param toSwap the actual dex classes to swap.
     */
    public boolean swap(List<ApkEntry> newFiles, String sessionId, List<DexClass> toSwap)
            throws DeployerException {
        // Builds the Request Protocol Buffer.
        Deploy.SwapRequest request =
                buildSwapRequest(
                        packageName, restart, sessionId, newFiles, toSwap, redefiners.keySet());

        // Send Request to agent
        ClassRedefiner redefiner = new InstallerBasedClassRedefiner(installer);
        sendSwapRequest(request, redefiner);

        // Send requests to the alternative redefiners
        for (ClassRedefiner r : redefiners.values()) {
            sendSwapRequest(request, r);
        }
        return true;
    }

    private static Deploy.SwapRequest buildSwapRequest(
            String packageName,
            boolean restart,
            String sessionId,
            List<ApkEntry> newFiles,
            List<DexClass> classes,
            Collection<Integer> pids) {
        Deploy.SwapRequest.Builder request = Deploy.SwapRequest.newBuilder();
        request.setPackageName(packageName);
        request.setRestartActivity(restart);
        for (DexClass clz : classes) {
            request.addClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clz.name)
                            .setDex(ByteString.copyFrom(clz.code)));
        }
        // Obtain the process names from the local apks
        Set<String> processNames = new HashSet<>();
        for (ApkEntry file : newFiles) {
            processNames.addAll(file.apk.processes);
        }
        request.setSessionId(sessionId);
        request.addAllProcessNames(processNames);
        request.addAllSkipProcessIds(pids);
        return request.build();
    }

    private static void sendSwapRequest(Deploy.SwapRequest request, ClassRedefiner redefiner)
            throws DeployerException {
        Deploy.SwapResponse swapResponse = redefiner.redefine(request);
        if (swapResponse.getStatus() != Deploy.SwapResponse.Status.OK) {
            throw new DeployerException(DeployerException.Error.REDEFINER_ERROR, "Swap failed");
        }
    }
}
