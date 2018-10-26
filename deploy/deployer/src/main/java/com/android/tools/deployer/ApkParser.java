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
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.google.common.collect.ImmutableList;
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

    // Manifest elements that may have an android:process attribute.
    private static final HashSet<String> MANIFEST_PROCESS_ELEMENTS =
            new HashSet<>(
                    Arrays.asList("activity", "application", "provider", "receiver", "service"));

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
        private final List<String> processNames;

        private ApkDetails(String fileName, List<String> processNames) {
            this.fileName = fileName;
            this.processNames = processNames;
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
            throw new DeployerException(DeployerException.Error.INVALID_APK, "Error reading APK");
        }
    }

    private List<ApkEntry> parse(String apkPath) throws IOException {
        File file = new File(apkPath);
        MappedByteBuffer mmap;
        String absolutePath = file.getAbsolutePath();
        String digest;
        HashMap<String, Long> crcs;
        try (RandomAccessFile raf = new RandomAccessFile(absolutePath, "r");
                FileChannel fileChannel = raf.getChannel()) {
            mmap = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            ApkArchiveMap map = parse(mmap);
            digest = generateDigest(raf, map);
            crcs = readCrcs(raf, map);
        }
        ApkDetails apkDetails;
        try (ZipFile zipFile = new ZipFile(absolutePath)) {
            ZipEntry manifestEntry = zipFile.getEntry("AndroidManifest.xml");
            InputStream stream = zipFile.getInputStream(manifestEntry);
            apkDetails = parseManifest(stream);
        }

        List<ApkEntry> files = new ArrayList<>();
        Apk apk = new Apk(apkDetails.fileName, digest, absolutePath, apkDetails.processNames);
        for (Map.Entry<String, Long> entry : crcs.entrySet()) {
            files.add(new ApkEntry(entry.getKey(), entry.getValue(), apk));
        }
        return files;
    }

    List<ApkEntry> parseDumps(List<Deploy.ApkDump> protoDumps) {
        List<ApkEntry> dumps = new ArrayList<>();
        for (Deploy.ApkDump dump : protoDumps) {
            ByteBuffer cd = dump.getCd().asReadOnlyByteBuffer();
            ByteBuffer signature = dump.getSignature().asReadOnlyByteBuffer();
            HashMap<String, Long> crcs = ZipUtils.readCrcs(cd);
            cd.rewind();
            String digest = ZipUtils.digest(signature.remaining() != 0 ? signature : cd);
            Apk apk = new Apk(dump.getName(), digest, null, ImmutableList.of());
            for (Map.Entry<String, Long> entry : crcs.entrySet()) {
                dumps.add(new ApkEntry(entry.getKey(), entry.getValue(), apk));
            }
        }
        return dumps;
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

    private HashMap<String, Long> readCrcs(RandomAccessFile randomAccessFile, ApkArchiveMap map)
            throws IOException {
        byte[] cdContent = new byte[(int) map.cdSize];
        randomAccessFile.seek(map.cdOffset);
        randomAccessFile.readFully(cdContent);
        ByteBuffer buffer = ByteBuffer.wrap(cdContent);
        return ZipUtils.readCrcs(buffer);
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
        HashSet<String> processNames = new HashSet<>();

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

            if (MANIFEST_PROCESS_ELEMENTS.contains(startChunk.getName())) {
                for (XmlAttribute attribute : startChunk.getAttributes()) {
                    if (attribute.name().equals("process")) {
                        processNames.add(attribute.rawValue());
                    }
                }
            }
        }

        if (packageName == null) {
            throw new IllegalArgumentException("Package name was not found in manifest");
        }

        String apkFileName = splitName == null ? "base.apk" : "split_" + splitName + ".apk";

        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (String processName : processNames) {
            if (processName.charAt(0) == ':') {
                // Private processes are prefixed with the package name, and are indicated in the
                // manifest with a leading ':'.
                builder.add(packageName + processName);
            } else {
                // Global processes are as-written in the manifest, and do not have a leading ':'.
                builder.add(processName);
            }
        }

        // Default process name is the name of the package.
        builder.add(packageName);
        return new ApkDetails(apkFileName, builder.build());
    }
}
