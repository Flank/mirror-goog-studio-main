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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ZipTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void checkEmbeddedZip() throws IOException {
        byte[] content = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        Path zipPath = temporaryFolder.newFile("temp.zip").toPath();
        TestDataCreator.writeAar(zipPath);

        Zip zip = new Zip(zipPath);
        assertThat(zip.exists()).isTrue();
        assertThat(zip.getEntries()).hasSize(4);

        Zip innerZip = zip.getEntryAsZip("classes.jar");
        Path classFile = innerZip.getEntry("com/example/SomeClass.class");
        assertNotNull(classFile);
        assertThat(Files.readAllBytes(classFile)).isEqualTo(content);
    }

    @Test
    public void testNotExist() throws IOException {
        Path notExist = temporaryFolder.newFolder().toPath().resolve("not_exist");

        Zip zip = new Zip(notExist);

        assertThat(zip.getEntries()).isEmpty();
        assertThat(zip.exists()).isFalse();
    }

    @Test
    public void fileConstructor() throws IOException {
        Path notExist = temporaryFolder.newFolder().toPath().resolve("not_exist");
        Zip zip = new Zip(notExist.toFile());
        assertThat(zip.toString()).contains(notExist.toString());
        assertThat((Object) zip.getFile()).isEqualTo(notExist);
    }

    @Test
    public void invalidFileSystem() throws IOException {
        Path zipPath = temporaryFolder.newFile("temp.zip").toPath();
        TestDataCreator.writeAar(zipPath);

        Zip zip = new Zip(zipPath);
        Path entry = zip.getEntry("classes.jar");
        assertNotNull(entry);
        try {
            //noinspection unused
            Zip innerZip = new Zip(entry);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.toString()).contains("getEntryAsZip");
        }
    }
}
