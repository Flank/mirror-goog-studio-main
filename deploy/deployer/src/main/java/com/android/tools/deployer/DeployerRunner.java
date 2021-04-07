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

import com.android.ddmlib.AdbInitOptions;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeployerRunner {

    private static final String DEX_DB_PATH = "/tmp/studio_dex.db";
    private static final String DEPLOY_DB_PATH = "/tmp/studio_deploy.db";

    private final DeploymentCacheDatabase cacheDb;
    private final SqlApkFileDatabase dexDb;
    private final MetricsRecorder metrics;
    private final UIService service;

    // Run it from bazel with the following command:
    // bazel run :deployer.runner org.wikipedia.alpha PATH_TO_APK1 PATH_TO_APK2
    public static void main(String[] args) {
        Trace.start();
        Trace.begin("main");
        int errorcode = tracedMain(args, new StdLogger(StdLogger.Level.VERBOSE));
        Trace.end();
        Trace.flush();
        if (errorcode != 0) {
            System.exit(errorcode);
        }
    }

    public static int tracedMain(String[] args, ILogger logger) {
        DeployerRunner runner =
                new DeployerRunner(
                        new File(DEPLOY_DB_PATH), new File(DEX_DB_PATH), new CommandLineService());
        return runner.run(args, logger);
    }

    public DeployerRunner(File deployCacheFile, File databaseFile, UIService service) {
        this(
                new DeploymentCacheDatabase(deployCacheFile),
                new SqlApkFileDatabase(databaseFile, null),
                service);
    }

    @VisibleForTesting
    public DeployerRunner(
            DeploymentCacheDatabase cacheDb, SqlApkFileDatabase dexDb, UIService service) {
        this.cacheDb = cacheDb;
        this.dexDb = dexDb;
        this.service = service;
        this.metrics = new MetricsRecorder();
    }

    public int run(String[] args, ILogger logger) {
        try {
            AndroidDebugBridge bridge = initDebugBridge();
            Trace.begin("getDevice()");
            IDevice device = getDevice(bridge);
            if (device == null) {
                logger.error(null, "%s", "No device found.");
                return -2;
            }
            Trace.end();
            return run(device, args, logger);
        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    public int run(IDevice device, String[] args, ILogger logger) {
        // Check that we have the parameters we need to run.
        if (args.length < 2) {
            logger.info("Usage: DeployerRunner packageName [packageBase,packageSplit1,...]");
            return -1;
        }

        DeployRunnerParameters parameters = DeployRunnerParameters.parse(args);
        String packageName = parameters.get(0);

        ArrayList<String> apks = new ArrayList<>();
        for (int i = 1; i < parameters.size(); i++) {
            apks.add(parameters.get(i));
        }

        EnumSet<ChangeType> optimisticInstallSupport = EnumSet.noneOf(ChangeType.class);
        if (parameters.isOptimisticInstall()) {
            optimisticInstallSupport.add(ChangeType.DEX);
            optimisticInstallSupport.add(ChangeType.NATIVE_LIBRARY);
            optimisticInstallSupport.add(ChangeType.RESOURCE);
        }

        metrics.getDeployMetrics().clear();
        AdbClient adb = new AdbClient(device, logger);
        Installer installer =
                new AdbInstaller(
                        parameters.getInstallersPath(), adb, metrics.getDeployMetrics(), logger);
        ExecutorService service = Executors.newFixedThreadPool(5);
        TaskRunner runner = new TaskRunner(service);
        DeployerOption deployerOption =
                new DeployerOption.Builder()
                        .setUseOptimisticSwap(true)
                        .setUseOptimisticResourceSwap(true)
                        .setUseStructuralRedefinition(true)
                        .setUseVariableReinitialization(true)
                        .setFastRestartOnSwapFail(false)
                        .setOptimisticInstallSupport(optimisticInstallSupport)
                        .enableCoroutineDebugger(true)
                        .build();

        Deployer deployer =
                new Deployer(
                        adb,
                        cacheDb,
                        dexDb,
                        runner,
                        installer,
                        this.service,
                        metrics,
                        logger,
                        deployerOption);
        try {
            if (parameters.getCommand() == DeployRunnerParameters.Command.INSTALL) {
                InstallOptions.Builder options = InstallOptions.builder().setAllowDebuggable();
                if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
                    options.setGrantAllPermissions();
                }

                Deployer.InstallMode installMode = Deployer.InstallMode.DELTA;
                if (parameters.isForceFullInstall()) {
                    installMode = Deployer.InstallMode.FULL;
                }
                deployer.install(packageName, apks, options.build(), installMode);
            } else if (parameters.getCommand() == DeployRunnerParameters.Command.FULLSWAP) {
                deployer.fullSwap(apks);
            } else if (parameters.getCommand() == DeployRunnerParameters.Command.CODESWAP) {
                deployer.codeSwap(apks, ImmutableMap.of());
            } else {
                throw new RuntimeException("UNKNOWN command");
            }
            runner.run();
        } catch (DeployerException e) {
            logger.error(
                    e, "Not possible to execute " + parameters.getCommand().name().toLowerCase());
            logger.warning(e.getDetails());
            return e.getError().ordinal();
        } finally {
            service.shutdown();
        }
        return 0;
    }

    public List<DeployMetric> getMetrics() {
        return metrics.getDeployMetrics();
    }

    private IDevice getDevice(AndroidDebugBridge bridge) {
        IDevice[] devices = bridge.getDevices();
        if (devices.length < 1) {
            return null;
        }
        return devices[0];
    }

    private AndroidDebugBridge initDebugBridge() {
        AndroidDebugBridge.init(AdbInitOptions.DEFAULT);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return bridge;
    }

    static class CommandLineService implements UIService {
        @Override
        public boolean prompt(String message) {
            System.err.println(message + ". Y/N?");
            try (Scanner scanner = new Scanner(System.in)) {
                return scanner.nextLine().equalsIgnoreCase("y");
            }
        }

        @Override
        public void message(String message) {
            System.err.println(message);
        }
    }
}
