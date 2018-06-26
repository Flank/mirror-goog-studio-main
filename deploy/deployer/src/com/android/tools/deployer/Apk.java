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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Apk {

    public enum ApkEntryStatus {
        CREATED,
        MODIFIED,
        DELETED
    }

    private final String localApkPath;

    public Apk(String path) {
        localApkPath = path;
    }

    public HashMap<String, ApkEntryStatus> diff(Path remoteCDDump) throws DeployerException {
        HashMap<String, Long> localCrcs = extractLocalCrcs();
        HashMap<String, Long> remoteCrcs = extractRemoteCrcs(remoteCDDump);

        HashMap<String, ApkEntryStatus> diffs = new HashMap<>();
        for (String key : localCrcs.keySet()) {
            if (!remoteCrcs.containsKey(key)) {
                diffs.put(key, ApkEntryStatus.DELETED);
            } else {
                // Check if modified.
                long remoteCrc = remoteCrcs.get(key);
                long localCrc = localCrcs.get(key);
                if (remoteCrc != localCrc) {
                    diffs.put(key, ApkEntryStatus.MODIFIED);
                }
            }
        }

        for (String key : remoteCrcs.keySet()) {
            if (!localCrcs.containsKey(key)) {
                diffs.put(key, ApkEntryStatus.CREATED);
            }
        }
        return diffs;
    }

    public String getPath() {
        return localApkPath;
    }

    private HashMap<String, Long> extractLocalCrcs() throws DeployerException {
        HashMap<String, Long> localCrcs = new HashMap<>();
        try {
            Path path = FileSystems.getDefault().getPath(localApkPath);
            if (!Files.exists(path)) {
                throw new DeployerException("APK file'" + localApkPath + "' does not exists.");
            }
            ZipFile zipFile = new ZipFile(localApkPath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                long crc = entry.getCrc();
                localCrcs.put(name, crc);
            }
        } catch (IOException e) {
            throw new DeployerException(
                    "Unable to retrieve local crcs for apk: '" + localApkPath + "'", e);
        }
        return localCrcs;
    }

    private HashMap<String, Long> extractRemoteCrcs(Path remoteCDDump) throws DeployerException {
        HashMap<String, Long> remoteCrcs = new HashMap<>();
        ZipCentralDirectory zcd = new ZipCentralDirectory(remoteCDDump.toString());
        zcd.getCrcs(remoteCrcs);
        return remoteCrcs;
    }
}
