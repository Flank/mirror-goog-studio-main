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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class LocalFileHeader {
    private static final int SIGNATURE = 0x04034b50;

    // Minium version = 2.0 (encoded dec = 20)
    public static final short DEFAULT_VERSION_NEEDED = 0x0014;
    public static final int LOCAL_FILE_HEADER_SIZE = 30;

    // Minimum number of bytes needed to create a virtual zip entry (an entry not present in
    // the Central Directory with name length = 0 and an extra field containing padding data).
    public static final long VIRTUAL_HEADER_SIZE =
            LOCAL_FILE_HEADER_SIZE
                    + CentralDirectoryRecord.EXTRA_SIZE_FIELD_SIZE
                    + CentralDirectoryRecord.EXTRA_ID_FIELD_SIZE;

    public static final short COMPRESSION_NONE = 0;
    public static final short COMPRESSION_DEFLATE = 8;

    // This is the extra marker value as what apkzlib uses.
    private static final short ALIGN_SIGNATURE = (short) 0xd935;

    static final long OFFSET_TO_NAME = 26;

    public static void fillVirtualEntry(@NonNull ByteBuffer virtualEntry) {
        virtualEntry.order(ByteOrder.LITTLE_ENDIAN);
        virtualEntry.putInt(SIGNATURE);
        virtualEntry.putShort(DEFAULT_VERSION_NEEDED); // Version needed
        virtualEntry.putShort((short) 0); // general purpose flag
        virtualEntry.putShort(COMPRESSION_NONE);
        virtualEntry.putShort((short) 0); // time
        virtualEntry.putShort((short) 0); // date
        virtualEntry.putInt(0); // CRC-32
        virtualEntry.putInt(0); // compressed size
        virtualEntry.putInt(0); // uncompressed size
        virtualEntry.putShort((short) 0); // file name length
        virtualEntry.putShort((short) (virtualEntry.capacity() - LOCAL_FILE_HEADER_SIZE));

        // Write the extra field header
        virtualEntry.putShort(ALIGN_SIGNATURE);

        // -2 for the short we are about to write
        short extraFieldSize = (short) (virtualEntry.capacity() - virtualEntry.position() - 2);
        virtualEntry.putShort(extraFieldSize);

        assert virtualEntry.remaining() == extraFieldSize;
        virtualEntry.rewind();
    }

    public static void writeEntry(@NonNull CentralDirectoryRecord entry, @NonNull ZipWriter writer)
            throws IOException {
        byte[] nameBytes = entry.getNameBytes();
        ByteBuffer extraField = buildExtraField(entry);
        int bytesNeeded = LOCAL_FILE_HEADER_SIZE + nameBytes.length + extraField.capacity();

        ByteBuffer buffer = ByteBuffer.allocate(bytesNeeded).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(SIGNATURE);
        buffer.putShort(DEFAULT_VERSION_NEEDED);
        buffer.putShort((short) 0); // general purpose flag
        buffer.putShort(entry.getCompressionFlag());
        buffer.putShort((short) 0); // time
        buffer.putShort((short) 0); // date
        buffer.putInt(entry.getCrc());
        buffer.putInt((int) entry.getCompressedSize());
        buffer.putInt((int) entry.getUncompressedSize());
        buffer.putShort((short) nameBytes.length);
        buffer.putShort((short) extraField.capacity()); // Extra size
        buffer.put(nameBytes);
        buffer.put(extraField);

        buffer.rewind();
        writer.write(buffer);
    }

    private static ByteBuffer buildExtraField(CentralDirectoryRecord entry) {
        if (entry.getExtraPadding() == 0) {
            return ByteBuffer.wrap(new byte[0]);
        }

        int bytesNeeded = entry.getExtraPadding();
        ByteBuffer buffer = ByteBuffer.allocate(bytesNeeded).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(ALIGN_SIGNATURE);

        int paddingSize =
                entry.getExtraPadding()
                        - CentralDirectoryRecord.EXTRA_ID_FIELD_SIZE
                        - CentralDirectoryRecord.EXTRA_SIZE_FIELD_SIZE;
        buffer.putShort((short) paddingSize);
        buffer.put(new byte[paddingSize]);
        buffer.rewind();

        return buffer;
    }

    public static long sizeFor(@NonNull Source source) {
        // TODO: Zip64 -> Factor in zip64 extra field requirement
        return LOCAL_FILE_HEADER_SIZE + source.getNameBytes().length;
    }
}
