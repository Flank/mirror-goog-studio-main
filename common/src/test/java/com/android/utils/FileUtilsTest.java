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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.google.common.io.Files;
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
    public void computeRelativePathOfFile() throws Exception {
        File d1 = mTemporaryFolder.newFolder("foo");
        File f2 = new File(d1, "bar");
        Files.touch(f2);

        assertEquals("bar", FileUtils.relativePath(f2, d1));
    }

    @Test
    public void computeRelativePathOfDirectory() throws Exception {
        File d1 = mTemporaryFolder.newFolder("foo");
        File f2 = new File(d1, "bar");
        f2.mkdir();

        assertEquals("bar" + File.separator, FileUtils.relativePath(f2, d1));
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
        assertTrue(
                FileUtils.isFileInDirectory(
                        new File(FileUtils.join("foo", "bar", "baz")), new File("foo")));
        assertTrue(
                FileUtils.isFileInDirectory(
                        new File(FileUtils.join("foo", "bar")), new File("foo")));
        assertFalse(FileUtils.isFileInDirectory(new File("foo"), new File("foo")));
        assertFalse(FileUtils.isFileInDirectory(new File("bar"), new File("foo")));
        assertFalse(
                FileUtils.isFileInDirectory(
                        new File("foo"), new File(FileUtils.join("foo", "bar"))));
    }

    @Test
    public void testIsSameFile() throws IOException {
        // Test basic case
        assertThat(FileUtils.isSameFile(new File("foo"), new File("foo"))).isTrue();
        assertThat(FileUtils.isSameFile(new File("foo"), new File("bar"))).isFalse();

        // Test absolute and relative paths
        assertThat(FileUtils.isSameFile(new File("foo").getAbsoluteFile(), new File("foo")))
                .isTrue();
        assertThat(FileUtils.isSameFile(new File("foo").getAbsoluteFile(), new File("bar")))
                .isFalse();

        // Test upper-case and lower-case paths
        boolean isFileSystemCaseSensitive = !new File("a").equals(new File("A"));
        if (isFileSystemCaseSensitive) {
            assertThat(FileUtils.isSameFile(new File("foo").getAbsoluteFile(), new File("FOO")))
                    .isFalse();
        } else {
            assertThat(FileUtils.isSameFile(new File("foo").getAbsoluteFile(), new File("FOO")))
                    .isTrue();
        }

        // Test ".." in paths
        assertThat(
                        FileUtils.isSameFile(
                                new File(FileUtils.join("foo", "bar", "..")), new File("foo")))
                .isTrue();
        assertThat(
                        FileUtils.isSameFile(
                                new File(FileUtils.join("foo", "bar", "..")), new File("bar")))
                .isFalse();

        // Test hard links
        File fooFile = mTemporaryFolder.newFile("foo");
        File fooHardLinkFile = new File(mTemporaryFolder.getRoot(), "fooHardLink");
        java.nio.file.Files.createLink(fooHardLinkFile.toPath(), fooFile.toPath());
        assertThat(FileUtils.isSameFile(fooHardLinkFile, fooFile)).isTrue();

        // Test symbolic links
        if (SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS) {
            File fooSymbolicLinkFile = new File(mTemporaryFolder.getRoot(), "fooSymbolicLink");
            java.nio.file.Files.createSymbolicLink(fooSymbolicLinkFile.toPath(), fooFile.toPath());
            assertThat(FileUtils.isSameFile(fooSymbolicLinkFile, fooFile)).isTrue();
        }
    }

    @Test
    public void testRelativePossiblyNonExistingPath() throws IOException {
        File inputDir = new File("/folders/1/5/main");
        File folder = new File(inputDir, "com/obsidian/v4/tv/home/playback");
        File fileToProcess = new File(folder, "CameraPlaybackGlue$1.class");
        assertEquals(
                FileUtils.join(
                        "com",
                        "obsidian",
                        "v4",
                        "tv",
                        "home",
                        "playback",
                        "CameraPlaybackGlue$1.class"),
                FileUtils.relativePossiblyNonExistingPath(fileToProcess, inputDir));
        fileToProcess = new File(folder, "CameraPlaybackGlue$CameraPlaybackHost.class");
        assertEquals(
                FileUtils.join(
                        "com",
                        "obsidian",
                        "v4",
                        "tv",
                        "home",
                        "playback",
                        "CameraPlaybackGlue$CameraPlaybackHost.class"),
                FileUtils.relativePossiblyNonExistingPath(fileToProcess, inputDir));
    }
}
