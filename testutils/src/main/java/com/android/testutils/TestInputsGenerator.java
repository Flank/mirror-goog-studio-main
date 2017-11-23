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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.Type;

/** Utility methods for generating inputs to be used in tests. */
public final class TestInputsGenerator {

    /** Generates a jar containing empty classes with the specified names. */
    public static void jarWithEmptyClasses(
            @NonNull Path path, @NonNull Collection<String> classNames) throws Exception {
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(path))) {
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

                writeToJar(outputStream, byteCode, fullName + SdkConstants.DOT_CLASS);
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
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (String name : entries) {
                zipOutputStream.putNextEntry(new ZipEntry(name));
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

    /** Generates jar containing the specified classes. */
    private static void jarWithClasses(@NonNull Path path, @NonNull Collection<Class<?>> classes)
            throws IOException {
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(path))) {
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
