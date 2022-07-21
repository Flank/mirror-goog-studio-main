/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.FileDiff;
import com.android.utils.ILogger;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/*
To support mobile install, the studio deployer implements the existing
mobile install deployment strategy of using root privileges to directly
modify APKs in the /data/app folder instead of using the package
manager.
 */
class RootPushApkInstaller {
    private final AdbClient adb;
    private final Installer installer;
    private final ILogger logger;

    public RootPushApkInstaller(AdbClient adb, Installer installer, ILogger logger) {
        this.adb = adb;
        this.installer = installer;
        this.logger = logger;
    }

    public boolean install(String packageName, List<Apk> apks) {
        if (!adb.getVersion()
                .isGreaterOrEqualThan(AndroidVersion.BINDER_CMD_AVAILABLE.getApiLevel())) {
            logger.warning("RootPush: CMD service not available on target device");
            return false;
        }

        // Apk push installation requires root to overwrite files in the /data/app directory.
        try {
            if (!adb.getDevice().isRoot() && !adb.getDevice().root()) {
                logger.warning("RootPush: Could not elevate to root");
                return false;
            }
        } catch (ShellCommandUnresponsiveException
                | IOException
                | TimeoutException
                | AdbCommandRejectedException ex) {
            logger.warning("RootPush: Elevation to root threw exception: " + ex);
            return false;
        }

        ApplicationDumper.Dump dump;
        try {
            dump = new ApplicationDumper(installer).dump(apks);
            if (dump.apks.isEmpty()) {
                logger.warning("RootPush: No APKs in dump");
                return false;
            }

            // Copying the APKs over the existing APKs is risky if the manifest has changed, since
            // changes could be present that the package manager needs to handle.
            for (FileDiff fileDiff : new ApkDiffer().diff(dump.apks, apks)) {
                if (fileDiff.oldFile != null
                        && fileDiff.oldFile.getName().equals("AndroidManifest.xml")) {
                    logger.info("RootPush: Manifest changes require pm install");
                    return false;
                }
            }
        } catch (DeployerException e) {
            logger.warning("RootPush: " + e);
            return false;
        }

        String installDir = Paths.get(dump.apks.get(0).path).getParent().toString();
        Deploy.RootPushInstallRequest.Builder request =
                Deploy.RootPushInstallRequest.newBuilder().setInstallDir(installDir);

        PatchSet patchSet =
                new PatchSetGenerator(PatchSetGenerator.WhenNoChanges.GENERATE_EMPTY_PATCH, logger)
                        .generateFromApks(apks, dump.apks);

        if (patchSet.getStatus() == PatchSet.Status.NoChanges) {
            return true;
        } else if (patchSet.getStatus() != PatchSet.Status.Ok) {
            logger.warning("RootPush: Bad patchset status '%s'", patchSet.getStatus());
            return false;
        }

        List<Deploy.PatchInstruction> patches = patchSet.getPatches();
        request.getInstallInfoBuilder()
                .addAllPatchInstructions(patches)
                .setPackageName(packageName);

        if (request.getInstallInfo().getSerializedSize() > PatchSetGenerator.MAX_PATCHSET_SIZE) {
            logger.warning("RootPush: Patch too large");
            return false;
        }

        Deploy.RootPushInstallResponse res;
        try {
            res = installer.rootPushInstall(request.build());
        } catch (IOException e) {
            logger.warning("RootPush: " + e);
            return false;
        }

        return res.getStatus() == Deploy.RootPushInstallResponse.Status.OK;
    }
}
