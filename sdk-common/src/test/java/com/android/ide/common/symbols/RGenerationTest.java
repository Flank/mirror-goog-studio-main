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

package com.android.ide.common.symbols;

import static com.android.ide.common.symbols.SymbolTestUtils.createSymbol;
import static com.android.testutils.truth.FileSubject.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.utils.FileUtils;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RGenerationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void librariesGetValuesFromMainSymbols() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(createSymbol("attr", "b", "int", "0x1"))
                        .add(createSymbol("string", "f", "int", "0x2"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("c.d")
                        .add(createSymbol("attr", "b", "int", "dd"))
                        .build();

        SymbolTable l1 =
                SymbolTable.builder()
                        .tablePackage("e.f")
                        .add(createSymbol("string", "f", "int", "hh"))
                        .build();

        File out = temporaryFolder.newFolder();
        RGeneration.generateRForLibraries(main, Arrays.asList(l0, l1), out, false);

        assertThat(FileUtils.join(out, "c", "d", "R.java")).contains("static int b = 0x1");
        assertThat(FileUtils.join(out, "c", "d", "R.java")).doesNotContain("static int f =");
        assertThat(FileUtils.join(out, "e", "f", "R.java")).doesNotContain("static int b =");
        assertThat(FileUtils.join(out, "e", "f", "R.java")).contains("static int f = 0x2");
    }

    @Test
    public void generationAllowedIfLibraryContainsExtraSymbols() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("c.d")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .add(createSymbol("string", "ffff", "int", "h"))
                        .build();

        File out = temporaryFolder.newFolder();

        RGeneration.generateRForLibraries(main, Collections.singleton(l0), out, false);
    }

    @Test
    public void librariesWithSamePackageAsMainDoNotGenerateAnything() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .build();

        File out = temporaryFolder.newFolder();

        RGeneration.generateRForLibraries(main, Collections.singleton(l0), out, false);
        assertFalse(FileUtils.join(out, "a", "b", "R.java").exists());
    }

    @Test
    public void librariesWithSamePackageShareRFile() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(createSymbol("attr", "b", "int", "0x1"))
                        .add(createSymbol("string", "f", "int", "0x2"))
                        .add(createSymbol("integer", "j", "int", "0x3"))
                        .add(createSymbol("menu", "n", "int", "0x4"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("a.c")
                        .add(createSymbol("attr", "b", "int", ""))
                        .build();

        SymbolTable l1 =
                SymbolTable.builder()
                        .tablePackage("a.c")
                        .add(createSymbol("string", "f", "int", ""))
                        .build();

        SymbolTable l2 =
                SymbolTable.builder()
                        .tablePackage("a.d")
                        .add(createSymbol("integer", "j", "int", ""))
                        .build();

        SymbolTable l3 =
                SymbolTable.builder()
                        .tablePackage("a.b.c")
                        .add(createSymbol("menu", "n", "int", ""))
                        .build();

        File out = temporaryFolder.newFolder();

        RGeneration.generateRForLibraries(main, Arrays.asList(l0, l1, l2, l3), out, false);

        assertThat(FileUtils.join(out, "a", "c", "R.java")).contains("static int b = 0x1");
        assertThat(FileUtils.join(out, "a", "c", "R.java")).contains("static int f = 0x2");
        assertThat(FileUtils.join(out, "a", "c", "R.java")).doesNotContain("static int j =");
        assertThat(FileUtils.join(out, "a", "c", "R.java")).doesNotContain("static int n =");
        assertThat(FileUtils.join(out, "a", "d", "R.java")).doesNotContain("static int b =");
        assertThat(FileUtils.join(out, "a", "d", "R.java")).doesNotContain("static int f =");
        assertThat(FileUtils.join(out, "a", "d", "R.java")).contains("static int j = 0x3");
        assertThat(FileUtils.join(out, "a", "d", "R.java")).doesNotContain("static int n =");
        assertThat(FileUtils.join(out, "a", "b", "c", "R.java")).doesNotContain("static int b =");
        assertThat(FileUtils.join(out, "a", "b", "c", "R.java")).doesNotContain("static int f =");
        assertThat(FileUtils.join(out, "a", "b", "c", "R.java")).doesNotContain("static int j =");
        assertThat(FileUtils.join(out, "a", "b", "c", "R.java")).contains("static int n = 0x4");
    }

    @Test
    public void finalIdsAreGeneratedOnlyIfRequested() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(createSymbol("attr", "b", "int", "0x1"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("c.d")
                        .add(createSymbol("attr", "b", "int", "0x2"))
                        .build();

        File out = temporaryFolder.newFolder();
        RGeneration.generateRForLibraries(main, Collections.singleton(l0), out, false);

        assertThat(FileUtils.join(out, "c", "d", "R.java")).contains("public static int b = 0x1");

        out = temporaryFolder.newFolder();
        RGeneration.generateRForLibraries(main, Collections.singleton(l0), out, true);

        assertThat(FileUtils.join(out, "c", "d", "R.java"))
                .contains("public static final int b = 0x1");
    }
}
