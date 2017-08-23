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
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LinkTest {

    @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Aapt2Jni aapt;
    private static Path compiledLenaPng, compiledStringsXml, compiledInvalidLayout, manifest;

    @BeforeClass
    public static void compileResources() throws Exception {
        // directories
        Path drawable = temporaryFolder.newFolder("drawable").toPath();
        Path values = temporaryFolder.newFolder("values").toPath();
        Path layoutDir = temporaryFolder.newFolder("layout").toPath();
        // files
        Path lena = drawable.resolve("lena.png");
        Path strings = values.resolve("strings.xml");
        Path layout = layoutDir.resolve("my_layout.xml");
        // write resources
        Aapt2TestFiles.writeLenaPng(lena);
        Aapt2TestFiles.writeStringsXml(strings);
        Aapt2TestFiles.writeIncorrectLayout(layout);

        Path compileOut = temporaryFolder.newFolder().toPath();
        Aapt2Result result =
                getAapt()
                        .compile(
                                Arrays.asList(
                                        "-o",
                                        compileOut.toString(),
                                        lena.toString(),
                                        strings.toString(),
                                        layout.toString()));

        assertEquals(0, result.getReturnCode());
        assertTrue(result.getMessages().isEmpty());

        compiledLenaPng =
                compileOut.resolve(Aapt2RenamingConventions.compilationRename(lena.toFile()));
        assertTrue(Files.exists(compiledLenaPng));
        compiledStringsXml =
                compileOut.resolve(Aapt2RenamingConventions.compilationRename(strings.toFile()));
        assertTrue(Files.isRegularFile(compiledStringsXml));
        compiledInvalidLayout =
                compileOut.resolve(Aapt2RenamingConventions.compilationRename(layout.toFile()));
        assertTrue(Files.isRegularFile(compiledInvalidLayout));
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
    public void link() throws Exception {
        Path resourceDotApUnderscore = temporaryFolder.newFile("resources.ap_").toPath();
        Path rDotJavaDir = temporaryFolder.newFolder("r").toPath();

        Aapt2Result result =
                getAapt()
                        .link(
                                Arrays.asList(
                                        "-o",
                                        resourceDotApUnderscore.toString(),
                                        "--manifest",
                                        manifest.toString(),
                                        "--auto-add-overlay",
                                        "-R",
                                        compiledLenaPng.toString(),
                                        "-R",
                                        compiledStringsXml.toString(),
                                        "--java",
                                        rDotJavaDir.toString()));

        assertEquals(0, result.getReturnCode());
        assertTrue(result.getMessages().isEmpty());
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
    public void linkFailure() throws Exception {
        Path resourceDotApUnderscore = temporaryFolder.newFile("resources2.ap_").toPath();

        Aapt2Result result =
                getAapt()
                        .link(
                                Arrays.asList(
                                        "-o",
                                        resourceDotApUnderscore.toString(),
                                        "--manifest",
                                        manifest.toString(),
                                        "-R",
                                        compiledStringsXml.toString()));

        assertEquals(1, result.getReturnCode());
        assertEquals(3, result.getMessages().size());

        assertEquals(Aapt2Result.Message.LogLevel.ERROR, result.getMessages().get(0).getLevel());
        assertEquals(compiledStringsXml.toString(), result.getMessages().get(0).getPath());
        assertEquals(-1, result.getMessages().get(0).getLine());
        assertEquals(
                "resource string/string_name does not override an existing resource",
                result.getMessages().get(0).getMessage());

        assertEquals(
                "define an <add-resource> tag or use --auto-add-overlay",
                result.getMessages().get(1).getMessage());
        assertEquals("failed parsing overlays", result.getMessages().get(2).getMessage());
    }

    @Test
    public void invalidOption() throws Exception {
        Aapt2Result result =
                getAapt().link(Collections.singletonList("--link-test----invalid-option"));
        assertEquals(1, result.getReturnCode());
        // NB: Argument parse failures are output directly to stderr.
        assertEquals(0, result.getMessages().size());
    }

    @Test
    public void invalidLayout() throws Exception {
        Path resourceDotApUnderscore = temporaryFolder.newFile("resources3.ap_").toPath();

        Aapt2Result result =
                getAapt()
                        .link(
                                Arrays.asList(
                                        "-o",
                                        resourceDotApUnderscore.toString(),
                                        "--manifest",
                                        manifest.toString(),
                                        "-R",
                                        compiledInvalidLayout.toString()));

        assertEquals(1, result.getReturnCode());
        assertEquals(2, result.getMessages().size());

        assertEquals(Aapt2Result.Message.LogLevel.ERROR, result.getMessages().get(0).getLevel());
        assertEquals(0, result.getMessages().get(0).getLine());
        assertEquals(
                "attribute 'android:gravity' not found", result.getMessages().get(0).getMessage());
    }

    private static Aapt2Jni getAapt() throws IOException, ExecutionException {
        if (aapt == null) {
            aapt = Aapt2TestFactory.get(temporaryFolder);
        }
        return aapt;
    }
}
