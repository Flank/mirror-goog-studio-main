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

package com.android.tools.bazel.repolinker;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RepoLinkerTest {

    private static final String POM_TEMPLATE =
            "<project>"
                    + "  <modelVersion>4.0.0</modelVersion>"
                    + "  <groupId>%s</groupId>"
                    + "  <artifactId>%s</artifactId>"
                    + "  <version>%s</version>"
                    + "</project>";

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private RepoLinker linker = null;
    private List<String> artifacts = null;
    private Path destination = null;

    private void addStubPom(File destination, String groupId, String artifactId, String version)
            throws IOException {
        Path destinationPath = destination.toPath();
        String pom = String.format(POM_TEMPLATE, groupId, artifactId, version);
        Files.write(destinationPath, pom.getBytes());
        artifacts.add(destinationPath.toString());
    }

    private void addStubArtifact(File destination, String classifier) throws IOException {
        Path destinationPath = destination.toPath();
        Files.write(destinationPath, "contents".getBytes());
        if (classifier == null) {
            artifacts.add(destinationPath.toString());
        } else {
            artifacts.add(destinationPath.toString() + "," + classifier);
        }
    }

    private void assertExists(String path) {
        assertThat(Files.exists(destination.resolve(path))).isTrue();
    }

    @Before
    public void setUp() throws IOException {
        linker = new RepoLinker();
        artifacts = new ArrayList<>();
        destination = temp.newFolder().toPath();
    }

    @Test
    public void link() throws Exception {
        addStubPom(temp.newFile("1.pom"), "group1", "artifact1", "1.0");
        addStubArtifact(temp.newFile("1.jar"), null);
        addStubPom(temp.newFile("2.pom"), "group2", "artifact2", "2.0");
        addStubArtifact(temp.newFile("2.jar"), null);

        linker.link(destination, artifacts);

        assertExists("group1/artifact1/1.0/artifact1-1.0.pom");
        assertExists("group1/artifact1/1.0/artifact1-1.0.jar");
        assertExists("group2/artifact2/2.0/artifact2-2.0.pom");
        assertExists("group2/artifact2/2.0/artifact2-2.0.jar");
    }

    @Test
    public void link_overwritesExisting() throws Exception {
        // Write a file to the destination.
        Path output = destination.resolve("group1/artifact1/1.0/artifact1-1.0.jar");
        Files.createDirectories(output.getParent());
        Files.write(output, "should_be_overwritten_by_contents".getBytes());
        assertExists("group1/artifact1/1.0/artifact1-1.0.jar");

        addStubPom(temp.newFile("1.pom"), "group1", "artifact1", "1.0");
        addStubArtifact(temp.newFile("1.jar"), null);
        linker.link(destination, artifacts);
        assertExists("group1/artifact1/1.0/artifact1-1.0.pom");
        assertExists("group1/artifact1/1.0/artifact1-1.0.jar");

        // Verify that the contents have been overwritten.
        List<String> contents = Files.readAllLines(output);
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0)).matches("contents");
    }

    @Test
    public void link_withClassifierJars() throws Exception {
        addStubPom(temp.newFile("1.pom"), "group1", "artifact1", "1.0");
        addStubArtifact(temp.newFile("1.jar"), "classifier1");
        addStubPom(temp.newFile("2.pom"), "group2", "artifact2", "2.0");
        addStubArtifact(temp.newFile("2.jar"), "classifier2");

        linker.link(destination, artifacts);

        assertExists("group1/artifact1/1.0/artifact1-1.0.pom");
        assertExists("group1/artifact1/1.0/artifact1-1.0-classifier1.jar");
        assertExists("group2/artifact2/2.0/artifact2-2.0.pom");
        assertExists("group2/artifact2/2.0/artifact2-2.0-classifier2.jar");
    }

    @Test
    public void link_withInjectedPluginVersion() throws Exception {
        // Create a new temporary jar file.
        File jar = temp.newFile("1.jar");
        try (OutputStream outputStream = Files.newOutputStream(jar.toPath())) {
            // Create a manifest for the jar file.
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

            // Write the jar file.
            JarOutputStream jarStream = new JarOutputStream(outputStream, manifest);
            jarStream.close();
        }
        addStubPom(temp.newFile("1.pom"), "com.android.tools.build", "gradle", "1.0");
        artifacts.add(jar.toPath().toAbsolutePath().toString());

        // Link repository.
        linker.link(destination, artifacts);

        assertExists("com/android/tools/build/gradle/1.0/gradle-1.0.pom");
        assertExists("com/android/tools/build/gradle/1.0/gradle-1.0.jar");

        // Verify the attributes of the jar file.
        Path outputPath = destination.resolve("com/android/tools/build/gradle/1.0/gradle-1.0.jar");
        try (JarFile outputJar = new JarFile(outputPath.toString())) {
            Manifest manifest = outputJar.getManifest();
            assertThat(manifest.getMainAttributes().getValue("Plugin-Version")).isEqualTo("1.0");
        }

        // Verify that the linked jar is a file rather than a link.
        assertThat(Files.isSymbolicLink(outputPath)).isFalse();
    }
}
