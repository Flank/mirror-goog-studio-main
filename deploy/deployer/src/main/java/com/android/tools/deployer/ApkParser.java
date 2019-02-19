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
import com.android.tools.tracer.Trace;
import com.google.devrel.gmscore.tools.apk.arsc.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkParser {
    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int CD_SIGNATURE = 0x02014b50;
    private static final byte[] SIGNATURE_BLOCK_MAGIC = "APK Sig Block 42".getBytes();

    private static class ApkArchiveMap {
        public static final long UNINITIALIZED = -1;
        long cdOffset = UNINITIALIZED;
        long cdSize = UNINITIALIZED;

        long signatureBlockOffset = UNINITIALIZED;
        long signatureBlockSize = UNINITIALIZED;

        long eocdOffset = UNINITIALIZED;
        long eocdSize = UNINITIALIZED;
    }

    private static class ApkDetails {
        private final String fileName;
        private final String packageName;
        private final List<String> targetPackages;

        private ApkDetails(
                String fileName,
                String packageName,
                List<String> targetPackages) {
            this.fileName = fileName;
            this.packageName = packageName;
            this.targetPackages = targetPackages;
        }
    }

    /** A class to manipulate .apk files. */
    public ApkParser() {}

    public List<ApkEntry> parsePaths(List<String> paths) throws DeployerException {
        try (Trace ignored = Trace.begin("parseApks")) {
            List<ApkEntry> newFiles = new ArrayList<>();
            for (String apkPath : paths) {
                newFiles.addAll(parse(apkPath));
            }
            return newFiles;
        } catch (IOException e) {
            throw DeployerException.parseFailed(e.getMessage());
        }
    }

    private List<ApkEntry> parse(String apkPath) throws IOException {
        File file = new File(apkPath);
        MappedByteBuffer mmap;
        String absolutePath = file.getAbsolutePath();
        String digest;
        HashMap<String, ZipUtils.ZipEntry> zipEntries;
        try (RandomAccessFile raf = new RandomAccessFile(absolutePath, "r");
                FileChannel fileChannel = raf.getChannel()) {
            mmap = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            ApkArchiveMap map = parse(mmap);
            digest = generateDigest(raf, map);
            zipEntries = readZipEntries(raf, map);
        }
        ApkDetails apkDetails;
        try (ZipFile zipFile = new ZipFile(absolutePath)) {
            ZipEntry manifestEntry = zipFile.getEntry("AndroidManifest.xml");
            InputStream stream = zipFile.getInputStream(manifestEntry);
            apkDetails = parseManifest(stream);
        }

        List<ApkEntry> files = new ArrayList<>();
        Apk apk =
                Apk.builder()
                        .setName(apkDetails.fileName)
                        .setChecksum(digest)
                        .setPath(absolutePath)
                        .setPackageName(apkDetails.packageName)
                        .setTargetPackages(apkDetails.targetPackages)
                        .setZipEntries(zipEntries)
                        .build();

        for (Map.Entry<String, ZipUtils.ZipEntry> entry : zipEntries.entrySet()) {
            files.add(new ApkEntry(entry.getKey(), entry.getValue().crc, apk));
        }
        return files;
    }

    // Parse the APK archive. The ByteBuffer is expected to contain the entire APK archive.
    private ApkArchiveMap parse(ByteBuffer bytes) {
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        ApkArchiveMap map = readCentralDirectoryRecord(bytes);

        // Make sure the magic number in the Central Directory is what is expected.
        bytes.position((int) map.cdOffset);
        if (bytes.getInt() != CD_SIGNATURE) {
            throw new IllegalArgumentException("Central Directory signature invalid.");
        }

        readSignatureBlock(bytes, map);
        return map;
    }

    private void readSignatureBlock(ByteBuffer buffer, ApkArchiveMap map) {
        // Search the Signature Block magic number
        buffer.position((int) map.cdOffset - SIGNATURE_BLOCK_MAGIC.length);
        byte[] signatureBlockMagicNumber = new byte[SIGNATURE_BLOCK_MAGIC.length];
        buffer.get(signatureBlockMagicNumber);
        //String signatureProspect = new String(signatureBlockMagicNumber);
        if (!Arrays.equals(signatureBlockMagicNumber, SIGNATURE_BLOCK_MAGIC)) {
            // This is not a signature block magic number.
            return;
        }

        // The magic number is not enough, we nee to make sure the upper and lower size are the same.
        buffer.position(buffer.position() - SIGNATURE_BLOCK_MAGIC.length - Long.BYTES);
        long lowerSignatureBlockSize = buffer.getLong();
        buffer.position((int) (buffer.position() + Long.BYTES - lowerSignatureBlockSize));
        long upperSignatureBlocSize = buffer.getLong();

        if (lowerSignatureBlockSize != upperSignatureBlocSize) {
            return;
        }

        // Everything matches (signature and upper/lower size, this is a confirmed signature block;
        map.signatureBlockOffset = buffer.position() - Long.BYTES;
        map.signatureBlockSize = lowerSignatureBlockSize;
    }

    private ApkArchiveMap readCentralDirectoryRecord(ByteBuffer buffer) {
        ApkArchiveMap map = new ApkArchiveMap();
        // Search the End of Central Directory Record
        // The End of Central Directory record size is 22 bytes if the comment section size is zero.
        // The comment section can be of any size, up to 65535 since it is stored on two bytes.
        // We start at the likely position of the beginning of the EoCD position and backtrack toward the
        // beginning of the buffer.
        buffer.position(buffer.capacity() - 22);
        while (buffer.getInt() != EOCD_SIGNATURE) {
            int position = buffer.position() - Integer.BYTES - 1;
            buffer.position(position);
        }
        map.eocdOffset = buffer.position() - Integer.BYTES;
        map.eocdSize = buffer.capacity() - map.eocdOffset;

        // Read the End of Central Directory Record and record its position in the map. For now skip fields we don't use.
        buffer.position(buffer.position() + Short.BYTES * 4);
        //short numDisks = bytes.getShort();
        //short cdStartDisk = bytes.getShort();
        //short numCDRonDisk = bytes.getShort();
        //short numCDRecords = buffer.getShort();
        map.cdSize = buffer.getInt();
        map.cdOffset = buffer.getInt();
        //short sizeComment = bytes.getShort();
        return map;
    }

    private HashMap<String, ZipUtils.ZipEntry> readZipEntries(
            RandomAccessFile randomAccessFile, ApkArchiveMap map) throws IOException {
        byte[] cdContent = new byte[(int) map.cdSize];
        randomAccessFile.seek(map.cdOffset);
        randomAccessFile.readFully(cdContent);
        ByteBuffer buffer = ByteBuffer.wrap(cdContent);
        return ZipUtils.readZipEntries(buffer);
    }

    private String generateDigest(RandomAccessFile randomAccessFile, ApkArchiveMap map)
            throws IOException {
        byte[] sigContent;
        if (map.signatureBlockOffset != ApkArchiveMap.UNINITIALIZED) {
            sigContent = new byte[(int) map.signatureBlockSize];
            randomAccessFile.seek(map.signatureBlockOffset);
            randomAccessFile.readFully(sigContent);
        } else {
            sigContent = new byte[(int) map.cdSize];
            randomAccessFile.seek(map.cdOffset);
            randomAccessFile.readFully(sigContent);
        }
        ByteBuffer buffer = ByteBuffer.wrap(sigContent);
        return ZipUtils.digest(buffer);
    }

    private ApkDetails parseManifest(InputStream decompressedManifest) throws IOException {
        BinaryResourceFile file = BinaryResourceFile.fromInputStream(decompressedManifest);
        List<Chunk> chunks = file.getChunks();

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Invalid APK, empty manifest");
        }

        if (!(chunks.get(0) instanceof XmlChunk)) {
            throw new IllegalArgumentException("APK manifest chunk[0] != XmlChunk");
        }

        String packageName = null;
        String splitName = null;
        List<String> targetPackages = new ArrayList<>();

        XmlChunk xmlChunk = (XmlChunk) chunks.get(0);
        for (Chunk chunk : xmlChunk.getChunks().values()) {
            if (!(chunk instanceof XmlStartElementChunk)) {
                continue;
            }

            XmlStartElementChunk startChunk = (XmlStartElementChunk) chunk;
            if (startChunk.getName().equals("manifest")) {
                for (XmlAttribute attribute : startChunk.getAttributes()) {
                    if (attribute.name().equals("split")) {
                        splitName = attribute.rawValue();
                    }

                    if (attribute.name().equals("package")) {
                        packageName = attribute.rawValue();
                    }
                }
            }

            if (startChunk.getName().equals("instrumentation")) {
                for (XmlAttribute attribute : startChunk.getAttributes()) {
                    if (attribute.name().equals("targetPackage")) {
                        targetPackages.add(attribute.rawValue());
                    }
                }
            }
        }

        if (packageName == null) {
            throw new IllegalArgumentException("Package name was not found in manifest");
        }

        String apkFileName = splitName == null ? "base.apk" : "split_" + splitName + ".apk";
        return new ApkDetails(apkFileName, packageName, targetPackages);
    }
}
