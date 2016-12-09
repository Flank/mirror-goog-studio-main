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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AarTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void checkContains() throws IOException {
        Path zipPath = temporaryFolder.newFile("temp.zip").toPath();
        TestDataCreator.writeAarWithLibJar(zipPath);

        Aar aar = new Aar(zipPath);

        assertThat(aar.toString()).contains(zipPath.toString());

        assertThat(aar.containsClass("Lcom/example/SomeClass;"))
                .named("Aar contains class (from main)")
                .isTrue();
        assertThat(aar.containsClass("Lcom/example/somelib/Lib;"))
                .named("Aar contains class (from secondary)")
                .isTrue();
        assertThat(aar.containsMainClass("Lcom/example/SomeClass;"))
                .named("Aar contains main class (from main)")
                .isTrue();
        assertThat(aar.containsMainClass("Lcom/example/somelib/Lib;"))
                .named("Aar contains main class (from secondary")
                .isFalse();
        assertThat(aar.containsSecondaryClass("Lcom/example/SomeClass;"))
                .named("Aar contains secondary class (from main)")
                .isFalse();
        assertThat(aar.containsSecondaryClass("Lcom/example/somelib/Lib;"))
                .named("Aar contains secondary class (from secondary)")
                .isTrue();

        assertNotNull("Contains java resource",
                aar.getJavaResource("com/example/SomeClass.class"));
        assertNull("Does not contain java resource",
                aar.getJavaResource("com/example/SomeClass2.class"));

        assertNotNull("Contains resource",
                aar.getResource("values/values.xml"));
        assertNull("Does not contain resource",
                aar.getResource("values/other.xml"));


        try {
            aar.containsClass("com.example.SomeClass2");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // expected.
        }
    }

    @Test
    public void checkFileConstructor() throws IOException {
        Path zipPath = temporaryFolder.newFile("temp.zip").toPath();
        TestDataCreator.writeAarWithLibJar(zipPath);
        Aar aar = new Aar(zipPath.toFile());
        assertThat(aar.toString()).contains(zipPath.toString());
    }

}
