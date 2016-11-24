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

package com.android.builder.symbols;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RGenerationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Checks that a given file has a line with the given contents.
     *
     * @param file the file to check
     * @param contents the contents expected in one of the lines
     * @return does the file contain a line with the given contents?
     * @throws Exception failed to read the file
     */
    private boolean fileContains(@NonNull File file, @NonNull String contents)
            throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        for (String line : lines) {
            if (line.contains(contents)) {
                return true;
            }
        }

        return false;
    }

    @Test
    public void librariesGetValuesFromMainSymbols() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(new Symbol("a", "b", "c", "d"))
                        .add(new Symbol("e", "f", "g", "h"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("c.d")
                        .add(new Symbol("a", "b", "cc", "dd"))
                        .build();

        SymbolTable l1 =
                SymbolTable.builder()
                        .tablePackage("e.f")
                        .add(new Symbol("e", "f", "gg", "hh"))
                        .build();

        File out = temporaryFolder.newFolder();
        RGeneration.generateRForLibraries(main, Arrays.asList(l0, l1), out, false);

        assertTrue(fileContains(FileUtils.join(out, "c", "d", "R.java"), "static c b = d"));
        assertFalse(fileContains(FileUtils.join(out, "c", "d", "R.java"), "static g f = h"));
        assertFalse(fileContains(FileUtils.join(out, "e", "f", "R.java"), "static c b = d"));
        assertTrue(fileContains(FileUtils.join(out, "e", "f", "R.java"), "static g f = h"));
    }

    @Test
    public void generationAllowedIfLibraryContainsExtraSymbols() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("c.d")
                        .add(new Symbol("a", "b", "c", "d"))
                        .add(new Symbol("eeee", "ffff", "g", "h"))
                        .build();

        File out = temporaryFolder.newFolder();

        RGeneration.generateRForLibraries(main, Collections.singleton(l0), out, false);
    }

    @Test
    public void librariesWithSamePackageAndNameAsMainDoNotGenerateAnything() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        File out = temporaryFolder.newFolder();

        RGeneration.generateRForLibraries(main, Collections.singleton(l0), out, false);
        assertFalse(FileUtils.join(out, "a", "b", "R.java").exists());
    }

    @Test
    public void librariesWithSamePackageAndNameShareRFile() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(new Symbol("a", "b", "c", "d"))
                        .add(new Symbol("e", "f", "g", "h"))
                        .add(new Symbol("i", "j", "k", "l"))
                        .add(new Symbol("m", "n", "o", "p"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .tableName("Roo")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        SymbolTable l1 =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .tableName("Roo")
                        .add(new Symbol("e", "f", "g", "h"))
                        .build();

        SymbolTable l2 =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .tableName("Room")
                        .add(new Symbol("i", "j", "k", "l"))
                        .build();

        SymbolTable l3 =
                SymbolTable.builder()
                        .tablePackage("a.b.c")
                        .tableName("Roo")
                        .add(new Symbol("m", "n", "o", "p"))
                        .build();

        File out = temporaryFolder.newFolder();

        RGeneration.generateRForLibraries(main, Arrays.asList(l0, l1, l2, l3), out, false);

        assertTrue(fileContains(FileUtils.join(out, "a", "b", "Roo.java"), "static c b = d"));
        assertTrue(fileContains(FileUtils.join(out, "a", "b", "Roo.java"), "static g f = h"));
        assertFalse(fileContains(FileUtils.join(out, "a", "b", "Roo.java"), "static k j = l"));
        assertFalse(fileContains(FileUtils.join(out, "a", "b", "Roo.java"), "static o n = p"));
        assertFalse(fileContains(FileUtils.join(out, "a", "b", "Room.java"), "static c b = d"));
        assertFalse(fileContains(FileUtils.join(out, "a", "b", "Room.java"), "static g f = h"));
        assertTrue(fileContains(FileUtils.join(out, "a", "b", "Room.java"), "static k j = l"));
        assertFalse(fileContains(FileUtils.join(out, "a", "b", "Room.java"), "static o n = p"));
        assertFalse(
                fileContains(FileUtils.join(out, "a", "b", "c", "Roo.java"), "static c b = d"));
        assertFalse(
                fileContains(FileUtils.join(out, "a", "b", "c", "Roo.java"), "static g f = h"));
        assertFalse(
                fileContains(FileUtils.join(out, "a", "b", "c", "Roo.java"), "static k j = l"));
        assertTrue(fileContains(FileUtils.join(out, "a", "b", "c", "Roo.java"), "static o n = p"));
    }

    @Test
    public void finalIdsAreGeneratedOnlyIfRequested() throws Exception {
        SymbolTable main =
                SymbolTable.builder()
                        .tablePackage("a.b")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        SymbolTable l0 =
                SymbolTable.builder()
                        .tablePackage("c.d")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        File out = temporaryFolder.newFolder();
        RGeneration.generateRForLibraries(main, Collections.singleton(l0), out, false);

        assertTrue(
                fileContains(FileUtils.join(out, "c", "d", "R.java"), "public static c b = d"));

        out = temporaryFolder.newFolder();
        RGeneration.generateRForLibraries(main, Collections.singleton(l0), out, true);

        assertTrue(
                fileContains(
                        FileUtils.join(out, "c", "d", "R.java"), "public static final c b = d"));
    }
}
