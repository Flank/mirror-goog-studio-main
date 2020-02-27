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
import com.android.tools.deployer.model.ApkEntryContent;
import com.android.tools.deployer.model.FileDiff;
import com.android.tools.idea.protobuf.ByteString;
import com.android.zipflinger.ZipArchive;
import com.google.common.collect.ArrayListMultimap;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ApkEntryExtractor {

    // Used to restrict the APK entries that are extracted base on each entry's name.
    private final Predicate<String> filter;

    public ApkEntryExtractor(Predicate<String> filter) {
        this.filter = filter;
    }

    public List<ApkEntryContent> extract(List<FileDiff> diffs) throws DeployerException {
        ArrayListMultimap<Apk, ApkEntry> entriesToExtract = ArrayListMultimap.create();

        diffs.stream()
                .filter(this::shouldExtract)
                .forEach(diff -> entriesToExtract.put(diff.newFile.getApk(), diff.newFile));

        List<ApkEntryContent> extracted = new ArrayList<>();
        for (Apk apk : entriesToExtract.keySet()) {
            extractFromApk(apk, entriesToExtract.get(apk), extracted);
        }

        // Sort before returning to ensure that the entry order is deterministic.
        extracted.sort(ApkEntryExtractor::compareExtracted);
        return extracted;
    }

    private void extractFromApk(Apk apk, List<ApkEntry> entries, List<ApkEntryContent> extracted)
            throws DeployerException {
        try (ZipArchive zip = new ZipArchive(new File(apk.path))) {
            for (ApkEntry apkEntry : entries) {
                ByteBuffer content = zip.getContent(apkEntry.getName());
                if (content == null) {
                    throw DeployerException.entryNotFound(apkEntry.getName(), apk.path);
                }
                extracted.add(new ApkEntryContent(apkEntry, ByteString.copyFrom(content)));
            }
        } catch (IOException io) {
            throw DeployerException.entryUnzipFailed(io);
        }
    }

    private boolean shouldExtract(FileDiff diff) {
        // Before we handle newly created files, we need to implement a proper overlay diff.
        return diff.status == FileDiff.Status.MODIFIED && filter.test(diff.newFile.getName());
    }

    private static int compareExtracted(ApkEntryContent a, ApkEntryContent b) {
        if (a.getApkName().equals(b.getApkName())) {
            return a.getName().compareTo(b.getName());
        }
        return a.getApkName().compareTo(b.getApkName());
    }
}
