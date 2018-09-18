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
import com.android.tools.deploy.swapper.*;
import com.android.utils.ILogger;
import com.google.protobuf.ByteString;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

public class Deployer {

    private static final ILogger LOGGER = Logger.getLogger(Deployer.class);
    private final String packageName;
    private final List<ApkFull> apks;
    private final AdbClient adb;
    private final DexArchiveDatabase db;
    private final Installer installer;

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
            public HashMap<String, ApkDiffer.ApkEntryStatus> diffs;
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
            String packageName,
            List<String> apkPaths,
            InstallerCallBack cb,
            AdbClient adb,
            DexArchiveDatabase db,
            Installer installer) {
        this.stopWatch = new StopWatch();
        this.packageName = packageName;
        this.adb = adb;
        this.db = db;
        this.installer = installer;
        this.apks = apkPaths.stream().map(ApkFull::new).collect(Collectors.toList());
    }

    /*
     * Generates a diff between the new apks and the remote apks installed on the device,
     * followed immediately with either a full install or a patch install depending on
     * the size of the patch.
     */
    public RunResponse install() throws IOException {
        for (ApkFull apk : apks) {
            cache(apk);
        }
        RunResponse response = new RunResponse();
        diffInstall(true, response);
        return response;
    }

    private Map<String, ApkDump> diffInstall(boolean kill, RunResponse response)
            throws IOException {
        stopWatch.start();
        Map<String, ApkDump> dumps = installer.dump(packageName);
        stopWatch.mark("Dumps retrieved");

        try {
            chechDumps(apks, dumps, response);
            if (response.status == RunResponse.Status.ERROR) {
                return dumps;
            }

            generateHashs(apks, dumps, response);
            if (response.status == RunResponse.Status.NOT_INSTALLED) {
                return dumps;
            }

            generateDiffs(apks, dumps, response);
        } finally {
            doInstall(kill);
        }

        return dumps;
    }

    public RunResponse codeSwap() throws IOException {
        RunResponse response = new RunResponse();
        Map<String, ApkDump> dumps = diffInstall(false, response);
        swap(response, dumps, false /* Restart Activity */);
        return response;
    }

    public RunResponse fullSwap() throws IOException {
        RunResponse response = new RunResponse();
        Map<String, ApkDump> dumps = diffInstall(false, response);
        swap(response, dumps, true /* Restart Activity */);
        return response;
    }

    private static void chechDumps(
            List<ApkFull> apks, Map<String, ApkDump> dumps, RunResponse response) {
        int remoteApks = dumps.size();
        if (remoteApks == 0) { // No remote apks. App probably was not installed.
            response.status = RunResponse.Status.NOT_INSTALLED;
            response.errorMessage = "App not installed";
        } else if (remoteApks != apks.size()) { // If the number doesn't match we cannot do a diff.
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

    private void generateHashs(
            List<ApkFull> apks, Map<String, ApkDump> dumps, RunResponse response) {
        for (ApkFull apk : apks) {
            RunResponse.Analysis analysis = findOrCreateAnalysis(response, apk.getPath());
            analysis.localApkHash = apk.getDigest();
            String name = apk.retrieveOnDeviceName();
            ApkDump dump = dumps.get(name);
            if (dump != null) {
                analysis.remoteApkHash = dump.getDigest();
            }
        }
    }

    private void generateDiffs(
            List<ApkFull> apks, Map<String, ApkDump> dumps, RunResponse response) {
        for (ApkFull apk : apks) {
            try {
                ApkDump dump = dumps.get(apk.retrieveOnDeviceName());
                if (dump == null) {
                    response.status = RunResponse.Status.ERROR;
                    response.errorMessage = "Cannot find dump for local apk: " + apk.getPath();
                    return;
                }
                RunResponse.Analysis analysis = findOrCreateAnalysis(response, apk.getPath());
                ApkDiffer apkDiffer1 = new ApkDiffer();
                analysis.diffs = apkDiffer1.diff(apk, dump);
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

    private void doInstall(boolean kill) {
        try {
            adb.installMultiple(apks, kill);
            stopWatch.mark("Install succeeded");
        } catch (DeployerException e) {
            stopWatch.mark("Install failed");
            LOGGER.error(e, null);
        }
    }

    private void swap(RunResponse response, Map<String, ApkDump> dumps, boolean restart)
            throws DeployerException {
        stopWatch.mark("Swap started.");
        ClassRedefiner redefiner = new InstallerBasedClassRedefiner(installer);
        Deploy.SwapRequest.Builder request = Deploy.SwapRequest.newBuilder();
        request.setPackageName(packageName);
        request.setRestartActivity(restart);
        for (ApkFull apk : apks) {
            HashMap<String, ApkDiffer.ApkEntryStatus> diffs =
                    response.result.get(apk.getPath()).diffs;

            try {
                DexArchive newApk = cache(apk);

                if (diffs.isEmpty()) {
                    System.out.println("Swapper: apk " + apk.getPath() + " has not changed.");
                    continue;
                }
                System.out.println(
                        "Swapper found "
                                + diffs.size()
                                + " changes in apk '"
                                + apk.getPath()
                                + "'.");

                String preSwapCheckError = PreswapCheck.verify(diffs);

                if (preSwapCheckError != null) {
                    System.out.println("Unable to swap: " + preSwapCheckError);
                    return;
                }

                // TODO: Only pass in a list of changed files instead of doing a full APK comparision.
                ApkDump apkDump = dumps.get(apk.retrieveOnDeviceName());
                DexArchive prevApk = DexArchive.buildFromDatabase(db, apkDump.getDigest());
                if (prevApk == null) {
                    System.out.println(
                            "Unable to retrieve apk in DB ''"
                                    + apkDump.getDigest()
                                    + "', skipping this apk.");
                    // TODO: propagate this error condition up
                    return;
                }

                DexArchiveComparator comparator = new DexArchiveComparator();
                comparator
                        .compare(prevApk, newApk)
                        .changedClasses
                        .forEach(
                                e ->
                                        request.addClasses(
                                                Deploy.ClassDef.newBuilder()
                                                        .setName(e.name)
                                                        .setDex(ByteString.copyFrom(e.dex))
                                                        .build()));

            } catch (Exception e) {
                System.out.println("Error while creating proto");
                throw new DeployerException(e);
            }
        }
        stopWatch.mark("Piping request...");
        redefiner.redefine(request.build());
        stopWatch.mark("Swap finished.");
    }

    private DexArchive cache(ApkFull apk) throws IOException {
        DexArchive newApk =
                DexArchive.buildFromHostFileSystem(
                        new ZipInputStream(new FileInputStream(apk.getPath())), apk.getDigest());
        newApk.cache(db);
        return newApk;
    }
}
