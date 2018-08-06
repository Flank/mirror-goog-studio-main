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

import com.android.utils.ILogger;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;

public class ApkFull {
    private static final ILogger LOGGER = Logger.getLogger(ApkFull.class);
    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int CD_SIGNATURE = 0x02014b50;
    private static final byte[] SIGNATURE_BLOCK_MAGIC = "APK Sig Block 42".getBytes();

    private final String path;
    private HashMap<String, Long> crcs = null;
    private String digest = null;

    public class ApkArchiveMap {
        public static final long UNINITIALIZED = -1;
        long cdOffset = UNINITIALIZED;
        long cdSize = UNINITIALIZED;

        long signatureBlockOffset = UNINITIALIZED;
        long signatureBlockSize = UNINITIALIZED;

        long eocdOffset = UNINITIALIZED;
        long eocdSize = UNINITIALIZED;
    }

    private final ApkArchiveMap map;

    /** A class to manipulate .apk files. */
    public ApkFull(String apkPath) {
        File file = new File(apkPath);
        MappedByteBuffer mmap;
        path = file.getAbsolutePath();
        try (RandomAccessFile raf = new RandomAccessFile(path, "r");
                FileChannel fileChannel = raf.getChannel()) {
            mmap = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            map = parse(mmap);
        } catch (IOException e) {
            throw new DeployerException("Unable to open apk archive: " + path, e);
        }
    }

    public HashMap<String, Long> getCrcs() {
        if (crcs != null) {
            return crcs;
        }
        try {
            RandomAccessFile raf = new RandomAccessFile(path, "r");
            crcs = readCrcs(raf, map);
        } catch (IOException e) {
            LOGGER.error(e, "Unable to retrieve CRCs for apk '%s'.", path);
        }
        return crcs;
    }

    public String getDigest() {
        if (digest != null) {
            return digest;
        }
        try {
            RandomAccessFile raf = new RandomAccessFile(path, "r");
            digest = generateDigest(raf, map);
        } catch (IOException e) {
            LOGGER.error(e, "Unable to generate digest for apk '%s'.", path);
        }
        return digest;
    }

    public boolean exists() {
        return Files.exists(Paths.get(path));
    }

    public String getPath() {
        return path;
    }

    // Parse the APK archive. The ByteBuffer is expected to contain the entire APK archive.
    private ApkArchiveMap parse(ByteBuffer bytes) {
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        ApkArchiveMap map = readCentralDirectoryRecord(bytes);

        // Make sure the magic number in the Central Directory is what is expected.
        bytes.position((int) map.cdOffset);
        if (bytes.getInt() != CD_SIGNATURE) {
            throw new DeployerException("Central Directory signature invalid.");
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

    ApkArchiveMap getMap() {
        return map;
    }
}
