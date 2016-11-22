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

import static org.junit.Assert.assertEquals;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SymbolIoTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testSingleInt() throws Exception {
        String r = "" +
                "int xml authenticator 0x7f040000\n";
        File file = mTemporaryFolder.newFile();
        Files.write(r, file, Charsets.UTF_8);

        SymbolTable table = SymbolIo.load(file);

        SymbolTable expected = new SymbolTable();
        expected.add(new Symbol("xml", "authenticator", "int", "0x7f040000"));
        assertEquals(table, expected);
    }

    @Test
    public void testStyleables() throws Exception {
        String r = "" +
                "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 }\n" +
                "int styleable LimitedSizeLinearLayout_max_height 1\n" +
                "int styleable LimitedSizeLinearLayout_max_width 0\n" +
                "int xml authenticator 0x7f040000\n";
        File file = mTemporaryFolder.newFile();
        Files.write(r, file, Charsets.UTF_8);

        SymbolTable table = SymbolIo.load(file);

        SymbolTable expected = new SymbolTable();
        expected.add(
                new Symbol(
                        "styleable",
                        "LimitedSizeLinearLayout",
                        "int[]",
                        "{ 0x7f010000, 0x7f010001 }"));
        expected.add(new Symbol("styleable", "LimitedSizeLinearLayout_max_height", "int", "1"));
        expected.add(new Symbol("styleable", "LimitedSizeLinearLayout_max_width", "int", "0"));
        expected.add(new Symbol("xml", "authenticator", "int", "0x7f040000"));
        assertEquals(table, expected);
    }
}
