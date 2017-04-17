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

package com.android.tools.aapt2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LinkTest {

    @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Path compiledLenaPng, compiledStringsXml, manifest;

    @BeforeClass
    public static void compilePng() throws IOException {
        Path drawable = temporaryFolder.newFolder("drawable").toPath();
        Path lena = drawable.resolve("lena.png");
        Aapt2TestFiles.writeLenaPng(lena);
        Path compileOut = temporaryFolder.newFolder().toPath();
        assertEquals(
                0, Aapt2Jni.compile(Arrays.asList("-o", compileOut.toString(), lena.toString())));
        compiledLenaPng =
                compileOut.resolve(Aapt2RenamingConventions.compilationRename(lena.toFile()));
        assertTrue(Files.exists(compiledLenaPng));
    }

    @BeforeClass
    public static void compileXml() throws IOException {
        Path values = temporaryFolder.newFolder("values").toPath();
        Path strings = values.resolve("strings.xml");
        Aapt2TestFiles.writeStringsXml(strings);
        Path compileOut = temporaryFolder.newFolder().toPath();
        assertEquals(
                0,
                Aapt2Jni.compile(Arrays.asList("-o", compileOut.toString(), strings.toString())));
        compiledStringsXml =
                compileOut.resolve(Aapt2RenamingConventions.compilationRename(strings.toFile()));
        assertTrue(Files.isRegularFile(compiledStringsXml));
    }

    @BeforeClass
    public static void writeManifest() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"");
        lines.add("    package=\"com.example.myapplication99\">");
        lines.add("</manifest>");
        manifest = temporaryFolder.newFile("AndroidManifest.xml").toPath();
        Files.write(manifest, lines, StandardCharsets.UTF_8);
    }

    @Test
    public void link() throws IOException {
        Path resourceDotApUnderscore = temporaryFolder.newFile("resources.ap_").toPath();

        assertEquals(
                0,
                Aapt2Jni.link(
                        Arrays.asList(
                                "-o",
                                resourceDotApUnderscore.toString(),
                                "--manifest",
                                manifest.toString(),
                                "--auto-add-overlay",
                                "-R",
                                compiledLenaPng.toString(),
                                "-R",
                                compiledStringsXml.toString())));

        assertTrue(Files.exists(resourceDotApUnderscore));
        Set<String> elements = new HashSet<>();
        try (ZipInputStream zipInputStream =
                new ZipInputStream(
                        new BufferedInputStream(Files.newInputStream(resourceDotApUnderscore)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                elements.add(entry.getName());
            }
        }

        Set<String> expected = new HashSet<>();
        expected.add("AndroidManifest.xml");
        expected.add("resources.arsc");
        expected.add("res/drawable/lena.png");
        assertEquals(expected, elements);
    }

    @Test
    public void linkFailure() throws IOException {
        assertEquals(1, Aapt2Jni.link(Collections.singletonList("--invalid-option")));
    }
}
