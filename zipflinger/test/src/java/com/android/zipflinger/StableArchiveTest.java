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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.Assert;
import org.junit.Test;

public class StableArchiveTest extends AbstractZipflingerTest {

    private void verifyBinaryIdentical(Path dst1, Path dst2) throws IOException {
        byte[] content1 = Files.readAllBytes(dst1);
        byte[] content2 = Files.readAllBytes(dst2);
        String message =
                String.format("Differing %s and %s", dst1.getFileName(), dst2.getFileName());
        Assert.assertArrayEquals(message, content1, content2);
    }

    @Test
    public void testDifferentOrderFiles() throws Exception {
        Path dst1 = getTestPath("testDifferentOrderFiles1.zip");
        try (Archive archive = new StableArchive(new ZipArchive(dst1))) {
            archive.add(new BytesSource(new byte[0], "a", 0));
            archive.add(new BytesSource(new byte[0], "b", 0));
        }

        Path dst2 = getTestPath("testDifferentOrderFiles2.zip");
        try (Archive archive = new StableArchive(new ZipArchive(dst2))) {
            archive.add(new BytesSource(new byte[0], "b", 0));
            archive.add(new BytesSource(new byte[0], "a", 0));
        }
        verifyBinaryIdentical(dst1, dst2);
    }

    @Test
    public void testDifferentOrderZips() throws Exception {
        String base = "testDifferentOrderZip";

        // Zip source 1
        Path src1 = getTestPath(base + "Source1.zip");
        try (Archive archive = new ZipArchive(src1)) {
            archive.add(new BytesSource(new byte[0], "a", 0));
            archive.add(new BytesSource(new byte[0], "b", 0));
        }

        // Zip source 2
        Path src2 = getTestPath(base + "Source2.zip");
        try (Archive archive = new StableArchive(new ZipArchive(src2))) {
            archive.add(new BytesSource(new byte[0], "c", 0));
            archive.add(new BytesSource(new byte[0], "d", 0));
        }

        // Create an archive by adding source 1 and then source 2.
        Path dst1 = getTestPath(base + "1.zip");
        try (Archive archive = new StableArchive(new ZipArchive(dst1))) {
            ZipSource zipSource1 = new ZipSource(src1);
            zipSource1.select("a", "a");
            zipSource1.select("b", "b");

            ZipSource zipSource2 = new ZipSource(src2);
            zipSource2.select("c", "c");
            zipSource2.select("d", "d");

            archive.add(zipSource1);
            archive.add(zipSource2);
        }

        // Same archive but add zipSource in different order.
        // Create an archive by adding source 2 and then source 1.
        Path dst2 = getTestPath(base + "2.zip");
        try (Archive archive = new StableArchive(new ZipArchive(dst2))) {
            ZipSource zipSource1 = new ZipSource(src1);
            zipSource1.select("a", "a");
            zipSource1.select("b", "b");

            ZipSource zipSource2 = new ZipSource(src2);
            zipSource2.select("c", "c");
            zipSource2.select("d", "d");

            archive.add(zipSource2);
            archive.add(zipSource1);
        }

        verifyBinaryIdentical(dst1, dst2);
    }

    @Test
    public void testDifferentOrderZipSelectedElements() throws Exception {
        String base = "testDifferentOrderZipEntries";

        // Zip source 1
        Path src1 = getTestPath(base + "Source1.zip");
        try (Archive archive = new ZipArchive(src1)) {
            archive.add(new BytesSource(new byte[0], "a", 0));
            archive.add(new BytesSource(new byte[0], "b", 0));
        }

        // Zip source 2
        Path src2 = getTestPath(base + "Source2.zip");
        try (Archive archive = new StableArchive(new ZipArchive(src2))) {
            archive.add(new BytesSource(new byte[0], "c", 0));
            archive.add(new BytesSource(new byte[0], "d", 0));
        }

        // Create an archive by adding source 1 (b, a) and then source 2 (d, c).
        Path dst1 = getTestPath(base + "1.zip");
        try (Archive archive = new StableArchive(new ZipArchive(dst1))) {
            ZipSource zipSource1 = new ZipSource(src1);
            zipSource1.select("b", "b");
            zipSource1.select("a", "a");

            ZipSource zipSource2 = new ZipSource(src2);
            zipSource2.select("d", "d");
            zipSource2.select("c", "c");

            archive.add(zipSource1);
            archive.add(zipSource2);
        }

        // Same archives order but different entry selection order.
        Path dst2 = getTestPath(base + "2.zip");
        try (Archive archive = new StableArchive(new ZipArchive(dst2))) {
            ZipSource zipSource1 = new ZipSource(src1);
            zipSource1.select("a", "a");
            zipSource1.select("b", "b");

            ZipSource zipSource2 = new ZipSource(src2);
            zipSource2.select("c", "c");
            zipSource2.select("d", "d");

            archive.add(zipSource1);
            archive.add(zipSource2);
        }

        verifyBinaryIdentical(dst1, dst2);
    }

    @Test
    public void testDifferentOrderDelete() throws Exception {
        String base = "testDifferentOrderZipEntries";

        // Zip source 1
        Path src1 = getTestPath(base + "Source1.zip");
        try (Archive archive = new ZipArchive(src1)) {
            archive.add(new BytesSource(new byte[0], "a", 0));
            archive.add(new BytesSource(new byte[0], "b", 0));
            archive.add(new BytesSource(new byte[0], "c", 0));
            archive.add(new BytesSource(new byte[0], "d", 0));
        }

        // Zip source 2 is a copy of Zip source 1
        Path src2 = getTestPath(base + "Source2.zip");
        Files.copy(src1, src2, StandardCopyOption.REPLACE_EXISTING);
        verifyBinaryIdentical(src1, src2);

        // Delete entries in order b, a, and c.
        try (Archive archive = new StableArchive(new ZipArchive(src1))) {
            archive.delete("b");
            archive.delete("a");
            archive.delete("c");
        }

        // Delete entries in reverse order (c, a, and b).
        try (Archive archive = new StableArchive(new ZipArchive(src2))) {
            archive.delete("c");
            archive.delete("a");
            archive.delete("b");
        }

        verifyBinaryIdentical(src1, src2);
    }
}
