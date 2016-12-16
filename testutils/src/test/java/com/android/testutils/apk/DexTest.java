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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DexTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void checkContains() throws IOException {
        Path dexPath = temporaryFolder.newFile("dex.dex").toPath();
        Files.write(
                dexPath,
                TestDataCreator.dexFile("com.example.Foo"),
                StandardOpenOption.TRUNCATE_EXISTING);

        Dex dex = new Dex(dexPath);

        assertThat(dex.toString()).contains(dexPath.toString());

        Map<String, DexBackedClassDef> classes = dex.getClasses();

        // Check caching
        assertThat(dex.getClasses()).isSameAs(classes);

        assertThat(dex.getClasses()).hasSize(1);
        assertThat(classes).containsKey("Lcom/example/Foo;");
    }


    @Test
    public void checkFileConstructor() throws IOException {
        Path dexPath = temporaryFolder.newFile("dex.dex").toPath();
        Files.write(
                dexPath,
                TestDataCreator.dexFile("com.example.Foo"),
                StandardOpenOption.TRUNCATE_EXISTING);

        Dex dex = new Dex(dexPath.toFile());

        assertThat(dex.toString()).contains(dexPath.toString());
    }
}
