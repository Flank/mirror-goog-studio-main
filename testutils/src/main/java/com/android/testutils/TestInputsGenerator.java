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

package com.android.testutils;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import kotlin.Pair;
import kotlin.collections.MapsKt;
import org.objectweb.asm.Type;

/** Utility methods for generating inputs to be used in tests. */
public final class TestInputsGenerator {

    /** Generates a jar containing empty classes with the specified names. */
    public static void jarWithEmptyClasses(
            @NonNull Path path, @NonNull Collection<String> classNames) throws Exception {
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(path))) {
            jarWithEmptyClasses(outputStream, classNames);
        }
    }

    public static byte[] jarWithEmptyClasses(@NonNull Collection<String> classNames)
            throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            jarWithEmptyClasses(outputStream, classNames);
            return outputStream.toByteArray();
        }
    }

    private static void jarWithEmptyClasses(
            @NonNull OutputStream outputStream, @NonNull Collection<String> classNames)
            throws Exception {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (String fullName : classNames) {
                int lastSeparator = fullName.lastIndexOf('/');
                String pkg = "";
                String className;
                if (lastSeparator != -1) {
                    pkg = fullName.substring(0, lastSeparator);
                    className = fullName.substring(lastSeparator + 1);
                } else {
                    className = fullName;
                }

                byte[] byteCode = TestClassesGenerator.emptyClass(pkg, className);

                writeToJar(zipOutputStream, byteCode, fullName + SdkConstants.DOT_CLASS);
            }
        }
    }

    /** Generates a dir containing empty classes with the specified names. */
    public static void dirWithEmptyClasses(
            @NonNull Path path, @NonNull Collection<String> classNames) throws Exception {
        Files.createDirectories(path);
        for (String fullName : classNames) {
            int lastSeparator = fullName.lastIndexOf('/');
            String pkg = "";
            String className;
            if (lastSeparator != -1) {
                pkg = fullName.substring(0, lastSeparator);
                className = fullName.substring(lastSeparator + 1);
            } else {
                className = fullName;
            }

            byte[] byteCode = TestClassesGenerator.emptyClass(pkg, className);
            writeToDir(path, byteCode, fullName + SdkConstants.DOT_CLASS);
        }
    }

    public static void writeJarWithEmptyEntries(
            @NonNull Path jar, @NonNull Iterable<String> entries) throws Exception {
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(jar))) {
            jarWithEmptyEntries(outputStream, entries);
        }
    }

    public static byte[] jarWithEmptyEntries(@NonNull Iterable<String> entries) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            jarWithEmptyEntries(outputStream, entries);
            return outputStream.toByteArray();
        }
    }

    private static void jarWithEmptyEntries(
            OutputStream outputStream, @NonNull Iterable<String> entries) throws Exception {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (String name : entries) {
                zipOutputStream.putNextEntry(new ZipEntry(name));
                zipOutputStream.closeEntry();
            }
        }
    }

    public static void writeJarWithTextEntries(
            @NonNull Path jar, @NonNull com.android.utils.Pair<String, String>... entries)
            throws Exception {
        writeJarWithTextEntries(
                jar,
                Arrays.stream(entries)
                        .collect(
                                Collectors.toMap(
                                        com.android.utils.Pair::getFirst,
                                        com.android.utils.Pair::getSecond)));
    }

    public static void writeJarWithTextEntries(
            @NonNull Path jar, @NonNull Map<String, String> entries) throws Exception {
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(jar))) {
            writeJarWithTextEntries(outputStream, entries);
        }
    }

    public static byte[] jarWithTextEntries(@NonNull Pair<String, String>... entries)
            throws Exception {
        return jarWithTextEntries(MapsKt.mapOf(entries));
    }

    public static byte[] jarWithTextEntries(@NonNull Map<String, String> entries) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeJarWithTextEntries(outputStream, entries);
            return outputStream.toByteArray();
        }
    }

    private static void writeJarWithTextEntries(
            @NonNull OutputStream jar, @NonNull Map<String, String> entries) throws Exception {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(jar)) {
            for (Map.Entry<String, String> pair : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(pair.getKey()));
                zipOutputStream.write(pair.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
    }

    /**
     * Generates dir/jar containing the specified classes, depending on if a path ends with .jar or
     * not.
     */
    public static void pathWithClasses(@NonNull Path path, @NonNull Collection<Class<?>> classes)
            throws IOException {
        if (path.toString().endsWith(SdkConstants.DOT_JAR)) {
            jarWithClasses(path, classes);
        } else {
            dirWithClasses(path, classes);
        }
    }

    /** Returns the bytes of a jar containing the specified classes */
    @NonNull
    public static byte[] jarWithClasses(@NonNull Collection<Class<?>> classes) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            jarWithClasses(byteArrayOutputStream, classes);
            return byteArrayOutputStream.toByteArray();
        }
    }

    /** Generates jar containing the specified classes. */
    private static void jarWithClasses(@NonNull Path path, @NonNull Collection<Class<?>> classes)
            throws IOException {
        try (OutputStream fileOutputStream = Files.newOutputStream(path)) {
            jarWithClasses(fileOutputStream, classes);
        }
    }

    private static void jarWithClasses(
            @NonNull OutputStream os, @NonNull Collection<Class<?>> classes) throws IOException {
        try (ZipOutputStream outputStream = new ZipOutputStream(os)) {
            for (Class<?> klass : classes) {
                byte[] data = getClassInput(klass);

                writeToJar(outputStream, data, getPath(klass));
            }
        }
    }

    /** Generates directory containing the specified classes. */
    private static void dirWithClasses(@NonNull Path path, @NonNull Collection<Class<?>> classes)
            throws IOException {
        Files.createDirectories(path);
        for (Class<?> klass : classes) {
            writeToDir(path, getClassInput(klass), getPath(klass));
        }
    }



    @NonNull
    public static String getPath(@NonNull Class<?> klass) {
        return Type.getInternalName(klass) + SdkConstants.DOT_CLASS;
    }

    private static void writeToJar(
            @NonNull ZipOutputStream outputStream, @NonNull byte[] data, @NonNull String name)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        outputStream.putNextEntry(entry);
        outputStream.write(data);
        outputStream.closeEntry();
    }

    private static void writeToDir(@NonNull Path path, @NonNull byte[] data, @NonNull String name)
            throws IOException {
        Path srcFile = path.resolve(name);
        Files.createDirectories(srcFile.getParent());
        Files.write(srcFile, data);
    }

    @NonNull
    private static byte[] getClassInput(Class<?> klass) throws IOException {
        try (InputStream stream = klass.getClassLoader().getResourceAsStream(getPath(klass))) {
            return ByteStreams.toByteArray(stream);
        }
    }
}
