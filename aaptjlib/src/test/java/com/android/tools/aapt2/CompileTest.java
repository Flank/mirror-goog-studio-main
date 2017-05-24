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

package com.android.tools.aapt2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CompileTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compilePng() throws Exception {
        Path drawable = temporaryFolder.newFolder("drawable").toPath();
        Path lena = drawable.resolve("lena.png");
        Path out = temporaryFolder.newFolder("out").toPath();

        Aapt2TestFiles.writeLenaPng(lena);

        Aapt2Result result = Aapt2Jni.compile(Arrays.asList("-o", out.toString(), lena.toString()));
        assertEquals(0, result.getReturnCode());
        assertTrue(result.getMessages().isEmpty());
        Path expectedOut = out.resolve(Aapt2RenamingConventions.compilationRename(lena.toFile()));
        assertTrue(Files.exists(expectedOut));
    }

    @Test
    public void compileXml() throws Exception {
        Path values = temporaryFolder.newFolder("values-w820dp").toPath();
        Path strings = values.resolve("strings-w820dp.xml");
        Path out = temporaryFolder.newFolder("out").toPath();

        Aapt2TestFiles.writeStringsXml(strings);

        Aapt2Result result =
                Aapt2Jni.compile(Arrays.asList("-o", out.toString(), strings.toString()));
        assertEquals(0, result.getReturnCode());
        assertTrue(result.getMessages().isEmpty());
        Path expectedOut =
                out.resolve(Aapt2RenamingConventions.compilationRename(strings.toFile()));
        assertTrue(Files.isRegularFile(expectedOut));
    }

    @Test
    public void compileXmlWithError() throws Exception {
        Path values = temporaryFolder.newFolder("values").toPath();
        Path strings = values.resolve("strings.xml");
        Path out = temporaryFolder.newFolder("out").toPath();

        List<String> lines = new ArrayList<>();
        lines.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        lines.add("");
        lines.add("<reso");
        Files.write(strings, lines, StandardCharsets.UTF_8);

        Aapt2Result result =
                Aapt2Jni.compile(Arrays.asList("-o", out.toString(), strings.toString()));
        assertEquals(1, result.getReturnCode());
        assertEquals(1, result.getMessages().size());
        assertEquals("xml parser error: unclosed token", result.getMessages().get(0).getMessage());
        assertEquals(strings.toString(), result.getMessages().get(0).getPath());
        assertEquals(Aapt2Result.Message.LogLevel.ERROR, result.getMessages().get(0).getLevel());
        assertEquals(0, result.getMessages().get(0).getLine());
        Path expectedOut =
                out.resolve(Aapt2RenamingConventions.compilationRename(strings.toFile()));
        assertFalse("Compiled file should not be created.", Files.exists(expectedOut));
    }
}
