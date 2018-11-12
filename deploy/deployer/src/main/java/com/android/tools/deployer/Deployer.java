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

import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.android.tools.deployer.model.FileDiff;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.deployer.tasks.TaskRunner.Task;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

public class Deployer {

    public static final String BASE_DIRECTORY = "/data/local/tmp/.studio";
    public static final String INSTALLER_DIRECTORY = BASE_DIRECTORY + "/bin";

    private final AdbClient adb;
    private final ApkFileDatabase db;
    private final Installer installer;
    private final TaskRunner runner;
    private final UIService service;

    public Deployer(
            AdbClient adb,
            ApkFileDatabase db,
            TaskRunner runner,
            Installer installer,
            UIService service) {
        this.adb = adb;
        this.db = db;
        this.runner = runner;
        this.installer = installer;
        this.service = service;
    }

    /**
     * Installs the given apks. This method will register the APKs in the database for subsequent
     * swaps
     */
    public void install(String packageName, List<String> apks, InstallOptions options)
            throws DeployerException {
        try (Trace ignored = Trace.begin("install")) {
            // There could be tasks running from the previous cycle that were
            // left to complete in the background. We wait for them to finish
            // So that we don't accumulate them.
            runner.join();

            ApkInstaller apkInstaller = new ApkInstaller(adb, service);
            apkInstaller.install(packageName, apks, options);


            // Inputs
            Task<List<String>> paths = runner.submit(apks);
            CachedDexSplitter splitter = new CachedDexSplitter(db, new D8DexSplitter());

            // Parse the apks
            Task<List<ApkEntry>> entries =
                    runner.submit("parsePaths", new ApkParser()::parsePaths, paths);

            // Update the database
            runner.submit("computeClassChecksums", splitter::cache, entries);
        }
    }

    public void codeSwap(
            String packageName, List<String> apks, Map<Integer, ClassRedefiner> redefiners)
            throws DeployerException {
        Trace.begin("codeSwap");
        swap(packageName, apks, false /* Restart Activity */, redefiners);
        Trace.end();
    }

    public void fullSwap(String packageName, List<String> apks) throws DeployerException {
        Trace.begin("fullSwap");
        swap(packageName, apks, true /* Restart Activity */, ImmutableMap.of());
        Trace.end();
    }

    private void swap(
            String packageName,
            List<String> paths,
            boolean restart,
            Map<Integer, ClassRedefiner> redefiners)
            throws DeployerException {

        CachedDexSplitter splitter = new CachedDexSplitter(db, new D8DexSplitter());

        // Get the list of files from the local apks
        List<ApkEntry> newFiles = new ApkParser().parsePaths(paths);

        // Get the list of files from the installed app
        List<ApkEntry> dumps = new ApkDumper(installer).dump(packageName);

        // Calculate the difference between them
        List<FileDiff> diffs = new ApkDiffer().diff(dumps, newFiles);

        // Push the apks to device and get the remote paths
        List<String> apkPaths = new ApkPusher(adb).push(newFiles);

        // Verify the changes are swappable and get only the dexes that we can change
        List<FileDiff> dexDiffs = new SwapVerifier().verify(diffs, restart);

        // Compare the local vs remote dex files.
        List<DexClass> toSwap = new DexComparator().compare(dexDiffs, splitter);

        // Update the database with the entire new apk. In the normal case this should
        // be a no-op because the dexes that were modified were extracted at comparison time.
        // However if the compare task doesn't get to execute we still update the database.
        splitter.cache(newFiles);

        // Do the swap
        ApkSwapper swapper = new ApkSwapper(installer, packageName, restart, redefiners);
        swapper.swap(newFiles, apkPaths, toSwap);
    }
}
