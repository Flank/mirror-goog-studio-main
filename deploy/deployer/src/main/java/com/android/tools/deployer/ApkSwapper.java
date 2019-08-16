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
import com.android.tools.idea.protobuf.ByteString;
import com.google.common.collect.Iterables;
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
    public boolean swap(
            ApplicationDumper.Dump dump,
            String sessionId,
            DexComparator.ChangedClasses changedClasses)
            throws DeployerException {

        // The application dump contains a map of [package name --> process ids]. If there are no
        // packages with running processes, the swap cannot be performed.
        if (dump.packagePids.isEmpty()) {
            throw DeployerException.unknownProcess();
        }

        // The native installer can't handle swapping more than one package at a time. The dump does
        // not enforce this limitation, so we need to check here to make sure we haven't been given
        // multiple applications to swap. This could happen if an instrumentation package targets
        // multiple other packages; we may elect to fix this limitation in the future.
        if (dump.packagePids.size() > 1) {
            throw DeployerException.swapMultiplePackages();
        }

        // TODO: Add a new installer command? Add a new flag?
        Deploy.SwapRequest swapRequest = buildSwapRequest(dump, sessionId, changedClasses);
        boolean needAgents = isSwapRequestInstallOnly(swapRequest);
        Thread t = null;

        // A hack to get around lambda capture having to be effectively final. This single item array is captured by the lambda but we
        // can still set its content should and exception occurs.
        DeployerException[] exceptions = new DeployerException[1];

        if (needAgents) {
            t =
                    new Thread(
                            () -> {
                                try {
                                    sendSwapRequest(
                                            swapRequest,
                                            new InstallerBasedClassRedefiner(installer));
                                } catch (DeployerException e) {
                                    exceptions[0] = e;
                                }
                            });
            t.start();
        }

        // Do the debugger swap.
        for (Map.Entry<Integer, ClassRedefiner> entry : redefiners.entrySet()) {
            sendSwapRequest(swapRequest, entry.getValue());
        }

        // Wait for installer to come back.
        if (t != null) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw DeployerException.interrupted(e.getMessage());
            }
            if (exceptions[0] != null) {
                throw exceptions[0];
            }
        } else {
            // We didn't start the installer request before since it will always succeed. Now that debugger swap is done,
            // we can commit the install.
            sendSwapRequest(swapRequest, new InstallerBasedClassRedefiner(installer));
        }
        return true;
    }

    private Deploy.SwapRequest buildSwapRequest(
            ApplicationDumper.Dump dump,
            String sessionId,
            DexComparator.ChangedClasses changedClasses)
            throws DeployerException {
        Map.Entry<String, List<Integer>> onlyPackage =
                Iterables.getOnlyElement(dump.packagePids.entrySet());

        Deploy.SwapRequest.Builder request =
                Deploy.SwapRequest.newBuilder()
                        .setPackageName(onlyPackage.getKey())
                        .setRestartActivity(restart)
                        .setSessionId(sessionId);

        for (DexClass clazz : changedClasses.newClasses) {
            request.addNewClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clazz.name)
                            .setDex(ByteString.copyFrom(clazz.code)));
        }

        for (DexClass clazz : changedClasses.modifiedClasses) {
            request.addModifiedClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clazz.name)
                            .setDex(ByteString.copyFrom(clazz.code)));
        }

        int extraAgents = 0;
        for (Integer pid : onlyPackage.getValue()) {
            if (redefiners.containsKey(pid)) {
                ClassRedefiner redefiner = redefiners.get(pid);
                switch (redefiner.canRedefineClass().support) {
                    case FULL:
                        continue;
                    case NEEDS_AGENT_SERVER:
                        extraAgents++;
                        continue;
                    case MAIN_THREAD_RUNNING:
                        request.addProcessIds(pid);
                        continue;
                    case NONE:
                        throw DeployerException.operationNotSupported(
                                "The redefiner is not able to swap the current state of the debug application. "
                                        + "All available threads are suspended but not on a breakpoint.");
                }
            } else {
                request.addProcessIds(pid);
            }
        }

        request.setExtraAgents(extraAgents);
        request.setArch(dump.arch);
        return request.build();
    }

    /**
     * Check if the swap request expects zero agents to talk to the agent server.
     *
     * <p>Such swap request will always succeed and, therefore, always install the APK.
     */
    private boolean isSwapRequestInstallOnly(Deploy.SwapRequest request) {
        return request.getProcessIdsCount() > 0 || request.getExtraAgents() > 0;
    }

    private static void sendSwapRequest(Deploy.SwapRequest request, ClassRedefiner redefiner)
            throws DeployerException {
        Deploy.SwapResponse swapResponse = redefiner.redefine(request);
        new InstallerResponseHandler().handle(swapResponse);
    }
}
