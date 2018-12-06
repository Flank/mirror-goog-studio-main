/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.testutils.TestUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMultimap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

/** Test to try and ensure that third_party_build_generator has run. */
public class ThirdPartyBuildConsistencyTest {

    private static String ERROR_MESSAGE =
            "tools/base/third_party/BUILD is inconsistent\n"
                    + "\n"
                    + "----------------------------------------------------------------------------------\n"
                    + "Please execute bazel run //tools/base/bazel:third_party_build_generator\n"
                    + "to update tools/base/third_party/BUILD from tools/buildSrc/dependencies.properties\n"
                    + "----------------------------------------------------------------------------------\n";

    /**
     * We expect every dependency in `tools/buildSrc/base/dependencies.properties` to also be in
     * `tools/base/third_party/BUILD`
     *
     * <p>There will be extra (transitive only) artifacts in `tools/base/third_party/BUILD`, but
     * never any divergent versions.
     */
    @Test
    public void ensureThirdPartyGeneratorRun() throws IOException {
        Path buildFile = TestUtils.getWorkspaceFile("tools/base/third_party/BUILD").toPath();
        Path localRepo =
                TestUtils.getWorkspaceFile("prebuilts/tools/common/m2/repository").toPath();
        ThirdPartyBuildGenerator thirdPartyBuildGenerator =
                new ThirdPartyBuildGenerator(buildFile, localRepo);
        String buildContents = Joiner.on("\n").join(Files.readAllLines(buildFile));

        Stream<String> depsFromDependenciesProperties =
                readDependenciesProperties()
                        .map(DefaultArtifact::new)
                        .map(thirdPartyBuildGenerator::getJarTarget);

        Set<String> missingDeps =
                depsFromDependenciesProperties
                        .filter(s -> !buildContents.contains(s))
                        .collect(Collectors.toSet());

        if (!missingDeps.isEmpty()) {
            throw new AssertionError(
                    ERROR_MESSAGE
                            + "\n"
                            + "tools/buildSrc/base/dependencies.properties specified\n"
                            + "    * "
                            + Joiner.on("\n    * ").join(missingDeps)
                            + "\n"
                            + "which is inconsistent with tools/base/third_party/BUILD\n\n");
        }
    }

    private static Stream<String> readDependenciesProperties() throws IOException {
        Path dependenciesProperties =
                TestUtils.getWorkspaceFile("tools/buildSrc/base/dependencies.properties").toPath();
        Properties dependencies = new Properties();
        try (InputStream inputStream = Files.newInputStream(dependenciesProperties)) {
            dependencies.load(inputStream);
        }
        return dependencies.stringPropertyNames().stream().map(dependencies::getProperty);
    }

    @Test
    public void checkThirdPartyIsSelfConsistent() throws IOException {
        Path buildFile = TestUtils.getWorkspaceFile("tools/base/third_party/BUILD").toPath();

        Pattern pattern =
                Pattern.compile(
                        "\"//prebuilts/tools/common/m2/repository/"
                                + "(?<artifact>[^:]+)/(?<version>[^:/]+):[^:]+\"");
        ImmutableMultimap.Builder<String, String> artifactsBuilder = ImmutableMultimap.builder();
        try (Stream<String> lines = Files.lines(buildFile)) {
            lines.forEach(
                    line -> {
                        Matcher matcher = pattern.matcher(line);
                        while (matcher.find()) {
                            String artifact = matcher.group("artifact");
                            String version = matcher.group("version");
                            artifactsBuilder.put(artifact, version);
                        }
                    });
        }
        ImmutableMultimap<String, String> artifactToVersion = artifactsBuilder.build();

        if (artifactToVersion.isEmpty()) {
            throw new IllegalStateException(
                    "Expected some artifacts in tools/base/third_party/BUILD");
        }

        for (String artifact : artifactToVersion.keySet()) {
            if (artifactToVersion.get(artifact).size() > 1) {
                throw new AssertionError(
                        ERROR_MESSAGE
                                + "\n"
                                + "Artifacts in tools/base/third_party/BUILD do not have consistent versions");
            }
        }
    }
}
