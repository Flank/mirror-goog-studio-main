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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableMap;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class DeployerRunner implements UIService {

    private static final ILogger LOGGER = Logger.getLogger();
    private static final String DB_PATH = "/tmp/studio.db";
    private final ApkFileDatabase db;

    // Run it from bazel with the following command:
    // bazel run :deployer.runner org.wikipedia.alpha PATH_TO_APK1 PATH_TO_APK2
    public static void main(String[] args) throws IOException {
        Trace.start();
        Trace.begin("main");
        tracedMain(args);
        Trace.end();
        Trace.flush();
    }

    public static void tracedMain(String[] args) throws IOException {
        try {
            ApkFileDatabase db = new SqlApkFileDatabase(new File(DB_PATH));
            DeployerRunner runner = new DeployerRunner(db);
            runner.run(args);
        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    public DeployerRunner(ApkFileDatabase db) {
        this.db = db;
    }

    public List<String> run(String[] args) {
        // Check that we have the parameters we need to run.
        if (args.length < 2) {
            printUsage();
            return Collections.emptyList();
        }

        DeployRunnerParameters parameters = DeployRunnerParameters.parse(args);
        String packageName = parameters.get(0);

        ArrayList<String> apks = new ArrayList<>();
        for (int i = 1; i < parameters.size(); i++) {
            apks.add(parameters.get(i));
        }

        Trace.begin("getDevice()");
        IDevice device = getDevice();
        if (device == null) {
            LOGGER.error(null, "%s", "No device found.");
            return Collections.emptyList();
        }
        Trace.end();

        // Run
        AdbClient adb = new AdbClient(device, LOGGER);
        Installer installer = new AdbInstaller(parameters.getInstallersPath(), adb, LOGGER);
        ExecutorService service = Executors.newFixedThreadPool(5);
        TaskRunner runner = new TaskRunner(service);
        Deployer deployer = new Deployer(adb, db, runner, installer, this, LOGGER);
        List<String> metrics;
        try {
            if (parameters.getCommand() == DeployRunnerParameters.Command.INSTALL) {
                InstallOptions.Builder options = InstallOptions.builder().setAllowDebuggable();
                if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
                    options.setGrantAllPermissions();
                }

                Deployer.InstallMode installMode = Deployer.InstallMode.FULL;
                if (parameters.isDeltaInstall()) {
                    installMode = Deployer.InstallMode.DELTA;
                }
                metrics =
                        collectIds(
                                deployer.install(packageName, apks, options.build(), installMode));
            } else if (parameters.getCommand() == DeployRunnerParameters.Command.FULLSWAP) {
                metrics = collectTaskIds(deployer.fullSwap(apks));
            } else if (parameters.getCommand() == DeployRunnerParameters.Command.CODESWAP) {
                metrics = collectTaskIds(deployer.codeSwap(apks, ImmutableMap.of()));
            } else {
                throw new RuntimeException("UNKNOWN command");
            }
            runner.run();
        } catch (DeployerException e) {
            e.printStackTrace(System.out);
            LOGGER.error(e, "Error executing the deployer");
            return Collections.emptyList();
        } finally {
            service.shutdown();
        }
        return metrics;
    }

    private List<String> collectTaskIds(List<TaskRunner.Task<?>> tasks) {
        return tasks.stream().map(TaskRunner.Task::getName).collect(Collectors.toList());
    }

    private List<String> collectIds(List<InstallMetric> install) {
        return install.stream().map(InstallMetric::getName).collect(Collectors.toList());
    }

    private IDevice getDevice() {
        // Get an IDevice
        AndroidDebugBridge.init(false);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        IDevice[] devices = bridge.getDevices();
        if (devices.length < 1) {
            return null;
        }
        return devices[0];
    }

    private static void printUsage() {
        LOGGER.info("Usage: DeployerRunner packageName [packageBase,packageSplit1,...]");
    }

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
