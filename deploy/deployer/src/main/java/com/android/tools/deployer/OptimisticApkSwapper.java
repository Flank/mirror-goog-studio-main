/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.protobuf.ByteString;
import com.android.utils.ILogger;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** An object that can perform swaps via an installer or custom redefiners. */
public class OptimisticApkSwapper {

    public static final class OverlayUpdate {
        private final DeploymentCacheDatabase.Entry cachedDump;
        private final DexComparator.ChangedClasses dexOverlays;
        private final Map<ApkEntry, ByteString> fileOverlays;

        public OverlayUpdate(
                DeploymentCacheDatabase.Entry cachedDump,
                DexComparator.ChangedClasses dexOverlays,
                Map<ApkEntry, ByteString> fileOverlays) {
            this.cachedDump = cachedDump;
            this.dexOverlays = dexOverlays;
            this.fileOverlays = fileOverlays;
        }
    }

    private final Installer installer;
    private final boolean restart;
    private final Map<Integer, ClassRedefiner> redefiners;
    private final AdbClient adb;
    private final ILogger logger;

    // Temp flag.
    private final boolean useStructuralRedefinition;
    private final boolean useVariableReinitialization;

    /**
     * @param installer used to perform swaps on device.
     * @param restart whether to restart the application or not.
     * @param redefiners an additional set of redefiners that will handle the swap for the given
     *     process ids
     */
    public OptimisticApkSwapper(
            Installer installer,
            Map<Integer, ClassRedefiner> redefiners,
            boolean restart,
            boolean useStructuralRedefinition,
            boolean useVariableReinitialization,
            AdbClient adb,
            ILogger logger) {
        this.installer = installer;
        this.redefiners = redefiners;
        this.restart = restart;
        this.useStructuralRedefinition = useStructuralRedefinition;
        this.useVariableReinitialization = useVariableReinitialization;
        this.adb = adb;
        this.logger = logger;
    }

    /**
     * Performs a swap with hopeful optimism.
     *
     * @param dump the application dump
     * @param sessionId the installation session
     * @param toSwap the actual dex classes to swap.
     */
    public OverlayId optimisticSwap(
            String packageId, List<Integer> pids, Deploy.Arch arch, OverlayUpdate overlayUpdate)
            throws DeployerException {
        final DeploymentCacheDatabase.Entry cachedDump = overlayUpdate.cachedDump;
        final DexComparator.ChangedClasses dexOverlays = overlayUpdate.dexOverlays;
        final Map<ApkEntry, ByteString> fileOverlays = overlayUpdate.fileOverlays;

        OverlayId nextOverlayId =
                new OverlayId(cachedDump.getOverlayId(), dexOverlays, fileOverlays.keySet());

        OverlayId expectedOverlayId = cachedDump.getOverlayId();
        Deploy.OverlaySwapRequest.Builder request =
                Deploy.OverlaySwapRequest.newBuilder()
                        .setPackageName(packageId)
                        .setRestartActivity(restart)
                        .setArch(arch)
                        .setExpectedOverlayId(
                                expectedOverlayId.isBaseInstall() ? "" : expectedOverlayId.getSha())
                        .setOverlayId(nextOverlayId.getSha());

        boolean hasDebuggerAttached = false;
        for (Integer pid : pids) {
            if (redefiners.containsKey(pid)) {
                ClassRedefiner redefiner = redefiners.get(pid);
                if (redefiner.canRedefineClass().support
                        != ClassRedefiner.RedefineClassSupport.FULL) {
                    throw new IllegalArgumentException(
                            "R+ Device should have FULL debugger swap support");
                }
                hasDebuggerAttached = true;
            } else {
                request.addProcessIds(pid);
            }
        }

        for (DexClass clazz : dexOverlays.newClasses) {
            request.addNewClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clazz.name)
                            .setDex(ByteString.copyFrom(clazz.code)));
        }

        for (DexClass clazz : dexOverlays.modifiedClasses) {
            request.addModifiedClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clazz.name)
                            .setDex(ByteString.copyFrom(clazz.code))
                            .addAllFields(clazz.variableStates));
        }

        for (Map.Entry<ApkEntry, ByteString> entry : fileOverlays.entrySet()) {
            request.addResourceOverlays(
                    Deploy.OverlayFile.newBuilder()
                            .setPath(entry.getKey().getQualifiedPath())
                            .setContent(entry.getValue()));
        }

        request.setStructuralRedefinition(useStructuralRedefinition);
        request.setVariableReinitialization(useVariableReinitialization);

        Deploy.OverlaySwapRequest swapRequest = request.build();

        if (hasDebuggerAttached) {
            // Anytime we have an non-Installer based redefiner, it is not going to do any OID
            // verification.
            // Therefore we are going to do a manual OID check first. Here is the order:

            //  1. Do a DUMP like verification on the app's current OID.
            //  2. Perform debugger swap
            //  3. Perform overlay swap that installs overlays as well as swapping
            //     processes that are not attached to d a debugger.
            //
            // Failure at any step would short circuit the whole operation. The only issue that
            // might
            // occur is when the debugger swap succeed and somehow the overlay install fails. We
            // would
            // have the running app be swapped but nothing installed. This is also the case in the
            // non-optimistic case and user would get an error message as well.
            try {
                Deploy.OverlayIdPushResponse response =
                        installer.verifyOverlayId(
                                request.getPackageName(), request.getExpectedOverlayId());
                if (response.getStatus() != Deploy.OverlayIdPushResponse.Status.OK) {
                    throw DeployerException.overlayIdMismatch();
                }
            } catch (IOException e) {
                throw DeployerException.installerIoException(e);
            }

            // Given the installer swap succeeded, we are targeting the right
            // device with the right APK in cache. We then proceed to do the debugger swaps.
            for (Map.Entry<Integer, ClassRedefiner> entry : redefiners.entrySet()) {
                sendSwapRequest(swapRequest, entry.getValue());
            }
        }

        // Do the installer swap first.
        sendSwapRequest(swapRequest, new InstallerBasedClassRedefiner(installer));

        return nextOverlayId;
    }

    private void sendSwapRequest(Deploy.OverlaySwapRequest request, ClassRedefiner redefiner)
            throws DeployerException {
        Deploy.SwapResponse swapResponse = redefiner.redefine(request);
        for (Deploy.AgentExceptionLog log : swapResponse.getAgentLogsList()) {
            // TODO: Report these as metrics somehow
            logger.info("Agent log: %s", log.toString());
        }
        new InstallerResponseHandler().handle(swapResponse);
    }
}
