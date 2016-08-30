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

package com.android.tools.maven;

import com.google.common.collect.Lists;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command line tool to download a new Maven artifact to prebuilts, together with all the transitive
 * dependencies.
 *
 * <p>It also creates {@code java_import} Bazel rules for newly created jars.
 */
public class AddDependency {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
        }

        Path repoDirectory = Paths.get(args[0]);
        if (!repoDirectory.getFileName().toString().equals("repository")
                || !repoDirectory.getParent().getFileName().toString().equals("m2")) {
            usage();
        }

        List<String> artifacts = Lists.newArrayList(args);
        artifacts.remove(0); // First argument is the path to m2/repository

        new AddDependency(repoDirectory).run(artifacts);
    }

    private final RepositorySystem mRepositorySystem;
    private final RepositorySystemSession mRepositorySystemSession;

    private AddDependency(Path localRepo) {
        mRepositorySystem = AetherUtils.getRepositorySystem();
        mRepositorySystemSession =
                AetherUtils.getRepositorySystemSession(mRepositorySystem, localRepo);
    }

    private void run(List<String> coordinates) throws DependencyResolutionException, IOException {
        CollectRequest request = new CollectRequest();
        request.setDependencies(
                coordinates
                        .stream()
                        .map(DefaultArtifact::new)
                        .map(artifact -> new Dependency(artifact, JavaScopes.COMPILE))
                        .collect(Collectors.toList()));

        request.setRepositories(AetherUtils.REPOSITORIES);

        DependencyResult result =
                mRepositorySystem.resolveDependencies(
                        mRepositorySystemSession, new DependencyRequest(request, null));

        for (ArtifactResult artifactResult : result.getArtifactResults()) {
            JavaImportGenerator.generateJavaImportRule(artifactResult.getArtifact());
        }
    }

    private static void usage() {
        System.err.println(
                "Usage: add_dependency path/to/m2/repository com.example:foo:1.0 com.example:bar:2.0 ...");
        System.exit(1);
    }
}
