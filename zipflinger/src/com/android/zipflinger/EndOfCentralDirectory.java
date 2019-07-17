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
package com.android.zipflinger;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class EndOfCentralDirectory {
    public static final int SIGNATURE = 0x06054b50;
    public static final int SIZE = 22;

    // Parse an EOCD and returns the CD location in buffer space.
    @NonNull
    public static Location parse(@NonNull ByteBuffer buffer, @NonNull Location cdLocation) {
        // Skip signature (4) + diskNumber (2) + cdDiskNumber (2) + #entries (2) + #cdEntries (2)
        buffer.position((int) cdLocation.first + 12);
        long cdSize = buffer.getInt() & 0xFFFFFFFFL;
        long cdOffset = buffer.getInt() & 0xFFFFFFFFL;
        return new Location(cdOffset, cdSize);
    }

    public static Location write(
            @NonNull ZipWriter writer, @NonNull Location cdLocation, long numEntries)
            throws IOException {
        ByteBuffer eocd = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(SIGNATURE);
        eocd.putShort((short) 0);
        eocd.putShort((short) 0);
        eocd.putShort((short) numEntries);
        eocd.putShort((short) numEntries);
        eocd.putInt((int) cdLocation.size());
        eocd.putInt((int) cdLocation.first);
        eocd.putShort((short) 0);

        eocd.rewind();
        writer.write(eocd);

        return new Location(cdLocation.last + 1, eocd.capacity());
    }
}
