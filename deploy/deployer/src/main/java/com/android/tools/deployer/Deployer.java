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
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

public class Deployer {

    public static final String BASE_DIRECTORY = "/data/local/tmp/.studio";
    public static final String APK_DIRECTORY = BASE_DIRECTORY + "/apks";
    public static final String DUMPS_DIRECTORY = BASE_DIRECTORY + "/dumps";
    public static final String INSTALLER_DIRECTORY = BASE_DIRECTORY + "/bin";

    private final String packageName;
    private final List<ApkFull> apks;
    private final AdbClient adb;
    private final DexArchiveDatabase db;
    private final Installer installer;
    private final ILogger logger;

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

    public Deployer(
            String packageName,
            List<String> apkPaths,
            AdbClient adb,
            DexArchiveDatabase db,
            Installer installer,
            ILogger logger) {
        this.stopWatch = new StopWatch(logger);
        this.packageName = packageName;
        this.adb = adb;
        this.db = db;
        this.installer = installer;
        this.logger = logger;
        this.apks = apkPaths.stream().map(ApkFull::new).collect(Collectors.toList());
    }

    /*
     * Generates a diff between the new apks and the remote apks installed on the device,
     * followed immediately with either a full install or a patch install depending on
     * the size of the patch.
     */
    public RunResponse install() throws IOException {
        stopWatch.start();
        for (ApkFull apk : apks) {
            cache(apk);
        }
        stopWatch.mark("Apk cached");
        RunResponse response = new RunResponse();
        try {
            adb.installMultiple(apks, true);
            stopWatch.mark("Install succeeded");
            response.status = RunResponse.Status.OK;
            response.errorMessage = "Install succeeded";
        } catch (DeployerException e) {
            response.status = RunResponse.Status.ERROR;
            response.errorMessage = "Install failed";
            stopWatch.mark("Install failed");
            logger.error(e, null);
        }
        return response;
    }

    private Map<String, ApkDump> diff(RunResponse response) throws IOException {
        stopWatch.start();
        Map<String, ApkDump> dumps = installer.dump(packageName);
        stopWatch.mark("Dumps retrieved");

        chechDumps(apks, dumps, response);
        if (response.status == RunResponse.Status.ERROR) {
            return dumps;
        }

        generateHashs(apks, dumps, response);
        if (response.status == RunResponse.Status.NOT_INSTALLED) {
            return dumps;
        }

        generateDiffs(apks, dumps, response);

        return dumps;
    }

    public RunResponse codeSwap() throws IOException {
        RunResponse response = new RunResponse();
        Map<String, ApkDump> dumps = diff(response);
        swap(response, dumps, false /* Restart Activity */);
        return response;
    }

    public RunResponse fullSwap() throws IOException {
        RunResponse response = new RunResponse();
        Map<String, ApkDump> dumps = diff(response);
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
            String name = apk.getApkDetails().fileName();
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
                ApkDump dump = dumps.get(apk.getApkDetails().fileName());
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

    private void swap(RunResponse response, Map<String, ApkDump> dumps, boolean restart)
            throws DeployerException {
        stopWatch.mark("Swap started.");
        ClassRedefiner redefiner = new InstallerBasedClassRedefiner(installer);
        Deploy.SwapRequest.Builder request = Deploy.SwapRequest.newBuilder();
        request.setPackageName(packageName);
        request.setRestartActivity(restart);

        HashSet<String> processNames = new HashSet<>();
        for (ApkFull apk : apks) {
            adb.shell(
                    new String[] {"rm", "-r", APK_DIRECTORY, ";", "mkdir", "-p", APK_DIRECTORY},
                    null);
            String target = APK_DIRECTORY + "/" + Paths.get(apk.getPath()).getFileName();
            adb.push(apk.getPath(), target);
            request.addApks(target);
            HashMap<String, ApkDiffer.ApkEntryStatus> diffs =
                    response.result.get(apk.getPath()).diffs;

            processNames.addAll(apk.getApkDetails().processNames());

            try {
                DexArchive newApk = cache(apk);

                if (diffs.isEmpty()) {
                    logger.info("Swapper: apk " + apk.getPath() + " has not changed.");
                    continue;
                }
                logger.info("Swapper found %d changes in apk '%s'.", diffs.size(), apk.getPath());
                String preSwapCheckError = PreswapCheck.verify(diffs);

                if (preSwapCheckError != null) {
                    response.status = RunResponse.Status.ERROR;
                    response.errorMessage = preSwapCheckError;
                    return;
                }

                // TODO: Only pass in a list of changed files instead of doing a full APK comparision.
                ApkDump apkDump = dumps.get(apk.getApkDetails().fileName());
                DexArchive prevApk = DexArchive.buildFromDatabase(db, apkDump.getDigest());
                if (prevApk == null) {
                    logger.info(
                            "Unable to retrieve apk in DB '%s', skipping this apk.",
                            apkDump.getDigest());
                    response.status = RunResponse.Status.ERROR;
                    response.errorMessage = "Unrecognized APK on device.";
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
                throw new DeployerException(e);
            }
        }

        request.addAllProcessNames(processNames);

        stopWatch.mark("Piping request...");
        Deploy.SwapResponse swapResponse = redefiner.redefine(request.build());
        stopWatch.mark("Swap finished.");

        // TODO: This is overwriting any existing errors in the object. Is that a problem?
        if (swapResponse.getStatus() == Deploy.SwapResponse.Status.OK) {
            response.status = RunResponse.Status.OK;
        } else {
            response.status = RunResponse.Status.ERROR;
            response.errorMessage = "Swap failed.";
        }
    }

    private DexArchive cache(ApkFull apk) throws IOException {
        DexArchive newApk =
                DexArchive.buildFromHostFileSystem(
                        new ZipInputStream(new FileInputStream(apk.getPath())), apk.getDigest());
        db.enqueueUpdate(
                delegate -> {
                    newApk.cache(delegate);
                    return null;
                });
        return newApk;
    }
}
