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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LocalMavenRepositoryGeneratorTest {

    @Test
    public void testGenerator() throws Exception {
        Path repoPath = Paths.get("tools/base/bazel/test/local_maven_repository_generator/fake_repository");
        List<String> coords = Arrays.asList(
            "com.google.example:a:1",
            "com.google.example:b:1",
            "com.google.example:h:pom:1",
            "com.google.example:j:jar:linux:1",
            "com.google.example:k:1",
            "com.google.example:l:1",
            "com.google.example:p:1"
        );
        String outputBuildFile = "generated.BUILD";
        LocalMavenRepositoryGenerator generator =
                new LocalMavenRepositoryGenerator(
                        repoPath, outputBuildFile, coords, true, false, Collections.emptyMap(), false);
        generator.run();

        Path golden = repoPath.resolveSibling("BUILD.golden");
        Path generated = Paths.get(outputBuildFile);

        assertTrue(generated.toFile().exists());
        String goldenFileContents = Files.readString(golden);
        String generatedFileContents = Files.readString(generated);
        if (!goldenFileContents.equals(generatedFileContents)) {
            System.err.println("=== Start generated file contents ===");
            System.err.println(generatedFileContents);
            System.err.println("=== End generated file contents ===");
            Files.copy(generated, TestUtils.getTestOutputDir().resolve(outputBuildFile));
        }
        assertEquals("The files differ!", goldenFileContents, generatedFileContents);
    }

    @Test
    public void testGeneratorNoResolve() throws Exception {
        Path repoPath =
                Paths.get("tools/base/bazel/test/local_maven_repository_generator/fake_repository");
        List<String> coords =
                Arrays.asList(
                        "com.google.example:a:1",
                        "com.google.example:b:1",
                        "com.google.example:h:pom:1",
                        "com.google.example:j:jar:linux:1");
        String outputBuildFile = "generated.noresolve.BUILD";
        LocalMavenRepositoryGenerator generator =
                new LocalMavenRepositoryGenerator(
                        repoPath, outputBuildFile, coords, false, false, Collections.emptyMap(), false);
        generator.run();

        Path golden = repoPath.resolveSibling("BUILD.noresolve.golden");
        Path generated = Paths.get(outputBuildFile);

        assertTrue(generated.toFile().exists());
        String goldenFileContents = Files.readString(golden);
        String generatedFileContents = Files.readString(generated);
        if (!goldenFileContents.equals(generatedFileContents)) {
            System.err.println("=== Start generated file contents ===");
            System.err.println(generatedFileContents);
            System.err.println("=== End generated file contents ===");
            Files.copy(generated, TestUtils.getTestOutputDir().resolve(outputBuildFile));
        }
        assertEquals("The files differ!", goldenFileContents, generatedFileContents);
    }
}
