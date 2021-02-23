/*
 * Copyright (C) 2020 The Android Open Source Project
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
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

class StreamSource extends Source {

    private final Path tmpStore;

    // Uses a tmp storage to store the payload then zero-copy it when needed.
    public StreamSource(@NonNull InputStream src, @NonNull String name, int compressionLevel)
            throws IOException {
        super(name);
        tmpStore = LargeFileSource.getTmpStoragePath(name);

        // Make sure we are not going to overwrite another tmp file.
        if (Files.exists(tmpStore)) {
            String msg =
                    String.format("Tmp storage '%s' already exists", tmpStore.toAbsolutePath());
            throw new IllegalStateException(msg);
        }

        // Just in case we crash before writeTo is called, attempt to clean up on VM exit.
        tmpStore.toFile().deleteOnExit();

        try (CheckedInputStream in = new CheckedInputStream(src, new CRC32())) {
            build(in, compressionLevel);
            crc = Ints.longToUint(in.getChecksum().getValue());
        } finally {
            src.close();
        }
    }

    private void build(InputStream in, int compressionLevel) throws IOException {
        if (compressionLevel == Deflater.NO_COMPRESSION) {
            buildStored(in);
        } else {
            buildDeflated(in, compressionLevel);
        }
    }

    private void buildDeflated(InputStream in, int compressionLevel) throws IOException {
        compressionFlag = LocalFileHeader.COMPRESSION_DEFLATE;
        // Pipe the src into the tmp compressed file.
        Deflater deflater = Compressor.getDeflater(compressionLevel);
        try (DeflaterOutputStream out =
                new DeflaterOutputStream(
                        Files.newOutputStream(tmpStore, StandardOpenOption.CREATE_NEW), deflater)) {
            int read;
            byte[] buffer = new byte[4096];
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        compressedSize = deflater.getBytesWritten();
        uncompressedSize = deflater.getBytesRead();
    }

    private void buildStored(InputStream in) throws IOException {
        compressionFlag = LocalFileHeader.COMPRESSION_NONE;
        long inputSize = 0;
        try (OutputStream out = Files.newOutputStream(tmpStore, StandardOpenOption.CREATE_NEW)) {
            int read;
            byte[] buffer = new byte[4096];
            while ((read = in.read(buffer)) != -1) {
                inputSize += read;
                out.write(buffer, 0, read);
            }
        }
        compressedSize = inputSize;
        uncompressedSize = inputSize;
    }

    @Override
    public void prepare() throws IOException {}

    @Override
    public long writeTo(@NonNull ZipWriter writer) throws IOException {
        try (FileChannel src = FileChannel.open(tmpStore, StandardOpenOption.READ)) {
            writer.transferFrom(src, 0, this.compressedSize);
            return this.compressedSize;
        } finally {
            Files.delete(tmpStore);
        }
    }
}
