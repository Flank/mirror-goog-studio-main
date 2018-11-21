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
            String argPackageName,
            List<String> argPaths,
            boolean argRestart,
            Map<Integer, ClassRedefiner> redefiners)
            throws DeployerException {
        runner.join();

        // Inputs
        Task<List<String>> paths = runner.submit(argPaths);
        Task<String> packageName = runner.submit(argPackageName);
        Task<Boolean> restart = runner.submit(argRestart);
        Task<DexSplitter> splitter = runner.submit(new CachedDexSplitter(db, new D8DexSplitter()));

        // Get the list of files from the local apks
        Task<List<ApkEntry>> newFiles =
                runner.submit("parseApks", new ApkParser()::parsePaths, paths);

        // Get the list of files from the installed app
        Task<List<ApkEntry>> dumps =
                runner.submit("dump", new ApkDumper(installer)::dump, packageName);

        // Calculate the difference between them
        Task<List<FileDiff>> diffs = runner.submit("diff", new ApkDiffer()::diff, dumps, newFiles);

        // Push the apks to device and get the remote paths
        Task<List<String>> apkPaths = runner.submit("push", new ApkPusher(adb)::push, newFiles);

        // Verify the changes are swappable and get only the dexes that we can change
        Task<List<FileDiff>> dexDiffs =
                runner.submit("verify", new SwapVerifier()::verify, diffs, restart);

        // Compare the local vs remote dex files.
        Task<List<DexClass>> toSwap =
                runner.submit("compare", new DexComparator()::compare, dexDiffs, splitter);

        // Do the swap
        ApkSwapper swapper = new ApkSwapper(installer, argPackageName, argRestart, redefiners);
        Task<Boolean> swap = runner.submit("swap", swapper::swap, newFiles, apkPaths, toSwap);

        // Update the database with the entire new apk. In the normal case this should
        // be a no-op because the dexes that were modified were extracted at comparison time.
        // However if the compare task doesn't get to execute we still update the database.
        // Note we artificially block this task until swap is done.
        runner.submit("cache", DexSplitter::cache, runner.block(splitter, swap), newFiles);

        // Wait only for swap to finish
        swap.get();
    }
}
