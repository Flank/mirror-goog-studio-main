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
import com.android.tools.deploy.swapper.DexArchiveDatabase;
import com.android.tools.deploy.swapper.InMemoryDexArchiveDatabase;
import com.android.utils.ILogger;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

class InstallerNotifier implements Deployer.InstallerCallBack {
    @Override
    public void onInstallationFinished(boolean status) {}
}

public class DeployerRunner {

    private static final ILogger LOGGER = Logger.getLogger(DeployerRunner.class);
    private static final String DB_PATH = "/tmp/studio.db";
    private final DexArchiveDatabase db;

    static InMemoryDexArchiveDatabase readDB() throws IOException {
        if (!Files.exists(Paths.get(DB_PATH))) {
            InMemoryDexArchiveDatabase db = new InMemoryDexArchiveDatabase();
            saveDB(db);
            return db;
        }

        try (FileInputStream file = new FileInputStream(DB_PATH);
                ObjectInputStream in = new ObjectInputStream(file)) {
            InMemoryDexArchiveDatabase db = (InMemoryDexArchiveDatabase) in.readObject();
            return db;
        } catch (InvalidClassException e) {
            // This may occur if the layout of InMemoryDexArchiveDatabase has changed since
            // last run.
            Files.delete(Paths.get(DB_PATH));
            return readDB();
        } catch (IOException | ClassNotFoundException e) {
            throw new DeployerException("Unable to load database", e);
        }
    }

    static void saveDB(InMemoryDexArchiveDatabase db) {
        try (FileOutputStream file = new FileOutputStream(DB_PATH);
                ObjectOutputStream out = new ObjectOutputStream(file); ) {
            out.writeObject(db);
        } catch (IOException e) {
            throw new DeployerException("Unable to save database", e);
        }
    }

    // Run it from bazel with the following command:
    // bazel run :deployer.runner org.wikipedia.alpha PATH_TO_APK1 PATH_TO_APK2
    public static void main(String[] args)
            throws IOException, ClassNotFoundException, InterruptedException {
        InMemoryDexArchiveDatabase db = null;
        try {
            db = readDB();
            DeployerRunner runner = new DeployerRunner(db);
            runner.run(args);
        } catch (RuntimeException e) {
            e.printStackTrace(System.out);
        } finally {
            saveDB(db);
            AndroidDebugBridge.terminate();
        }
    }

    public DeployerRunner(DexArchiveDatabase db) {
        this.db = db;
    }

    public void run(String[] args) throws IOException {
        // Check that we have the parameters we need to run.
        if (args.length < 2) {
            printUsage();
            return;
        }

        // Get package name.
        String packageName = args[0];

        // Get all apks with base and splits.
        ArrayList<String> apks = new ArrayList();
        for (int i = 1; i < args.length; i++) {
            apks.add(args[i]);
        }

        IDevice device = getDevice();
        if (device == null) {
            LOGGER.error(null, "%s", "No device found.");
            return;
        }

        // Run
        AdbClient adb = new AdbClient(device);
        Installer installer = new Installer(adb);
        Deployer deployer =
                new Deployer(packageName, apks, new InstallerNotifier(), adb, db, installer);
        Deployer.RunResponse response = deployer.fullSwap();

        if (response.status != Deployer.RunResponse.Status.OK) {
            LOGGER.info("%s", response.errorMessage);
            return;
        }

        // Output apks differences found.
        for (String apkName : response.result.keySet()) {
            Deployer.RunResponse.Analysis analysis = response.result.get(apkName);
            for (String key : analysis.diffs.keySet()) {
                ApkDiffer.ApkEntryStatus status = analysis.diffs.get(key);
                switch (status) {
                    case CREATED:
                        LOGGER.info("%s has been CREATED.", key);
                        break;
                    case DELETED:
                        LOGGER.info("%s has been DELETED.", key);
                        break;
                    case MODIFIED:
                        LOGGER.info("%s has been MODIFIED.", key);
                        break;
                }
            }
        }
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
