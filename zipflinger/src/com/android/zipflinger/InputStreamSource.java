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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

public class InputStreamSource extends Source {

    private ByteBuffer buffer;
    private final InputStream inputStream;
    private final int compressionLevel;

    /**
     * InputStreamSource takes takes ownership of the InputStream and close it after emptying it.
     *
     * @param inputStream
     * @param name
     * @param compressionLevel One of java.util.zip.Deflater compression level.
     */
    public InputStreamSource(
            @NonNull InputStream inputStream, @NonNull String name, int compressionLevel) {
        super(name);
        this.inputStream = inputStream;
        this.compressionLevel = compressionLevel;
    }

    @Override
    void prepare() throws IOException {
        NoCopyByteArrayOutputStream ncbos = new NoCopyByteArrayOutputStream(16000);
        byte[] tmpBuffer = new byte[16000];
        int bytesRead;
        while ((bytesRead = inputStream.read(tmpBuffer)) != -1) {
            ncbos.write(tmpBuffer, 0, bytesRead);
        }
        inputStream.close();

        uncompressedSize = ncbos.getCount();
        crc = Crc32.crc32(ncbos.buf(), 0, ncbos.getCount());
        if (compressionLevel == Deflater.NO_COMPRESSION) {
            buffer = ncbos.getByteBuffer();
            compressedSize = uncompressedSize;
            compressionFlag = LocalFileHeader.COMPRESSION_NONE;
        } else {
            buffer = Compressor.deflate(ncbos.buf(), 0, ncbos.getCount(), compressionLevel);
            compressedSize = buffer.limit();
            compressionFlag = LocalFileHeader.COMPRESSION_DEFLATE;
        }
    }

    @Override
    int writeTo(@NonNull ZipWriter writer) throws IOException {
        return writer.write(buffer);
    }
}
