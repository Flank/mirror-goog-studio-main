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

package com.android.tools.apk.analyzer;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestResources;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchivesTest {
    private Archive archive;
    private FileSystem fs;

    @Before
    public void setup() throws IOException {
        archive = Archives.open(TestResources.getFile("/test.apk").toPath());
        fs = archive.getContentRoot().getFileSystem();
    }

    @After
    public void cleanup() throws IOException {
        archive.close();
    }

    @Test
    public void open_windowsPath() throws IOException {
        byte[] apkData = Files.readAllBytes(TestResources.getFile("/test.apk").toPath());

        FileSystem memFs = Jimfs.newFileSystem(Configuration.windows());

        Path folder = Files.createDirectory(memFs.getPath("foo"));
        Path apk = folder.resolve("foo.apk");
        Files.write(apk, apkData, StandardOpenOption.CREATE);

        try (Archive archive = Archives.open(apk)) {
            assertThat(archive).isNotNull();
        }
    }

    @Test
    public void binaryXml_manifest() {
        assertThat(archive.isBinaryXml(fs.getPath("/AndroidManifest.xml"), new byte[] {0x3, 0x0}))
                .isTrue();
    }

    @Test
    public void binaryXml_wrongContent() {
        assertThat(archive.isBinaryXml(fs.getPath("/AndroidManifest.xml"), new byte[] {0x0, 0x0}))
                .isFalse();
    }

    @Test
    public void binaryXml_layoutXml() {
        assertThat(archive.isBinaryXml(fs.getPath("/res/layout/foo.xml"), new byte[] {0x3, 0x0}))
                .isTrue();
    }

    @Test
    public void binaryXml_rawXml() {
        assertThat(archive.isBinaryXml(fs.getPath("/res/raw/foo.xml"), new byte[] {0x3, 0x0}))
                .isFalse();
    }
}
