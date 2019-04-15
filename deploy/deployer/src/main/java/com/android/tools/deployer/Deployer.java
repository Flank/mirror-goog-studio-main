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

import com.android.sdklib.AndroidVersion;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.android.tools.deployer.model.FileDiff;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.deployer.tasks.TaskRunner.Task;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Deployer {

    public static final String BASE_DIRECTORY = "/data/local/tmp/.studio";
    public static final String INSTALLER_DIRECTORY = BASE_DIRECTORY + "/bin";

    private final AdbClient adb;
    private final ApkFileDatabase db;
    private final Installer installer;
    private final TaskRunner runner;
    private final UIService service;
    private final Collection<DeployMetric> metrics;
    private final ILogger logger;

    public Deployer(
            AdbClient adb,
            ApkFileDatabase db,
            TaskRunner runner,
            Installer installer,
            UIService service,
            Collection<DeployMetric> metrics,
            ILogger logger) {
        this.adb = adb;
        this.db = db;
        this.runner = runner;
        this.installer = installer;
        this.service = service;
        this.metrics = metrics;
        this.logger = logger;
    }

    enum Tasks {
        CACHE,
        DUMP,
        DIFF,
        PREINSTALL,
        VERIFY,
        COMPARE,
        SWAP,
        PARSE_PATHS
    }

    public enum InstallMode {
        DELTA, // If an application is already installed on the a device, send only what has changed.
        FULL // Send application full apk regardless of the device state.
    }

    /**
     * Information related to a swap or install.
     *
     * <p>Note that there is indication to success or failure of the operation. Failure is indicated
     * by {@link DeployerException} thus this object is created only on successful deployments.
     */
    public class Result {
        public boolean skippedInstall = false;
    }

    /**
     * Installs the given apks. This method will register the APKs in the database for subsequent
     * swaps
     */
    public Result install(
            String packageName, List<String> apks, InstallOptions options, InstallMode installMode)
            throws DeployerException {
        Result result = new Result();
        try (Trace ignored = Trace.begin("install")) {
            ApkInstaller apkInstaller = new ApkInstaller(adb, service, installer, logger);
            result.skippedInstall =
                    !apkInstaller.install(packageName, apks, options, installMode, metrics);

            // Inputs
            Task<List<String>> paths = runner.create(apks);
            CachedDexSplitter splitter = new CachedDexSplitter(db, new D8DexSplitter());

            // Parse the apks
            Task<List<ApkEntry>> entries =
                    runner.create(Tasks.PARSE_PATHS, new ApkParser()::parsePaths, paths);

            // Update the database
            runner.create(Tasks.CACHE, splitter::cache, entries);

            runner.runAsync();
            return result;
        }
    }

    public Result codeSwap(List<String> apks, Map<Integer, ClassRedefiner> redefiners)
            throws DeployerException {
        try (Trace ignored = Trace.begin("codeSwap")) {
            return swap(apks, false /* Restart Activity */, redefiners);
        }
    }

    public Result fullSwap(List<String> apks) throws DeployerException {
        try (Trace ignored = Trace.begin("fullSwap")) {
            return swap(apks, true /* Restart Activity */, ImmutableMap.of());
        }
    }

    private Result swap(
            List<String> argPaths, boolean argRestart, Map<Integer, ClassRedefiner> redefiners)
            throws DeployerException {

        if (!adb.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.O)) {
            throw DeployerException.apiNotSupported();
        }

        // Inputs
        Task<List<String>> paths = runner.create(argPaths);
        Task<Boolean> restart = runner.create(argRestart);
        Task<DexSplitter> splitter = runner.create(new CachedDexSplitter(db, new D8DexSplitter()));

        // Get the list of files from the local apks
        Task<List<ApkEntry>> newFiles =
                runner.create(Tasks.PARSE_PATHS, new ApkParser()::parsePaths, paths);

        // Get the list of files from the installed app
        Task<ApplicationDumper.Dump> dumps =
                runner.create(Tasks.DUMP, new ApplicationDumper(installer)::dump, newFiles);

        // Calculate the difference between them
        Task<List<FileDiff>> diffs =
                runner.create(Tasks.DIFF, new ApkDiffer()::diff, dumps, newFiles);

        // Push the apks to device and get the remote paths
        Task<String> sessionId =
                runner.create(
                        Tasks.PREINSTALL,
                        new ApkPreInstaller(adb, installer, logger)::preinstall,
                        dumps,
                        newFiles);

        // Verify the changes are swappable and get only the dexes that we can change
        Task<List<FileDiff>> dexDiffs =
                runner.create(Tasks.VERIFY, new SwapVerifier()::verify, diffs, restart);

        // Compare the local vs remote dex files.
        Task<List<DexClass>> toSwap =
                runner.create(Tasks.COMPARE, new DexComparator()::compare, dexDiffs, splitter);

        // Do the swap
        ApkSwapper swapper = new ApkSwapper(installer, redefiners, argRestart);
        runner.create(Tasks.SWAP, swapper::swap, dumps, sessionId, toSwap);

        List<Task<?>> tasks = runner.run();

        // Update the database with the entire new apk. In the normal case this should
        // be a no-op because the dexes that were modified were extracted at comparison time.
        // However if the compare task doesn't get to execute we still update the database.
        // Note we artificially block this task until swap is done.
        runner.create(Tasks.CACHE, DexSplitter::cache, splitter, newFiles);

        // Wait only for swap to finish
        runner.runAsync();

        // null metrics are from tasks that are not started.
        tasks.stream()
                .map(task -> task.getMetric())
                .filter(Objects::nonNull)
                .forEach(metric -> metrics.add(metric));

        Result result = new Result();
        result.skippedInstall = sessionId.get().equals("<SKIPPED-INSTALLATION>");
        return result;
    }
}
