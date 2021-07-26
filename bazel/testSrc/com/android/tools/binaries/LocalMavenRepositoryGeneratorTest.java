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

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalMavenRepositoryGeneratorTest {

    @Test
    public void testGenerator() throws Exception {
        Path repoPath = Paths.get("tools/base/bazel/test/local_maven_repository_generator/fake_repository");
        List<String> coords = Arrays.asList("com.google.example:a:1", "com.google.example:b:1");
        String outputBuildFile = "generated.BUILD";
        LocalMavenRepositoryGenerator generator =
                new LocalMavenRepositoryGenerator(repoPath, outputBuildFile, coords, false);
        generator.run();

        Path golden = repoPath.resolveSibling("BUILD.golden");
        Path generated = Paths.get(outputBuildFile);

        assertTrue(generated.toFile().exists());
        assertEquals("The files differ!",
                Files.readString(golden),
                Files.readString(generated));
    }
}
