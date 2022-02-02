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

import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MavenConsistencyTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testConsistency() throws Exception {
        List<String> artifacts = List.of(System.getenv("MAVEN_ARTIFACTS").split(";"));
        List<String> data = List.of(System.getenv("MAVEN_DATA").split(";"));
        String repoPath = System.getenv("MAVEN_REPO_PATH");
        List<String> remoteRepoKeys = List.of(System.getenv("MAVEN_REMOTE_REPO_KEYS").split(";"));
        List<String> remoteRepoValues = List.of(System.getenv("MAVEN_REMOTE_REPO_VALUES").split(";"));
        Map<String, String> remoteRepos = Streams.zip(remoteRepoKeys.stream(), remoteRepoValues.stream(), Maps::immutableEntry)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        File tempOutputBuildFile = temporaryFolder.newFile();
        LocalMavenRepositoryGenerator generator =
                new LocalMavenRepositoryGenerator(
                        Paths.get(repoPath),
                        tempOutputBuildFile.toPath().toString(),
                        artifacts,
                        data,
                        false,
                        remoteRepos,
                        true);
        generator.run();

        String checkedInOutputFile = System.getenv("MAVEN_OUTPUT_FILE");
        String checkedInFileContents = Files.readString(Paths.get(checkedInOutputFile));
        String generatedFileContents = Files.readString(tempOutputBuildFile.toPath());
        Assert.assertEquals(generatedFileContents, checkedInFileContents);
    }
}
