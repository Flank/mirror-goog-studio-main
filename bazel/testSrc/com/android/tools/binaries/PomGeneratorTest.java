/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.binaries;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class PomGeneratorTest {

    @Test
    public void testGenerator() throws Exception {
        PomGenerator generator = new PomGenerator();

        File outputPom = new File("output.pom");

        Path testDataDir = Paths.get("tools/base/bazel/test/pom_generator/");
        List<File> pomDependencies = new ArrayList<File>() {{
            add(testDataDir.resolve("groovy-all-3.0.7.pom").toFile());
            add(testDataDir.resolve("guava-30.1-jre.pom").toFile());
        }};

        generator.generatePom(
                null,
                outputPom,
                pomDependencies,
                "group",
                "artifact",
                "version",
                false
        );

        String goldenFileContents = Files.readString(testDataDir.resolve("golden.pom"));
        String generatedFileContents = Files.readString(outputPom.toPath());
        if (!goldenFileContents.equals(generatedFileContents)) {
            System.err.println("=== Start of generated file contents ===");
            System.err.println(generatedFileContents);
            System.err.println("=== End of generated file contents ===");
            fail("Generated file does not match golden file.");
        }
    }
}
