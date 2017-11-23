/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.apk.Zip;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.junit.Assert;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * Helper to help read/test the content of generated zip file.
 */
public class ZipHelper {

    @Nullable
    public static File extractFile(File zipFile, String path) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                return null;
            }

            // extract the file
            File apk = File.createTempFile("findAndExtractFromZip", "apk");
            apk.deleteOnExit();
            Files.asByteSink(apk).writeFrom(zip.getInputStream(entry));
            return apk;
        } catch (IOException e) {
            throw new IOException("Failed to open " + zipFile, e);
        }
    }

    @Nullable
    public static File extractFile(Zip zip, String path) throws IOException {
        Path entry = zip.getEntry(path);
        if (entry == null) {
            return null;
        }

        // extract the file
        File apk = File.createTempFile("findAndExtractFromZip", "apk");
        apk.deleteOnExit();
        try {
            java.nio.file.Files.copy(entry, apk.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IOException("Failed to open " + zip, e);
        }
        return apk;
    }

    @Nullable
    public static byte[] extractEntry(@NonNull File zipFile, @NonNull String path)
            throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                return null;
            }
            try (InputStream stream = zip.getInputStream(entry)) {
                return ByteStreams.toByteArray(stream);
            }
        } catch (IOException e) {
            throw new IOException("Failed to open " + zipFile, e);
        }
    }

    /**
     * Checks that a zip file contains a specific file.
     */
    public static void checkFileExists(
            @NonNull File archive,
            @NonNull String path)
            throws IOException {
        Map<String, byte[]> pathToContent = Collections.singletonMap(path, null);
        checkArchive(archive, pathToContent, ImmutableSet.<String>of());
    }

    /**
     * Checks that a zip file contains all files in the list.
     */
    public static void checkFileExists(
            @NonNull File archive,
            @NonNull Set<String> paths)
            throws IOException {
        Map<String, byte[]> pathToContent = Maps.newLinkedHashMap();
        for (String path : paths) {
            pathToContent.put(path, null);
        }
        checkArchive(archive, pathToContent, ImmutableSet.<String>of());
    }

    /**
     * Checks that a zip file do not contains any all files in the set.
     */
    public static void checkFileDoesNotExist(
            @NonNull File archive,
            @NonNull Set<String> paths)
            throws IOException {
        checkArchive(archive, Collections.<String, byte[]>emptyMap(), paths);
    }

    /**
     * Checks that a zip file contains a file with the specified content
     *
     * @param archive the zip file to check.
     * @param path    an expected file inside archive
     * @param content the expected content of the file inside archive
     */
    public static void checkContent(
            @NonNull File archive,
            @NonNull String path,
            @NonNull String content)
            throws IOException {
        Map<String, byte[]> pathToContent =
                Collections.singletonMap(path, content.getBytes(Charsets.UTF_8));
        checkArchive(archive, pathToContent, ImmutableSet.<String>of(), true);
    }

    public static void checkContent(
            @NonNull File archive,
            @NonNull String path,
            @NonNull byte[] content)
            throws IOException {
        Map<String, byte[]> pathToContent = Collections.singletonMap(path, content);
        checkArchive(archive, pathToContent, ImmutableSet.<String>of());
    }

    /**
     * Checks that a zip file contains files, optionally with specific content.
     *
     * @param archive         the file to check.
     * @param pathToContents  a map of zip entry path and text-content. if content is null, only
     *                        presence of file inside the zip is checked.
     * @param notPresentPaths a list of paths that should *not* be present in the archive.
     */
    public static void checkArchive(
            @NonNull File archive,
            @NonNull Map<String, byte[]> pathToContents,
            @NonNull Set<String> notPresentPaths)
            throws IOException {
        checkArchive(archive, pathToContents, notPresentPaths, false);
    }

    public static void checkArchive(
            @NonNull File archive,
            @NonNull Map<String, byte[]> pathToContents,
            @NonNull Set<String> notPresentPaths,
            boolean isContentString)
            throws IOException {
        assertTrue("File '" + archive.getPath() + "' does not exist.", archive.isFile());
        ZipInputStream zis = null;
        FileInputStream fis;
        Set<String> notFound = Sets.newHashSet();
        notFound.addAll(pathToContents.keySet());
        fis = new FileInputStream(archive);
        try {
            zis = new ZipInputStream(fis);

            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String name = entry.getName();

                assertFalse("Found not expected path: " + name,
                        notPresentPaths.contains(name));

                byte[] expected = pathToContents.get(name);
                if (expected != null) {
                    notFound.remove(name);
                    if (!entry.isDirectory()) {
                        byte[] bytes = ByteStreams.toByteArray(zis);
                        if (bytes != null) {
                            if (isContentString) {
                                // trim contents if we are checking with string.
                                String contents = new String(bytes, Charsets.UTF_8).trim();
                                String expectedContents = new String(expected, Charsets.UTF_8).trim();
                                Assert.assertEquals("Contents in " + name + " did not match",
                                        expectedContents, contents);
                            } else {
                                assertArrayEquals(expected, bytes);
                            }
                        }
                    }
                } else if (pathToContents.keySet().contains(name)) {
                    notFound.remove(name);
                }
                entry = zis.getNextEntry();
            }
        } finally {
            fis.close();
            if (zis != null) {
                zis.close();
            }
        }

        assertTrue("Did not find the following paths in the " + archive.getPath() + " file: " +
                notFound, notFound.isEmpty());
    }

    public static void extractFile(
            @NonNull File zipFile,
            @NonNull String entryPath,
            @NonNull File destFile) throws IOException {
        assertTrue("File '" + zipFile.getPath() + "' does not exist.", zipFile.isFile());
        ZipInputStream zis = null;
        FileInputStream fis;
        fis = new FileInputStream(zipFile);
        try {
            zis = new ZipInputStream(fis);

            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String name = entry.getName();

                if (entryPath.equals(name)) {
                    byte[] bytes = ByteStreams.toByteArray(zis);
                    if (bytes != null) {
                        Files.write(bytes, destFile);
                        break;
                    }
                }

                entry = zis.getNextEntry();
            }
        } finally {
            fis.close();
            if (zis != null) {
                zis.close();
            }
        }
    }

    public static Set<String> getZipEntries(@NonNull File file) throws IOException{
        Set<String> entries = Sets.newHashSet();

        try(ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
            while (zipFileEntries.hasMoreElements()) {
                entries.add(zipFileEntries.nextElement().getName());
            }
        }

        return entries;
    }

    public static FieldNode checkClassFile(@NonNull File jarFile, @NonNull String entryName,
            @NonNull String fieldName) throws IOException {

        try (ZipFile zipFile = new ZipFile(jarFile)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            assertThat(entry).named(entryName + " entry").isNotNull();
            ClassReader classReader = new ClassReader(zipFile.getInputStream(entry));
            ClassNode mainTestClassNode = new ClassNode(Opcodes.ASM5);
            classReader.accept(mainTestClassNode, 0);

            FieldNode fieldNode = null;
            for(Object o: mainTestClassNode.fields){
                FieldNode fn = (FieldNode) o;
                if (fn.name.equals(fieldName)){
                    fieldNode = fn;
                    break;
                }
            }

            assert fieldNode != null;
            return fieldNode;
        }
    }
}

