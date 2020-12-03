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
package com.android.builder.packaging;

import static com.google.common.truth.Truth.assertThat;

import com.android.zipflinger.Entry;
import com.android.zipflinger.ZipArchive;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestJarFlinger {
    private static final byte[] CLASS_MAGIC_NUMBER =
            new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x0};

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path getOutPath(String filename) throws IOException {
        return new File(temporaryFolder.newFolder(), filename).toPath();
    }

    private Path createJar(String name, int numFiles) throws IOException {
        Path path = getOutPath(name);
        Files.createDirectories(path.getParent());
        try (JarOutputStream jos =
                new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            for (int i = 0; i < numFiles; i++) {
                ZipEntry entry = new ZipEntry(name + "/com/example/Class-" + i + ".class");
                jos.putNextEntry(entry);
                jos.write(CLASS_MAGIC_NUMBER);
                jos.closeEntry();
            }
        }
        return path;
    }

    @Test
    public void testBasicFilterClassesOnly() throws IOException {
        Path path = getOutPath("testBasicFilterIncludeClasses");

        Predicate<String> predicate = archivePath -> archivePath.endsWith(".class");
        try (JarFlinger flinger = new JarFlinger(path, predicate)) {
            flinger.addJar(createJar("j.jar", 1));
        }

        Map<String, Entry> entries = ZipArchive.listEntries(path);
        for (String name : entries.keySet()) {
            Assert.assertTrue("Found !.class entry:" + name, predicate.test(name));
        }
    }

    @Test
    public void testBasicFilterExcludeClasses() throws IOException {
        Path path = getOutPath("testBasicFilterExcludeClasses.zip");

        Predicate<String> predicate = archivePath -> !archivePath.endsWith(".class");
        try (JarFlinger flinger = new JarFlinger(path, predicate)) {
            flinger.addJar(createJar("j.jar", 1));
        }

        Map<String, Entry> entries = ZipArchive.listEntries(path);
        for (String name : entries.keySet()) {
            Assert.assertTrue("Found .class entry:" + name, predicate.test(name));
        }
    }

    @Test
    public void testJarMerging() throws IOException {
        Path path = getOutPath("testJarMerging.zip");

        Path jar1Path = createJar("j.jar", 4);
        Path jar2Path = createJar("k.jar", 3);
        try (JarFlinger flinger = new JarFlinger(path)) {
            flinger.addJar(jar1Path);
            flinger.addJar(jar2Path);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(path);
        Assert.assertEquals("Archive should have seven entries", 7, entries.size());
    }

    @Test
    public void testFilesMerging() throws IOException {
        Path path = getOutPath("testFilesMerging.zip");

        Path contentJar = createJar("j.jar", 1);
        try (JarFlinger flinger = new JarFlinger(path)) {
            flinger.addFile("file1", contentJar);
            flinger.addFile("file2", contentJar);
        }

        Map<String, Entry> entries = ZipArchive.listEntries(path);
        Assert.assertEquals("Archive should have two entries", 2, entries.size());
        Assert.assertTrue("Archive should contain entry 'file1", entries.containsKey("file1"));
        Assert.assertTrue("Archive should contain entry 'file2", entries.containsKey("file2"));
    }

    @Test
    public void testDirectoryWithSymlinks() throws Exception {
        Path path = getOutPath("testDirectoryWithSymlinks.zip");
        Path dir = createDirectoryWithSymlinks();

        try (JarFlinger flinger = new JarFlinger(path)) {
            flinger.addDirectory(dir);
        }

        assertThat(ZipArchive.listEntries(path).keySet())
                .containsExactly("linked/file.txt", "regular.txt", "regular.txt.link");
    }

    private static Path createDirectoryWithSymlinks() throws IOException {
        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

        Path realDir = fs.getPath("/real");
        Files.createDirectories(realDir);
        Files.write(realDir.resolve("file.txt"), new byte[0]);

        Path symlinks = fs.getPath("/symlinks");
        Files.createDirectory(symlinks);
        Files.createSymbolicLink(symlinks.resolve("linked"), realDir);

        Path regularFile = symlinks.resolve("regular.txt");
        Files.write(regularFile, new byte[0]);

        Path linkToRegularFile = symlinks.resolve("regular.txt.link");
        Files.createSymbolicLink(linkToRegularFile, regularFile);

        return symlinks;
    }
}
