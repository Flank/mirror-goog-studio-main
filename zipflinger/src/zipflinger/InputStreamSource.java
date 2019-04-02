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
import java.io.InputStream;
import java.nio.ByteBuffer;

public class InputStreamSource extends Source {

    private ByteBuffer buffer;
    private final InputStream inputStream;
    private final boolean compress;

    /**
     * InputStreamSource takes takes ownership of the InputStream and close it after emptying it.
     *
     * @param inputStream
     * @param name
     * @param compress
     */
    public InputStreamSource(
            @NonNull InputStream inputStream, @NonNull String name, boolean compress) {
        super(name);
        this.inputStream = inputStream;
        this.compress = compress;
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
        if (compress) {
            buffer = Compressor.deflate(ncbos.buf(), 0, ncbos.getCount());
            compressedSize = buffer.limit();
            compressionFlag = LocalFileHeader.COMPRESSION_DEFLATE;
        } else {
            buffer = ncbos.getByteBuffer();
            compressedSize = uncompressedSize;
            compressionFlag = LocalFileHeader.COMPRESSION_NONE;
        }
    }

    @Override
    void writeTo(@NonNull ZipWriter writer) throws IOException {
        writer.write(buffer);
    }
}
