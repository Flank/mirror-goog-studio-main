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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Deployer {

    private static final ILogger LOGGER = Logger.getLogger(Deployer.class);
    private final String packageName;
    private final ArrayList<Apk> apks = new ArrayList<>();
    private final InstallerCallBack installerCallBack;
    private final AdbClient adb;
    private final Path workingDirectory;

    // TODO:
    // Until we figure out the threading model of IR2, run installation in a background thread so
    // Hot Swap is not blocked. At the very minimum, this should be a static field when we release.
    private ExecutorService installerThread = Executors.newSingleThreadExecutor();
    private StopWatch stopWatch;

    // status field is always set.
    // If status is ERROR, only errorMessage is set.
    // If status is OK, all fields except errorMessage are set.
    // If status is NO_INSTALLED, only localApkHash fields are set.
    public static class RunResponse {
        public enum Status {
            OK,
            NOT_INSTALLED,
            ERROR
        }

        public static class Analysis {
            public HashMap<String, Apk.ApkEntryStatus> diffs;
            public String localApkHash;
            public String remoteApkHash;
        }

        public Status status = Status.OK; // Always set.
        public String errorMessage; // Set only if status != OK.
        public HashMap<String, Analysis> result =
                new HashMap<>(); // Set only if status == OK otherwise content is undefined.
    }


    public interface InstallerCallBack {
        void onInstallationFinished(boolean status);
    }

    public Deployer(
            String packageName, List<String> apkPaths, InstallerCallBack cb, AdbClient adb) {
        this.stopWatch = new StopWatch();
        this.packageName = packageName;
        this.installerCallBack = cb;
        this.adb = adb;
        try {
            workingDirectory = Files.createTempDirectory("ir2deployer");
        } catch (IOException e) {
            throw new DeployerException("Unable to create a temporary directory.", e);
        }

        String dumpLocation = Paths.get(workingDirectory.toString(), packageName).toString();
        for (String path : apkPaths) {
            apks.add(new Apk(path, dumpLocation));
        }
    }

    /*
     * Generates a diff between the new apks and the remote apks installed on the device,
     * followed immediately with either a full install or a patch install depending on
     * the size of the patch.
     */
    public RunResponse run() {
        stopWatch.start();
        RunResponse response = new RunResponse();

        getRemoteApkDumps(packageName, workingDirectory.toAbsolutePath().toString());
        stopWatch.mark("Dumps retrieved");

        try {
            chechDumps(apks, response);
            if (response.status == RunResponse.Status.ERROR) {
                return response;
            }

            generateHashs(apks, response);
            if (response.status == RunResponse.Status.NOT_INSTALLED) {
                return response;
            }

            generateDiffs(apks, response);
        } finally {
            install();
        }

        return response;
    }

    private void chechDumps(ArrayList<Apk> apks, RunResponse response) {
        int remoteApks = 0;
        for (Apk apk : apks) {
            if (apk.getRemoteArchive().exists()) {
                remoteApks++;
            }
        }
        // If no remote apks were retrieved, it is likely the app was not installed.
        if (remoteApks == 0) {
            response.status = RunResponse.Status.NOT_INSTALLED;
            response.errorMessage = "App not installed";
        }

        // Check the local apk split structure is the same as the remote split.
        if (remoteApks != apks.size()) {
            response.status = RunResponse.Status.ERROR;
            response.errorMessage = "Remote apks count differ, unable to generate a diff.";
        }
    }

    private RunResponse.Analysis findOrCreateAnalysis(RunResponse response, String apkName) {
        RunResponse.Analysis analysis = response.result.get(apkName);
        if (analysis == null) {
            analysis = new RunResponse.Analysis();
            response.result.put(apkName, analysis);
        }
        return analysis;
    }

    private void generateHashs(ArrayList<Apk> apks, RunResponse response) {
        for (Apk apk : apks) {
            RunResponse.Analysis analysis = findOrCreateAnalysis(response, apk.getPath());
            analysis.localApkHash = apk.getLocalArchive().getDigest();
            if (apk.getRemoteArchive().exists()) {
                analysis.remoteApkHash = apk.getRemoteArchive().getDigest();
            }
        }
    }

    private void generateDiffs(ArrayList<Apk> apks, RunResponse response) {
        for (Apk apk : apks) {
            try {
                RunResponse.Analysis analysis = findOrCreateAnalysis(response, apk.getPath());
                analysis.diffs = apk.diff();
                response.result.put(apk.getPath(), analysis);
            } catch (DeployerException e) {
                response.status = RunResponse.Status.ERROR;
                StringWriter stringWriter = new StringWriter();
                PrintWriter printWriter = new PrintWriter(stringWriter);
                e.printStackTrace(printWriter);
                response.errorMessage = stringWriter.toString();
            }
        }
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
