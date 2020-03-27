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
import com.android.tools.deployer.model.ApkEntryContent;
import com.android.tools.deployer.model.DexClass;
import com.android.tools.idea.protobuf.ByteString;
import com.android.utils.ILogger;
import java.util.List;
import java.util.Map;

/** An object that can perform swaps via an installer or custom redefiners. */
public class OptimisticApkSwapper {

    public static final class SwapData {
        public final OverlayId expectedOverlayId;
        public final OverlayId overlayId;
        public final DexComparator.ChangedClasses dexOverlays;
        public final List<ApkEntryContent> fileOverlays;

        public SwapData(
                OverlayId expectedOverlayId,
                DexComparator.ChangedClasses dexOverlays,
                List<ApkEntryContent> fileOverlays)
                throws DeployerException {
            this.expectedOverlayId = expectedOverlayId;
            this.overlayId = new OverlayId(expectedOverlayId, dexOverlays, fileOverlays);
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
            AdbClient adb,
            ILogger logger) {
        this.installer = installer;
        this.redefiners = redefiners;
        this.restart = restart;
        this.useStructuralRedefinition = useStructuralRedefinition;
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
    public boolean optimisticSwap(
            String packageId, List<Integer> pids, Deploy.Arch arch, SwapData swapData)
            throws DeployerException {
        Deploy.OverlaySwapRequest.Builder request =
                Deploy.OverlaySwapRequest.newBuilder()
                        .setPackageName(packageId)
                        .setRestartActivity(restart)
                        .addAllProcessIds(pids)
                        .setArch(arch)
                        .setExpectedOverlayId(swapData.expectedOverlayId.getSha())
                        .setOverlayId(swapData.overlayId.getSha());

        for (DexClass clazz : swapData.dexOverlays.newClasses) {
            request.addNewClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clazz.name)
                            .setDex(ByteString.copyFrom(clazz.code)));
        }

        for (DexClass clazz : swapData.dexOverlays.modifiedClasses) {
            request.addModifiedClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(clazz.name)
                            .setDex(ByteString.copyFrom(clazz.code))
                            .addAllFields(clazz.variableStates));
        }

        for (ApkEntryContent file : swapData.fileOverlays) {
            request.addResourceOverlays(
                    Deploy.OverlayFile.newBuilder()
                            .setPath(file.getName())
                            .setContent(file.getContent()));
        }

        request.setStructuralRedefinition(useStructuralRedefinition);

        // TODO: Debugger stuff. Given that this will be R+ we don't need the complicated workaround
        // the JDI bug in O.

        sendSwapRequest(request.build(), new InstallerBasedClassRedefiner(installer));
        return true;
    }

    private static void sendSwapRequest(Deploy.OverlaySwapRequest request, ClassRedefiner redefiner)
            throws DeployerException {
        Deploy.SwapResponse swapResponse = redefiner.redefine(request);
        new InstallerResponseHandler().handle(swapResponse);
    }
}
