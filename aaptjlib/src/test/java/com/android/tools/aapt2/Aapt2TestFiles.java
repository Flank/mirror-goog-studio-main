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

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public class Aapt2TestFiles {

    private Aapt2TestFiles() {}

    static void writeLenaPng(@Nonnull Path lenaPng) throws IOException {
        Files.createDirectories(lenaPng.getParent());
        try (InputStream is = CompileTest.class.getResourceAsStream("/lena.png");
                OutputStream fos = Files.newOutputStream(lenaPng)) {
            assertNotNull(is);
            byte[] buf = new byte[1024 * 1024];
            int r;
            while ((r = is.read(buf)) >= 0) {
                fos.write(buf, 0, r);
            }
        }
    }

    static void writeStringsXml(@Nonnull Path stringsXml) throws IOException {
        Files.createDirectories(stringsXml.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        lines.add("<resources>");
        lines.add("    <string");
        lines.add("        name=\"string_name\"");
        lines.add("        >text_string</string>");
        lines.add("</resources>");
        Files.write(stringsXml, lines, StandardCharsets.UTF_8);
    }

    static void writeIncorrectLayout(@Nonnull Path layoutXml) throws IOException {
        Files.createDirectories(layoutXml.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        lines.add("<LinearLayout");
        lines.add("    xmlns:android=\"http://schemas.android.com/apk/res/android\"");
        lines.add("    android:gravity=\"centervertical\"");
        lines.add("/>");
        Files.write(layoutXml, lines, StandardCharsets.UTF_8);
    }
}
