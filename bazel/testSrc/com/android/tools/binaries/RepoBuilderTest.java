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

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RepoBuilderTest {

    private static final String POM_TEMPLATE =
            "<project>"
                    + "  <modelVersion>4.0.0</modelVersion>"
                    + "  <groupId>%s</groupId>"
                    + "  <artifactId>%s</artifactId>"
                    + "  <version>%s</version>"
                    + "</project>";

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private RepoBuilder builder = null;
    private List<String> artifacts = null;
    private Path destination = null;

    private void addStubPom(String shortPath, String groupId, String artifactId, String version)
            throws IOException {
        Path destinationPath = Paths.get("bazel-out", shortPath);
        Path fullPath = temp.getRoot().toPath().resolve(destinationPath);
        Files.createDirectories(fullPath.getParent());
        String pom = String.format(POM_TEMPLATE, groupId, artifactId, version);
        Files.write(fullPath, pom.getBytes());
        String spec = destinationPath.toString() + "," + shortPath;
        artifacts.add(spec);
    }

    private void addStubArtifact(String shortPath, String classifier) throws IOException {
        Path destinationPath = Paths.get("bazel-out", shortPath);
        Path fullPath = temp.getRoot().toPath().resolve(destinationPath);
        Files.createDirectories(fullPath.getParent());
        Files.write(fullPath, "contents".getBytes());
        String spec = destinationPath.toString() + "," + shortPath;
        if (classifier == null) {
            artifacts.add(spec);
        } else {
            artifacts.add(spec + "," + classifier);
        }
    }

    private void assertExists(String path) {
        assertThat(Files.exists(destination.resolve(path))).isTrue();
    }

    @Before
    public void setUp() throws IOException {
        builder = new RepoBuilder(temp.getRoot());
        artifacts = new ArrayList<>();
        destination = temp.newFolder().toPath();
    }

    @Test
    public void resolve() throws Exception {
        addStubPom("1.pom", "group1", "artifact1", "1.0");
        addStubArtifact("1.jar", null);
        addStubPom("2.pom", "group2", "artifact2", "2.0");
        addStubArtifact("2.jar", null);

        Map<String, String> paths = new HashMap<>();
        Map<String, String> shortPaths = new HashMap<>();
        builder.resolve(artifacts, paths, shortPaths);

        assertThat(paths.get("group1/artifact1/1.0/artifact1-1.0.pom"))
                .isEqualTo("bazel-out/1.pom");
        assertThat(paths.get("group1/artifact1/1.0/artifact1-1.0.jar"))
                .isEqualTo("bazel-out/1.jar");
        assertThat(paths.get("group2/artifact2/2.0/artifact2-2.0.pom"))
                .isEqualTo("bazel-out/2.pom");
        assertThat(paths.get("group2/artifact2/2.0/artifact2-2.0.jar"))
                .isEqualTo("bazel-out/2.jar");

        assertThat(shortPaths.get("group1/artifact1/1.0/artifact1-1.0.pom")).isEqualTo("1.pom");
        assertThat(shortPaths.get("group1/artifact1/1.0/artifact1-1.0.jar")).isEqualTo("1.jar");
        assertThat(shortPaths.get("group2/artifact2/2.0/artifact2-2.0.pom")).isEqualTo("2.pom");
        assertThat(shortPaths.get("group2/artifact2/2.0/artifact2-2.0.jar")).isEqualTo("2.jar");
    }

    @Test
    public void resolve_withClassifierJars() throws Exception {
        addStubPom("1.pom", "group1", "artifact1", "1.0");
        addStubArtifact("1.jar", "classifier1");
        addStubPom("2.pom", "group2", "artifact2", "2.0");
        addStubArtifact("2.jar", "classifier2");

        Map<String, String> paths = new HashMap<>();
        Map<String, String> shortPaths = new HashMap<>();
        builder.resolve(artifacts, paths, shortPaths);

        assertThat(paths.get("group1/artifact1/1.0/artifact1-1.0.pom"))
                .isEqualTo("bazel-out/1.pom");
        assertThat(paths.get("group1/artifact1/1.0/artifact1-1.0-classifier1.jar"))
                .isEqualTo("bazel-out/1.jar");
        assertThat(paths.get("group2/artifact2/2.0/artifact2-2.0.pom"))
                .isEqualTo("bazel-out/2.pom");
        assertThat(paths.get("group2/artifact2/2.0/artifact2-2.0-classifier2.jar"))
                .isEqualTo("bazel-out/2.jar");

        assertThat(shortPaths.get("group1/artifact1/1.0/artifact1-1.0.pom")).isEqualTo("1.pom");
        assertThat(shortPaths.get("group1/artifact1/1.0/artifact1-1.0-classifier1.jar"))
                .isEqualTo("1.jar");
        assertThat(shortPaths.get("group2/artifact2/2.0/artifact2-2.0.pom")).isEqualTo("2.pom");
        assertThat(shortPaths.get("group2/artifact2/2.0/artifact2-2.0-classifier2.jar"))
                .isEqualTo("2.jar");
    }
}
