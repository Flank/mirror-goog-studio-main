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

import java.util.HashMap;

public class Apk {

    public enum ApkEntryStatus {
        CREATED,
        MODIFIED,
        DELETED
    }

    private final ApkFull localArchive;
    private final ApkDump remoteArchive;
    private final String path;

    public Apk(String path, String workingDirectory) {
        this.path = path;
        localArchive = new ApkFull(path);
        remoteArchive = new ApkDump(path, workingDirectory);
    }

    // Traverse local and remote list of crcs in order to detect what has changed in a local apk.
    public HashMap<String, ApkEntryStatus> diff() throws DeployerException {
        HashMap<String, Long> localCrcs = localArchive.getCrcs();
        HashMap<String, Long> remoteCrcs = remoteArchive.getCrcs();

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

    public ApkFull getLocalArchive() {
        return localArchive;
    }

    public ApkDump getRemoteArchive() {
        return remoteArchive;
    }

    public String getPath() {
        return path;
    }
}
