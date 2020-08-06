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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.junit.Assert;
import org.junit.Test;

public class CompressorTest extends AbstractZipflingerTest {

    @Test
    public void testDeflateInflate() throws IOException, DataFormatException {
        Path src = getPath("file4.txt");
        byte[] uncompressed = Files.readAllBytes(src);
        ByteBuffer compressed = Compressor.deflate(uncompressed, Deflater.DEFAULT_COMPRESSION);

        int uncompressedSize = uncompressed.length;
        int compressedSize = compressed.limit();

        Assert.assertTrue(
                "Compressed size <= uncompressed size", compressedSize <= uncompressedSize);

        Inflater inflater = new Inflater(true);
        inflater.setInput(compressed.array(), 0, compressedSize);
        byte[] inflated = new byte[uncompressedSize];
        int inflatedLength = inflater.inflate(inflated);

        Assert.assertEquals(
                "Inflated length is equal to original length", uncompressedSize, inflatedLength);
        Assert.assertArrayEquals("Before/After bytes", uncompressed, inflated);
    }
}
