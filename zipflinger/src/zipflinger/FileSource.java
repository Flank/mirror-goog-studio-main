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
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSource extends Source {
    private final File file;
    private ByteBuffer buffer;
    private final boolean compress;

    public FileSource(@NonNull File file, @NonNull String name, boolean compress) {
        super(name);
        this.file = file;
        this.compress = compress;
    }

    @Override
    void prepare() throws IOException {
        Path filePath = file.toPath();
        byte[] rawBytes = Files.readAllBytes(filePath);

        crc = Crc32.crc32(rawBytes);
        uncompressedSize = rawBytes.length;
        if (compress) {
            buffer = Compressor.deflate(rawBytes);
            compressedSize = buffer.limit();
            compressionFlag = LocalFileHeader.COMPRESSION_DEFLATE;
        } else {
            buffer = ByteBuffer.wrap(rawBytes);
            compressedSize = rawBytes.length;
            compressionFlag = LocalFileHeader.COMPRESSION_NONE;
        }
    }

    @Override
    void writeTo(@NonNull ZipWriter writer) throws IOException {
        writer.write(buffer);
    }
}
