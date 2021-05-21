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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Deployer {

    public static final String BASE_DIRECTORY = "/data/local/tmp/.studio";
    public static final String INSTALLER_DIRECTORY = BASE_DIRECTORY + "/bin";
    public static final String INSTALLER_TMP_DIRECTORY = BASE_DIRECTORY + "/tmp";

    private final AdbClient adb;
    private final SqlApkFileDatabase dexDb;
    private final DeploymentCacheDatabase deployCache;
    private final Installer installer;
    private final TaskRunner runner;
    private final UIService service;
    private final MetricsRecorder metrics;
    private final ILogger logger;
    private final DeployerOption options;

    public Deployer(
            AdbClient adb,
            DeploymentCacheDatabase deployCache,
            SqlApkFileDatabase dexDb,
            TaskRunner runner,
            Installer installer,
            UIService service,
            MetricsRecorder metrics,
            ILogger logger,
            DeployerOption options) {
        this.adb = adb;
        this.deployCache = deployCache;
        this.dexDb = dexDb;
        this.runner = runner;
        this.installer = installer;
        this.service = service;
        this.metrics = metrics;
        this.logger = logger;
        this.options = options;
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
        OPTIMISTIC_INSTALL,

        // New DDMLib
        GET_PIDS,
        GET_ARCH,
        COMPUTE_FRESHINSTALL_OID,

        // Coroutine debugger
        INSTALL_COROUTINE_DEBUGGER
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
        public boolean coroutineDebuggerInstalled = false;
    }

    /**
     * Persists the content of the provide APKs on the device. If the operation succeeds, the dex
     * files from the APKs are stored in the dex database.
     *
     * <p>The implementation of the persistence depends on target device API level and the values of
     * the passed-in flags useOptimisticSwap and useOptimisticInstall.
     *
     * <p>When installing on a device with an API level < 30 (pre-R), or if the useOptimisticSwap
     * flag is set to false, a standard install is performed.
     *
     * <p>If useOptimisticSwap is true, but useOptimisticInstall is false, a standard delta install
     * is performed, and the currently cached deployment information for the application/target
     * device is dropped.
     *
     * <p>If both flags are true, an optimistic install is attempted. If the optimistic install
     * fails for any reason, the deployment falls back to the pre-R install path.
     *
     * <p>Setting useOptimisticInstall to true should never impact the success of this method;
     * failures from optimistic installations are recorded in metrics but not thrown up to the top
     * level.
     */
    public Result install(
            String packageName, List<String> apks, InstallOptions options, InstallMode installMode)
            throws DeployerException {
        try (Trace ignored = Trace.begin("install")) {
            if (supportsNewPipeline()) {
                installMode =
                        installMode == InstallMode.DELTA ? InstallMode.DELTA_NO_SKIP : installMode;
                return maybeOptimisticInstall(packageName, apks, options, installMode);
            }

            ApkInstaller apkInstaller = new ApkInstaller(adb, service, installer, logger);

            // Inputs
            Task<String> pkgName = runner.create(packageName);
            Task<List<String>> paths = runner.create(apks);

            // Parse the apks
            Task<List<Apk>> apkList =
                    runner.create(Tasks.PARSE_PATHS, new ApkParser()::parsePaths, paths);

            Result result = new Result();
            result.skippedInstall =
                    !apkInstaller.install(
                            packageName, apks, options, installMode, metrics.getDeployMetrics());

            Task<Boolean> installCoroutineDebugger = null;
            if (useCoroutineDebugger()) {
                // TODO(b/185399333): instead of using apks, add  task to get arch by opening apk
                // has to happen after install
                installCoroutineDebugger =
                        runner.create(
                                Tasks.INSTALL_COROUTINE_DEBUGGER,
                                this::installCoroutineDebugger,
                                pkgName,
                                apkList);
            }

            CachedDexSplitter splitter = new CachedDexSplitter(dexDb, new D8DexSplitter());
            runner.create(Tasks.CACHE, splitter::cache, apkList);
            runner.runAsync();

            if (installCoroutineDebugger != null) {
                installCoroutineDebugger.get();
            }
            return result;
        }
    }

    private Result maybeOptimisticInstall(
            String pkgName,
            List<String> paths,
            InstallOptions installOptions,
            InstallMode installMode)
            throws DeployerException {
        Task<String> packageName = runner.create(pkgName);
        Task<String> deviceSerial = runner.create(adb.getSerial());
        Task<List<Apk>> apks =
                runner.create(Tasks.PARSE_PATHS, new ApkParser()::parsePaths, runner.create(paths));

        Task<Boolean> installCoroutineDebugger = null;

        boolean installSuccess = false;
        if (!options.optimisticInstallSupport.isEmpty()) {
            OptimisticApkInstaller apkInstaller =
                    new OptimisticApkInstaller(
                            installer, adb, deployCache, metrics, options, logger);
            Task<OverlayId> overlayId =
                    runner.create(
                            Tasks.OPTIMISTIC_INSTALL, apkInstaller::install, packageName, apks);

            if (useCoroutineDebugger()) {
                // TODO(b/185399333): instead of using apks, add task to get arch by opening
                // apk
                // has to happen after install
                installCoroutineDebugger =
                        runner.create(
                                Tasks.INSTALL_COROUTINE_DEBUGGER,
                                (String pkg, List<Apk> apksList, OverlayId overlay) ->
                                        installCoroutineDebugger(pkg, apksList),
                                packageName,
                                apks,
                                overlayId);
            }

            TaskResult result = runner.run();
            installSuccess = result.isSuccess();
            if (installSuccess) {
                runner.create(
                        Tasks.DEPLOY_CACHE_STORE,
                        deployCache::store,
                        deviceSerial,
                        packageName,
                        apks,
                        overlayId);
            }
            result.getMetrics().forEach(metrics::add);
        }

        // This needs to happen no matter which path we're on, so create the task now.
        CachedDexSplitter splitter = new CachedDexSplitter(dexDb, new D8DexSplitter());
        runner.create(Tasks.CACHE, splitter::cache, apks);

        Result deployResult = new Result();
        if (!installSuccess) {
            ApkInstaller apkInstaller = new ApkInstaller(adb, service, installer, logger);
            deployResult.skippedInstall =
                    !apkInstaller.install(
                            pkgName,
                            paths,
                            installOptions,
                            installMode,
                            metrics.getDeployMetrics());

            if (useCoroutineDebugger()) {
                // TODO(b/185399333): instead of using apks, add  task to get arch by opening apk
                // has to happen after install
                installCoroutineDebugger =
                        runner.create(
                                Tasks.INSTALL_COROUTINE_DEBUGGER,
                                this::installCoroutineDebugger,
                                packageName,
                                apks);
            }

            runner.create(
                    Tasks.DEPLOY_CACHE_STORE, deployCache::invalidate, deviceSerial, packageName);
        }

        runner.runAsync();

        if (installCoroutineDebugger != null) {
            deployResult.coroutineDebuggerInstalled = installCoroutineDebugger.get();
        }

        return deployResult;
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
            if (supportsNewPipeline() && options.useOptimisticResourceSwap) {
                return optimisticSwap(apks, /* Restart Activity */ true, ImmutableMap.of());
            } else {
                return swap(apks, true /* Restart Activity */, ImmutableMap.of());
            }
        }
    }

    private boolean installCoroutineDebugger(String packageName, List<Apk> apk)
            throws DeployerException {
        Deploy.Arch arch = adb.getArchFromApk(apk);
        try {
            Deploy.InstallCoroutineAgentResponse response =
                    installer.installCoroutineAgent(packageName, arch);
            return response.getStatus() == Deploy.InstallCoroutineAgentResponse.Status.OK;
        } catch (IOException e) {
            throw DeployerException.installerIoException(e);
        }
    }

    /** Returns true if coroutine debugger is enabled in DeployerOptions and API version is P+ */
    private boolean useCoroutineDebugger() {
        // --attach-agent was added on API 28. Furthermore before API 28 there is no guarantee
        // for the code_cache folder to be created during app install.
        return this.options.enableCoroutineDebugger
                && adb.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.P);
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
                        Tasks.EXTRACT_APK_ENTRIES,
                        new ApkEntryExtractor(filter)::extractFromDiffs,
                        diffs);

        // Verify the changes are swappable and get only the dexes that we can change
        Task<List<FileDiff>> dexDiffs =
                runner.create(Tasks.VERIFY, new SwapVerifier()::verify, newFiles, diffs, restart);

        // Compare the local vs remote dex files.
        Task<DexComparator.ChangedClasses> changedClasses =
                runner.create(Tasks.COMPARE, new DexComparator()::compare, dexDiffs, splitter);

        // Perform the swap.
        OptimisticApkSwapper swapper =
                new OptimisticApkSwapper(installer, redefiners, argRestart, options, metrics);
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
        if (options.fastRestartOnSwapFail && !swapResultTask.get().hotswapSucceeded) {
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
        return options.useOptimisticSwap
                && adb.getVersion().getApiLevel() >= AndroidVersion.VersionCodes.R;
    }
}
