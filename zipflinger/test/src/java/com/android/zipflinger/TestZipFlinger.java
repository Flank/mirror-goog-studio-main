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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import org.junit.Assert;
import org.junit.Test;

public class TestZipFlinger extends TestBase {

    private static final int COMP_SPED = Deflater.BEST_SPEED;
    private static final int COMP_NONE = Deflater.NO_COMPRESSION;

    @Test
    public void testDeleteRecord() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testDeleteRecord.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            zipArchive.delete("file2.txt");
        }

        // Test zip is a valid archive.
        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Assert.assertEquals("Num entries", 2, entries.size());
        Assert.assertTrue("Entries contains file1.txt", entries.containsKey("file1.txt"));
        Assert.assertFalse("Entries contains file2.txt", entries.containsKey("file2.txt"));
        Assert.assertTrue("Entries contains file3.txt", entries.containsKey("file3.txt"));

        // Topdown parsing with SDK zip.
        verifyArchive(dst.toFile());
    }

    @Test
    public void testAddFileEntry() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testAddFileEntry.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            BytesSource bs = new BytesSource(getFile("file4.txt"), "file4.txt", COMP_NONE);
            zipArchive.add(bs);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Assert.assertEquals("Num entries", 4, entries.size());
        Assert.assertTrue("Entries contains file1.txt", entries.containsKey("file1.txt"));
        Assert.assertTrue("Entries contains file2.txt", entries.containsKey("file2.txt"));
        Assert.assertTrue("Entries contains file3.txt", entries.containsKey("file3.txt"));
        Assert.assertTrue("Entries contains file4.txt", entries.containsKey("file4.txt"));

        // Topdown parsing with SDK zip.
        verifyArchive(dst.toFile());
    }

    @Test
    public void testAddCompressedEntry() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testAddCompressedEntry.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        ZipArchive zipArchive = new ZipArchive(dst.toFile());
        BytesSource bs = new BytesSource(getFile("file4.txt"), "file4.txt", COMP_SPED);
        zipArchive.add(bs);
        zipArchive.close();

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Assert.assertEquals("Num entries", 4, entries.size());
        Assert.assertTrue("Entries contains file1.txt", entries.containsKey("file1.txt"));
        Assert.assertTrue("Entries contains file2.txt", entries.containsKey("file2.txt"));
        Assert.assertTrue("Entries contains file3.txt", entries.containsKey("file3.txt"));
        Assert.assertTrue("Entries contains file4.txt", entries.containsKey("file4.txt"));
        Entry file4 = entries.get("file4.txt");
        Assert.assertNotEquals(
                "file4.txt size", file4.getCompressedSize(), file4.getUncompressedSize());

        verifyArchive(dst.toFile());
    }

    @Test
    public void testModifyingClosedArchive() throws IOException {
        Path dst = getTestPath("newArchive.zip");

        ZipArchive zipArchive = new ZipArchive(dst.toFile());
        zipArchive.close();

        boolean exceptionCaught = false;
        try {
            zipArchive.add(new BytesSource(dst.toFile(), "", COMP_NONE));
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Exception thrown on post-close add", exceptionCaught);

        exceptionCaught = false;
        try {
            zipArchive.add(new BytesSource(new byte[10], "deadbeed", COMP_SPED));
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Exception thrown on post-close addCommpressedSource", exceptionCaught);

        exceptionCaught = false;
        try {
            zipArchive.add(new ZipSource(getFile("1-2-3files.zip")));
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Exception thrown on post-close add", exceptionCaught);

        exceptionCaught = false;
        try {
            zipArchive.delete("null");
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Exception thrown on post-close delete", exceptionCaught);

        exceptionCaught = false;
        try {
            zipArchive.close();
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Exception thrown on double close", exceptionCaught);
    }

    @Test
    public void testFileSourceCompressed() throws IOException {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testFileSourceCompressed.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            BytesSource source = new BytesSource(getFile("file4.txt"), "file4.txt", COMP_SPED);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Entry entry = entries.get("file4.txt");

        Assert.assertTrue(
                "File source was compressed",
                entry.getUncompressedSize() != entry.getCompressedSize());
    }

    @Test
    public void testInputStreamSourceCompressed() throws IOException {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testInputStreamSourceCompressed.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            InputStream stream = new FileInputStream(getFile("file4.txt"));
            BytesSource source = new BytesSource(stream, "file4.txt", COMP_SPED);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Entry entry = entries.get("file4.txt");

        Assert.assertTrue(
                "File source was compressed",
                entry.getUncompressedSize() != entry.getCompressedSize());
    }

    @Test
    public void testBytesSourceCompressed() throws IOException {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testBytesSourceCompressed.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            byte[] bytes = Files.readAllBytes(getPath("file4.txt"));
            BytesSource source = new BytesSource(bytes, "file4.txt", COMP_SPED);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Entry entry = entries.get("file4.txt");

        Assert.assertTrue(
                "File source was compressed",
                entry.getUncompressedSize() != entry.getCompressedSize());
    }

    @Test
    public void testFileSourceAlignment() throws IOException {
        for (long aligment : ALIGNMENTS) {
            testFileSourceAlignment(aligment);
        }
    }

    private static String makeString(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append('f');
        }
        return sb.toString();
    }

    private void testFileSourceAlignment(long alignment) throws IOException {
        Path dst = getTestPath("testFileSourceAlignment-" + alignment + ".zip");
        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            for (int length = 0; length < alignment; length++) {
                String name = makeString(length);
                BytesSource source = new BytesSource(getFile("file4.txt"), name, COMP_NONE);
                source.align(alignment);
                zipArchive.add(source);
            }
        }

        Map<String, Entry> entries = verifyArchive(dst.toFile());

        for (Entry entry : entries.values()) {
            String name = entry.getName();
            String message = "FileSource align on " + alignment + "namesize=" + name.length();
            Assert.assertEquals(message, 0, entry.getPayloadLocation().first % alignment);
        }
        Files.delete(dst);
    }

    @Test
    public void testBytesSourceAlignment() throws IOException {
        for (long aligment : ALIGNMENTS) {
            testBytesSourceAlignment(aligment);
        }
    }

    public void testBytesSourceAlignment(long alignment) throws IOException {
        Path dst = getTestPath("testBytesSourceAlignment-" + alignment + ".zip");

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            byte[] bytes = Files.readAllBytes(getPath("file4.txt"));
            BytesSource fileSource = new BytesSource(bytes, "file4.txt", COMP_NONE);
            fileSource.align(alignment);
            zipArchive.add(fileSource);
        }

        Map<String, Entry> entries = verifyArchive(dst.toFile());
        Entry entry = entries.get("file4.txt");

        Assert.assertEquals(
                "BytesSource alignment", 0, entry.getPayloadLocation().first % alignment);
        Files.delete(dst);
    }

    @Test
    public void testZipSourceAlignment() throws IOException {
        for (long aligment : ALIGNMENTS) {
            testZipSourceAlignment(aligment);
        }
    }

    public void testZipSourceAlignment(long alignment) throws IOException {
        Path dst = getTestPath("testZipSourceAlignment-" + alignment + ".zip");

        ZipSource source = new ZipSource(getFile("4-5files.zip"));
        source.select("file4.txt", "file4.txt").align(alignment);
        source.select("file5.txt", "file5.txt").align(alignment);

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            zipArchive.add(source);
        }

        Map<String, Entry> entries = verifyArchive(dst.toFile());

        Entry entry = entries.get("file4.txt");
        Assert.assertEquals("ZipSource alignment", 0, entry.getPayloadLocation().first % alignment);

        entry = entries.get("file5.txt");
        Assert.assertEquals("ZipSource alignment", 0, entry.getPayloadLocation().first % alignment);
        Files.delete(dst);
    }

    @Test
    public void testInputSourceAlignment() throws IOException {
        for (long aligment : ALIGNMENTS) {
            testInputSourceAlignment(aligment);
        }
    }

    public void testInputSourceAlignment(long alignment) throws IOException {
        Path dst = getTestPath("testInputSourceAlignment.zip");

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile());
                InputStream stream = new FileInputStream(getFile("file4.txt"))) {
            BytesSource source = new BytesSource(stream, "file4.txt", COMP_NONE);
            source.align(alignment);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Entry entry = entries.get("file4.txt");

        Assert.assertEquals(
                "InputSource alignment", 0, entry.getPayloadLocation().first % alignment);
        Files.delete(dst);
    }

    @Test
    public void testNameCollision() throws IOException {
        ZipArchive zipArchive = new ZipArchive(getTestFile("nonexistant.zip"));
        File file = getFile("1-2-3files.zip");
        BytesSource source = new BytesSource(file, "name", COMP_NONE);

        boolean exceptionCaught = false;
        try {
            zipArchive.add(source);
            zipArchive.add(source);
            zipArchive.close();
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Name collision detected", exceptionCaught);
    }

    @Test
    public void testExistantDoubleDelete() throws IOException {
        Path src = getPath("1-2-3files.zip");
        Path archive = getTestPath("testExistantDoubleDelete.zip");
        Files.copy(src, archive, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(archive.toFile())) {
            zipArchive.delete("file4.txt");
            zipArchive.delete("file4.txt");
        }
    }

    @Test
    public void testNonExistantDelete() throws IOException {
        Path src = getPath("1-2-3files.zip");
        Path archive = getTestPath("testNonExistantDelete.zip");
        Files.copy(src, archive, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(archive.toFile())) {
            zipArchive.delete("non-existant.txt");
        }
    }

    // Test deleting an entry resulting in virtual entry.
    @Test
    public void testVirtualEntryExistingEntryDeleted() throws IOException {
        File file = getTestFile("testVirtualEntryExisting.zip");
        try (ZipArchive archive = new ZipArchive(file)) {
            byte[] entry1Bytes = new byte[1_000];
            BytesSource source1 = new BytesSource(entry1Bytes, "entry1", COMP_NONE);
            archive.add(source1);

            byte[] entry2Bytes = new byte[1_000];
            BytesSource source2 = new BytesSource(entry2Bytes, "entry2", COMP_NONE);
            archive.add(source2);

            byte[] entry3Bytes = new byte[1_000];
            BytesSource source3 = new BytesSource(entry3Bytes, "entry3", COMP_NONE);
            archive.add(source3);
        }

        try (ZipArchive archiveDeleter = new ZipArchive(file)) {
            archiveDeleter.delete("entry2");
        }

        verifyArchive(file);
    }

    @Test
    public void testVirtualEntryNewEntryDeleted() throws IOException {
        File file = getTestFile("testVirtualEntryNew.zip");
        try (ZipArchive archive = new ZipArchive(file)) {
            byte[] entry1Bytes = new byte[1_000];
            BytesSource source1 = new BytesSource(entry1Bytes, "entry1", COMP_NONE);
            archive.add(source1);

            byte[] entry2Bytes = new byte[1_000];
            BytesSource source2 = new BytesSource(entry2Bytes, "entry2", COMP_NONE);
            archive.add(source2);

            byte[] entry3Bytes = new byte[1_000];
            BytesSource source3 = new BytesSource(entry3Bytes, "entry3", COMP_NONE);
            archive.add(source3);

            archive.delete("entry2");
        }

        verifyArchive(file);
    }

    @Test
    public void testVirtualEntryLargeDelete() throws IOException {
        File file = getTestFile("testVirtualEntryLargeDelete.zip");
        try (ZipArchive archive = new ZipArchive(file)) {

            byte[] padding1Bytes = new byte[1];
            BytesSource source1 = new BytesSource(padding1Bytes, "padding1", COMP_NONE);
            archive.add(source1);

            byte[] entry2Bytes = new byte[1_000_000];
            BytesSource source2 = new BytesSource(entry2Bytes, "entry1", COMP_NONE);
            archive.add(source2);

            BytesSource source3 = new BytesSource(padding1Bytes, "padding2", COMP_NONE);
            archive.add(source3);

            byte[] entry4Bytes = new byte[65_536];
            BytesSource source4 = new BytesSource(entry4Bytes, "entry2", COMP_NONE);
            archive.add(source4);

            BytesSource source5 = new BytesSource(padding1Bytes, "padding3", COMP_NONE);
            archive.add(source5);

            byte[] entry6Bytes = new byte[32_767];
            BytesSource source6 = new BytesSource(entry6Bytes, "entry3", COMP_NONE);
            archive.add(source6);

            BytesSource source7 = new BytesSource(padding1Bytes, "padding4", COMP_NONE);
            archive.add(source7);
        }

        try (ZipArchive archive = new ZipArchive(file)) {
            archive.delete("entry1");
            archive.delete("entry2");
            archive.delete("entry3");
        }

        verifyArchive(file);
    }
    // Test deleting an entry resulting in multiple virtual entry (more than 64KiB entry).
    @Test
    public void testMultipleVirtualEntry() throws IOException {
        File file = getTestFile("testMultipleVirtualEntry.zip");
        try (ZipArchive archive = new ZipArchive(file)) {
            byte[] entry1Bytes = new byte[1_000];
            BytesSource source1 = new BytesSource(entry1Bytes, "entry1", COMP_NONE);
            archive.add(source1);

            byte[] entry2Bytes = new byte[128_000_000];
            BytesSource source2 = new BytesSource(entry2Bytes, "entry2", COMP_NONE);
            archive.add(source2);

            byte[] entry3Bytes = new byte[1_000];
            BytesSource source3 = new BytesSource(entry3Bytes, "entry3", COMP_NONE);
            archive.add(source3);
        }

        verifyArchive(file);
    }

    @Test
    public void TestDirectories() throws IOException {
        File file = getFile("zip_with_directories.zip");
        Map<String, Entry> entries = ZipArchive.listEntries(file);

        Assert.assertTrue("Folder found", entries.get("contents/").isDirectory());
        Assert.assertTrue("Sub-folder found", entries.get("contents/folder/").isDirectory());
        Assert.assertFalse("File1 found", entries.get("contents/folder/b.txt").isDirectory());
        Assert.assertFalse("File2 found", entries.get("contents/a.txt").isDirectory());
    }

    @Test
    public void testCompressionDetection() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testCompressionDetection.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            BytesSource source = new BytesSource(new byte[0], "file4.txt", COMP_NONE);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Assert.assertTrue("file1.txt is compressed", entries.get("file1.txt").isCompressed());
        Assert.assertTrue("file2.txt is compressed", entries.get("file2.txt").isCompressed());
        Assert.assertTrue("file3.txt is compressed", entries.get("file3.txt").isCompressed());
        Assert.assertFalse("file4.txt is not compressed", entries.get("file4.txt").isCompressed());
    }

    @Test
    public void testCompressionMode() throws Exception {
        File archiveFile = getTestFile("testCompressionMode.zip");
        File input = getFile("text.txt");

        try (ZipArchive zipArchive = new ZipArchive(archiveFile)) {
            BytesSource source = new BytesSource(input, "text.tx", Deflater.NO_COMPRESSION);
            zipArchive.add(source);
        }
        long storedSize = Files.size(archiveFile.toPath());

        Files.deleteIfExists(archiveFile.toPath());
        try (ZipArchive zipArchive = new ZipArchive(archiveFile)) {
            BytesSource source = new BytesSource(input, "text.tx", Deflater.BEST_SPEED);
            zipArchive.add(source);
        }
        long speedSize = Files.size(archiveFile.toPath());

        Assert.assertTrue("NO_COMPRESSION is bigger than BEST_SPEED", storedSize > speedSize);

        Files.deleteIfExists(archiveFile.toPath());
        try (ZipArchive zipArchive = new ZipArchive(archiveFile)) {
            BytesSource source = new BytesSource(input, "text.tx", Deflater.BEST_COMPRESSION);
            zipArchive.add(source);
        }
        long compressionSize = Files.size(archiveFile.toPath());

        Assert.assertTrue(
                "BEST_SPEED is bigger than BEST_COMPRESSION", speedSize > compressionSize);
    }


    @Test
    public void testZipEntryChangeCompression() throws Exception {
        Path src = getPath("two_files.zip");
        Path dst = getTestPath("testZipEntryChanges.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Entry> entries = ZipArchive.listEntries(src.toFile());
        Assert.assertTrue("Entry compressed", entries.get("compressed.random").isCompressed());
        Assert.assertFalse("Entry !compressed", entries.get("uncompressed.random").isCompressed());

        try (ZipArchive archive = new ZipArchive(dst.toFile())) {
            ZipSource zipSource = new ZipSource(src.toFile());
            zipSource.select("uncompressed.random", "a", Deflater.NO_COMPRESSION);
            zipSource.select("uncompressed.random", "b", Deflater.BEST_SPEED);
            zipSource.select("compressed.random", "c", Deflater.NO_COMPRESSION);
            zipSource.select("compressed.random", "d", Deflater.BEST_SPEED);
            archive.add(zipSource);
        }
        verifyArchive(dst.toFile());

        entries = ZipArchive.listEntries(dst.toFile());
        Assert.assertFalse("Entry a was modified", entries.get("a").isCompressed());
        Assert.assertTrue("Entry b was not modified", entries.get("b").isCompressed());
        Assert.assertFalse("Entry c was not modified", entries.get("c").isCompressed());
        Assert.assertTrue("Entry d was modified", entries.get("d").isCompressed());
    }

    @Test
    public void testZipExtraction() throws Exception {
        try (ZipArchive archive = new ZipArchive(getTestFile("testZipExtraction.zip"))) {

            // Add compressed file
            File file1File = getFile("file1.txt");
            byte[] file1Bytes = Files.readAllBytes(file1File.toPath());
            archive.add(new BytesSource(file1Bytes, "file1.txt", Deflater.BEST_SPEED));
            ByteBuffer file1ByteBuffer = archive.getContent("file1.txt");
            Assert.assertArrayEquals(
                    "Extracted content does not match what was presented for compression",
                    file1Bytes,
                    toByteArray(file1ByteBuffer));

            // Add uncompressed file
            archive.add(new BytesSource(file1Bytes, "file1-2.txt", Deflater.NO_COMPRESSION));
            ByteBuffer file1_2ByteBuffer = archive.getContent("file1-2.txt");
            Assert.assertArrayEquals(
                    "Extracted content does not match what was presented for storage(uncompressed)",
                    file1Bytes,
                    toByteArray(file1_2ByteBuffer));
        }
    }

    @Test
    public void testZipExtractionFromJavaZip() throws Exception {
        File[] files = {getFile("file1.txt"), getFile("file2.txt"), getFile("file3.txt")};
        File archive = getTestFile("testZipExtractionFromJavaZip.zip");
        createZip(archive, files);

        try (ZipArchive zipArchive = new ZipArchive(archive)) {
            for (File file : files) {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                byte[] archiveBytes = toByteArray(zipArchive.getContent(file.getName()));
                Assert.assertArrayEquals(fileBytes, archiveBytes);
            }
        }
    }

    @Test
    public void testZipExtractionFromZipUtils() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testZipExtractionFromZipUtils.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive archive = new ZipArchive(dst.toFile())) {
            File[] files = {getFile("file1.txt"), getFile("file2.txt"), getFile("file3.txt")};
            for (File file : files) {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                byte[] archiveBytes = toByteArray(archive.getContent(file.getName()));
                Assert.assertArrayEquals(fileBytes, archiveBytes);
            }
        }
    }

    @Test
    public void testVirtualSpecialEntry() throws Exception {
        // Test the case where the space to fill in a virtual entry is between
        // max uint16_t and (max uint16_t - LHF size - name size (=1)).
        // This is a edge case where the virtual entry filling algorithm has to account
        // for not consuming as much as possible but leave enough space for the next LFH.
        int entrySize =
                Math.toIntExact(Ints.USHRT_MAX - LocalFileHeader.LOCAL_FILE_HEADER_SIZE - 2);
        Path dst = getTestPath("testVirtualSpecialEntry.zip");
        try (ZipArchive archive = new ZipArchive(dst.toFile())) {
            archive.add(new BytesSource(new byte[entrySize], "a", 0));
            archive.add(new BytesSource(new byte[10], "b", 0));
            archive.delete("a");
        }
    }

    @Test
    public void testBigZipParsing() throws Exception {
        File archive = getTestFile("testBigkZipParsing.zip");
        int numFiles = 3;
        int sizePerFile = 1_000_000_000;
        createZip(numFiles, sizePerFile, archive);
        Assert.assertTrue(archive.length() > numFiles * sizePerFile);

        try (ZipArchive zipArchive = new ZipArchive(archive)) {
            List<String> list = zipArchive.listEntries();
            Assert.assertEquals("Num entries differ", list.size(), 3);
        }
        boolean deleted = archive.delete();
        Assert.assertTrue("Archive was not deleted", deleted);
    }

    @Test
    public void testBigZipGeneration() throws Exception {
        File archive = getTestFile("testBigZipGeneration.zip");
        try (ZipArchive zipArchive = new ZipArchive(archive)) {
            for (int i = 0; i < 3; i++) {
                byte[] bytes = new byte[1_000_000_000];
                BytesSource source = new BytesSource(bytes, "file" + i, Deflater.NO_COMPRESSION);
                zipArchive.add(source);
            }
        }
        Assert.assertTrue(
                "Zip file below expected size", Files.size(archive.toPath()) > 3_000_000_000L);
        verifyArchive(archive);
        boolean deleted = archive.delete();
        Assert.assertTrue("Archive was not deleted", deleted);
    }

    @Test
    // Regression test for b/143215332 where "int cannot be converted to ushort" in the case
    // gap filling space is a multiple of (65_535 + [30-33]) bytes
    public void testGapTooBigForOneVirtualEntry() throws Exception {
        Path dst = getTestPath("testGapTooBigForOneVirtualEntry.zip");
        try (ZipArchive archive = new ZipArchive(dst.toFile())) {
            // This entry will result in a 30 (header) + 1 (filename length) + max ushort (payload)
            // size
            archive.add(new BytesSource(new byte[65_535], "1", 0));
            archive.add(new BytesSource(new byte[0], "file2", 0));
        }
        try (ZipArchive archive = new ZipArchive(dst.toFile())) {
            archive.delete("1");
        }
        verifyArchive(dst.toFile());
    }

    @Test
    public void testDeleteSmallestPossibleEntry() throws Exception {
        Path dst = getTestPath("testDeleteSmallestPossibleEntry.zip");
        try (ZipArchive archive = new ZipArchive(dst.toFile())) {
            archive.add(new BytesSource(new byte[0], "", 0));
            archive.add(new BytesSource(new byte[0], "file2", 0));
        }
        try (ZipArchive archive = new ZipArchive(dst.toFile())) {
            archive.delete("");
        }
        verifyArchive(dst.toFile());
    }

    private ByteBuffer extractCentralDirectory(Path archivePath) throws IOException {
        ZipMap map = ZipMap.from(archivePath.toFile(), false);

        Path cdDumpPath = getTestPath("cd_dump");
        try (FileChannel channel =
                        FileChannel.open(
                                cdDumpPath,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.READ,
                                StandardOpenOption.CREATE);
                ZipWriter writer = new ZipWriter(channel)) {
            map.getCentralDirectory().write(writer);
        }

        // Extract the CD from the archive
        byte[] cdBytes = Files.readAllBytes(cdDumpPath);
        return ByteBuffer.wrap(cdBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    // Regression test for b/141861587
    public void testVersionMadeByZero() throws Exception {
        int fileSize = 4;
        Path dst = getTestPath("testMadeByZero.zip");
        try (ZipArchive archive = new ZipArchive(dst.toFile())) {
            archive.add(new BytesSource(new byte[fileSize], "file1", 0));
        }
        ByteBuffer cd = extractCentralDirectory(dst);

        // Check signature
        int signature = cd.getInt();
        Assert.assertEquals("CD signature not found", signature, CentralDirectoryRecord.SIGNATURE);

        // Check version Made-kby
        short versionMadeBy = cd.getShort();
        Assert.assertEquals("Version Made-By field in CD[0] = 0", versionMadeBy, 0);

        // Just to make sure we have the right record, skip to size and usize and check there
        cd.position(cd.position() + 14);

        int compressedSize = cd.getInt();
        Assert.assertEquals("Bad CSize", fileSize, compressedSize);

        int ucompressedSize = cd.getInt();
        Assert.assertEquals("Bad USize", fileSize, ucompressedSize);
    }

    // Regression test for b/144189353 (JD9 treats zero time/data stamp as invalid).
    @Test
    public void testTimeAndDateNotZero() throws Exception {
        // Make sure the default values are not zero.
        Assert.assertNotEquals("Bad time", CentralDirectoryRecord.DEFAULT_TIME, 0);
        Assert.assertNotEquals("Bad date", CentralDirectoryRecord.DEFAULT_DATE, 0);

        Path dst = getTestPath("testTimeAndDateNotZero.zip");
        try (ZipArchive archive = new ZipArchive(dst.toFile())) {
            archive.add(new BytesSource(new byte[0], "", 0));
        }

        // Check Local File Header values
        ByteBuffer lfh = ByteBuffer.wrap(Files.readAllBytes(dst)).order(ByteOrder.LITTLE_ENDIAN);
        Assert.assertEquals("Bad time", CentralDirectoryRecord.DEFAULT_TIME, lfh.getShort(10));
        Assert.assertEquals("Bad date", CentralDirectoryRecord.DEFAULT_DATE, lfh.getShort(12));

        // Check Central Directory values
        ByteBuffer cd = extractCentralDirectory(dst);
        Assert.assertEquals("Bad time", CentralDirectoryRecord.DEFAULT_TIME, cd.getShort(12));
        Assert.assertEquals("Bad date", CentralDirectoryRecord.DEFAULT_DATE, cd.getShort(14));
    }
}
