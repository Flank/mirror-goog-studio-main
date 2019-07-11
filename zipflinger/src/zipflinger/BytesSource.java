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
import java.util.zip.Deflater;

public class BytesSource extends Source {

    private ByteBuffer buffer;

    /**
     * @param bytes
     * @param name
     * @param compressionLevel One of java.util.zip.Deflater compression level.
     */
    public BytesSource(@NonNull byte[] bytes, @NonNull String name, int compressionLevel)
            throws IOException {
        super(name);
        crc = Crc32.crc32(bytes);
        uncompressedSize = bytes.length;
        if (compressionLevel == Deflater.NO_COMPRESSION) {
            buffer = ByteBuffer.wrap(bytes);
            compressedSize = uncompressedSize;
            compressionFlag = LocalFileHeader.COMPRESSION_NONE;
        } else {
            buffer = Compressor.deflate(bytes, compressionLevel);
            compressedSize = buffer.limit();
            compressionFlag = LocalFileHeader.COMPRESSION_DEFLATE;
        }
    }

    @Override
    void prepare() {}

    @Override
    int writeTo(@NonNull ZipWriter writer) throws IOException {
        return writer.write(buffer);
    }
}
