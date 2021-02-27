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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.Deflater;

import org.junit.Assert;
import org.junit.Test;

public class ZipFlingerTest extends AbstractZipflingerTest {
    private static final int COMP_SPED = Deflater.BEST_SPEED;
    private static final int COMP_NONE = Deflater.NO_COMPRESSION;

    @Test
    public void testDeleteRecord() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testDeleteRecord.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst)) {
            zipArchive.delete("file2.txt");
        }

        // Test zip is a valid archive.
        Map<String, Entry> entries = ZipArchive.listEntries(dst);
        Assert.assertEquals("Num entries", 2, entries.size());
        Assert.assertTrue("Entries contains file1.txt", entries.containsKey("file1.txt"));
        Assert.assertFalse("Entries contains file2.txt", entries.containsKey("file2.txt"));
        Assert.assertTrue("Entries contains file3.txt", entries.containsKey("file3.txt"));

        // Topdown parsing with SDK zip.
        verifyArchive(dst);
    }

    @Test
    public void testAddFileEntry() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testAddFileEntry.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst)) {
            BytesSource bs = new BytesSource(getPath("file4.txt"), "file4.txt", COMP_NONE);
            zipArchive.add(bs);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst);
        Assert.assertEquals("Num entries", 4, entries.size());
        Assert.assertTrue("Entries contains file1.txt", entries.containsKey("file1.txt"));
        Assert.assertTrue("Entries contains file2.txt", entries.containsKey("file2.txt"));
        Assert.assertTrue("Entries contains file3.txt", entries.containsKey("file3.txt"));
        Assert.assertTrue("Entries contains file4.txt", entries.containsKey("file4.txt"));

        // Topdown parsing with SDK zip.
        verifyArchive(dst);
    }

    @Test
    public void testAddCompressedEntry() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testAddCompressedEntry.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        ZipArchive zipArchive = new ZipArchive(dst);
        BytesSource bs = new BytesSource(getPath("file4.txt"), "file4.txt", COMP_SPED);
        zipArchive.add(bs);
        zipArchive.close();

        Map<String, Entry> entries = ZipArchive.listEntries(dst);
        Assert.assertEquals("Num entries", 4, entries.size());
        Assert.assertTrue("Entries contains file1.txt", entries.containsKey("file1.txt"));
        Assert.assertTrue("Entries contains file2.txt", entries.containsKey("file2.txt"));
        Assert.assertTrue("Entries contains file3.txt", entries.containsKey("file3.txt"));
        Assert.assertTrue("Entries contains file4.txt", entries.containsKey("file4.txt"));
        Entry file4 = entries.get("file4.txt");
        Assert.assertNotEquals(
                "file4.txt size", file4.getCompressedSize(), file4.getUncompressedSize());

        verifyArchive(dst);
    }

    @Test
    public void testModifyingClosedArchive() throws IOException {
        Path dst = getTestPath("newArchive.zip");

        ZipArchive zipArchive = new ZipArchive(dst);
        zipArchive.close();

        boolean exceptionCaught = false;
        try {
            zipArchive.add(new BytesSource(dst, "", COMP_NONE));
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
            zipArchive.add(new ZipSource(getPath("1-2-3files.zip")));
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

        try (ZipArchive zipArchive = new ZipArchive(dst)) {
            BytesSource source = new BytesSource(getPath("file4.txt"), "file4.txt", COMP_SPED);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst);
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

        try (ZipArchive zipArchive = new ZipArchive(dst)) {
            InputStream stream = Files.newInputStream(getPath("file4.txt"));
            BytesSource source = new BytesSource(stream, "file4.txt", COMP_SPED);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst);
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

        try (ZipArchive zipArchive = new ZipArchive(dst)) {
            byte[] bytes = Files.readAllBytes(getPath("file4.txt"));
            BytesSource source = new BytesSource(bytes, "file4.txt", COMP_SPED);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst);
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
        try (ZipArchive zipArchive = new ZipArchive(dst)) {
            for (int length = 0; length < alignment; length++) {
                String name = makeString(length);
                BytesSource source = new BytesSource(getPath("file4.txt"), name, COMP_NONE);
                source.align(alignment);
                zipArchive.add(source);
            }
        }

        Map<String, Entry> entries = verifyArchive(dst);

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

        try (ZipArchive zipArchive = new ZipArchive(dst)) {
            byte[] bytes = Files.readAllBytes(getPath("file4.txt"));
            BytesSource fileSource = new BytesSource(bytes, "file4.txt", COMP_NONE);
            fileSource.align(alignment);
            zipArchive.add(fileSource);
        }

        Map<String, Entry> entries = verifyArchive(dst);
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

        ZipSource source = new ZipSource(getPath("4-5files.zip"));
        source.select("file4.txt", "file4.txt", ZipSource.COMPRESSION_NO_CHANGE, alignment);
        source.select("file5.txt", "file5.txt", ZipSource.COMPRESSION_NO_CHANGE, alignment);

        try (ZipArchive zipArchive = new ZipArchive(dst)) {
            zipArchive.add(source);
        }

        Map<String, Entry> entries = verifyArchive(dst);

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

        try (ZipArchive zipArchive = new ZipArchive(dst);
                InputStream stream = Files.newInputStream(getPath("file4.txt"))) {
            BytesSource source = new BytesSource(stream, "file4.txt", COMP_NONE);
            source.align(alignment);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst);
        Entry entry = entries.get("file4.txt");

        Assert.assertEquals(
                "InputSource alignment", 0, entry.getPayloadLocation().first % alignment);
        Files.delete(dst);
    }

    @Test
    public void testNameCollision() throws IOException {
        ZipArchive zipArchive = new ZipArchive(getTestPath("nonexistent.zip"));
        Path file = getPath("1-2-3files.zip");
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
    public void testExistentDoubleDelete() throws IOException {
        Path src = getPath("1-2-3files.zip");
        Path archive = getTestPath("testExistentDoubleDelete.zip");
        Files.copy(src, archive, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(archive)) {
            zipArchive.delete("file4.txt");
            zipArchive.delete("file4.txt");
        }
    }

    @Test
    public void testNonExistentDelete() throws IOException {
        Path src = getPath("1-2-3files.zip");
        Path archive = getTestPath("testNonExistentDelete.zip");
        Files.copy(src, archive, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(archive)) {
            zipArchive.delete("non-existent.txt");
        }
    }

    // Test deleting an entry resulting in virtual entry.
    @Test
    public void testVirtualEntryExistingEntryDeleted() throws IOException {
        Path file = getTestPath("testVirtualEntryExisting.zip");
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
        Path file = getTestPath("testVirtualEntryNew.zip");
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
        Path file = getTestPath("testVirtualEntryLargeDelete.zip");
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
        Path file = getTestPath("testMultipleVirtualEntry.zip");
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
        Path file = getPath("zip_with_directories.zip");
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

        try (ZipArchive zipArchive = new ZipArchive(dst)) {
            BytesSource source = new BytesSource(new byte[0], "file4.txt", COMP_NONE);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst);
        Assert.assertTrue("file1.txt is compressed", entries.get("file1.txt").isCompressed());
        Assert.assertTrue("file2.txt is compressed", entries.get("file2.txt").isCompressed());
        Assert.assertTrue("file3.txt is compressed", entries.get("file3.txt").isCompressed());
        Assert.assertFalse("file4.txt is not compressed", entries.get("file4.txt").isCompressed());
    }

    @Test
    public void testCompressionMode() throws Exception {
        Path archiveFile = getTestPath("testCompressionMode.zip");
        Path input = getPath("text.txt");

        try (ZipArchive zipArchive = new ZipArchive(archiveFile)) {
            BytesSource source = new BytesSource(input, "text.tx", Deflater.NO_COMPRESSION);
            zipArchive.add(source);
        }
        long storedSize = Files.size(archiveFile);

        Files.deleteIfExists(archiveFile);
        try (ZipArchive zipArchive = new ZipArchive(archiveFile)) {
            BytesSource source = new BytesSource(input, "text.tx", Deflater.BEST_SPEED);
            zipArchive.add(source);
        }
        long speedSize = Files.size(archiveFile);

        Assert.assertTrue("NO_COMPRESSION is bigger than BEST_SPEED", storedSize > speedSize);

        Files.deleteIfExists(archiveFile);
        try (ZipArchive zipArchive = new ZipArchive(archiveFile)) {
            BytesSource source = new BytesSource(input, "text.tx", Deflater.BEST_COMPRESSION);
            zipArchive.add(source);
        }
        long compressionSize = Files.size(archiveFile);

        Assert.assertTrue(
                "BEST_SPEED is bigger than BEST_COMPRESSION", speedSize > compressionSize);
    }


    @Test
    public void testZipEntryChangeCompression() throws Exception {
        Path src = getPath("two_files.zip");
        Path dst = getTestPath("testZipEntryChanges.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Entry> entries = ZipArchive.listEntries(src);
        Assert.assertTrue("Entry compressed", entries.get("compressed.random").isCompressed());
        Assert.assertFalse("Entry !compressed", entries.get("uncompressed.random").isCompressed());

        try (ZipArchive archive = new ZipArchive(dst)) {
            ZipSource zipSource = new ZipSource(src);
            zipSource.select(
                    "uncompressed.random", "a", Deflater.NO_COMPRESSION, Source.NO_ALIGNMENT);
            zipSource.select("uncompressed.random", "b", Deflater.BEST_SPEED, Source.NO_ALIGNMENT);
            zipSource.select(
                    "compressed.random", "c", Deflater.NO_COMPRESSION, Source.NO_ALIGNMENT);
            zipSource.select("compressed.random", "d", Deflater.BEST_SPEED, Source.NO_ALIGNMENT);
            archive.add(zipSource);
        }
        verifyArchive(dst);

        entries = ZipArchive.listEntries(dst);
        Assert.assertFalse("Entry a was modified", entries.get("a").isCompressed());
        Assert.assertTrue("Entry b was not modified", entries.get("b").isCompressed());
        Assert.assertFalse("Entry c was not modified", entries.get("c").isCompressed());
        Assert.assertTrue("Entry d was modified", entries.get("d").isCompressed());
    }

    @Test
    public void testZipExtraction() throws Exception {
        try (ZipArchive archive = new ZipArchive(getTestPath("testZipExtraction.zip"))) {

            // Add compressed file
            Path file1File = getPath("file1.txt");
            byte[] file1Bytes = Files.readAllBytes(file1File);
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
        Path[] files = {getPath("file1.txt"), getPath("file2.txt"), getPath("file3.txt")};
        Path archive = getTestPath("testZipExtractionFromJavaZip.zip");
        createZip(archive, files);

        try (ZipArchive zipArchive = new ZipArchive(archive)) {
            for (Path file : files) {
                byte[] fileBytes = Files.readAllBytes(file);
                byte[] archiveBytes =
                        toByteArray(zipArchive.getContent(file.getFileName().toString()));
                Assert.assertArrayEquals(fileBytes, archiveBytes);
            }
        }
    }

    @Test
    public void testZipExtractionFromZipUtils() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getTestPath("testZipExtractionFromZipUtils.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive archive = new ZipArchive(dst)) {
            Path[] files = {getPath("file1.txt"), getPath("file2.txt"), getPath("file3.txt")};
            for (Path file : files) {
                byte[] fileBytes = Files.readAllBytes(file);
                byte[] archiveBytes =
                        toByteArray(archive.getContent(file.getFileName().toString()));
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
        try (ZipArchive archive = new ZipArchive(dst)) {
            archive.add(new BytesSource(new byte[entrySize], "a", 0));
            archive.add(new BytesSource(new byte[10], "b", 0));
            archive.delete("a");
        }
    }

    @Test
    public void testBigZipParsing() throws Exception {
        Path archive = getTestPath("testBigkZipParsing.zip");
        int numFiles = 3;
        int sizePerFile = 1_000_000_000;
        createZip(numFiles, sizePerFile, archive);
        Assert.assertTrue(Files.size(archive) > numFiles * sizePerFile);

        try (ZipArchive zipArchive = new ZipArchive(archive)) {
            List<String> list = zipArchive.listEntries();
            Assert.assertEquals("Num entries differ", list.size(), 3);
        }
        Files.delete(archive);
    }

    @Test
    public void testBigZipGeneration() throws Exception {
        Path archive = getTestPath("testBigZipGeneration.zip");
        try (ZipArchive zipArchive = new ZipArchive(archive)) {
            for (int i = 0; i < 3; i++) {
                byte[] bytes = new byte[1_000_000_000];
                BytesSource source = new BytesSource(bytes, "file" + i, Deflater.NO_COMPRESSION);
                zipArchive.add(source);
            }
        }
        Assert.assertTrue("Zip file below expected size", Files.size(archive) > 3_000_000_000L);
        verifyArchive(archive);
        Files.delete(archive);
    }

    @Test
    // Regression test for b/143215332 where "int cannot be converted to ushort" in the case
    // gap filling space is a multiple of (65_535 + [30-33]) bytes
    public void testGapTooBigForOneVirtualEntry() throws Exception {
        Path dst = getTestPath("testGapTooBigForOneVirtualEntry.zip");
        try (ZipArchive archive = new ZipArchive(dst)) {
            // This entry will result in a 30 (header) + 1 (filename length) + max ushort (payload)
            // size
            archive.add(new BytesSource(new byte[65_535], "1", 0));
            archive.add(new BytesSource(new byte[0], "file2", 0));
        }
        try (ZipArchive archive = new ZipArchive(dst)) {
            archive.delete("1");
        }
        verifyArchive(dst);
    }

    @Test
    public void testDeleteSmallestPossibleEntry() throws Exception {
        Path dst = getTestPath("testDeleteSmallestPossibleEntry.zip");
        try (ZipArchive archive = new ZipArchive(dst)) {
            archive.add(new BytesSource(new byte[0], "", 0));
            archive.add(new BytesSource(new byte[0], "file2", 0));
        }
        try (ZipArchive archive = new ZipArchive(dst)) {
            archive.delete("");
        }
        verifyArchive(dst);
    }

    private ByteBuffer extractCentralDirectory(Path archivePath) throws IOException {
        ZipMap map = ZipMap.from(archivePath, false);

        Path cdDumpPath = getTestPath("cd_dump");
        try (ZipWriter writer = new ZipWriter(cdDumpPath)) {
            map.getCentralDirectory().write(writer);
        }

        // Extract the CD from the archive
        byte[] cdBytes = Files.readAllBytes(cdDumpPath);
        return ByteBuffer.wrap(cdBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    // Regression test for b/141861587
    public void testAttributes() throws Exception {
        int fileSize = 4;
        Path dst = getTestPath("testMadeByZero.zip");
        try (ZipArchive archive = new ZipArchive(dst)) {
            archive.add(new BytesSource(new byte[fileSize], "file1", 0));
        }
        ByteBuffer cd = extractCentralDirectory(dst);

        // Check signature
        int signature = cd.getInt();
        Assert.assertEquals("CD signature not found", signature, CentralDirectoryRecord.SIGNATURE);

        // Check version Made-kby
        short versionMadeBy = cd.getShort();
        Assert.assertEquals("Version Made-By field", versionMadeBy, Source.MADE_BY_UNIX);

        // Just to make sure we have the right record, skip to size and usize and check there
        cd.position(cd.position() + 14);

        int compressedSize = cd.getInt();
        Assert.assertEquals("Bad CSize", fileSize, compressedSize);

        int ucompressedSize = cd.getInt();
        Assert.assertEquals("Bad USize", fileSize, ucompressedSize);

        cd.position(cd.position() + 10);
        int externalAttributes = cd.getInt();
        int expectedAttributes = Source.PERMISSION_DEFAULT;
        Assert.assertEquals("External Attributes", expectedAttributes, externalAttributes);
    }

    @Test
    public void testFullFileSource() throws Exception {
        // Create an executable file
        Path execFilePath = getTestPath("x.exe");
        Files.createFile(execFilePath);
        execFilePath.toFile().setExecutable(true);

        // Create a symbolic link
        Path symbFilePath = getTestPath("symb");
        Files.createSymbolicLink(symbFilePath, execFilePath);

        // Create an archive dst containing both + a followed symbolic link
        Path dst = getTestPath("testFullFileSource.zip");
        try (ZipArchive archive = new ZipArchive(dst)) {
            FullFileSource fs = new FullFileSource(execFilePath, "x", Deflater.NO_COMPRESSION);
            archive.add(fs);

            fs =
                    new FullFileSource(
                            symbFilePath,
                            "s",
                            Deflater.NO_COMPRESSION,
                            FullFileSource.Symlink.DO_NOT_FOLLOW);
            archive.add(fs);

            fs = new FullFileSource(symbFilePath, "f", Deflater.NO_COMPRESSION);
            archive.add(fs);
        }

        ZipMap map = ZipMap.from(dst);

        // Check executable
        Entry x = map.getEntries().get("x");
        boolean isExecutable = (x.getExternalAttributes() & Source.PERMISSION_EXEC) == Source.PERMISSION_EXEC;
        Assert.assertTrue("Executable attribute preserved", isExecutable);

        // Check symbolic link was not followed
        Entry s = map.getEntries().get("s");
        boolean isSymbolicLink = (s.getExternalAttributes() & Source.PERMISSION_LINK) == Source.PERMISSION_LINK;
        Assert.assertTrue("SymbolicLink attribute preserved", isSymbolicLink);

        //  Check symbolic link was followed
        Entry f = map.getEntries().get("f");
        isSymbolicLink = (f.getExternalAttributes() & Source.PERMISSION_LINK) == Source.PERMISSION_LINK;
        Assert.assertFalse("SymbolicLink attribute preserved", isSymbolicLink);
    }

    @Test
    public void testZipMergingAttributes() throws Exception {
        // Create an executable file
        Path execFilePath = getTestPath("x.exe");
        Files.createFile(execFilePath);
        execFilePath.toFile().setExecutable(true);

        // Create a symbolic link
        Path symbFilePath = getTestPath("symb");
        Files.createSymbolicLink(symbFilePath, execFilePath);

        // Create an archive src containing both + a followed symbolic link
        Path src = getTestPath("testZipMergingAttributesSrc.zip");
        try (ZipArchive archive = new ZipArchive(src)) {
            FullFileSource fs = new FullFileSource(execFilePath, "x", Deflater.NO_COMPRESSION);
            archive.add(fs);

            fs =
                    new FullFileSource(
                            symbFilePath,
                            "s",
                            Deflater.NO_COMPRESSION,
                            FullFileSource.Symlink.DO_NOT_FOLLOW);
            archive.add(fs);

            fs = new FullFileSource(symbFilePath, "f", Deflater.NO_COMPRESSION);
            archive.add(fs);
        }

        // Transfer entries from one archive to an archive "dst"
        Path dst = getTestPath("testZipMergingAttributesDst.zip");
        try (ZipArchive archive = new ZipArchive(dst)) {
            ZipSource zipSource = ZipSource.selectAll(src);
           archive.add(zipSource);
        }

        ZipMap map = ZipMap.from(dst);

        // Check executable
        Entry x = map.getEntries().get("x");
        boolean isExecutable = (x.getExternalAttributes() & Source.PERMISSION_EXEC) == Source.PERMISSION_EXEC;
        Assert.assertTrue("Executable attribute preserved", isExecutable);

        // Check symbolic link was not followed
        Entry s = map.getEntries().get("s");
        boolean isSymbolicLink = (s.getExternalAttributes() & Source.PERMISSION_LINK) == Source.PERMISSION_LINK;
        Assert.assertTrue("SymbolicLink attribute preserved", isSymbolicLink);

        //  Check symbolic link was followed
        Entry f = map.getEntries().get("f");
        isSymbolicLink = (f.getExternalAttributes() & Source.PERMISSION_LINK) == Source.PERMISSION_LINK;
        Assert.assertFalse("SymbolicLink attribute preserved", isSymbolicLink);
    }

    // Regression test for b/144189353 (JD9 treats zero time/data stamp as invalid).
    @Test
    public void testTimeAndDateNotZero() throws Exception {
        // Make sure the default values are not zero.
        Assert.assertNotEquals("Bad time", CentralDirectoryRecord.DEFAULT_TIME, 0);
        Assert.assertNotEquals("Bad date", CentralDirectoryRecord.DEFAULT_DATE, 0);

        Path dst = getTestPath("testTimeAndDateNotZero.zip");
        try (ZipArchive archive = new ZipArchive(dst)) {
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

    @Test
    public void testEmptyArchive() throws Exception {
        Path dst = getTestPath("testEmptyArchive.zip");
        try (ZipArchive archive = new ZipArchive(dst)) {}

        verifyArchive(dst);
        Assert.assertEquals(
                "Archive size differ from ECOD", Files.size(dst), EndOfCentralDirectory.SIZE);
    }

    @Test
    public void testParseReadOnlyFile() throws Exception {
        Path src = getPath("1-2-3files.zip");
        FileTime lastModifiedTime = Files.getLastModifiedTime(src);
        try (ZipArchive archive = new ZipArchive(src)) {
            List<String> entries = archive.listEntries();
            Assert.assertEquals("Unexpected number of entries", entries.size(), 3);
        }
        FileTime modifiedTime = Files.getLastModifiedTime(src);
        Assert.assertEquals(
                "Archive should not have been modified", lastModifiedTime, modifiedTime);
    }

    @Test
    public void testInvalidLFHName() throws Exception {
        // Create a normal archive
        Path file = getTestPath("testInvalidLFHName.zip");
        try (ZipArchive archive = new ZipArchive(file)) {
            BytesSource src = new BytesSource(new byte[0], "a", Deflater.NO_COMPRESSION);
            archive.add(src);
        }

        // Try to parse it. It should be fine
        ZipMap.from(file, false);

        // Make the LFH invalid by having the entry name length zero while the CD is left untouched
        BytesSource zeroSrc = new BytesSource(new byte[0], "", Deflater.NO_COMPRESSION);
        try (ZipWriter writer = new ZipWriter(file)) {
            LocalFileHeader lfh = new LocalFileHeader(zeroSrc);
            lfh.write(writer);
        }

        // Parsing should now fail
        String message = "";
        try {
            ZipMap.from(file, false);
        } catch (IllegalStateException e) {
            message = e.getMessage();
        }

        int indexOfParameter = ZipMap.LFH_LENGTH_ERROR.indexOf('%');
        String constantString = ZipMap.LFH_LENGTH_ERROR.substring(0, indexOfParameter);
        Assert.assertTrue(message.startsWith(constantString));
    }

    @Test
    public void testTransferFromInputStream() throws Exception {
        byte[] content1 = new byte[] {1, 2, 3, 4};
        byte[] content2 = new byte[] {5, 6, 7, 8, 9, 10};
        ByteArrayInputStream stream1 = new ByteArrayInputStream(content1);

        Path file = getTestPath("testTransferFromInputStream.zip");
        try (ZipArchive archive = new ZipArchive(file)) {
            archive.add(
                    new BytesSource(content1, "file1.txt", Deflater.NO_COMPRESSION) {
                        @Override
                        public long writeTo(ZipWriter writer) throws IOException {
                            writer.transferFrom(Channels.newChannel(stream1), content1.length);
                            return content1.length;
                        }
                    });
            archive.add(new BytesSource(content2, "file2.txt", Deflater.NO_COMPRESSION));
        }

        Map<String, Entry> entries = verifyArchive(file);
        Assert.assertEquals("Num entries", 2, entries.size());
        Assert.assertTrue("Entries contains file1.txt", entries.containsKey("file1.txt"));
        Assert.assertTrue("Entries contains file2.txt", entries.containsKey("file2.txt"));

        try (ZipArchive zip = new ZipArchive(file)) {
            Assert.assertEquals(ByteBuffer.wrap(content1), zip.getContent("file1.txt"));
            Assert.assertEquals(ByteBuffer.wrap(content2), zip.getContent("file2.txt"));
        }
    }

    private Path createRandomFile(String filename, byte[] bytes) throws IOException {
        Random random = new Random(0);
        random.nextBytes(bytes);

        Path path = getTestPath(filename);
        try (OutputStream out =
                Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            out.write(bytes);
        }
        return path;
    }

    @Test
    public void testLargeSource() throws IOException {
        byte[] bytes = new byte[4096];
        Path srcPath = createRandomFile("testLargeSource.txt", bytes);

        Path tmpPath = getTestPath("testLargeSourceTmpPath.tmp");

        // Test compressed FileBackedSource
        Path compressedDstFile = getTestPath("testLargeSourceCompressed.zip");
        try (ZipArchive zipArchive = new ZipArchive(compressedDstFile)) {
            LargeFileSource s = new LargeFileSource(srcPath, tmpPath, "x", 1);
            zipArchive.add(s);
        }

        verifyArchive(compressedDstFile);
        try (ZipRepo zipRepo = new ZipRepo(compressedDstFile)) {
            ByteBuffer buffer = zipRepo.getContent("x");
            Assert.assertEquals("FileBacked entry differ", ByteBuffer.wrap(bytes), buffer);
        }
        Assert.assertFalse("LargeSource tmp file was not deleted", Files.exists(tmpPath));

        // Test uncompressed FileBackedSource
        Path uncompressedDstFile = getTestPath("testLargeSourceUncompressed.zip");
        try (ZipArchive zipArchive = new ZipArchive(uncompressedDstFile)) {
            LargeFileSource s = new LargeFileSource(srcPath, tmpPath, "x", 0);
            zipArchive.add(s);
        }

        verifyArchive(uncompressedDstFile);
        try (ZipRepo zipRepo = new ZipRepo(uncompressedDstFile)) {
            ByteBuffer buffer = zipRepo.getContent("x");
            Assert.assertEquals("FileBacked entry differ", ByteBuffer.wrap(bytes), buffer);
        }
        Assert.assertFalse("LargeSource tmp file was not deleted", Files.exists(tmpPath));
    }

    @Test
    public void testLargeCompressedSourceNoTmp() throws IOException {
        byte[] bytes = new byte[4096];
        Path srcPath = createRandomFile("testLargeCompressedSourceNoTmp.txt", bytes);

        Path archiveFile = getTestPath("testLargeCompressedSourceNoTmp.zip");
        try (ZipArchive zipArchive = new ZipArchive(archiveFile)) {
            LargeFileSource s = new LargeFileSource(srcPath, "x", 1);
            zipArchive.add(s);

            s = new LargeFileSource(srcPath, "y", 1);
            zipArchive.add(s);
        }

        verifyArchive(archiveFile);
        try (ZipRepo zipRepo = new ZipRepo(archiveFile)) {
            ByteBuffer buffer = zipRepo.getContent("x");
            Assert.assertEquals("FileBacked entry differ", ByteBuffer.wrap(bytes), buffer);

            buffer = zipRepo.getContent("y");
            Assert.assertEquals("FileBacked entry differ", ByteBuffer.wrap(bytes), buffer);
        }
    }

    @Test
    public void testDetectLargeFileTmpCollision() throws Exception {
        Path tmpCollider = getTestPath("tmpFileToCollide.txt");
        Files.createFile(tmpCollider);

        Path fooSrc = getTestPath("testTmpCollisionSrc.txt");
        Files.createFile(fooSrc);

        Path f = getTestPath("testTmpCollisionArchive.zip");
        try (ZipArchive archive = new ZipArchive(f)) {
            LargeFileSource s = new LargeFileSource(fooSrc, tmpCollider, "x", 1);
            archive.add(s);
            Assert.fail("Tmp file collision not detected");
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void testDetectBadParameter() throws Exception {
        Path fooPath = getTestPath("testDetectBadParameter.txt");
        Files.createFile(fooPath);

        Path f = getTestPath("testDetectBadParameter.zip");

        try (ZipArchive archive = new ZipArchive(f)) {
            LargeFileSource s = new LargeFileSource(fooPath, null, "x", 0);
            archive.add(s);

            try {
                s = new LargeFileSource(fooPath, null, "x", 1);
                archive.add(s);
                Assert.fail("Adding a compressed large file without a tmp path did not throw");
            } catch (IllegalStateException e) {
            }
        }
    }

    @Test
    public void testSmallInputStream() throws Exception {
        int streamSize = 500;

        Path a = getTestPath("testSmallInputStreamCompression.zip");
        runInputStreamSource(a, Deflater.BEST_COMPRESSION, streamSize, streamSize);

        Path b = getTestPath("testSmallInputStreamNoCompression.zip");
        runInputStreamSource(b, Deflater.NO_COMPRESSION, streamSize, streamSize);
    }

    void runInputStreamSource(Path path, int compressionLevel, long streamSize, int large_limit) throws IOException {

        try (ZipArchive archive = new ZipArchive(path); InputStream in = new MockInputStream(streamSize)) {
            archive.add(Sources.from(in, "foo", compressionLevel, large_limit));
        }

        try (ZipRepo zipRepo = new ZipRepo(path)) {
            InputStream actual = zipRepo.getInputStream("foo");
            InputStream expected = new MockInputStream(streamSize);
            Assert.assertTrue("InputStream entry differ",  streamMatch(actual, expected));
        }

        verifyArchive(path);
    }

    private boolean streamMatch(InputStream as, InputStream es) throws IOException {
        while(true) {
            int a = as.read();
            int e = es.read();

            if (a != e) {
                return false;
            }

            if (a == -1) {
                return true;
            }
        }
    }

    @Test
    public void testLargeInputStream() throws Exception {
        int streamSize = 20000;

        Path a = getTestPath("testLargeInputStreamCompression.zip");
        runInputStreamSource(a, Deflater.BEST_COMPRESSION, streamSize, 5000);

        Path b = getTestPath("testLargeInputStreamNoCompression.zip");
        runInputStreamSource(b, Deflater.NO_COMPRESSION, streamSize, 5000);
    }
}
