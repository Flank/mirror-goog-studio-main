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

package com.android.builder.dexing;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.apkzlib.zip.ZFile;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Testing the class file inputs for the dx. */
public class ClassFileInputTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testDirectoryCorrectlyRead() throws IOException {
        List<String> filesNames =
                ImmutableList.of("A.class", "B.class", "ignored.txt", "dir/C.class");
        temporaryFolder.newFolder("dir");
        for (String s : filesNames) {
            temporaryFolder.newFile(s);
        }

        validateEntries(
                temporaryFolder.getRoot(), ImmutableList.of("A.class", "B.class", "dir/C.class"));
    }

    @Test
    public void testEmptyDirectory() throws IOException {
        temporaryFolder.newFolder("dir");
        validateEntries(temporaryFolder.getRoot(), ImmutableList.of());
    }

    @Test
    public void testDirectoryAllNonClassFiles() throws IOException {
        temporaryFolder.newFile("ignore.txt");
        temporaryFolder.newFolder("a");
        temporaryFolder.newFile("a/ignore.txt");
        temporaryFolder.newFile("a/ignore_2.txt");

        validateEntries(temporaryFolder.getRoot(), ImmutableList.of());
    }

    @Test
    public void testDirectoryOnlyClassFiles() throws IOException {
        List<String> filesNames = ImmutableList.of("A.class", "B.class", "dir/C.class");
        temporaryFolder.newFolder("dir");
        for (String s : filesNames) {
            temporaryFolder.newFile(s);
        }

        validateEntries(temporaryFolder.getRoot(), filesNames);
    }

    @Test
    public void testJarCorrectlyRead() throws IOException {
        File jarFile = FileUtils.join(temporaryFolder.getRoot(), "input.jar");
        try (ZFile zFile = new ZFile(jarFile)) {
            zFile.add("A.class", dummyContent());
            zFile.add("C.class", dummyContent());
            zFile.add("ignore.txt", dummyContent());
            zFile.add("dir/D.class", dummyContent());
        }

        validateEntries(jarFile, ImmutableList.of("A.class", "C.class", "dir/D.class"));
    }

    @Test
    public void testEmptyJar() throws IOException {
        File jarFile = FileUtils.join(temporaryFolder.getRoot(), "input.jar");
        try (ZFile zFile = new ZFile(jarFile)) {
            // empty block, we want an empty archive
        }

        validateEntries(jarFile, ImmutableList.of());
    }

    @Test
    public void testJarNoClassFiles() throws IOException {
        File jarFile = FileUtils.join(temporaryFolder.getRoot(), "input.jar");
        try (ZFile zFile = new ZFile(jarFile)) {
            zFile.add("ignored.txt", dummyContent());
            zFile.add("ignored_2.txt", dummyContent());
            zFile.add("dir/ignored.txt", dummyContent());
        }

        validateEntries(jarFile, ImmutableList.of());
    }

    @Test
    public void testJarOnlyClassFiles() throws IOException {
        File jarFile = FileUtils.join(temporaryFolder.getRoot(), "input.jar");
        try (ZFile zFile = new ZFile(jarFile)) {
            zFile.add("A.class", dummyContent());
            zFile.add("dir/B.class", dummyContent());
            zFile.add("dir/dir/C.class", dummyContent());
        }

        validateEntries(jarFile, ImmutableList.of("A.class", "dir/B.class", "dir/dir/C.class"));
    }

    @Test
    public void testLowerCaseClassFile() throws IOException {
        File jarFile = FileUtils.join(temporaryFolder.getRoot(), "input.jar");
        try (ZFile zFile = new ZFile(jarFile)) {
            zFile.add("aA.class", dummyContent());
        }

        validateEntries(jarFile, ImmutableList.of("aA.class"));
    }

    @Test
    public void testInnerClassClassFile() throws IOException {
        File jarFile = FileUtils.join(temporaryFolder.getRoot(), "input.jar");
        try (ZFile zFile = new ZFile(jarFile)) {
            zFile.add("A$InnerClass.class", dummyContent());
        }

        validateEntries(jarFile, ImmutableList.of("A$InnerClass.class"));
    }

    @Test
    public void checkClassFileRenaming() {
        assertThat(ClassFileEntry.withDexExtension(Paths.get("A.class")))
                .isEqualTo(Paths.get("A.dex"));
        assertThat(ClassFileEntry.withDexExtension(Paths.get("A$a.class")))
                .isEqualTo(Paths.get("A$a.dex"));
        assertThat(ClassFileEntry.withDexExtension(Paths.get("/A.class")))
                .isEqualTo(Paths.get("/A.dex"));
        assertThat(ClassFileEntry.withDexExtension(Paths.get("a/A.class")))
                .isEqualTo(Paths.get("a/A.dex"));
        assertThat(ClassFileEntry.withDexExtension(Paths.get("a/.class/A.class")))
                .isEqualTo(Paths.get("a/.class/A.dex"));
        assertThat(ClassFileEntry.withDexExtension(Paths.get("a\\class\\A.class")))
                .isEqualTo(Paths.get("a\\class\\A.dex"));
        assertThat(ClassFileEntry.withDexExtension(Paths.get("a\\A.class")))
                .isEqualTo(Paths.get("a\\A.dex"));

        try {
            DexArchiveEntry.withClassExtension(Paths.get("Failure.txt"));
        } catch (IllegalStateException e) {
            // should throw
        }
    }

    private void validateEntries(@NonNull File rootPath, @NonNull List<String> fileNames)
            throws IOException {
        List<String> filesRead = Lists.newArrayList();
        ClassFileInputs.fromPath(rootPath.toPath())
                .entries(path -> true)
                .forEach(
                        entry -> {
                            String path =
                                    PathUtils.toSystemIndependentPath(entry.getRelativePath());
                            filesRead.add(path);
                        });
        assertThat(filesRead).containsExactlyElementsIn(fileNames);
    }

    private InputStream dummyContent() {
        return new ByteArrayInputStream(new byte[] {0, 1, 2, 3});
    }
}
