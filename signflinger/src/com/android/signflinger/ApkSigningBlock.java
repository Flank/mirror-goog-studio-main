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

package com.android.signflinger;

import com.android.annotations.NonNull;
import com.android.zipflinger.ZipInfo;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

class ApkSigningBlock {

    static void addToArchive(
            @NonNull RandomAccessFile raf, @NonNull byte[] sig, @NonNull ZipInfo zipInfo) {
        if (zipInfo.eocd.size() != 22) {
            throw new IllegalStateException(
                    "Commented eocd is not supported (was this apk not created with zipflinger?).");
        }

        ByteBuffer signatureBlock = ByteBuffer.wrap(sig);
        try (FileChannel channel = raf.getChannel()) {
            // Load cd + eocd to memory
            byte[] cdAndEocd = new byte[(int) (zipInfo.cd.size() + zipInfo.eocd.size())];
            raf.seek(zipInfo.cd.first);
            raf.read(cdAndEocd);

            // Adjust EOCD offset to CD.
            ByteBuffer cdAndEocdBuffer = ByteBuffer.wrap(cdAndEocd).order(ByteOrder.LITTLE_ENDIAN);
            cdAndEocdBuffer.position(cdAndEocdBuffer.capacity() - 6); // offset to CD

            int offset = (int) (zipInfo.cd.first + signatureBlock.capacity());
            cdAndEocdBuffer.putInt(offset);

            // Write sig block signature
            raf.seek(zipInfo.cd.first);
            raf.write(signatureBlock.array());

            // Write cd + eocd
            raf.write(cdAndEocdBuffer.array());

            channel.truncate(raf.length());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
