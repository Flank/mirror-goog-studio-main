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
import com.google.devrel.gmscore.tools.apk.arsc.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkDump {

    private final Path cdDumpPath;
    private final Path sigDumpPath;

    // Lazy initialized
    private String digest = null;
    private HashMap<String, Long> crcs = null;

    /**
     * A class to manipulate an apk central directory and signature blokc dump files.
     *
     * <p>CD dump files contain the metadata of an apk located on a remote device. A cd dump file
     * contains only the Central Directory and the End of Central Directory.
     *
     * <p>Block dump files contain the V2/V3 Signature Block located between the APK payload and the
     * APK CD record.
     */
    public ApkDump(String apkPath, String dumpDirectory) {
        String onDeviceName = retrieveOnDeviceName(apkPath);

        cdDumpPath = Paths.get(dumpDirectory, onDeviceName + ".remotecd");
        sigDumpPath = Paths.get(dumpDirectory, onDeviceName + ".remoteblock");
    }

    public HashMap<String, Long> getCrcs() {
        if (crcs != null) {
            return crcs;
        }
        crcs = readCrcs(cdDumpPath);
        return crcs;
    }

    public String getDigest() {
        if (digest != null) {
            return digest;
        }
        digest = generateDigest(cdDumpPath, sigDumpPath);
        return digest;
    }

    public boolean exists() {
        return Files.exists(cdDumpPath);
    }

    // Generates a hash for a given APK. If there is a signature block, hash it. Otherwise, hash the Central Directory record.
    private String generateDigest(Path cdPath, Path sigPath) {
        try {
            byte[] data;
            if (Files.exists(sigPath)) {
                // TODO: Parse the signature block and use the top level digest instead.
                data = Files.readAllBytes(sigPath);
            } else {
                data = Files.readAllBytes(cdPath);
            }
            ByteBuffer buffer = ByteBuffer.wrap(data);
            return ZipUtils.digest(buffer);
        } catch (IOException e) {
            throw new DeployerException("Unable to generate digest", e);
        }
    }

    private HashMap<String, Long> readCrcs(Path filename) {
        try {
            byte[] data = Files.readAllBytes(filename);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            return ZipUtils.readCrcs(buffer);
        } catch (IOException e) {
            // It is possible the expected apk was no present on the remove device.
            // In this case return an hashmap without crcs.
            return new HashMap<>();
        }
    }

    // Package Manager renames apk files according to the content of AndroidManifest.xml.
    // If found, value of node "manifest", attribute "split" is used.
    // Otherwise, "base.apk" is used.
    private String retrieveOnDeviceName(String apkPath) {
        try {
            ZipFile zipFile = new ZipFile(apkPath);
            ZipEntry manifestEntry = zipFile.getEntry("AndroidManifest.xml");
            InputStream stream = zipFile.getInputStream(manifestEntry);
            String splitValue = getSplitValue(stream);
            if (splitValue == null) {
                splitValue = "base";
            } else {
                splitValue = "split_" + splitValue;
            }
            return splitValue + ".apk";
        } catch (IOException e) {
            throw new DeployerException("Unable to retrieve on device name for " + apkPath, e);
        }
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
            throw new DeployerException("APK manifest chunk[0] != XmlChunk");
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
}
