/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.utils;

import static java.nio.file.StandardOpenOption.CREATE;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Lists;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;

public class CombineNoticesTest {

    private FileSystem fileSystem;
    private Path input1;
    private Path input2;
    private Path input2a;
    private Path output;

    String licenseText1 =
            "This is the text of license 1.\n"
                    + "Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n"
                    + "Integer tempus elit ut molestie hendrerit.\n";

    String licenseText2 =
            "This is the text of license 2.\n"
                    + "Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n"
                    + "Pellentesque in luctus erat.\n";

    @Before
    public void setUp() throws Exception {
        Configuration config = Configuration.unix();
        config = config.toBuilder().setWorkingDirectory("/").setAttributeViews("posix").build();
        fileSystem = Jimfs.newFileSystem(config);
        input1 = fileSystem.getPath("/inDir1/foo.NOTICE");
        Files.createDirectories(input1.getParent());
        Files.write(input1, licenseText1.getBytes(), CREATE);
        input2 = fileSystem.getPath("/inDir2/bar.NOTICE");
        Files.createDirectories(input2.getParent());
        Files.write(input2, licenseText2.getBytes(), CREATE);
        input2a = fileSystem.getPath("/inDir2a/baz.NOTICE");
        Files.createDirectories(input2a.getParent());
        Files.write(input2a, licenseText2.getBytes(), CREATE);
        output = fileSystem.getPath("/outDir/subOutDir/output");
        Files.createDirectories(output.getParent());
    }

    @Test
    public void nonExistingOutputDir() {
        Path nonExistingOutput = fileSystem.getPath("/dummy/foo");
        try {
            CombineNotices.run(nonExistingOutput, Lists.newArrayList(input1, input2));
            fail("Expected exception");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains(nonExistingOutput.getParent().toString()));
        }
    }

    @Test
    public void nonExistingInputFile() {
        Path nonExistingInput = fileSystem.getPath("/dummy/foo");
        try {
            CombineNotices.run(output, Lists.newArrayList(input1, nonExistingInput, input2));
            fail("Expected exception");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains(nonExistingInput.toString()));
        }
    }

    @Test
    public void combineNotices() throws Exception {
        CombineNotices.run(output, Lists.newArrayList(input2, input1, input2a));
        String outputString = new String(Files.readAllBytes(output));
        assertTrue(
                outputString.contains(
                        "============================================================\n"
                                + "Notices for file(s):\n"
                                + "bar.NOTICE\n"
                                + "baz.NOTICE\n"
                                + "------------------------------------------------------------\n"
                                + licenseText2));
        assertTrue(
                outputString.contains(
                        "============================================================\n"
                                + "Notices for file(s):\n"
                                + "foo.NOTICE\n"
                                + "------------------------------------------------------------\n"
                                + licenseText1));
    }
}
