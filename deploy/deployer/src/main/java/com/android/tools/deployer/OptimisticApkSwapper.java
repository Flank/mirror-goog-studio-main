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

import static com.android.tools.deployer.InstallerResponseHandler.RedefinitionCapability;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.InstallerResponseHandler.SuccessStatus;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.android.tools.idea.protobuf.ByteString;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
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
    private final MetricsRecorder metrics;
    private final DeployerOption options;

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
            DeployerOption options,
            MetricsRecorder metrics) {
        this.installer = installer;
        this.redefiners = redefiners;
        this.restart = restart;
        this.options = options;
        this.metrics = metrics;
    }

    /**
     * Performs a swap with hopeful optimism.
     *
     * @param dump the application dump
     * @param sessionId the installation session
     * @param toSwap the actual dex classes to swap.
     */
    public SwapResult optimisticSwap(
            String packageId, List<Integer> pids, Deploy.Arch arch, OverlayUpdate overlayUpdate)
            throws DeployerException {
        final DeploymentCacheDatabase.Entry cachedDump = overlayUpdate.cachedDump;
        final DexComparator.ChangedClasses dexOverlays = overlayUpdate.dexOverlays;
        final Map<ApkEntry, ByteString> fileOverlays = overlayUpdate.fileOverlays;

        OverlayId.Builder overlayIdBuilder = OverlayId.builder(cachedDump.getOverlayId());

        OverlayId expectedOverlayId = cachedDump.getOverlayId();
        Deploy.OverlaySwapRequest.Builder request =
                Deploy.OverlaySwapRequest.newBuilder()
                        .setPackageName(packageId)
                        .setRestartActivity(restart)
                        .setArch(arch)
                        .setExpectedOverlayId(
                                expectedOverlayId.isBaseInstall() ? "" : expectedOverlayId.getSha())
                        .setAlwaysUpdateOverlay(options.fastRestartOnSwapFail);

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
            String file = String.format(Locale.US, "%s.dex", clazz.name);
            overlayIdBuilder.addOverlayFile(file, clazz.checksum);
        }

        for (DexClass clazz : dexOverlays.modifiedClasses) {
            request.addModifiedClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clazz.name)
                            .setDex(ByteString.copyFrom(clazz.code))
                            .addAllFields(clazz.variableStates));
            String file = String.format(Locale.US, "%s.dex", clazz.name);
            overlayIdBuilder.addOverlayFile(file, clazz.checksum);
        }

        for (Map.Entry<ApkEntry, ByteString> entry : fileOverlays.entrySet()) {
            request.addResourceOverlays(
                    Deploy.OverlayFile.newBuilder()
                            .setPath(entry.getKey().getQualifiedPath())
                            .setContent(entry.getValue()));
            overlayIdBuilder.addOverlayFile(
                    entry.getKey().getQualifiedPath(), entry.getKey().getChecksum());
        }

        request.setStructuralRedefinition(options.useStructuralRedefinition);
        request.setVariableReinitialization(options.useVariableReinitialization);

        OverlayId overlayId = overlayIdBuilder.build();
        request.setOverlayId(overlayId.getSha());

        Deploy.OverlaySwapRequest swapRequest = request.build();

        SuccessStatus successStatus = SuccessStatus.OK;

        if (hasDebuggerAttached) {
            // Anytime we have an non-Installer based redefiner, it is not going to do any OID
            // verification.
            // Therefore we are going to do a manual OID check first. Here is the order:
            //
            //  1. Do a DUMP like verification on the app's current OID.
            //  2. Perform debugger swap.
            //  3. Perform overlay swap that installs overlays as well as swapping
            //     processes that are not attached to d a debugger.
            //
            // Failure at any step would short circuit the whole operation. The only issue that
            // might occur is when the debugger swap succeeds and somehow the overlay install
            // fails. We would have the running app be swapped but nothing installed. This is
            // also the case in the non-optimistic case and user would get an error message as
            // well.
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

            // Given the installer verification succeeded, we are targeting the right
            // device with the right APK in cache. We then proceed to do the debugger swaps.
            // If the debugger swap failed but the fallback flag is set, the redefiner will
            // signal the caller with SWAP_FAILED_BUT_OVERLAY_UPDATED.
            for (Map.Entry<Integer, ClassRedefiner> entry : redefiners.entrySet()) {
                switch (sendSwapRequest(swapRequest, entry.getValue())) {
                    case SWAP_FAILED_BUT_APP_UPDATED:
                        successStatus = SuccessStatus.SWAP_FAILED_BUT_APP_UPDATED;
                        break;
                    case OK:
                        break;
                    default:
                        throw new IllegalStateException("Unknown swap status");
                }
            }
        }

        // Do the installer swap.
        switch (sendSwapRequest(swapRequest, new InstallerBasedClassRedefiner(installer))) {
            case SWAP_FAILED_BUT_APP_UPDATED:
                successStatus = SuccessStatus.SWAP_FAILED_BUT_APP_UPDATED;
                break;
            case OK:
                break;
            default:
                throw new IllegalStateException("Unknown swap status");
        }

        return new SwapResult(overlayId, successStatus == SuccessStatus.OK);
    }

    private SuccessStatus sendSwapRequest(
            Deploy.OverlaySwapRequest request, ClassRedefiner redefiner) throws DeployerException {
        Deploy.SwapResponse swapResponse = redefiner.redefine(request);
        metrics.add(swapResponse.getAgentLogsList());
        return new InstallerResponseHandler(
                        options.useStructuralRedefinition
                                ? RedefinitionCapability.ALLOW_ADD_FIELD
                                : RedefinitionCapability.MOFIFY_CODE_ONLY)
                .handle(swapResponse);
    }

    public static class SwapResult {
        public final OverlayId overlayId;
        public final boolean hotswapSucceeded;

        private SwapResult(OverlayId overlayId, boolean hotswapSucceeded) {
            this.overlayId = overlayId;
            this.hotswapSucceeded = hotswapSucceeded;
        }
    }
}
