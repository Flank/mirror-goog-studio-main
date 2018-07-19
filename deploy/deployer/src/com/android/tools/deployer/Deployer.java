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

import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Deployer {

    private static final ILogger LOGGER = new StdLogger(StdLogger.Level.VERBOSE);
    private final String packageName;
    private final ArrayList<Apk> apks = new ArrayList<>();
    private final InstallerCallBack installerCallBack;
    private final AdbClient adb;

    // TODO:
    // Until we figure out the threading model of IR2, run installation in a background thread so
    // Hot Swap is not blocked. At the very minimum, this should be a static field when we release.
    ExecutorService installerThread = Executors.newSingleThreadExecutor();

    StopWatch stopWatch;

    public interface InstallerCallBack {
        void onInstallationFinished(boolean status);
    }

    public Deployer(
            String packageName, List<String> apkPaths, InstallerCallBack cb, AdbClient adb) {
        this.stopWatch = new StopWatch();
        this.packageName = packageName;
        this.installerCallBack = cb;
        this.adb = adb;
        for (String path : apkPaths) {
            apks.add(new Apk(path));
        }
    }

    /* Generates a diff between the new apks and the remote apks installed on the device,
     * followed immediately with either a full install or a patch install depending on
     * the size of the patch.
     *
     * Returns a map containing the diff for each apks. If a diff could not be generated for an apk
     * the hash entry will be null.
     */
    public HashMap<String, HashMap<String, Apk.ApkEntryStatus>> run() {
        stopWatch.start();

        Path workingDirectory;
        try {
            workingDirectory = Files.createTempDirectory("ir2deployer");
        } catch (IOException e) {
            throw new DeployerException("Unable to create a temporary directory.", e);
        }

        // Create a tmp directory where the remote dmps files will be stored.
        getRemoteApkDumps(packageName, workingDirectory.toAbsolutePath().toString());
        stopWatch.mark("Dumps retrieved");

        // Make sure all splits have valid spit attribute and there is only one base.
        Set<String> expectedAPKNmes = new HashSet<>();
        for (Apk apk : apks) {
            String onDeviceName = apk.getOnDeviceName();
            if (expectedAPKNmes.contains(onDeviceName)) {
                throw new DeployerException("APK set expects '" + onDeviceName + "' twice.");
            }
            expectedAPKNmes.add(onDeviceName);
        }

        // Generate diffs for all apks in the list
        HashMap<String, HashMap<String, Apk.ApkEntryStatus>> diffs = new HashMap<>();
        for (Apk apk : apks) {
            String dumpFilename = getExpectedDumpFilename(apk);
            diffs.put(
                    apk.getPath(),
                    apk.diff(
                            Paths.get(
                                    workingDirectory.toString(), packageName, "/", dumpFilename)));
        }
        stopWatch.mark("Diff generated");

        install();
        return diffs;
    }

    // TODO: If the installer extracted v2 signature block, use it to generates apk patches.
    // For now just install the full apks set.
    private void install() {
        installerThread.submit(
                () -> {
                    boolean succeeded = true;
                    try {
                        adb.installMultiple(apks);
                        stopWatch.mark("Install succeeded");
                    } catch (DeployerException e) {
                        succeeded = false;
                        stopWatch.mark("Install failed");
                        LOGGER.error(e, null);
                    } finally {
                        installerCallBack.onInstallationFinished(succeeded);
                        installerThread.shutdown();
                    }
                });
    }

    // TODO: Add support for split. In order to do this, this function needs to:
    //  - Open the local apk.
    //  - Parse AndroidManifest binary XML.
    //  - Look for "manifest" node, attribute "split". If not present, return base.apk. Otherwise
    //    return the value found.
    private String getExpectedDumpFilename(Apk apk) throws DeployerException {
        return apk.getOnDeviceName() + ".remotecd";
    }

    private void getRemoteApkDumps(String packageName, String dst) throws DeployerException {
        // Generate dumps on remote device.
        String[] cmd = {
            "cd", "/data/local/tmp/.ir2/bin", "&&", "./ir2_installer", "dump", packageName
        };
        adb.shell(cmd);

        // Pull entire directory of dumps from remote device.
        String remoteDirectory = "/data/local/tmp/.ir2/dumps/" + packageName;
        adb.pull(remoteDirectory, dst);
    }
}
