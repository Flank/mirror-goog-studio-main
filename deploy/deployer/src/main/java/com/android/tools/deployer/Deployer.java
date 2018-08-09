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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipInputStream;

public class Deployer {

    private static final ILogger LOGGER = Logger.getLogger(Deployer.class);
    private final String packageName;
    private final ArrayList<Apk> apks = new ArrayList<>();
    private final AdbClient adb;
    private final Path workingDirectory;
    private final DexArchiveDatabase db;

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
            String packageName,
            List<String> apkPaths,
            InstallerCallBack cb,
            AdbClient adb,
            DexArchiveDatabase db) {
        this.stopWatch = new StopWatch();
        this.packageName = packageName;
        this.adb = adb;
        this.db = db;

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
    public RunResponse install() throws IOException {
        for (Apk apk : apks) {
            cache(apk);
        }
        return diffInstall(true);
    }

    private RunResponse diffInstall(boolean kill) {
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
            doInstall(kill);
        }

        return response;
    }

    public RunResponse fullSwap() {
        RunResponse response = diffInstall(false);
        swap(response);
        return response;
    }

    private void chechDumps(ArrayList<Apk> apks, RunResponse response) {
        int remoteApks = 0;
        for (Apk apk : apks) {
            if (apk.getRemoteArchive().exists()) {
                remoteApks++;
            }
        }

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

    private void doInstall(boolean kill) {
        try {
            adb.installMultiple(apks, kill);
            stopWatch.mark("Install succeeded");
        } catch (DeployerException e) {
            stopWatch.mark("Install failed");
            LOGGER.error(e, null);
        }
    }

    private void swap(RunResponse response) throws DeployerException {

        ClassRedefiner redefiner = new AgentBasedClassRedefiner(adb);
        Deploy.SwapRequest.Builder request = Deploy.SwapRequest.newBuilder();
        request.setPackageName(packageName);
        request.setRestartActivity(true);
        for (Apk apk : apks) {
            HashMap<String, Apk.ApkEntryStatus> diffs = response.result.get(apk.getPath()).diffs;

            try {
                DexArchive newApk = cache(apk);

                if (diffs.isEmpty()) {
                    continue;
                }

                // TODO: Only pass in a list of changed files instead of doing a full APK comparision.
                DexArchive prevApk =
                        DexArchive.buildFromDatabase(db, apk.getRemoteArchive().getDigest());
                if (prevApk == null) {
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
                throw new DeployerException(e);
            }
        }

        redefiner.redefine(request.build());
    }

    private DexArchive cache(Apk apk) throws IOException {
        DexArchive newApk =
                DexArchive.buildFromHostFileSystem(
                        new ZipInputStream(new FileInputStream(apk.getLocalArchive().getPath())),
                        apk.getLocalArchive().getDigest());
        newApk.cache(db);
        return newApk;
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
