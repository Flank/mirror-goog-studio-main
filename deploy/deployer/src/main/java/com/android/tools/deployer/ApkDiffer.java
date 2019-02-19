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

import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.FileDiff;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApkDiffer {

    public List<FileDiff> diff(ApplicationDumper.Dump oldFiles, List<ApkEntry> newFiles)
            throws DeployerException {

        Map<String, Map<String, ApkEntry>> oldMap = groupFiles(oldFiles.apkEntries);
        Map<String, Map<String, ApkEntry>> newMap = groupFiles(newFiles);

        if (newMap.size() != oldMap.size()) {
            throw DeployerException.apkCountMismatch();
        }

        if (!newMap.keySet().equals(oldMap.keySet())) {
            throw DeployerException.apkNameMismatch();
        }

        // Traverse local and remote list of crcs in order to detect what has changed in a local apk.
        List<FileDiff> diffs = new ArrayList<>();
        for (ApkEntry newFile : newFiles) {
            ApkEntry oldFile = oldMap.get(newFile.apk.name).get(newFile.name);
            if (oldFile == null) {
                diffs.add(new FileDiff(null, newFile, FileDiff.Status.CREATED));
            } else {
                // Check if modified.
                if (oldFile.checksum != newFile.checksum) {
                    diffs.add(new FileDiff(oldFile, newFile, FileDiff.Status.MODIFIED));
                }
            }
        }

        for (ApkEntry oldFile : oldFiles.apkEntries) {
            ApkEntry newFile = newMap.get(oldFile.apk.name).get(oldFile.name);
            if (newFile == null) {
                diffs.add(new FileDiff(oldFile, null, FileDiff.Status.DELETED));
            }
        }
        return diffs;
    }

    public Map<String, Map<String, ApkEntry>> groupFiles(List<ApkEntry> oldFiles) {
        Map<String, Map<String, ApkEntry>> oldMap = new HashMap<>();
        for (ApkEntry file : oldFiles) {
            Map<String, ApkEntry> map = oldMap.computeIfAbsent(file.apk.name, k -> new HashMap<>());
            map.put(file.name, file);
        }
        return oldMap;
    }
}
