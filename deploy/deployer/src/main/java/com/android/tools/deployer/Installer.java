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

import com.android.tools.deploy.proto.Deploy;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Installer {

    public static final String INSTALLER_BINARY_NAME = "installer";
    public static final String INSTALLER_DIRECTORY = "/data/local/tmp/.studio/bin";
    public static final String INSTALLER_PATH = INSTALLER_DIRECTORY + "/" + INSTALLER_BINARY_NAME;
    public static final String ANDROID_EXECUTABLE_PATH = "/tools/base/deploy/installer/android";
    private final AdbClient adb;
    private final String installersFolder;

    /**
     * The on-device binary facade.
     *
     * @param path a path to a directory with all the per-abi android executables.
     * @param adb the {@code AdbClient} to use.
     */
    public Installer(AdbClient adb) {
        this(null, adb);
    }

    public Installer(String installersFolder, AdbClient adb) {
        this.adb = adb;
        this.installersFolder = installersFolder;
    }

    public Map<String, ApkDump> dump(String packageName) throws IOException {
        String[] cmd = {INSTALLER_PATH, "dump", packageName};
        retryShell(cmd, null);

        // Pull entire directory of dumps from remote device.
        String remoteDirectory = "/data/local/tmp/.studio/dumps/" + packageName;
        File directory = Files.createTempDirectory(".dumps").toFile();
        adb.pull(remoteDirectory, directory.toString());
        File packageDumps = new File(directory, packageName);

        Map<String, ApkDump> dumps = new HashMap<>();
        File[] files = packageDumps.listFiles();
        if (files == null) {
            throw new IOException("Cannot list files on " + packageDumps.toString());
        }

        for (File file : files) {
            if (file.isFile()) {
                if (file.isFile() && file.getName().endsWith(".remotecd")) {
                    String name = file.getName().replaceFirst("\\.remotecd$", "");
                    File signatureFile = new File(file.getParent(), name + ".remoteblock");
                    byte[] contentDirectory = Files.readAllBytes(file.toPath());
                    byte[] signature =
                            signatureFile.exists()
                                    ? Files.readAllBytes(signatureFile.toPath())
                                    : null;
                    dumps.put(name, new ApkDump(name, contentDirectory, signature));
                }
            }
        }
        return dumps;
    }

    public void swap(Deploy.SwapRequest request) {
        byte[] data = request.toByteArray();
        String[] cmd = {INSTALLER_PATH, "swap", "0", String.valueOf(data.length)};
        retryShell(cmd, data);
    }

    public void retryShell(String[] cmd, byte[] data) {
        byte[] output = adb.shell(cmd, data);
        // TODO: Detect error when protobuf parsing fails
        if (new String(output, Charsets.UTF_8)
                .startsWith("/system/bin/sh: " + INSTALLER_PATH + ":")) {
            prepare();
            adb.shell(cmd, data);
        }
    }

    public void prepare() {
        File installerFile = null;
        List<String> abis = adb.getAbis();
        // The jar archive contains the android executables:
        // tools/base/deploy/installer/android/x86/installer
        // tools/base/deploy/installer/android/armeabi-v7a/installer
        // tools/base/deploy/installer/android/arm64-v8a/installer
        // Loop over the supported architectures and push it to the drive.
        // TODO: Factor in that an app may be running in 32-bit on a 64-bit device. In this case
        //       we will have to push two binaries. Or we could cut support of 32-bit apps.
        for (String abi : abis) {
            String installerJarPath = abi + "/" + INSTALLER_BINARY_NAME;
            try (InputStream inputStream = getResource(installerJarPath)) {
                // Do we have the device architecture in the jar?
                if (inputStream == null) {
                    continue;
                }
                System.out.println("Pushed installer '" + installerJarPath + "'");
                // We have a match, extract it in a tmp file.
                installerFile = File.createTempFile(".studio_installer", abi);
                Files.copy(
                        inputStream,
                        Paths.get(installerFile.getAbsolutePath()),
                        StandardCopyOption.REPLACE_EXISTING);
                break;
            } catch (IOException e) {
                throw new DeployerException(
                        "Unable to extract installer binary to push to device.", e);
            }
        }
        if (installerFile == null) {
            throw new DeployerException(
                    "Cannot find suitable installer for abis: " + Arrays.toString(abis.toArray()));
        }

        adb.shell(new String[] {"mkdir", "-p", INSTALLER_DIRECTORY}, null);
        adb.push(installerFile.getAbsolutePath(), INSTALLER_PATH);
        adb.shell(new String[] {"chmod", "+x", INSTALLER_PATH}, null);
    }

    InputStream getResource(String path) throws FileNotFoundException {
        InputStream stream = null;
        if (this.installersFolder == null) {
            stream = Installer.class.getResourceAsStream(ANDROID_EXECUTABLE_PATH + "/" + path);
        } else {
            stream = new FileInputStream(installersFolder + "/" + path);
        }
        return stream;
    }
}
