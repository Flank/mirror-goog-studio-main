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
import com.android.utils.ILogger;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeployerRunner {

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
        Trace.consume(new SystraceConsumer("/tmp/report.json", LOGGER));
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

    public void run(String[] args) throws IOException {
        // Check that we have the parameters we need to run.
        if (args.length < 2) {
            printUsage();
            return;
        }

        String command = args[0];
        String packageName = args[1];
        ArrayList<String> apks = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            apks.add(args[i]);
        }

        Trace.begin("getDevice()");
        IDevice device = getDevice();
        if (device == null) {
            LOGGER.error(null, "%s", "No device found.");
            return;
        }
        Trace.end();

        // Run
        AdbClient adb = new AdbClient(device, LOGGER);
        Installer installer = new Installer(adb, LOGGER);
        ExecutorService service = Executors.newFixedThreadPool(5);
        Deployer deployer = new Deployer(adb, db, new TaskRunner(service), installer);
        if (command.equals("install")) {
            deployer.install(apks);
        } else {
            try {
                if (command.equals("fullswap")) {
                    deployer.fullSwap(packageName, apks);
                } else if (command.equals("codeswap")) {
                    deployer.codeSwap(packageName, apks);
                }
            } catch (DeployerException e) {
                LOGGER.error(e, "Error executing the deployer");
            }
        }
        service.shutdown();
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
}
