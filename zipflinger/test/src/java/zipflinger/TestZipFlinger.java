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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class TestZipFlinger extends TestBase {

    @Test
    public void testDeleteRecord() throws Exception {
        Path src = getPath("1-2-3files.zip");
        Path dst = getPath("testDeleteRecord.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        ZipArchive zipArchive = new ZipArchive(dst.toFile());
        zipArchive.delete("file2.txt");
        zipArchive.close();

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
        Path dst = getPath("testAddFileEntry.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        ZipArchive zipArchive = new ZipArchive(dst.toFile());
        FileSource fs = new FileSource(getFile("file4.txt"), "file4.txt", false);
        zipArchive.add(fs);
        zipArchive.close();

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
        Path dst = getPath("testAddCompressedEntry.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        ZipArchive zipArchive = new ZipArchive(dst.toFile());
        FileSource fs = new FileSource(getFile("file4.txt"), "file4.txt", true);
        zipArchive.add(fs);
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
        Path dst = getPath("newArchive.zip");

        ZipArchive zipArchive = new ZipArchive(dst.toFile());
        zipArchive.close();

        boolean exceptionCaught = false;
        try {
            zipArchive.add(new FileSource(new File(""), "", false));
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Exception thrown on post-close add", exceptionCaught);

        exceptionCaught = false;
        try {
            zipArchive.add(new InputStreamSource(null, "deadbeed", false));
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Exception thrown on post-close addCommpressedSource", exceptionCaught);

        exceptionCaught = false;
        try {
            zipArchive.add(new ZipSource(new File("deadbeed"), null));
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
        Path dst = getPath("testFileSourceCompressed.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        ZipArchive zipArchive = new ZipArchive(dst.toFile());
        FileSource source = new FileSource(getFile("file4.txt"), "file4.txt", true);
        zipArchive.add(source);
        zipArchive.close();

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Entry entry = entries.get("file4.txt");

        Assert.assertTrue(
                "File source was compressed",
                entry.getUncompressedSize() != entry.getCompressedSize());
    }

    @Test
    public void testInputStreamSourceCompressed() throws IOException {
        Path src = getPath("1-2-3files.zip");
        Path dst = getPath("testInputStreamSourceCompressed.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            InputStream stream = new FileInputStream(getFile("file4.txt"));
            InputStreamSource source = new InputStreamSource(stream, "file4.txt", true);
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
        Path dst = getPath("testBytesSourceCompressed.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        ZipArchive zipArchive = new ZipArchive(dst.toFile());

        byte[] bytes = Files.readAllBytes(getPath("file4.txt"));
        BytesSource source = new BytesSource(bytes, "file4.txt", true);
        zipArchive.add(source);
        zipArchive.close();

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

    private String makeString(char character, int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append(character);
        }
        return sb.toString();
    }

    private void testFileSourceAlignment(long alignment) throws IOException {
        Path dst = getPath("testFileSourceAlignment.zip");
        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            for (int length = 0; length < alignment; length++) {
                String name = makeString('f', length);
                FileSource fileSource = new FileSource(getFile("file4.txt"), name, false);
                fileSource.align(alignment);
                zipArchive.add(fileSource);
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
        Path dst = getPath("testBytesSourceAlignment.zip");

        ZipArchive zipArchive = new ZipArchive(dst.toFile());

        byte[] bytes = Files.readAllBytes(getPath("file4.txt"));
        BytesSource fileSource = new BytesSource(bytes, "file4.txt", false);
        fileSource.align(alignment);
        zipArchive.add(fileSource);
        zipArchive.close();

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
        Path dst = getPath("testZipSourceAlignment.zip");

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
        Path dst = getPath("testInputSourceAlignment.zip");

        ZipArchive zipArchive = new ZipArchive(dst.toFile());

        InputStream stream = new FileInputStream(getFile("file4.txt"));
        InputStreamSource fileSource = new InputStreamSource(stream, "file4.txt", false);
        fileSource.align(alignment);
        zipArchive.add(fileSource);
        zipArchive.close();
        stream.close();

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Entry entry = entries.get("file4.txt");

        Assert.assertEquals(
                "InputSource alignment", 0, entry.getPayloadLocation().first % alignment);
        Files.delete(dst);
    }

    @Test
    public void testNameCollision() throws IOException {
        ZipArchive zipArchive = new ZipArchive(new File("nonexistant.bla"));
        File file = getFile("1-2-3files.zip");
        FileSource fileSource = new FileSource(file, "name", false);

        boolean exceptionCaught = false;
        try {
            zipArchive.add(fileSource);
            zipArchive.add(fileSource);
            zipArchive.close();
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Name collision detected", exceptionCaught);
    }

    @Test
    public void testExistantDoubleDelete() throws IOException {
        ZipArchive zipArchive = new ZipArchive(new File("1-2-3files.zip"));
        zipArchive.delete("file4.txt");
        zipArchive.delete("file4.txt");
        zipArchive.close();
    }

    @Test
    public void testNonExistantDelete() throws IOException {
        ZipArchive zipArchive = new ZipArchive(new File("1-2-3files.zip"));
        zipArchive.delete("non-existant.txt");
        zipArchive.close();
    }

    // Test deleting an entry resulting in virtual entry.
    @Test
    public void testVirtualEntry() throws IOException {
        File file = getFile("testVirtualEntry.zip");
        ZipArchive archive = new ZipArchive(file);

        byte[] entry1Bytes = new byte[1_000];
        BytesSource source1 = new BytesSource(entry1Bytes, "entry1", false);
        archive.add(source1);

        byte[] entry2Bytes = new byte[1_000];
        BytesSource source2 = new BytesSource(entry2Bytes, "entry2", false);
        archive.add(source2);

        byte[] entry3Bytes = new byte[1_000];
        BytesSource source3 = new BytesSource(entry3Bytes, "entry3", false);
        archive.add(source3);

        archive.close();

        verifyArchive(file);
    }

    // Test deleting an entry resulting in multiple virtual entry (more than 64KiB entry).
    @Test
    public void testMultipleVirtualEntry() throws IOException {
        File file = getFile("testMultipleVirtualEntry.zip");
        ZipArchive archive = new ZipArchive(file);

        byte[] entry1Bytes = new byte[1_000];
        BytesSource source1 = new BytesSource(entry1Bytes, "entry1", false);
        archive.add(source1);

        byte[] entry2Bytes = new byte[128_000_000];
        BytesSource source2 = new BytesSource(entry2Bytes, "entry2", false);
        archive.add(source2);

        byte[] entry3Bytes = new byte[1_000];
        BytesSource source3 = new BytesSource(entry3Bytes, "entry3", false);
        archive.add(source3);

        archive.close();

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
        Path dst = getPath("testCompressionDetection.zip");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

        try (ZipArchive zipArchive = new ZipArchive(dst.toFile())) {
            BytesSource source = new BytesSource(new byte[0], "file4.txt", false);
            zipArchive.add(source);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(dst.toFile());
        Assert.assertTrue("file1.txt is compressed", entries.get("file1.txt").isCompressed());
        Assert.assertTrue("file2.txt is compressed", entries.get("file2.txt").isCompressed());
        Assert.assertTrue("file3.txt is compressed", entries.get("file3.txt").isCompressed());
        Assert.assertFalse("file4.txt is not compressed", entries.get("file4.txt").isCompressed());
    }
}
