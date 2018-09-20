/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test cases for {@link PathUtils}. */
public class PathUtilsTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testPathDelete() throws IOException {
        Path root = temporaryFolder.getRoot().toPath().resolve("root");
        java.nio.file.Files.createDirectories(root.resolve("a/a"));
        java.nio.file.Files.createDirectories(root.resolve("b"));
        java.nio.file.Files.createDirectories(root.resolve("c/c/c"));

        java.nio.file.Files.write(root.resolve("a/a/t.txt"), ImmutableList.of("content"));
        java.nio.file.Files.write(root.resolve("b/t.txt"), ImmutableList.of("content"));

        PathUtils.deleteRecursivelyIfExists(root);

        assertThat(java.nio.file.Files.notExists(root)).isTrue();
    }

    @Test
    public void testPathDeleteOnlyDirs() throws IOException {
        Path root = temporaryFolder.getRoot().toPath().resolve("root");
        java.nio.file.Files.createDirectories(root.resolve("a/a"));
        java.nio.file.Files.createDirectories(root.resolve("b"));
        java.nio.file.Files.createDirectories(root.resolve("c/c/c"));

        PathUtils.deleteRecursivelyIfExists(root);
        assertThat(java.nio.file.Files.notExists(root)).isTrue();
    }

    @Test
    public void testPathDeleteNonExisting() throws IOException {
        PathUtils.deleteRecursivelyIfExists(
                temporaryFolder.getRoot().toPath().resolve("non-existing"));
    }

    @Test
    public void testPathDeleteFile() throws IOException {
        Path root = temporaryFolder.getRoot().toPath();
        java.nio.file.Files.write(root.resolve("t.txt"), ImmutableList.of("content"));

        PathUtils.deleteRecursivelyIfExists(root.resolve("t.txt"));
        assertThat(java.nio.file.Files.notExists(root.resolve("t.txt"))).isTrue();
    }

    @Test
    public void testPathDeleteSymlinkToDir() throws IOException {
        // Symbolic links don't work on Windows.
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS);
        File firstDir = temporaryFolder.newFolder("folders", "1", "2", "3");
        temporaryFolder.newFolder("folders", "secondFolder");
        // Test symlink to directory behavior.
        File symbolicLinkFile = new File(temporaryFolder.getRoot(), "/folders/secondFolder/2");
        java.nio.file.Files.createSymbolicLink(
                symbolicLinkFile.toPath(), firstDir.getParentFile().toPath());
        PathUtils.deleteRecursivelyIfExists(symbolicLinkFile.toPath());
        assertThat(java.nio.file.Files.exists(symbolicLinkFile.toPath())).isFalse();
        assertThat(java.nio.file.Files.exists(firstDir.toPath())).isTrue();
    }

    @Test
    public void testPathDeleteSymlinkToFile() throws IOException {
        // Symbolic links don't work on Windows.
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS);
        File firstDir = temporaryFolder.newFolder("folders", "1", "2");
        Path linkedToPath =
                java.nio.file.Files.write(
                        firstDir.toPath().resolve("3.txt"), ImmutableList.of("content"));
        temporaryFolder.newFolder("folders", "secondFolder");
        // Test symlink to directory behavior.
        File symbolicLinkFile = new File(temporaryFolder.getRoot(), "/folders/secondFolder/2");
        java.nio.file.Files.createSymbolicLink(symbolicLinkFile.toPath(), linkedToPath);
        PathUtils.deleteRecursivelyIfExists(symbolicLinkFile.toPath());
        assertThat(java.nio.file.Files.exists(symbolicLinkFile.toPath())).isFalse();
        assertThat(java.nio.file.Files.exists(linkedToPath)).isTrue();
    }
}
