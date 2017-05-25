/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.testutils.apk;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.MemoryDataStore;

public class TestDataCreator {

    static final byte[] FAKE_CLASS = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

    static void writeAar(@NonNull Path zipFile) throws IOException {
        writeAar(zipFile, false);
    }

    static void writeAarWithLibJar(@NonNull Path zipFile) throws IOException {
        writeAar(zipFile, true);
    }

    private static void writeAar(@NonNull Path zipFile, boolean libJar) throws IOException {
        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(
                             Files.newOutputStream(
                                     zipFile,
                                     StandardOpenOption.TRUNCATE_EXISTING))) {
            zipOutputStream.putNextEntry(new ZipEntry("classes.jar"));
            zipOutputStream.write(fakeJar("com.example.SomeClass"));
            zipOutputStream.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zipOutputStream.putNextEntry(new ZipEntry("R.txt"));
            zipOutputStream.putNextEntry(new ZipEntry("res/values/values.xml"));
            zipOutputStream.write("values file content".getBytes());
            if (libJar) {
                zipOutputStream.putNextEntry(new ZipEntry("libs/some_lib.jar"));
                zipOutputStream.write(fakeJar("com.example.somelib.Lib"));
            }
        }
    }

    static byte[] fakeJar(@NonNull String className) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream inner = new ZipOutputStream(baos)) {
            inner.putNextEntry(new ZipEntry(className.replace('.', '/') + ".class"));
            inner.write(FAKE_CLASS);
        }
        return baos.toByteArray();
    }

    static void writeApkWithMonoDex(@NonNull Path apk) throws IOException {
        writeApk(apk, false);
    }

    static void writeApkWithMultiDex(@NonNull Path apk) throws IOException {
        writeApk(apk, true);
    }

    private static void writeApk(@NonNull Path apk, boolean multi) throws IOException {
        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(
                             Files.newOutputStream(
                                     apk,
                                     StandardOpenOption.TRUNCATE_EXISTING))) {
            zipOutputStream.putNextEntry(new ZipEntry("classes.dex"));
            zipOutputStream.write(
                    dexFile(multi ? "com.example.SomeMultiClass" : "com.example.SomeClass"));
            zipOutputStream.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zipOutputStream.putNextEntry(new ZipEntry("java_resource"));
            zipOutputStream.putNextEntry(new ZipEntry("res/values/values.xml"));
            if (multi) {
                zipOutputStream.putNextEntry(new ZipEntry("classes2.dex"));
                zipOutputStream.write(dexFile("com.example.somelib.Lib"));
                zipOutputStream.putNextEntry(new ZipEntry("classes3.dex"));
                zipOutputStream.write(dexFile("com.example.somelib2.Lib2"));
            }
            zipOutputStream.write("values file content".getBytes());
        }
    }

    static byte[] dexFile(@NonNull String className) throws IOException {
        DexBuilder dexBuilder = new DexBuilder(Opcodes.getDefault());

        dexBuilder.internClassDef(
                "L" + className.replace('.', '/') + ";",
                0x01,
                "Ljava/lang/Object;",
                null,
                null,
                ImmutableSet.of(),
                null,
                null);

        MemoryDataStore dexData = new MemoryDataStore();
        dexBuilder.writeTo(dexData);

        return dexData.getData();
    }
}
