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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

                ZipEntry entry = new ZipEntry(fullName + SdkConstants.DOT_CLASS);
                outputStream.putNextEntry(entry);
                outputStream.write(byteCode, 0, byteCode.length);
                outputStream.closeEntry();
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
            Path srcFile = path.resolve(fullName + SdkConstants.DOT_CLASS);
            Files.createDirectories(srcFile.getParent());
            Files.write(path.resolve(fullName + SdkConstants.DOT_CLASS), byteCode);
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
}
