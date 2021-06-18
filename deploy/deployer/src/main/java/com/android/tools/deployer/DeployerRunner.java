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
import com.android.tools.deployer.tasks.Canceller;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DeployerRunner {

    private static final int SUCCESS = 0;

    // These values are > 1000 in order to prevent collision with the DeployerException.Error
    // ordinal that is returned if a DeployerException is thrown during deployment.
    private static final int ERR_SPECIFIED_DEVICE_NOT_FOUND = 1002;
    private static final int ERR_NO_MATCHING_DEVICE = 1003;
    private static final int ERR_BAD_ARGS = 1004;

    private static final String DEX_DB_PATH = "/tmp/studio_dex.db";
    private static final String DEPLOY_DB_PATH = "/tmp/studio_deploy.db";

    private final DeploymentCacheDatabase cacheDb;
    private final SqlApkFileDatabase dexDb;
    private final MetricsRecorder metrics;
    private final UIService service;

    // Run it from bazel with the following command:
    // bazel run :deployer.runner INSTALL --device=<target device> <package name> <apk 1> <apk 2>
    // ... <apk N>
    public static void main(String[] args) {
        Trace.start();
        Trace.begin("main");
        int errorCode = tracedMain(args, new StdLogger(StdLogger.Level.VERBOSE));
        Trace.end();
        Trace.flush();
        System.exit(errorCode);
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
        if (args.length < 3) {
            logger.info(
                    "Usage: %s {install | codeswap | fullswap} [--device=<name>] packageName baseApk [splitApk1, splitApk2, ...]");
            return ERR_BAD_ARGS;
        }

        try {
            DeployRunnerParameters parameters = DeployRunnerParameters.parse(args);
            Map<String, IDevice> devices = waitForDevices(parameters.getTargetDevices(), logger);

            if (devices.isEmpty()) {
                logger.error(null, "No device connected to ddmlib");
                return ERR_NO_MATCHING_DEVICE;
            }

            for (String expectedDevice : parameters.getTargetDevices()) {
                if (!devices.containsKey(expectedDevice)) {
                    logger.error(null, "Could not find specified device: %s", expectedDevice);
                    return ERR_SPECIFIED_DEVICE_NOT_FOUND;
                }
            }

            for (IDevice device : devices.values()) {
                int status = run(device, parameters, logger);
                if (status != SUCCESS) {
                    logger.error(null, "Error deploying to device: %s", device.getName());
                    return status;
                }
            }

            return SUCCESS;
        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    // Left in to support how DeployService calls us.
    public int run(IDevice device, String[] args, ILogger logger) {
        return run(device, DeployRunnerParameters.parse(args), logger);
    }

    private int run(IDevice device, DeployRunnerParameters parameters, ILogger logger) {
        EnumSet<ChangeType> optimisticInstallSupport = EnumSet.noneOf(ChangeType.class);
        if (parameters.isOptimisticInstall()) {
            optimisticInstallSupport.add(ChangeType.DEX);
            optimisticInstallSupport.add(ChangeType.NATIVE_LIBRARY);
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
                deployer.install(
                        parameters.getApplicationId(),
                        parameters.getApks(),
                        options.build(),
                        installMode);
            } else if (parameters.getCommand() == DeployRunnerParameters.Command.FULLSWAP) {
                deployer.fullSwap(parameters.getApks(), Canceller.NO_OP);
            } else if (parameters.getCommand() == DeployRunnerParameters.Command.CODESWAP) {
                deployer.codeSwap(parameters.getApks(), ImmutableMap.of(), Canceller.NO_OP);
            } else {
                throw new RuntimeException("UNKNOWN command");
            }
            runner.run(Canceller.NO_OP);
        } catch (DeployerException e) {
            logger.error(
                    e, "Not possible to execute " + parameters.getCommand().name().toLowerCase());
            logger.warning(e.getDetails());
            return e.getError().ordinal();
        } finally {
            service.shutdown();
        }
        return SUCCESS;
    }

    public List<DeployMetric> getMetrics() {
        return metrics.getDeployMetrics();
    }

    private Map<String, IDevice> waitForDevices(List<String> targetDevices, ILogger logger) {
        try (Trace unused = Trace.begin("waitForDevices()")) {
            int expectedDevices = targetDevices.isEmpty() ? 1 : targetDevices.size();
            CountDownLatch latch = new CountDownLatch(expectedDevices);
            ConcurrentHashMap<String, IDevice> devices = new ConcurrentHashMap<>();

            AndroidDebugBridge.IDeviceChangeListener listener =
                    new AndroidDebugBridge.IDeviceChangeListener() {
                        @Override
                        public void deviceConnected(IDevice device) {
                            if (targetDevices.isEmpty()
                                    || targetDevices.contains(device.getName())) {
                                logger.info("FOUND DEVICE: " + device.getName());
                                devices.put(device.getName(), device);
                                latch.countDown();
                            }
                        }

                        @Override
                        public void deviceDisconnected(IDevice device) {}

                        @Override
                        public void deviceChanged(IDevice device, int changeMask) {}
                    };

            AndroidDebugBridge.init(AdbInitOptions.DEFAULT);
            AndroidDebugBridge.addDeviceChangeListener(listener);

            // This needs to be done *after* we add the listener, or else we risk missing devices.
            if (AndroidDebugBridge.createBridge(5, TimeUnit.SECONDS) == null) {
                logger.error(null, "Could not create debug bridge");
                return Collections.emptyMap();
            }

            try {
                if (latch.await(30, TimeUnit.SECONDS)) {
                    return devices;
                }
                return Collections.emptyMap();
            } catch (InterruptedException e) {
                return Collections.emptyMap();
            } finally {
                AndroidDebugBridge.removeDeviceChangeListener(listener);
            }
        }
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
