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
import com.android.tools.deployer.model.DexClass;
import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;

/** An object that can perform swaps via an installer or custom redefiners. */
public class ApkSwapper {
    private final Installer installer;
    private final boolean restart;
    private final Map<Integer, ClassRedefiner> redefiners;

    /**
     * @param installer used to perform swaps on device.
     * @param restart whether to restart the application or not.
     * @param redefiners an additional set of redefiners that will handle the swap for the given
     *     process ids
     */
    public ApkSwapper(
            Installer installer, Map<Integer, ClassRedefiner> redefiners, boolean restart) {
        this.installer = installer;
        this.redefiners = redefiners;
        this.restart = restart;
    }

    /**
     * Performs the swap.
     *
     * @param dump the application dump
     * @param sessionId the installation session
     * @param toSwap the actual dex classes to swap.
     */
    public boolean swap(ApplicationDumper.Dump dump, String sessionId, List<DexClass> toSwap)
            throws DeployerException {
        // The application dump contains a map of [package name --> process ids]. If there are no
        // packages with running processes, the swap cannot be performed.
        if (dump.packagePids.isEmpty()) {
            throw new DeployerException(
                    DeployerException.Error.DUMP_UNKNOWN_PACKAGE,
                    "Cannot list processes for package. Is the app running?");
        }

        // The native installer can't handle swapping more than one package at a time. The dump does
        // not enforce this limitation, so we need to check here to make sure we haven't been given
        // multiple applications to swap. This could happen if an instrumentation package targets
        // multiple other packages; we may elect to fix this limitation in the future.
        if (dump.packagePids.size() > 1) {
            throw new DeployerException(
                    DeployerException.Error.REDEFINER_ERROR, "Cannot swap multiple packages");
        }

        Deploy.SwapRequest request = buildSwapRequest(dump, sessionId, toSwap);

        // TODO: If multiple processes have a debugger attached, we'll do extra swaps. Fix?
        sendSwapRequest(request, new InstallerBasedClassRedefiner(installer));
        for (Map.Entry<Integer, ClassRedefiner> entry : redefiners.entrySet()) {
            sendSwapRequest(request, entry.getValue());
        }

        return true;
    }

    private Deploy.SwapRequest buildSwapRequest(
            ApplicationDumper.Dump dump, String sessionId, List<DexClass> toSwap) {
        Map.Entry<String, List<Integer>> onlyPackage =
                Iterables.getOnlyElement(dump.packagePids.entrySet());

        Deploy.SwapRequest.Builder request =
                Deploy.SwapRequest.newBuilder()
                        .setPackageName(onlyPackage.getKey())
                        .setRestartActivity(restart)
                        .setSessionId(sessionId);

        for (DexClass clazz : toSwap) {
            request.addClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clazz.name)
                            .setDex(ByteString.copyFrom(clazz.code)));
        }

        for (Integer pid : onlyPackage.getValue()) {
            if (redefiners.containsKey(pid)) {
                continue;
            }
            request.addProcessIds(pid);
        }

        return request.build();
    }

    private static void sendSwapRequest(Deploy.SwapRequest request, ClassRedefiner redefiner)
            throws DeployerException {
        Deploy.SwapResponse swapResponse = redefiner.redefine(request);
        if (swapResponse.getStatus() != Deploy.SwapResponse.Status.OK) {
            if (swapResponse.getJvmtiErrorCodeCount() == 0) {
                throw new DeployerException(
                        DeployerException.Error.REDEFINER_ERROR, "Redefiner Error");
            } else {
                throw new JvmtiRedefinerException(swapResponse.getJvmtiErrorCodeList());
            }
        }
    }
}
