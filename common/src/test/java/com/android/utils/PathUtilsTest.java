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

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
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
}
