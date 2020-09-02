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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.zip.Deflater;
import org.junit.Assert;
import org.junit.Test;

public class RepoTest extends AbstractZipflingerTest {

    @Test
    public void testGetContent() throws Exception {
        byte[][] files = new byte[2][1024];

        Random r = new Random(0);
        for (byte[] bytes : files) {
            r.nextBytes(bytes);
        }

        File file = getTestFile("testGetContent.zip");
        try (ZipArchive archive = new ZipArchive(file)) {
            for (int i = 0; i < files.length; i++) {
                archive.add(
                        new BytesSource(
                                files[i], Integer.toString(i), Deflater.NO_COMPRESSION + i));
            }
        }

        try (ZipRepo repo = new ZipRepo(file)) {
            for (int i = 0; i < files.length; i++) {
                String entryName = Integer.toString(i);
                try (InputStream inputStream = repo.getContent(entryName)) {
                    assertZipEntryMatch(inputStream, files[i]);
                }
            }
        }
    }

    private void assertZipEntryMatch(InputStream inputStream, byte[] content) throws IOException {
        NoCopyByteArrayOutputStream outputStream = new NoCopyByteArrayOutputStream(content.length);
        byte[] buffer = new byte[8192];

        // Exhauste input stream content
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }

        byte[] extracted = outputStream.toByteArray();
        Assert.assertEquals("Content size differ", content.length, extracted.length);
        Assert.assertArrayEquals("Content differs", content, extracted);
    }
}
