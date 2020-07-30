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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.junit.Assert;
import org.junit.Test;

public class Zip64Test extends AbstractZipflingerTest {

    private static final int ONE_GIB = 1 << 30;

    @Test
    public void testZip64Parsing() throws Exception {
        File archive = getFile("5GiBFile.zip");
        verifyArchive(archive);
    }

    @Test
    public void testZip64Writing() throws Exception {
        File dst = getTestFile("testZip64Writing.zip");
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
        File dst = getTestFile("testZip64Transfer.zip");
        File bigZipFile = getFile("5GiBFile.zip");
        try (ZipArchive archive = new ZipArchive(dst, Zip64.Policy.ALLOW)) {
            ZipSource zipSource = new ZipSource(bigZipFile);
            zipSource.select("empty5GiB", "empty5GiB");
            archive.add(zipSource);
        }
        verifyArchive(dst);
    }

    @Test
    public void testForbiddenZip64Add() throws Exception {
        File dst = getTestFile("testForbiddenZip64Add.zip");
        File bigZipFile = getFile("5GiBFile.zip");
        try (ZipArchive archive = new ZipArchive(dst, Zip64.Policy.FORBID)) {
            ZipSource zipSource = new ZipSource(bigZipFile);
            zipSource.select("empty5GiB", "empty5GiB");
            archive.add(zipSource);
            Assert.fail("Adding " + bigZipFile.getName() + " to non-zip64 should have failed");
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void testAllowedZip64Add() throws Exception {
        File dst = getTestFile("testAllowedZip64Add.zip");
        File bigZipFile = getFile("5GiBFile.zip");
        try (ZipArchive archive = new ZipArchive(dst, Zip64.Policy.ALLOW)) {
            ZipSource zipSource = new ZipSource(bigZipFile);
            zipSource.select("empty5GiB", "empty5GiB");
            archive.add(zipSource);
        }
        verifyArchive(dst);
    }

    @Test
    public void testForbiddenZip64Opening() throws Exception {
        File bigZipFile = getFile("5GiBFile.zip");
        File src = getTestFile("testForbiddenZip64Opening.zip");
        Files.copy(bigZipFile.toPath(), src.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try (ZipArchive archive = new ZipArchive(src, Zip64.Policy.FORBID)) {
            Assert.fail(
                    "Opening " + bigZipFile.getName() + " as FORBIDDEN zip64 should have failed");
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void testAllowedZip64Opening() throws Exception {
        File bigZipFile = getFile("5GiBFile.zip");
        File src = getTestFile("testAllowedZip64Opening.zip");
        Files.copy(bigZipFile.toPath(), src.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try (ZipArchive archive = new ZipArchive(src, Zip64.Policy.ALLOW)) {}
        verifyArchive(bigZipFile);
    }

    @Test
    public void testForbiddenZip64Entries() throws Exception {
        File dst = getTestFile("testAllowedZip64Entries.zip");
        long numEntries = Ints.USHRT_MAX + 1;
        try {
            createArchive(dst, numEntries, Zip64.Policy.FORBID);
            Assert.fail("Archive with " + numEntries + " entries should have failed");
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void testAllowedZip64Entries() throws Exception {
        File dst = getTestFile("testAllowedZip64Entries.zip");
        long numEntries = Ints.USHRT_MAX + 1;
        createArchive(dst, numEntries, Zip64.Policy.ALLOW);
        verifyArchive(dst);
    }

    private static void createArchive(File dst, long numEntries, Zip64.Policy policy)
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
