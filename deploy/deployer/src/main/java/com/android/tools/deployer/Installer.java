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
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Installer {

    private static final String NAME = "installer";
    public static final String INSTALLER_PATH = "/data/local/tmp/.studio/bin/" + NAME;
    private final AdbClient adb;
    private final String path;

    /**
     * The on-device binary facade.
     *
     * @param path a path to a directory with all the per-abi android executables.
     * @param adb the {@code AdbClient} to use.
     */
    public Installer(String path, AdbClient adb) {
        this.path = path;
        this.adb = adb;
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
                .startsWith("/system/bin/sh: " + INSTALLER_PATH + ": not found")) {
            prepare();
            adb.shell(cmd, data);
        }
    }

    public void prepare() {
        File file = null;
        List<String> abis = adb.getAbis();
        for (String abi : abis) {
            File candidate = new File(path, abi + "/" + NAME);
            if (candidate.exists()) {
                file = candidate;
                break;
            }
        }
        if (file == null) {
            throw new DeployerException(
                    "Cannot find suitable installer for abis: " + Arrays.toString(abis.toArray()));
        }
        adb.push(file.getAbsolutePath(), INSTALLER_PATH);
    }
}
