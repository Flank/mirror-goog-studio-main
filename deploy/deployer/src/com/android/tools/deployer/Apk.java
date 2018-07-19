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

import com.google.common.collect.Lists;
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile;
import com.google.devrel.gmscore.tools.apk.arsc.Chunk;
import com.google.devrel.gmscore.tools.apk.arsc.XmlAttribute;
import com.google.devrel.gmscore.tools.apk.arsc.XmlChunk;
import com.google.devrel.gmscore.tools.apk.arsc.XmlStartElementChunk;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Apk {

    public enum ApkEntryStatus {
        CREATED,
        MODIFIED,
        DELETED
    }

    private final ZipFile zipFile;
    private String onDeviceName = null;

    public Apk(String path) throws DeployerException {
        try {
            zipFile = new ZipFile(path);
        } catch (IOException e) {
            throw new DeployerException("Unable to read Zipfile.", e);
        }
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

    public String getOnDeviceName() {
        if (onDeviceName != null) {
            return onDeviceName;
        }

        try {
            ZipEntry manifestEntry = zipFile.getEntry("AndroidManifest.xml");
            InputStream stream = zipFile.getInputStream(manifestEntry);
            String splitValue = getSplitValue(stream);
            if (splitValue == null) {
                splitValue = "base";
            } else {
                splitValue = "split_" + splitValue;
            }
            onDeviceName = splitValue + ".apk";
        } catch (IOException e) {
            throw new DeployerException("Unable to retrieve on device name for " + getPath(), e);
        }
        return onDeviceName;
    }

    public String getPath() {
        return zipFile.getName();
    }

    private List<Chunk> sortByOffset(Map<Integer, Chunk> contentChunks) {
        List<Integer> offsets = Lists.newArrayList(contentChunks.keySet());
        Collections.sort(offsets);
        List<Chunk> chunks = new ArrayList<>(offsets.size());
        for (Integer offset : offsets) {
            chunks.add(contentChunks.get(offset));
        }
        return chunks;
    }

    private String getSplitValue(InputStream decompressedManifest) throws IOException {
        BinaryResourceFile file = BinaryResourceFile.fromInputStream(decompressedManifest);
        List<Chunk> chunks = file.getChunks();

        if (chunks.size() == 0) {
            throw new DeployerException("Invalid APK, empty manifest");
        }

        if (!(chunks.get(0) instanceof XmlChunk)) {
            throw new DeployerException("APK '" + getPath() + "' manifest chunk[0] != XmlChunk");
        }

        XmlChunk xmlChunk = (XmlChunk) chunks.get(0);
        List<Chunk> contentChunks = sortByOffset(xmlChunk.getChunks());

        for (Chunk chunk : contentChunks) {
            if (chunk instanceof XmlStartElementChunk) {
                XmlStartElementChunk startChunk = (XmlStartElementChunk) chunk;
                if (startChunk.getName().equals("manifest")) {
                    for (XmlAttribute attribute : startChunk.getAttributes()) {
                        if (attribute.name().equals("split")) {
                            return attribute.rawValue();
                        }
                    }
                    return null;
                }
            }
        }
        return null;
    }

    // Retrieve the local APK crcs. The expected file is a valid zip archive which
    // can be accessed via java.util.zip package.
    private HashMap<String, Long> extractLocalCrcs() throws DeployerException {
        HashMap<String, Long> localCrcs = new HashMap<>();
        Path path = FileSystems.getDefault().getPath(getPath());
        if (!Files.exists(path)) {
            throw new DeployerException("APK file'" + getPath() + "' does not exists.");
        }

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            long crc = entry.getCrc();
            localCrcs.put(name, crc);
        }
        return localCrcs;
    }

    // Retrieve the remote APK crcs. The expected file is a dump of the Central Directory Record
    // and is not a valid zip archive. The Central Directory Record is expected to start at offset
    // zero.
    private HashMap<String, Long> extractRemoteCrcs(Path remoteCDDump) throws DeployerException {
        HashMap<String, Long> remoteCrcs = new HashMap<>();
        ZipCentralDirectory zcd = new ZipCentralDirectory(remoteCDDump.toString());
        zcd.getCrcs(remoteCrcs);
        return remoteCrcs;
    }
}
