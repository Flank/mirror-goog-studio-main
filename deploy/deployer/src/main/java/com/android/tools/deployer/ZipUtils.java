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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.io.BaseEncoding;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ZipUtils {

    private static final int CENTRAL_DIRECTORY_FILE_HEADER_MAGIC = 0x02014b50;
    private static final int CENTRAL_DIRECTORY_FILE_HEADER_SIZE = 46;
    private static final int LOCAL_DIRECTORY_FILE_HEADER_SIZE = 30;
    private static final String DIGEST_ALGORITHM = "SHA-1";

    public static class ZipEntry implements Serializable {
        public final long crc;
        public final String name;
        public final long start; // Offset in the archive to the Local File Header location

        // Offset in the archive to the last byte of the payload we know of.
        // Since we don't have the lfh extra size, we can only generate an index value that is
        // "equal or before" the end of a zip entry which is good enough to mark areas as clean
        // for delta-push to work.
        public final long approx_end;


        // Array with all attributes of an entry in the Local File Header. Used for deltaPushing.
        public final byte[] localFileHeader;

        ZipEntry(long crc, String name, long start, long approx_end, byte[] localFileHeader) {
            this.crc = crc;
            this.name = name;
            this.start = start;
            this.approx_end = approx_end;
            this.localFileHeader = localFileHeader;
        }
    }

    @VisibleForTesting
    public static Map<String, ZipEntry> readZipEntries(byte[] buf) {
        ByteBuffer buffer = ByteBuffer.wrap(buf);
        return readZipEntries(buffer)
                .stream()
                .collect(Collectors.toMap(e -> e.name, Functions.identity()));
    }

    public static List<ZipEntry> readZipEntries(ByteBuffer buf) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        List<ZipEntry> entries = new ArrayList<>();
        while (buf.remaining() >= CENTRAL_DIRECTORY_FILE_HEADER_SIZE
                && buf.getInt() == CENTRAL_DIRECTORY_FILE_HEADER_MAGIC) {
            // Read all the data
            short version = buf.getShort();
            short versionNeeded = buf.getShort();
            short flags = buf.getShort();
            short compression = buf.getShort();
            short modTime = buf.getShort();
            short modDate = buf.getShort();
            long crc = uintToLong(buf.getInt());
            long compressedSize = uintToLong(buf.getInt());
            long decompressedSize = uintToLong(buf.getInt());
            int pathLength = ushortToInt(buf.getShort());
            int extraLength = ushortToInt(buf.getShort());
            int commentLength = ushortToInt(buf.getShort());
            // Skip 2 (disk number) + 2 (internal attributes)+ 4 (external attributes)
            buf.position(buf.position() + 8);
            long start = uintToLong(buf.getInt()); // offset to local file entry header

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
            fakeEntry.putInt(longToUint(compressedSize));
            fakeEntry.putInt(longToUint(decompressedSize));
            fakeEntry.putShort(intToUShort(pathLength));
            fakeEntry.putShort(intToUShort(extraLength));
            fakeEntry.put(pathBytes);

            // Keep track of boundaries of the entry in the zip archive since those are used while
            // deltaPushing. Since we don't have the lfh extra size, we can only approximate the
            // end boundary.  -1 is because approx_end must point to the last byte and not after
            // the last byte.
            long approx_end = start + LOCAL_DIRECTORY_FILE_HEADER_SIZE + pathLength - 1;
            approx_end += compression == 0 ? decompressedSize : compressedSize;
            ZipEntry entry = new ZipEntry(crc, name, start, approx_end, localFileHeader);
            entries.add(entry);
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

    public static short intToUShort(int integer) {
        if ((integer & 0xFF_FF_00_00) != 0) {
            throw new IllegalStateException("Cannot cast int to uint16 (does not fit)");
        }
        return (short) integer;
    }

    public static long uintToLong(int integer) {
        return integer & 0xFF_FF_FF_FFL;
    }

    public static int longToUint(long integer) {
        if ((integer & 0xFF_FF_FF_FF_00_00_00_00L) != 0) {
            throw new IllegalStateException("Cannot cast long to uint32 (does not fit)");
        }
        return (int) integer;
    }

    public static int ushortToInt(short integer) {
        return integer & 0xFF_FF;
    }
}
