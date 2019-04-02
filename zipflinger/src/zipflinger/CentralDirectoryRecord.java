/*
 * Copyright (C) 2019 The Android Open Source Project
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
package zipflinger;

import com.android.annotations.NonNull;
import java.nio.ByteBuffer;

class CentralDirectoryRecord {

    public static final int SIGNATURE = 0x02014b50;
    public static final int SIZE = 46;
    public static final int DATA_DESCRIPTOR_FLAG = 0x0008;
    public static final int DATA_DESCRIPTOR_SIGNATURE = 0x08074b50;
    public static final int EXTRA_ID_FIELD_SIZE = 2;
    public static final int EXTRA_SIZE_FIELD_SIZE = 2;

    // Unix = 3 (0x0300) + Version 3.0 (0x001E)
    public static final short DEFAULT_VERSION_MADE_BY = 0x031E;

    private byte[] nameBytes;
    private int crc;
    private long compressedSize;
    private long uncompressedSize;
    private long offsetToLFH;
    private short compressionFlag;
    private final int extraPadding;

    CentralDirectoryRecord(
            @NonNull byte[] nameBytes,
            int crc,
            long compressedSize,
            long uncompressedSize,
            long offsetToLFH,
            short compressionFlag,
            int extraPadding) {
        this.nameBytes = nameBytes;
        this.crc = crc;
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;
        this.offsetToLFH = offsetToLFH;
        this.compressionFlag = compressionFlag;
        this.extraPadding = extraPadding;
    }

    void write(@NonNull ByteBuffer buf) {
        buf.putInt(SIGNATURE);
        buf.putShort(DEFAULT_VERSION_MADE_BY);
        buf.putShort(LocalFileHeader.DEFAULT_VERSION_NEEDED);
        buf.putShort((short) 0); // flag
        buf.putShort(compressionFlag);
        buf.putShort((short) 0); // time
        buf.putShort((short) 0); // date
        buf.putInt(crc);
        buf.putInt((int) compressedSize);
        buf.putInt((int) uncompressedSize);
        buf.putShort((short) nameBytes.length);
        //TODO Zip64 -> Write extra zip64 field
        buf.putShort((short) 0); // Extra size
        buf.putShort((short) 0); // comment size
        buf.putShort((short) 0); // disk # start
        buf.putShort((short) 0); // internal att
        buf.putInt(0); // external att
        buf.putInt((int) offsetToLFH);
        buf.put(nameBytes);
    }

    @NonNull
    byte[] getNameBytes() {
        return nameBytes;
    }

    int getCrc() {
        return crc;
    }

    long getCompressedSize() {
        return compressedSize;
    }

    long getUncompressedSize() {
        return uncompressedSize;
    }

    short getCompressionFlag() {
        return compressionFlag;
    }

    long getSize() {
        //TODO Zip64 -> Factor in if a zip64 extra field is needed.
        return SIZE + nameBytes.length;
    }

    int getExtraPadding() {
        return extraPadding;
    }
}
