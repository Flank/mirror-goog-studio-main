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
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.FileDiff;
import com.android.tools.deployer.tasks.Task;
import com.android.tools.deployer.tasks.TaskResult;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Deployer {

    public static final String BASE_DIRECTORY = "/data/local/tmp/.studio";
    public static final String INSTALLER_DIRECTORY = BASE_DIRECTORY + "/bin";

    private final AdbClient adb;
    private final SqlApkFileDatabase dexDb;
    private final DeploymentCacheDatabase deployCache;
    private final Installer installer;
    private final TaskRunner runner;
    private final UIService service;
    private final MetricsRecorder metrics;
    private final ILogger logger;

    // Temp flag.
    private final boolean useOptimisticSwap;
    private final boolean useOptimisticResourceSwap;
    private final boolean useStructuralRedefinition;
    private final boolean useVariableReinitialization;
    private final boolean fastRestartOnSwapFail;

    public Deployer(
            AdbClient adb,
            DeploymentCacheDatabase deployCache,
            SqlApkFileDatabase dexDb,
            TaskRunner runner,
            Installer installer,
            UIService service,
            MetricsRecorder metrics,
            ILogger logger,
            boolean useOptimisticSwap,
            boolean useOptimisticResourceSwap,
            boolean useStructuralRedefinition,
            boolean useVariableReinitialization,
            boolean fastRestartOnSwapFail) {
        this.adb = adb;
        this.deployCache = deployCache;
        this.dexDb = dexDb;
        this.runner = runner;
        this.installer = installer;
        this.service = service;
        this.metrics = metrics;
        this.logger = logger;
        this.useOptimisticSwap = useOptimisticSwap;
        this.useOptimisticResourceSwap = useOptimisticResourceSwap;
        this.useStructuralRedefinition = useStructuralRedefinition;
        this.useVariableReinitialization = useVariableReinitialization;
        this.fastRestartOnSwapFail = fastRestartOnSwapFail;
    }

    enum Tasks {
        CACHE,
        DUMP,
        DIFF,
        PREINSTALL,
        VERIFY,
        COMPARE,
        SWAP,
        PARSE_PATHS,

        // pipeline 2.0
        PARSE_APP_IDS,
        DEPLOY_CACHE_STORE,
        OPTIMISTIC_DUMP,
        VERIFY_DUMP,
        EXTRACT_APK_ENTRIES,
        COLLECT_SWAP_DATA,
        OPTIMISTIC_SWAP,

        // New DDMLib
        GET_PIDS,
        GET_ARCH,
        COMPUTE_FRESHINSTALL_OID,
    }

    public enum InstallMode {
        DELTA, // If an application is already installed on the a device, send only what has changed.
        DELTA_NO_SKIP, // Delta install but don't skip installation should there be no changes.
        FULL // Send application full apk regardless of the device state.
    }

    /**
     * Information related to a swap or install.
     *
     * <p>Note that there is indication to success or failure of the operation. Failure is indicated
     * by {@link DeployerException} thus this object is created only on successful deployments.
     */
    public static class Result {
        public boolean skippedInstall = false;
        public boolean needsRestart = false;
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

            // Inputs
            Task<List<String>> paths = runner.create(apks);

            // Parse the apks
            Task<List<Apk>> apkList =
                    runner.create(Tasks.PARSE_PATHS, new ApkParser()::parsePaths, paths);

            if (supportsNewPipeline()) {
                installMode =
                        installMode == InstallMode.DELTA ? InstallMode.DELTA_NO_SKIP : installMode;
                result.skippedInstall =
                        !apkInstaller.install(
                                packageName,
                                apks,
                                options,
                                installMode,
                                metrics.getDeployMetrics());

                Task<String> appId =
                        runner.create(
                                Tasks.PARSE_APP_IDS, ApplicationDumper::getPackageName, apkList);
                runner.create(
                        Tasks.DEPLOY_CACHE_STORE,
                        deployCache::invalidate,
                        runner.create(adb.getSerial()),
                        appId);
            } else {
                result.skippedInstall =
                        !apkInstaller.install(
                                packageName,
                                apks,
                                options,
                                installMode,
                                metrics.getDeployMetrics());
            }

            CachedDexSplitter splitter = new CachedDexSplitter(dexDb, new D8DexSplitter());

            // Update the database
            runner.create(Tasks.CACHE, splitter::cache, apkList);

            runner.runAsync();
            return result;
        }
    }

    public Result codeSwap(List<String> apks, Map<Integer, ClassRedefiner> debuggerRedefiners)
            throws DeployerException {
        try (Trace ignored = Trace.begin("codeSwap")) {
            if (supportsNewPipeline()) {
                return optimisticSwap(apks, false /* Restart Activity */, debuggerRedefiners);
            } else {
                return swap(apks, false /* Restart Activity */, debuggerRedefiners);
            }
        }
    }

    public Result fullSwap(List<String> apks) throws DeployerException {
        try (Trace ignored = Trace.begin("fullSwap")) {
            if (supportsNewPipeline() && useOptimisticResourceSwap) {
                return optimisticSwap(apks, /* Restart Activity */ true, ImmutableMap.of());
            } else {
                return swap(apks, true /* Restart Activity */, ImmutableMap.of());
            }
        }
    }

    private Result swap(
            List<String> argPaths,
            boolean argRestart,
            Map<Integer, ClassRedefiner> debuggerRedefiners)
            throws DeployerException {

        if (!adb.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.O)) {
            throw DeployerException.apiNotSupported();
        }

        // Inputs
        Task<List<String>> paths = runner.create(argPaths);
        Task<Boolean> restart = runner.create(argRestart);
        Task<DexSplitter> splitter =
                runner.create(new CachedDexSplitter(dexDb, new D8DexSplitter()));

        // Get the list of files from the local apks
        Task<List<Apk>> newFiles =
                runner.create(Tasks.PARSE_PATHS, new ApkParser()::parsePaths, paths);

        // Get the list of files from the installed app
        Task<ApplicationDumper.Dump> dumps =
                runner.create(Tasks.DUMP, new ApplicationDumper(installer)::dump, newFiles);

        // Calculate the difference between them
        Task<List<FileDiff>> diffs =
                runner.create(
                        Tasks.DIFF,
                        (dump, newApks) -> new ApkDiffer().diff(dump.apks, newApks),
                        dumps,
                        newFiles);

        // Push and pre install the apks
        Task<String> sessionId =
                runner.create(
                        Tasks.PREINSTALL,
                        new ApkPreInstaller(adb, installer, logger)::preinstall,
                        dumps,
                        newFiles,
                        diffs);

        // Verify the changes are swappable and get only the dexes that we can change
        Task<List<FileDiff>> dexDiffs =
                runner.create(Tasks.VERIFY, new SwapVerifier()::verify, diffs, restart);

        // Compare the local vs remote dex files.
        Task<DexComparator.ChangedClasses> toSwap =
                runner.create(Tasks.COMPARE, new DexComparator()::compare, dexDiffs, splitter);

        // Do the swap
        ApkSwapper swapper = new ApkSwapper(installer, debuggerRedefiners, argRestart, adb, logger);
        runner.create(Tasks.SWAP, swapper::swap, swapper::error, dumps, sessionId, toSwap);

        TaskResult result = runner.run();
        result.getMetrics().forEach(metrics::add);

        // Update the database with the entire new apk. In the normal case this should
        // be a no-op because the dexes that were modified were extracted at comparison time.
        // However if the compare task doesn't get to execute we still update the database.
        // Note we artificially block this task until swap is done.
        if (result.isSuccess()) {
            runner.create(Tasks.CACHE, DexSplitter::cache, splitter, newFiles);

            // Wait only for swap to finish
            runner.runAsync();
        } else {
            throw result.getException();
        }

        Result deployResult = new Result();
        deployResult.skippedInstall = sessionId.get().equals(ApkPreInstaller.SKIPPED_INSTALLATION);
        return deployResult;
    }

    private Result optimisticSwap(
            List<String> argPaths, boolean argRestart, Map<Integer, ClassRedefiner> redefiners)
            throws DeployerException {

        if (!adb.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.O)) {
            throw DeployerException.apiNotSupported();
        }

        // Inputs
        Task<List<String>> paths = runner.create(argPaths);
        Task<Boolean> restart = runner.create(argRestart);
        Task<DexSplitter> splitter =
                runner.create(new CachedDexSplitter(dexDb, new D8DexSplitter()));
        Task<String> deviceSerial = runner.create(adb.getSerial());

        // Get the list of files from the local apks
        Task<List<Apk>> newFiles =
                runner.create(Tasks.PARSE_PATHS, new ApkParser()::parsePaths, paths);

        // Get the App info. Some from the APK, some from DDMLib.
        Task<String> packageName =
                runner.create(Tasks.PARSE_APP_IDS, ApplicationDumper::getPackageName, newFiles);
        Task<List<Integer>> pids = runner.create(Tasks.GET_PIDS, adb::getPids, packageName);
        Task<Deploy.Arch> arch = runner.create(Tasks.GET_ARCH, adb::getArch, pids);

        // Get the list of files from the installed app assuming deployment cache is correct.
        Task<DeploymentCacheDatabase.Entry> speculativeDump =
                runner.create(
                        Tasks.OPTIMISTIC_DUMP,
                        this::dumpWithCache,
                        packageName,
                        deviceSerial,
                        newFiles);

        // Calculate the difference between them speculating the deployment cache is correct.
        Task<List<FileDiff>> diffs =
                runner.create(Tasks.DIFF, new ApkDiffer()::specDiff, speculativeDump, newFiles);

        // Extract files from the APK for overlays. Currently only extract resources.
        Predicate<String> filter = file -> file.startsWith("res") || file.startsWith("assets");
        Task<Map<ApkEntry, ByteString>> extractedFiles =
                runner.create(
                        Tasks.EXTRACT_APK_ENTRIES, new ApkEntryExtractor(filter)::extract, diffs);

        // Verify the changes are swappable and get only the dexes that we can change
        Task<List<FileDiff>> dexDiffs =
                runner.create(Tasks.VERIFY, new SwapVerifier()::verify, newFiles, diffs, restart);

        // Compare the local vs remote dex files.
        Task<DexComparator.ChangedClasses> changedClasses =
                runner.create(Tasks.COMPARE, new DexComparator()::compare, dexDiffs, splitter);

        // Perform the swap.
        OptimisticApkSwapper swapper =
                new OptimisticApkSwapper(
                        installer,
                        redefiners,
                        argRestart,
                        useStructuralRedefinition,
                        useVariableReinitialization,
                        fastRestartOnSwapFail,
                        metrics);
        Task<OptimisticApkSwapper.OverlayUpdate> overlayUpdate =
                runner.create(
                        Tasks.COLLECT_SWAP_DATA,
                        OptimisticApkSwapper.OverlayUpdate::new,
                        speculativeDump,
                        changedClasses,
                        extractedFiles);
        Task<OptimisticApkSwapper.SwapResult> swapResultTask =
                runner.create(
                        Tasks.OPTIMISTIC_SWAP,
                        swapper::optimisticSwap,
                        packageName,
                        pids,
                        arch,
                        overlayUpdate);

        TaskResult result = runner.run();
        result.getMetrics().forEach(metrics::add);

        if (!result.isSuccess()) {
            throw result.getException();
        }

        // Update the database with the entire new apk. In the normal case this should
        // be a no-op because the dexes that were modified were extracted at comparison time.
        // However if the compare task doesn't get to execute we still update the database.
        // Note we artificially block this task until swap is done.
        runner.create(Tasks.CACHE, DexSplitter::cache, splitter, newFiles);
        runner.create(
                Tasks.DEPLOY_CACHE_STORE,
                (serial, pkgName, files, swap) ->
                        deployCache.store(serial, pkgName, files, swap.overlayId),
                deviceSerial,
                packageName,
                newFiles,
                swapResultTask);

        // Wait only for swap to finish
        runner.runAsync();

        Result deployResult = new Result();
        // TODO: May be notify user we IWI'ed.
        // deployResult.didIwi = true;
        if (fastRestartOnSwapFail && !swapResultTask.get().hotswapSucceeded) {
            deployResult.needsRestart = true;
        }
        return deployResult;
    }

    private DeploymentCacheDatabase.Entry dumpWithCache(
            String packageName, String deviceSerial, List<Apk> apks) throws DeployerException {
        String serial = adb.getSerial();
        DeploymentCacheDatabase.Entry entry = deployCache.get(serial, packageName);
        if (entry != null && !entry.getOverlayId().isBaseInstall()) {
            return entry;
        }

        // If we have no cache data or an install without OID file, we use the classic dump.
        ApplicationDumper dumper = new ApplicationDumper(installer);
        List<Apk> deviceApks = dumper.dump(apks).apks;
        deployCache.store(serial, packageName, deviceApks, new OverlayId(deviceApks));
        return deployCache.get(serial, packageName);
    }

    public boolean supportsNewPipeline() {
        // New API level is not yet determined yet so this is only for fake android tests:
        if (adb.getVersion().getApiLevel() > 29) {
            return true;
        }

        // This check works on real device only.
        String codeName = adb.getVersion().getCodename();
        return useOptimisticSwap && codeName != null && codeName.equals("R");
    }
}
