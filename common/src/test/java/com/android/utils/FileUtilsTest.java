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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    public void testGetCaseSensitivityAwareCanonicalPath() throws IOException {
        assertThat(FileUtils.getCaseSensitivityAwareCanonicalPath(new File("foo/bar/..")))
                .isEqualTo(FileUtils.getCaseSensitivityAwareCanonicalPath(new File("foo")));
        assertThat(FileUtils.getCaseSensitivityAwareCanonicalPath(new File("foo")))
                .isNotEqualTo(FileUtils.getCaseSensitivityAwareCanonicalPath(new File("bar")));

        boolean isFileSystemCaseSensitive = !new File("a").equals(new File("A"));
        if (isFileSystemCaseSensitive) {
            assertThat(FileUtils.getCaseSensitivityAwareCanonicalPath(new File("foo")))
                    .isNotEqualTo(FileUtils.getCaseSensitivityAwareCanonicalPath(new File("Foo")));
        } else {
            assertThat(FileUtils.getCaseSensitivityAwareCanonicalPath(new File("foo")))
                    .isEqualTo(FileUtils.getCaseSensitivityAwareCanonicalPath(new File("Foo")));
        }
    }

    @Test
    public void testRelativePossiblyNonExistingPath() throws IOException {
        File inputDir = new File("/folders/1/5/main");
        File folder = new File(inputDir, "com/obsidian/v4/tv/home/playback");
        File fileToProcess = new File(folder, "CameraPlaybackGlue$1.class");
        assertEquals("com/obsidian/v4/tv/home/playback/CameraPlaybackGlue$1.class",
                FileUtils.relativePossiblyNonExistingPath(fileToProcess, inputDir));
        fileToProcess = new File(folder, "CameraPlaybackGlue$CameraPlaybackHost.class");
        assertEquals("com/obsidian/v4/tv/home/playback/CameraPlaybackGlue$CameraPlaybackHost.class",
                FileUtils.relativePossiblyNonExistingPath(fileToProcess, inputDir));
    }
}
