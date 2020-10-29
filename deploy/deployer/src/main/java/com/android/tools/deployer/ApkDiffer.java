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

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.FileDiff;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ApkDiffer {

    public List<FileDiff> specDiff(DeploymentCacheDatabase.Entry cacheEntry, List<Apk> newApks)
            throws DeployerException {
        if (cacheEntry == null) {
            // TODO: We could just fall back to non-optimistic swap.
            throw DeployerException.remoteApkNotFound();
        }

        // This function performs a standard diff, but additionally ensures that any resource not
        // currently present in the overlay is added to the list of file diffs.
        DiffFunction compare =
                (oldFile, newFile) -> {
                    Optional<FileDiff> normalDiff = standardDiff(oldFile, newFile);

                    // If there's a real diff, prefer to return that.
                    if (normalDiff.isPresent()) {
                        return normalDiff;
                    }

                    // If newFile == null, standardDiff would have returned a diff. We can assume
                    // newFile is not null from this point forward.
                    boolean inOverlay =
                            cacheEntry
                                    .getOverlayContents()
                                    .containsFile(newFile.getQualifiedPath());
                    boolean isResource = newFile.getName().startsWith("res");
                    if (!inOverlay && isResource) {
                        return Optional.of(
                                new FileDiff(
                                        null, newFile, FileDiff.Status.RESOURCE_NOT_IN_OVERLAY));
                    }

                    return Optional.empty();
                };

        return diff(cacheEntry.getApks(), newApks, compare);
    }

    public List<FileDiff> diff(List<Apk> oldApks, List<Apk> newApks) throws DeployerException {
        return diff(oldApks, newApks, ApkDiffer::standardDiff);
    }

    public List<FileDiff> diff(List<Apk> oldApks, List<Apk> newApks, DiffFunction compare)
            throws DeployerException {
        List<ApkEntry> oldFiles = new ArrayList<>();
        Map<String, Map<String, ApkEntry>> oldMap = new HashMap<>();
        groupFiles(oldApks, oldFiles, oldMap);

        List<ApkEntry> newFiles = new ArrayList<>();
        Map<String, Map<String, ApkEntry>> newMap = new HashMap<>();
        groupFiles(newApks, newFiles, newMap);

        if (newMap.size() != oldMap.size()) {
            throw DeployerException.apkCountMismatch();
        }

        if (!newMap.keySet().equals(oldMap.keySet())) {
            throw DeployerException.apkNameMismatch();
        }

        // Traverse local and remote list of crcs in order to detect what has changed in a local apk.
        List<FileDiff> diffs = new ArrayList<>();
        for (ApkEntry newFile : newFiles) {
            ApkEntry oldFile = oldMap.get(newFile.getApk().name).get(newFile.getName());
            compare.diff(oldFile, newFile).ifPresent(diffs::add);
        }

        for (ApkEntry oldFile : oldFiles) {
            ApkEntry newFile = newMap.get(oldFile.getApk().name).get(oldFile.getName());
            if (newFile == null) {
                compare.diff(oldFile, null).ifPresent(diffs::add);
            }
        }
        return diffs;
    }

    private static void groupFiles(
            List<Apk> apks, List<ApkEntry> entries, Map<String, Map<String, ApkEntry>> map) {
        for (Apk apk : apks) {
            map.putIfAbsent(apk.name, apk.apkEntries);
            entries.addAll(apk.apkEntries.values());
        }
    }

    private static Optional<FileDiff> standardDiff(ApkEntry oldFile, ApkEntry newFile) {
        FileDiff.Status status = null;
        if (oldFile == null) {
            status = FileDiff.Status.CREATED;
        } else if (newFile == null) {
            status = FileDiff.Status.DELETED;
        } else if (oldFile.getChecksum() != newFile.getChecksum()) {
            status = FileDiff.Status.MODIFIED;
        }

        if (status != null) {
            return Optional.of(new FileDiff(oldFile, newFile, status));
        }

        return Optional.empty();
    }

    @FunctionalInterface
    private interface DiffFunction {
        Optional<FileDiff> diff(ApkEntry oldFile, ApkEntry newFile);
    }
}
