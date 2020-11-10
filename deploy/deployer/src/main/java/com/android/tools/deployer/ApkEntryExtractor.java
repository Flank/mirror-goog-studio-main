/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.protobuf.ByteString;
import com.android.zipflinger.ZipArchive;
import com.google.common.collect.ArrayListMultimap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;

public class ApkEntryExtractor {

    // Used to restrict the APK entries that are extracted base on each entry's name.
    private final Predicate<String> filter;

    public ApkEntryExtractor() {
        this.filter = t -> true;
    }

    public ApkEntryExtractor(Predicate<String> filter) {
        this.filter = filter;
    }

    public SortedMap<ApkEntry, ByteString> extractFromDiffs(List<FileDiff> diffs)
            throws DeployerException {
        ArrayListMultimap<Apk, ApkEntry> entriesToExtract = ArrayListMultimap.create();
        diffs.stream()
                .filter(this::shouldExtract)
                .forEach(diff -> entriesToExtract.put(diff.newFile.getApk(), diff.newFile));
        return extractFromApks(entriesToExtract);
    }

    public SortedMap<ApkEntry, ByteString> extractFromEntries(Collection<ApkEntry> entries)
            throws DeployerException {
        ArrayListMultimap<Apk, ApkEntry> entriesToExtract = ArrayListMultimap.create();
        entries.stream()
                .filter(entry -> filter.test(entry.getName()))
                .forEach(entry -> entriesToExtract.put(entry.getApk(), entry));
        return extractFromApks(entriesToExtract);
    }

    private SortedMap<ApkEntry, ByteString> extractFromApks(
            ArrayListMultimap<Apk, ApkEntry> entriesToExtract) throws DeployerException {
        SortedMap<ApkEntry, ByteString> extracted =
                new TreeMap<>(ApkEntryExtractor::compareApkEntries);
        for (Apk apk : entriesToExtract.keySet()) {
            extractFromApk(apk, entriesToExtract.get(apk), extracted);
        }
        return extracted;
    }

    private void extractFromApk(
            Apk apk, List<ApkEntry> entries, Map<ApkEntry, ByteString> extracted)
            throws DeployerException {
        try (ZipArchive zip = new ZipArchive(Paths.get(apk.path))) {
            for (ApkEntry apkEntry : entries) {
                ByteBuffer content = zip.getContent(apkEntry.getName());
                if (content == null) {
                    throw DeployerException.entryNotFound(apkEntry.getName(), apk.path);
                }
                extracted.put(apkEntry, ByteString.copyFrom(content));
            }
        } catch (IOException io) {
            throw DeployerException.entryUnzipFailed(io);
        }
    }

    private boolean shouldExtract(FileDiff diff) {
        switch (diff.status) {
            case CREATED:
            case MODIFIED:
            case RESOURCE_NOT_IN_OVERLAY:
                return filter.test(diff.newFile.getName());
            case DELETED:
                return false;
            default:
                throw new IllegalArgumentException("Unexpected diff status: " + diff.status);
        }
    }

    private static int compareApkEntries(ApkEntry a, ApkEntry b) {
        return a.getQualifiedPath().compareTo(b.getQualifiedPath());
    }
}
