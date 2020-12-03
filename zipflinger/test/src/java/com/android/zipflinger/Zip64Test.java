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

import static java.util.zip.Deflater.NO_COMPRESSION;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.Assert;
import org.junit.Test;

public class Zip64Test extends AbstractZipflingerTest {
    private static final int ONE_GIB = 1 << 30;

    @Test
    public void testZip64Parsing() throws Exception {
        Path archive = getPath("5GiBFile.zip");
        verifyArchive(archive);
    }

    @Test
    public void testZip64Writing() throws Exception {
        Path dst = getTestPath("testZip64Writing.zip");
        byte[] bytes = new byte[ONE_GIB];
        try (ZipArchive archive = new ZipArchive(dst, Zip64.Policy.ALLOW)) {
            for (int i = 0; i < 5; i++) {
                BytesSource source = new BytesSource(bytes, "file" + i, NO_COMPRESSION);
                archive.add(source);
            }
        }
        verifyArchive(dst);
    }

    @Test
    public void testZip64Transfer() throws Exception {
        Path dst = getTestPath("testZip64Transfer.zip");
        Path bigZipFile = getPath("5GiBFile.zip");
        try (ZipArchive archive = new ZipArchive(dst, Zip64.Policy.ALLOW)) {
            ZipSource zipSource = new ZipSource(bigZipFile);
            zipSource.select("empty5GiB", "empty5GiB");
            archive.add(zipSource);
        }
        verifyArchive(dst);
    }

    @Test
    public void testForbiddenZip64Add() throws Exception {
        Path dst = getTestPath("testForbiddenZip64Add.zip");
        Path bigZipFile = getPath("5GiBFile.zip");
        try (ZipArchive archive = new ZipArchive(dst, Zip64.Policy.FORBID)) {
            ZipSource zipSource = new ZipSource(bigZipFile);
            zipSource.select("empty5GiB", "empty5GiB");
            archive.add(zipSource);
            Assert.fail("Adding " + bigZipFile.getFileName() + " to non-zip64 should have failed");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testAllowedZip64Add() throws Exception {
        Path dst = getTestPath("testAllowedZip64Add.zip");
        Path bigZipFile = getPath("5GiBFile.zip");
        try (ZipArchive archive = new ZipArchive(dst, Zip64.Policy.ALLOW)) {
            ZipSource zipSource = new ZipSource(bigZipFile);
            zipSource.select("empty5GiB", "empty5GiB");
            archive.add(zipSource);
        }
        verifyArchive(dst);
    }

    @Test
    public void testForbiddenZip64Opening() throws Exception {
        Path bigZipFile = getPath("5GiBFile.zip");
        Path src = getTestPath("testForbiddenZip64Opening.zip");
        Files.copy(bigZipFile, src, StandardCopyOption.REPLACE_EXISTING);
        try (ZipArchive archive = new ZipArchive(src, Zip64.Policy.FORBID)) {
            Assert.fail(
                    "Opening "
                            + bigZipFile.getFileName()
                            + " as FORBIDDEN zip64 should have failed");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testAllowedZip64Opening() throws Exception {
        Path bigZipFile = getPath("5GiBFile.zip");
        Path src = getTestPath("testAllowedZip64Opening.zip");
        Files.copy(bigZipFile, src, StandardCopyOption.REPLACE_EXISTING);
        try (ZipArchive archive = new ZipArchive(src, Zip64.Policy.ALLOW)) {}
        verifyArchive(bigZipFile);
    }

    @Test
    public void testForbiddenZip64Entries() throws Exception {
        Path dst = getTestPath("testAllowedZip64Entries.zip");
        long numEntries = Ints.USHRT_MAX + 1;
        try {
            createArchive(dst, numEntries, Zip64.Policy.FORBID);
            Assert.fail("Archive with " + numEntries + " entries should have failed");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testAllowedZip64Entries() throws Exception {
        Path dst = getTestPath("testAllowedZip64Entries.zip");
        long numEntries = Ints.USHRT_MAX + 1;
        createArchive(dst, numEntries, Zip64.Policy.ALLOW);
        verifyArchive(dst);
    }

    private static void createArchive(Path dst, long numEntries, Zip64.Policy policy)
            throws IOException {
        byte[] empty = new byte[0];
        try (ZipArchive archive = new ZipArchive(dst, policy)) {
            for (int i = 0; i < numEntries; i++) {
                BytesSource source = new BytesSource(empty, Integer.toString(i), NO_COMPRESSION);
                archive.add(source);
            }
        }
    }
}
