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

import com.google.common.io.BaseEncoding;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class ZipUtils {

    private static final int CENTRAL_DIRECTORY_FILE_HEADER_MAGIC = 0x02014b50;
    private static final int CENTRAL_DIRECTORY_FILE_HEADER_SIZE = 46;
    private static final int LOCAL_DIRECTORY_FILE_HEADER_SIZE = 30;
    private static final String DIGEST_ALGORITHM = "SHA-1";

    public static class ZipEntry {
        public final long crc;
        public final String name;
        public final long start; // Offset in the archive to the Local File Header location
        public final long end; // Offset in the archive to the last byte of the payload.
        public final long payloadStart; // Offset in the archive to the first byte of the payload.
        public final int extraLength; // Size of the extra field.

        // Array with all attributes of an entry in the Local File Header. Used for deltaPushing.
        public final byte[] localFileHeader;

        ZipEntry(
                long crc,
                String name,
                long start,
                long end,
                long payloadStart,
                int extraLength,
                byte[] localFileHeader) {
            this.crc = crc;
            this.name = name;
            this.start = start;
            this.end = end;
            this.payloadStart = payloadStart;
            this.extraLength = extraLength;
            this.localFileHeader = localFileHeader;
        }
    }

    public static HashMap<String, ZipEntry> readZipEntries(byte[] buf) {
        ByteBuffer buffer = ByteBuffer.wrap(buf);
        return readZipEntries(buffer);
    }

    public static HashMap<String, ZipEntry> readZipEntries(ByteBuffer buf) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        HashMap<String, ZipEntry> entries = new HashMap<>();
        while (buf.remaining() >= CENTRAL_DIRECTORY_FILE_HEADER_SIZE
                && buf.getInt() == CENTRAL_DIRECTORY_FILE_HEADER_MAGIC) {
            // Read all the data
            short version = buf.getShort();
            short versionNeeded = buf.getShort();
            short flags = buf.getShort();
            short compression = buf.getShort();
            short modTime = buf.getShort();
            short modDate = buf.getShort();
            long crc = buf.getInt() & 0xFFFFFFFFL;
            int compressedSize = buf.getInt();
            int decompressedSize = buf.getInt();
            short pathLength = buf.getShort();
            int extraLength = buf.getShort();
            short commentLength = buf.getShort();
            // Skip 2 (disk number) + 2 (internal attributes)+ 4 (external attributes)
            buf.position(buf.position() + 8);
            long start = buf.getInt(); // offset to local file entry header

            // Read the filename
            byte[] pathBytes = new byte[pathLength];
            buf.get(pathBytes);
            String name = new String(pathBytes, Charset.forName("UTF-8"));
            buf.position(buf.position() + extraLength + commentLength);

            // Create a fake zip file entry header which will be used to build a dirtyMap while
            // delta pushing.
            byte[] localFileHeader = new byte[LOCAL_DIRECTORY_FILE_HEADER_SIZE + pathBytes.length];
            ByteBuffer fakeEntry = ByteBuffer.wrap(localFileHeader).order(ByteOrder.LITTLE_ENDIAN);
            fakeEntry.putLong(start);
            fakeEntry.putShort(versionNeeded);
            fakeEntry.putShort(modTime);
            fakeEntry.putShort(modDate);
            fakeEntry.putInt((int) crc);
            fakeEntry.putInt(compression);
            fakeEntry.putInt(decompressedSize);
            fakeEntry.putShort((short) name.length());
            fakeEntry.putShort((short) extraLength);
            fakeEntry.put(pathBytes);

            // Keep track of boundaries of the entry in the zip archive since those are used while
            // deltaPushing.
            long payloadStart = start + LOCAL_DIRECTORY_FILE_HEADER_SIZE + pathLength + extraLength;
            long end = payloadStart - 1 + compressedSize;
            ZipEntry entry =
                    new ZipEntry(crc, name, start, end, payloadStart, extraLength, localFileHeader);
            entries.put(entry.name, entry);
        }
        return entries;
    }

    public static String digest(ByteBuffer buffer) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "MessageDigest:" + DIGEST_ALGORITHM + " unavailable.", e);
        }
        // TODO: Parse the block and hash the top level signature instead of hashing the entire block.
        messageDigest.update(buffer);
        byte[] digestBytes = messageDigest.digest();
        return BaseEncoding.base16().lowerCase().encode(digestBytes);
    }
}
