/*
 * Copyright (C) 2022 The Android Open Source Project
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

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Assert;
import org.junit.Test;

public class CommentTest extends AbstractZipflingerTest {

    @Test
    public void testTooBigComment() throws Exception {
        Path dst = getTestPath("testBigComment.zip");
        try (ZipWriter writer = new ZipWriter(dst)) {
            byte[] comment = new byte[(int) Ints.USHRT_MAX + 1];
            Location location = new Location(0, 0);
            EndOfCentralDirectory.write(writer, location, 0, comment);
            Assert.fail("No exception throw on big comment");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    @Test
    public void testComment() throws Exception {
        byte[] comment = "hello".getBytes();
        Path dst = getTestPath("testBigComment.zip");

        // Create an archive which is just an EOCD with a comment (this is a legal archive).
        try (ZipWriter writer = new ZipWriter(dst)) {
            Location location = new Location(0, 0);
            EndOfCentralDirectory.write(writer, location, 0, comment);
        }

        // Add an entry to the archive
        try (ZipArchive archive = new ZipArchive(dst)) {
            archive.add(new BytesSource(new byte[0], "foo", 0));
        }

        // Make sure the comment of the initial archive was preserved.
        try (ZipRepo repo = new ZipRepo(dst)) {
            Assert.assertArrayEquals(repo.getComment(), comment);
        }
    }

    @Test
    public void testCommentTooSmall() throws Exception {
        Path dst = getTestPath("testBigComment.zip");
        try (ZipWriter writer = new ZipWriter(dst)) {
            byte[] comment = new byte[(int) Ints.USHRT_MAX];
            Location location = new Location(0, 0);
            EndOfCentralDirectory.write(writer, location, 0, comment);
        }

        // Chop one byte from the command
        try (FileChannel file = FileChannel.open(dst, StandardOpenOption.WRITE)) {
            file.truncate(file.size() - 1);
        }

        try (ZipRepo repo = new ZipRepo(dst)) {
            Assert.fail("Failed to detect comment section is too short");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    @Test
    public void testCommentTransfer() throws Exception {
        Path path = getTestPath("testCommentTransfer.zip");
        byte[] comment = "hello".getBytes();

        try (ZipWriter writer = new ZipWriter(path)) {
            Location location = new Location(0, 0);
            EndOfCentralDirectory.write(writer, location, 0, comment);
        }

        try(ZipArchive archive = new ZipArchive(path)) {
            ZipSource zipSrc = new ZipSource(path);
            archive.setComment(zipSrc.getComment());
        }

        try (ZipRepo repo = new ZipRepo(path)) {
            byte[] expected = comment;
            byte[] actual = repo.getComment();
            Assert.assertArrayEquals("Different comment", expected, actual);
        }

    }
}
