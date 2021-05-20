/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test cases for {@link FileUtils}.
 */
public class FileUtilsTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testCopyFilesDoesNotCopyReadonlyBit() throws IOException {
        File fileIn = new File(mTemporaryFolder.getRoot(), "fileIn.txt");
        File fileOut = new File(mTemporaryFolder.getRoot(), "fileOut.txt");
        fileIn.createNewFile();
        fileIn.setWritable(true);
        FileUtils.copyFile(fileIn, fileOut);
        assertThat(fileOut.canWrite()).isTrue();
    }

    @Test
    public void testMkdirs() throws IOException {
        File directory = new File(mTemporaryFolder.getRoot(), "foo");
        FileUtils.mkdirs(directory);
        assertTrue(directory.isDirectory());

        directory = mTemporaryFolder.newFolder("bar");
        FileUtils.mkdirs(directory);
        assertTrue(directory.isDirectory());

        directory = mTemporaryFolder.newFile("baz");
        try {
            FileUtils.mkdirs(directory);
            fail("expected RuntimeException");
        } catch (RuntimeException exception) {
            assertTrue(exception.getMessage().startsWith("Cannot create directory"));
        }
    }

    @Test
    public void testSanitizeFileName() {
        assertThat(FileUtils.sanitizeFileName("foo:\\/*\"?|<>bar-123"))
                .isEqualTo("foo_________bar-123");
    }

    @Test
    public void testParentDirExists() throws IOException {
        // Test absolute paths
        File rootDir = mTemporaryFolder.newFolder();
        File fooFile = new File(rootDir, "foo");
        assertThat(FileUtils.parentDirExists(fooFile)).isTrue();

        File barFile = FileUtils.join(rootDir, "foo", "bar");
        assertThat(FileUtils.parentDirExists(barFile)).isFalse();

        // Test relative paths
        fooFile = new File("foo");
        assertThat(FileUtils.parentDirExists(fooFile)).isTrue();

        barFile = new File(FileUtils.join("foo", "bar"));
        assertThat(FileUtils.parentDirExists(barFile)).isFalse();
    }

    @Test
    public void testIsFileInDirectory() {
        // Test basic cases
        assertTrue(FileUtils.isFileInDirectory(new File("/a/b"), new File("/a")));
        assertTrue(FileUtils.isFileInDirectory(new File("/a/b/c"), new File("/a")));
        assertFalse(FileUtils.isFileInDirectory(new File("/a/b"), new File("/c/d")));

        // Test absolute and relative paths
        assertTrue(FileUtils.isFileInDirectory(new File("a/b"), new File("a")));
        assertFalse(FileUtils.isFileInDirectory(new File("/a/b"), new File("a")));
        assertFalse(FileUtils.isFileInDirectory(new File("a/b"), new File("/a")));

        // Test ".." in paths
        assertTrue(FileUtils.isFileInDirectory(new File("/a/b/c/.."), new File("/a/d/..")));

        // Test other corner cases
        assertFalse(FileUtils.isFileInDirectory(new File("/a"), new File("/a")));
        assertFalse(FileUtils.isFileInDirectory(new File("/a"), new File("/b")));
        assertFalse(FileUtils.isFileInDirectory(new File("/a"), new File("/a/b")));
        assertFalse(FileUtils.isFileInDirectory(new File("/ab/ab"), new File("/a")));

        // Test case sensitivity
        if (isFileSystemCaseSensitive()) {
            assertFalse(FileUtils.isFileInDirectory(new File("/a/b"), new File("/A")));
        } else {
            assertTrue(FileUtils.isFileInDirectory(new File("/a/b"), new File("/A")));
        }
    }

    @Test
    public void testIsSameFile() throws IOException {
        // Test basic cases
        assertThat(FileUtils.isSameFile(new File("foo"), new File("foo"))).isTrue();
        assertThat(FileUtils.isSameFile(new File("foo"), new File("bar"))).isFalse();

        // Test absolute and relative paths
        assertThat(FileUtils.isSameFile(new File("foo").getAbsoluteFile(), new File("foo")))
                .isTrue();
        assertThat(FileUtils.isSameFile(new File("foo").getAbsoluteFile(), new File("bar")))
                .isFalse();

        // Test ".." in paths
        assertThat(
                        FileUtils.isSameFile(
                                new File(FileUtils.join("foo", "bar", "..")), new File("foo")))
                .isTrue();
        assertThat(
                        FileUtils.isSameFile(
                                new File(FileUtils.join("foo", "bar", "..")), new File("bar")))
                .isFalse();

        // Test case sensitivity
        if (isFileSystemCaseSensitive()) {
            assertThat(FileUtils.isSameFile(new File("foo").getAbsoluteFile(), new File("FOO")))
                    .isFalse();
        } else {
            assertThat(FileUtils.isSameFile(new File("foo").getAbsoluteFile(), new File("FOO")))
                    .isTrue();
        }

        // Test hard links
        File fooFile = mTemporaryFolder.newFile("foo");
        File fooHardLinkFile = new File(mTemporaryFolder.getRoot(), "fooHardLink");
        assertThat(FileUtils.isSameFile(fooHardLinkFile, fooFile)).isFalse();
        java.nio.file.Files.createLink(fooHardLinkFile.toPath(), fooFile.toPath());
        assertThat(FileUtils.isSameFile(fooHardLinkFile, fooFile)).isTrue();

        // Test symbolic links
        if (SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS) {
            File fooSymbolicLinkFile = new File(mTemporaryFolder.getRoot(), "fooSymbolicLink");
            assertThat(FileUtils.isSameFile(fooSymbolicLinkFile, fooFile)).isFalse();
            java.nio.file.Files.createSymbolicLink(fooSymbolicLinkFile.toPath(), fooFile.toPath());
            assertThat(FileUtils.isSameFile(fooSymbolicLinkFile, fooFile)).isTrue();
        }
    }

    private static boolean isFileSystemCaseSensitive() {
        return !new File("a").equals(new File("A"));
    }
}
