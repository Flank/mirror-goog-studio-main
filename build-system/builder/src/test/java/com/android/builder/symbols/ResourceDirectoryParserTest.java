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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ResourceDirectoryParserTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Random random = new Random();

    /**
     * Creates a file in a directory with specific data. The file is created in the path
     * {@code path} resolved against {@code directory}. For example, if {@code path} is
     * {@code a/b}, the file to create is named {@code b} and it is placed inside subdirectory
     * {@code a} of {@code directory}. The subdirectory is created if needed
     *
     * @param data the data to write in the file
     * @param directory the directory where the file is created.
     * @param path the path for the file, relative to {@code directory} using forward slashes as
     * separators
     * @throws Exception failed to create the required directories or to write the data in the
     * file
     */
    private static void make(@NonNull byte[] data, @NonNull File directory, @NonNull String path)
            throws Exception {
        Path file = directory.toPath().resolve(path);
        FileUtils.mkdirs(file.getParent().toFile());
        Files.write(file, data);
    }

    /**
     * Same as {@link #make(byte[], File, String)}, but writing random data in the file instead
     * of receiving specific data to write.
     * @param directory the directory where the file is created.
     * @param path the path for the file, relative to {@code directory} using forward slashes as
     * separators
     * @throws Exception failed to create the required directories or to write the data in the
     * file
     */
    private static void makeRandom(@NonNull File directory, @NonNull String path) throws Exception {
        int byteCount = random.nextInt(1000);
        byte[] bytes = new byte[byteCount];
        random.nextBytes(bytes);
        make(bytes, directory, path);

    }

    @Test
    public void parseEmptyResourceDirectory() throws Exception {
        File directory = temporaryFolder.newFolder();

        SymbolTable parsed =
                ResourceDirectoryParser.parseDirectory(directory, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder().build();

        assertEquals(expected, parsed);
    }

    @Test
    public void parseDrawablesAndRaws() throws Exception {
        File directory = temporaryFolder.newFolder();

        makeRandom(directory, "drawable/foo.png");
        makeRandom(directory, "drawable/bar.png");
        FileUtils.mkdirs(new File(directory, "drawable-en"));
        makeRandom(directory, "drawable-en-hdpi/foo.png");
        makeRandom(directory, "raw/foo.png");
        makeRandom(directory, "raw-en/foo.png");
        makeRandom(directory, "raw-en/bar.png");

        SymbolTable parsed =
                ResourceDirectoryParser.parseDirectory(directory, IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("drawable", "bar", "int", 0x7f_09_0001))
                        .add(SymbolTestUtils.createSymbol("drawable", "foo", "int", 0x7f_09_0002))
                        .add(SymbolTestUtils.createSymbol("raw", "bar", "int", 0x7f_14_0002))
                        .add(SymbolTestUtils.createSymbol("raw", "foo", "int", 0x7f_14_0001))
                        .build();

        assertEquals(expected, parsed);
    }

    @Test
    public void parseValues() throws Exception {
        File directory = temporaryFolder.newFolder();

        String values =
                ""
                        + "<resources>"
                        + "  <color name=\"a\">#000000</color>"
                        + "  <color name=\"b\">#000000</color>"
                        + "</resources>";
        String values_en =
                ""
                        + "<resources>"
                        + "  <color name=\"b\">#000000</color>"
                        + "  <color name=\"c\">#000000</color>"
                        + "</resources>";

        make(values.getBytes(), directory, "values/col.xml");
        make(values_en.getBytes(), directory, "values-en/col.xml");

        SymbolTable parsed =
                ResourceDirectoryParser.parseDirectory(directory, IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("color", "a", "int", 0x7f_06_0001))
                        .add(SymbolTestUtils.createSymbol("color", "b", "int", 0x7f_06_0002))
                        .add(SymbolTestUtils.createSymbol("color", "c", "int", 0x7f_06_0004))
                        .build();

        assertEquals(expected, parsed);
    }

    @Test
    public void failWithException() throws Exception {
        File directory = temporaryFolder.newFolder();

        String values =
                ""
                        + "<resources>"
                        + "  <color name=\"a\">#000000</color>"
                        + "  <color name=\"a\">#000000</color>"
                        + "</resources>";

        make(values.getBytes(), directory, "values/col.xml");

        try {
            ResourceDirectoryParser.parseDirectory(directory, IdProvider.sequential());
            fail();
        } catch (ResourceDirectoryParseException e) {
            assertThat(e.getMessage()).contains(FileUtils.join("values", "col.xml"));
        }
    }
}
